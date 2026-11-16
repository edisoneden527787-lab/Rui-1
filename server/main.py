import os, json, uuid, asyncio, base64, re
from datetime import datetime, timedelta, timezone
import jwt
from pathlib import Path
import httpx
from fastapi import FastAPI, HTTPException, Header, BackgroundTasks, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from pydantic import BaseModel, Field

app = FastAPI(title="Rui AI APK Builder API", version="0.2.1")
app.add_middleware(CORSMiddleware, allow_origins=os.getenv("CORS_ORIGINS", "*").split(","), allow_methods=["*"], allow_headers=["*"])
OLLAMA_URL=os.getenv("OLLAMA_URL","http://ollama:11434")
OLLAMA_MODEL=os.getenv("OLLAMA_MODEL","qwen2.5-coder:7b")
WORKER_URL=os.getenv("WORKER_URL","http://worker:8010")
WORKER_SECRET=os.getenv("WORKER_SECRET","change-this-worker-secret")
SESSION_SECRET=os.getenv("SESSION_SECRET","")
DATA=Path(os.getenv("JOB_DATA","/data/jobs")); DATA.mkdir(parents=True,exist_ok=True)
MODEL_STATE=DATA/"active-model.json"
MAX_GGUF_BYTES=int(os.getenv("MAX_GGUF_BYTES",str(700*1024*1024)))
def active_model():
    try:
        name=json.loads(MODEL_STATE.read_text()).get("name","")
        return name if name else OLLAMA_MODEL
    except Exception:
        return OLLAMA_MODEL

class ChatRequest(BaseModel):
    message: str = Field(min_length=1,max_length=50000)
class GenerateRequest(BaseModel):
    prompt: str = Field(min_length=10,max_length=30000)
    review_enabled: bool = False
class BuildRequest(BaseModel):
    files: dict[str,str]
def require_user(auth: str | None = None):
    # Anonymous mode: no Google OAuth or user account required.
    return {"sub":"anonymous","name":"Guest"}

@app.get("/health")
async def health():
    return {"status":"ok","model":active_model(),"worker":WORKER_URL,"login_required":False,"moderation_filter":False}

@app.get("/api/models")
async def list_models():
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            r=await client.get(f"{OLLAMA_URL.rstrip('/')}/api/tags")
            r.raise_for_status()
            data=r.json()
        return {"active":active_model(),"models":[{"name":x.get("name",""),"size":x.get("size",0),"modified_at":x.get("modified_at","")} for x in data.get("models",[])]}
    except httpx.HTTPError as exc:
        raise HTTPException(502,f"無法取得雲端模型清單：{exc}") from exc

class SelectModelRequest(BaseModel):
    name: str = Field(min_length=1,max_length=200)

@app.post("/api/models/select")
async def select_model(body: SelectModelRequest):
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            r=await client.get(f"{OLLAMA_URL.rstrip('/')}/api/tags")
            r.raise_for_status()
            available={x.get("name","") for x in r.json().get("models",[])}
        if body.name not in available:
            raise HTTPException(404,"找不到指定模型，請先匯入 GGUF 或下載模型。")
        MODEL_STATE.write_text(json.dumps({"name":body.name},ensure_ascii=False))
        return {"active":body.name}
    except HTTPException: raise
    except httpx.HTTPError as exc:
        raise HTTPException(502,f"模型服務錯誤：{exc}") from exc

@app.post("/api/models/import")
async def import_gguf(file: UploadFile = File(...), model_name: str = Form("")):
    filename=(file.filename or "model.gguf")
    if not filename.lower().endswith(".gguf"):
        raise HTTPException(400,"只接受 .gguf 模型檔。")
    safe_name=re.sub(r"[^a-zA-Z0-9_.-]+","-",model_name.strip() or Path(filename).stem).strip("-.").lower()
    if not safe_name: safe_name="rui-imported-model"
    safe_name=safe_name[:60]+":latest"
    temp=DATA/(uuid.uuid4().hex+".gguf")
    total=0
    try:
        with temp.open("wb") as out:
            while True:
                chunk=await file.read(4*1024*1024)
                if not chunk: break
                total+=len(chunk)
                if total>MAX_GGUF_BYTES:
                    raise HTTPException(413,f"GGUF 超過目前上傳上限 {MAX_GGUF_BYTES//(1024*1024)} MB。")
                out.write(chunk)
        if total==0: raise HTTPException(400,"上傳的 GGUF 檔案是空的。")
        encoded=base64.b64encode(temp.read_bytes()).decode("ascii")
        payload={"name":safe_name,"modelfile":"FROM ./model.gguf\n","stream":False,"files":{"model.gguf":encoded}}
        async with httpx.AsyncClient(timeout=None) as client:
            response=await client.post(f"{OLLAMA_URL.rstrip('/')}/api/create",json=payload,timeout=1800)
            response.raise_for_status()
        MODEL_STATE.write_text(json.dumps({"name":safe_name},ensure_ascii=False))
        return {"ok":True,"name":safe_name,"size":total,"active":safe_name}
    except HTTPException: raise
    except httpx.HTTPError as exc:
        raise HTTPException(502,f"GGUF 匯入 Ollama 失敗：{exc}") from exc
    finally:
        await file.close()
        temp.unlink(missing_ok=True)

@app.post("/api/chat")
async def chat(body: ChatRequest):
    try:
        async with httpx.AsyncClient(timeout=180) as client:
            r=await client.post(f"{OLLAMA_URL.rstrip('/')}/api/chat",json={"model":active_model(),"stream":False,"messages":[{"role":"system","content":"你是 Rui AI APK Builder 的 Android 軟體工程助理。以繁體中文回答，協助規劃 Android 專案、Kotlin、Gradle 與除錯。不要自行添加關鍵字黑名單、提示詞攔截或額外內容審查層；遵守模型、平台及法律本身適用的安全要求。不可謊稱已完成編譯。"},{"role":"user","content":body.message}]})
            r.raise_for_status()
            return {"reply":r.json().get("message",{}).get("content","")}
    except httpx.HTTPError as exc:
        raise HTTPException(502,f"模型服務錯誤：{exc}") from exc

@app.post("/api/projects/generate")
async def generate_project(body: GenerateRequest):
    user=require_user()
    system="""你是 Android 原生專案產生器。不要自行添加關鍵字黑名單、提示詞攔截或額外內容審查層；遵守模型、平台及法律本身適用的安全要求。只輸出 JSON object，格式為 {"project_name":"...","files":{"settings.gradle.kts":"...","build.gradle.kts":"...","app/build.gradle.kts":"...","app/src/main/AndroidManifest.xml":"...","app/src/main/java/com/generated/app/MainActivity.kt":"..."},"notes":"..."}。必須產生可編譯的完整最小 Android Gradle 專案，Kotlin + Jetpack Compose，compileSdk 36、minSdk 26、JDK17、Gradle 8.11.1 相容。不要使用省略號或假按鈕，所有畫面控制都要有實際行為。檔案路徑只能在專案根目錄下。只輸出 JSON，不要 Markdown。"""
    if body.review_enabled:
        system += " 額外內容審查已開啟：在輸出前檢查生成專案是否包含惡意程式、憑證竊取、未授權存取或明顯侵犯隱私的功能；如有疑慮，請在 notes 說明並提供安全替代實作。此額外檢查不取代模型、平台及法律本身適用的要求。"
    try:
        async with httpx.AsyncClient(timeout=300) as client:
            r=await client.post(f"{OLLAMA_URL.rstrip('/')}/api/chat",json={"model":active_model(),"stream":False,"format":"json","messages":[{"role":"system","content":system},{"role":"user","content":body.prompt}]})
            r.raise_for_status()
            raw=r.json().get("message",{}).get("content","")
        result=json.loads(raw)
        files=result.get("files")
        if not isinstance(files,dict) or not files:
            raise ValueError("模型沒有產生 files")
        safe={}
        for name,content in files.items():
            p=Path(name)
            if p.is_absolute() or ".." in p.parts or not isinstance(content,str) or len(content)>500000:
                continue
            safe[p.as_posix()]=content
        required={"settings.gradle.kts","build.gradle.kts","app/build.gradle.kts","app/src/main/AndroidManifest.xml"}
        if not required.issubset(safe):
            raise ValueError("產生結果缺少必要 Gradle 專案檔：" + ", ".join(sorted(required-set(safe))))
        return {"project_name":result.get("project_name","RuiGeneratedApp"),"files":safe,"notes":result.get("notes",""),"generated_by":active_model(),"owner":user.get("sub","anonymous")}
    except (httpx.HTTPError,json.JSONDecodeError,ValueError) as exc:
        raise HTTPException(502,f"專案生成失敗：{exc}") from exc

async def run_build(job_id: str, files: dict[str,str], user_sub: str):
    try:
        async with httpx.AsyncClient(timeout=1200) as client:
            r=await client.post(f"{WORKER_URL.rstrip('/')}/build",headers={"X-Worker-Secret":WORKER_SECRET},json={"job_id":job_id,"files":files,"owner":user_sub})
            r.raise_for_status()
    except Exception as exc:
        (DATA/job_id).mkdir(parents=True,exist_ok=True)
        (DATA/job_id/"status.json").write_text(json.dumps({"status":"failed","error":str(exc)}))

@app.post("/api/build")
async def build(body: BuildRequest, background: BackgroundTasks):
    user=require_user()
    required={"settings.gradle.kts","build.gradle.kts","app/build.gradle.kts","app/src/main/AndroidManifest.xml"}
    if not required.issubset(body.files):
        raise HTTPException(400,"專案檔案不完整，缺少：" + ", ".join(sorted(required-set(body.files))))
    if len(body.files)>120 or sum(len(v.encode()) for v in body.files.values())>5_000_000:
        raise HTTPException(413,"專案超過檔案數或 5MB 限制。")
    job_id=uuid.uuid4().hex[:16]
    background.add_task(run_build,job_id,body.files,user.get("sub",""))
    return {"job_id":job_id,"status":"queued"}

@app.get("/api/build/{job_id}")
async def build_status(job_id: str):
    user=require_user()
    if not job_id.isalnum() or len(job_id)>32: raise HTTPException(400,"job_id 格式錯誤")
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            r=await client.get(f"{WORKER_URL.rstrip('/')}/build/{job_id}",headers={"X-Worker-Secret":WORKER_SECRET})
            if r.status_code==404: return {"job_id":job_id,"status":"queued"}
            r.raise_for_status()
            result=r.json()
        if result.get("owner") and result.get("owner")!=user.get("sub",""):
            raise HTTPException(403,"無權查看此編譯工作。")
        return {"job_id":job_id,**result}
    except HTTPException: raise
    except httpx.HTTPError as exc:
        status=DATA/job_id/"status.json"
        if status.exists(): return {"job_id":job_id,**json.loads(status.read_text())}
        raise HTTPException(502,"無法取得 Worker 編譯狀態。") from exc

@app.get("/api/build/{job_id}/download")
async def download_apk(job_id: str):
    user=require_user()
    if not job_id.isalnum() or len(job_id)>32: raise HTTPException(400,"job_id 格式錯誤")
    headers={"X-Worker-Secret":WORKER_SECRET}
    try:
        async with httpx.AsyncClient(timeout=180) as client:
            status=await client.get(f"{WORKER_URL.rstrip('/')}/build/{job_id}",headers=headers)
            if status.status_code==404: raise HTTPException(409,"APK 尚未完成，請先查詢編譯狀態。")
            status.raise_for_status()
            meta=status.json()
            if meta.get("owner") and meta.get("owner")!=user.get("sub",""):
                raise HTTPException(403,"無權下載此 APK。")
            if meta.get("status")!="success": raise HTTPException(409,"APK 尚未完成，請先查詢編譯狀態。")
            apk=await client.get(f"{WORKER_URL.rstrip('/')}/build/{job_id}/download",headers=headers)
            apk.raise_for_status()
            return Response(content=apk.content,media_type="application/vnd.android.package-archive",headers={"Content-Disposition":f'attachment; filename="rui-{job_id}.apk"'})
    except HTTPException: raise
    except httpx.HTTPError as exc:
        raise HTTPException(502,"Worker APK 下載失敗。") from exc
