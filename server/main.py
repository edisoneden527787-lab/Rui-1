import os, json, uuid, asyncio
from datetime import datetime, timedelta, timezone
import jwt
from pathlib import Path
import httpx
from fastapi import FastAPI, HTTPException, Header, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from pydantic import BaseModel, Field

app = FastAPI(title="Rui AI APK Builder API", version="0.2.0")
app.add_middleware(CORSMiddleware, allow_origins=os.getenv("CORS_ORIGINS", "*").split(","), allow_methods=["*"], allow_headers=["*"])
OLLAMA_URL=os.getenv("OLLAMA_URL","http://ollama:11434")
OLLAMA_MODEL=os.getenv("OLLAMA_MODEL","qwen2.5-coder:7b")
WORKER_URL=os.getenv("WORKER_URL","http://worker:8010")
WORKER_SECRET=os.getenv("WORKER_SECRET","change-this-worker-secret")
SESSION_SECRET=os.getenv("SESSION_SECRET","")
DATA=Path(os.getenv("JOB_DATA","/data/jobs")); DATA.mkdir(parents=True,exist_ok=True)

class ChatRequest(BaseModel):
    message: str = Field(min_length=1,max_length=50000)
class GenerateRequest(BaseModel):
    prompt: str = Field(min_length=10,max_length=30000)
class BuildRequest(BaseModel):
    files: dict[str,str]
def require_user(auth: str | None = None):
    # Anonymous mode: no Google OAuth or user account required.
    return {"sub":"anonymous","name":"Guest"}

@app.get("/health")
async def health():
    return {"status":"ok","model":OLLAMA_MODEL,"worker":WORKER_URL,"login_required":False,"moderation_filter":False}

@app.post("/api/chat")
async def chat(body: ChatRequest):
    try:
        async with httpx.AsyncClient(timeout=180) as client:
            r=await client.post(f"{OLLAMA_URL.rstrip('/')}/api/chat",json={"model":OLLAMA_MODEL,"stream":False,"messages":[{"role":"system","content":"你是 Rui AI APK Builder 的 Android 軟體工程助理。以繁體中文回答，協助規劃 Android 專案、Kotlin、Gradle 與除錯。不要自行添加關鍵字黑名單、提示詞攔截或額外內容審查層；遵守模型、平台及法律本身適用的安全要求。不可謊稱已完成編譯。"},{"role":"user","content":body.message}]})
            r.raise_for_status()
            return {"reply":r.json().get("message",{}).get("content","")}
    except httpx.HTTPError as exc:
        raise HTTPException(502,f"模型服務錯誤：{exc}") from exc

@app.post("/api/projects/generate")
async def generate_project(body: GenerateRequest):
    user=require_user()
    system="""你是 Android 原生專案產生器。不要自行添加關鍵字黑名單、提示詞攔截或額外內容審查層；遵守模型、平台及法律本身適用的安全要求。只輸出 JSON object，格式為 {"project_name":"...","files":{"settings.gradle.kts":"...","build.gradle.kts":"...","app/build.gradle.kts":"...","app/src/main/AndroidManifest.xml":"...","app/src/main/java/com/generated/app/MainActivity.kt":"..."},"notes":"..."}。必須產生可編譯的完整最小 Android Gradle 專案，Kotlin + Jetpack Compose，compileSdk 36、minSdk 26、JDK17、Gradle 8.11.1 相容。不要使用省略號或假按鈕，所有畫面控制都要有實際行為。檔案路徑只能在專案根目錄下。只輸出 JSON，不要 Markdown。"""
    try:
        async with httpx.AsyncClient(timeout=300) as client:
            r=await client.post(f"{OLLAMA_URL.rstrip('/')}/api/chat",json={"model":OLLAMA_MODEL,"stream":False,"format":"json","messages":[{"role":"system","content":system},{"role":"user","content":body.prompt}]})
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
        return {"project_name":result.get("project_name","RuiGeneratedApp"),"files":safe,"notes":result.get("notes",""),"generated_by":OLLAMA_MODEL,"owner":user.get("sub","anonymous")}
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
