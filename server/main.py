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
MODEL_PULL_STATE=DATA/"model-pull.json"
# Keep the built-in catalog within the existing 500 MB model volume.
# Only expose the lightweight model; larger models require user-provided storage.
BUILTIN_MODELS={
    "qwen2.5-coder:0.5b":"Qwen2.5 Coder 0.5B · 輕量程式模型（單一內建模型）"
}
def save_pull_state(data):
    MODEL_PULL_STATE.write_text(json.dumps(data,ensure_ascii=False))
async def pull_builtin_model(name: str):
    save_pull_state({"status":"downloading","name":name,"message":"正在連線模型倉庫","completed":0,"total":0})
    try:
        async with httpx.AsyncClient(timeout=None) as client:
            async with client.stream("POST",f"{OLLAMA_URL.rstrip('/')}/api/pull",json={"name":name,"stream":True},timeout=None) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line: continue
                    try: item=json.loads(line)
                    except json.JSONDecodeError: continue
                    save_pull_state({"status":"downloading","name":name,"message":item.get("status","下載模型中"),"completed":item.get("completed",0),"total":item.get("total",0)})
        MODEL_STATE.write_text(json.dumps({"name":name},ensure_ascii=False))
        save_pull_state({"status":"success","name":name,"message":"模型下載並載入完成","completed":1,"total":1})
    except Exception as exc:
        save_pull_state({"status":"failed","name":name,"message":str(exc),"completed":0,"total":0})

@app.get("/api/models/catalog")
async def model_catalog():
    installed=set()
    try:
        async with httpx.AsyncClient(timeout=20) as client:
            response=await client.get(f"{OLLAMA_URL.rstrip('/')}/api/tags")
            response.raise_for_status()
            installed={item.get("name","") for item in response.json().get("models",[])}
    except httpx.HTTPError as exc:
        raise HTTPException(502,f"無法讀取雲端模型：{exc}") from exc
    return {"models":[{"name":name,"description":description,"installed":name in installed} for name,description in BUILTIN_MODELS.items()],"active":active_model(),"storage_limit_mb":500,"storage_policy":"single-lightweight-model"}

@app.post("/api/models/pull/{name:path}")
async def start_model_pull(name: str, background: BackgroundTasks):
    if name not in BUILTIN_MODELS:
        raise HTTPException(400,"不在內建模型清單中。")
    state={}
    try: state=json.loads(MODEL_PULL_STATE.read_text())
    except Exception: pass
    if state.get("status")=="downloading":
        raise HTTPException(409,"目前已有模型正在下載。")
    # Reuse an already-installed model instead of downloading a duplicate.
    try:
        async with httpx.AsyncClient(timeout=20) as client:
            response=await client.get(f"{OLLAMA_URL.rstrip('/')}/api/tags")
            response.raise_for_status()
            installed={item.get("name","") for item in response.json().get("models",[])}
        if name in installed:
            MODEL_STATE.write_text(json.dumps({"name":name},ensure_ascii=False))
            save_pull_state({"status":"success","name":name,"message":"模型已預先安裝，已直接切換使用，沒有重新下載","completed":1,"total":1})
            return {"status":"success","name":name,"message":"已切換至現有模型"}
    except httpx.HTTPError as exc:
        raise HTTPException(502,f"無法檢查現有模型：{exc}") from exc
    background.add_task(pull_builtin_model,name)
    save_pull_state({"status":"queued","name":name,"message":"檢查完成，開始下載輕量模型","completed":0,"total":0})
    return {"status":"queued","name":name}

@app.get("/api/models/pull-status")
async def model_pull_status():
    try: return json.loads(MODEL_PULL_STATE.read_text())
    except Exception: return {"status":"idle","message":"目前沒有模型下載工作"}

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
    return {"sub":"anonymous","name":"Guest"}

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
        # Stream the upload to disk; never hold the raw GGUF and its Base64 copy together.
        with temp.open("wb") as out:
            while True:
                chunk=await file.read(1024*1024)
                if not chunk: break
                total+=len(chunk)
                if total>MAX_GGUF_BYTES:
                    raise HTTPException(413,f"GGUF 檔案有 {total//(1024*1024)} MB，超過伺服器上限 {MAX_GGUF_BYTES//(1024*1024)} MB。")
                out.write(chunk)
        if total==0: raise HTTPException(400,"上傳的 GGUF 檔案是空的。")

        # Ollama's /api/create accepts Base64 file content. Stream the JSON body
        # in aligned chunks to avoid creating another full-size in-memory copy.
        payload=json.dumps({"name":safe_name,"modelfile":"FROM ./model.gguf\\n","stream":False,"files":{"model.gguf":""}},separators=(",",":"))
        if not payload.endswith('""}}'):
            raise RuntimeError("無法建立 GGUF 匯入請求。")
        prefix=payload[:-4].encode("utf-8")
        async def request_body():
            yield prefix
            with temp.open("rb") as source:
                while True:
                    chunk=source.read(3*1024*1024)
                    if not chunk: break
                    yield base64.b64encode(chunk)
            yield b'"}}'
        async with httpx.AsyncClient(timeout=None) as client:
            response=await client.post(
                f"{OLLAMA_URL.rstrip('/')}/api/create",
                content=request_body(),
                headers={"Content-Type":"application/json"},
                timeout=1800
            )
            if response.is_error:
                detail=response.text[:3000]
                raise HTTPException(502,f"Ollama 建立模型失敗（HTTP {response.status_code}）：{detail}")
            try:
                result=response.json()
            except Exception:
                result={}
            if result.get("error"):
                raise HTTPException(502,f"Ollama 匯入失敗：{str(result['error'])[:2000]}")
            tags=await client.get(f"{OLLAMA_URL.rstrip('/')}/api/tags")
            tags.raise_for_status()
            installed={item.get("name","") for item in tags.json().get("models",[])}
            if safe_name not in installed:
                raise HTTPException(502,"Ollama 回報建立完成，但模型清單找不到新模型；未切換目前模型。")
            check=await client.post(f"{OLLAMA_URL.rstrip('/')}/api/show",json={"name":safe_name})
            if check.is_error:
                raise HTTPException(502,f"模型已建立但載入驗證失敗（HTTP {check.status_code}）：{check.text[:1500]}")
        MODEL_STATE.write_text(json.dumps({"name":safe_name},ensure_ascii=False))
        return {"ok":True,"name":safe_name,"size":total,"active":safe_name,"verified":True,"message":"GGUF 已匯入，且 Ollama 已確認模型存在。"}
    except HTTPException:
        raise
    except OSError as exc:
        raise HTTPException(507,f"雲端暫存空間不足，GGUF 未能完成匯入：{exc}") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(502,f"GGUF 與 Ollama 通訊失敗：{exc}") from exc
    except Exception as exc:
        raise HTTPException(500,f"GGUF 匯入程序錯誤：{type(exc).__name__}: {exc}") from exc
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
