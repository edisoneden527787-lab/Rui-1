import os
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

app = FastAPI(title="Rui AI APK Builder API", version="0.1.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://ollama:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5-coder:7b")

class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=50000)

@app.get("/health")
async def health():
    return {"status": "ok", "model": OLLAMA_MODEL}

@app.post("/api/chat")
async def chat(body: ChatRequest):
    try:
        async with httpx.AsyncClient(timeout=180.0) as client:
            response = await client.post(
                f"{OLLAMA_URL.rstrip('/')}/api/chat",
                json={
                    "model": OLLAMA_MODEL,
                    "stream": False,
                    "messages": [
                        {"role": "system", "content": "你是 Rui AI APK Builder 的 Android 軟體工程助理。以繁體中文回答，協助規劃 Android 專案、Kotlin、Gradle 與除錯。不得聲稱已編譯或交付未實際產生的 APK。"},
                        {"role": "user", "content": body.message},
                    ],
                },
            )
            response.raise_for_status()
            data = response.json()
            return {"reply": data.get("message", {}).get("content", "")}
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"模型服務錯誤：{exc}") from exc
