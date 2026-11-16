# Rui AI APK Builder

## 已完成的功能層
- Android Compose 客戶端：聊天介面與雲端 API URL 設定。
- Ollama API：聊天、AI 產生 Gradle/Kotlin 專案檔案。
- Google ID Token 驗證 API：`POST /api/auth/google`。
- 雲端 APK Worker：`POST /api/build` 觸發隔離容器中的 Gradle assembleDebug；可查詢狀態與下載 APK。
- APK 編譯採用 JDK 17、Android SDK 36、Gradle 8.11.1。

## 雲端部署
1. 建立 Google Cloud OAuth 2.0「Web application」用戶端，將其 Client ID 設為 `GOOGLE_CLIENT_ID`。
2. 設定隨機長密碼 `WORKER_SECRET`，不可使用範例值。
3. 建立 .env：
   ```env
   GOOGLE_CLIENT_ID=你的Google_Web_Client_ID
   WORKER_SECRET=請換成至少32字元隨機值
   OLLAMA_MODEL=qwen2.5-coder:7b
   ```
4. 執行 `docker compose up -d --build`。
5. 首次拉取模型：`docker compose exec ollama ollama pull qwen2.5-coder:7b`。
6. 將 API 服務透過 HTTPS 反向代理公開。不要直接把 Worker 的 8010 埠暴露到公網。

## API 流程
- `GET /health`：健康檢查。
- `POST /api/auth/google`：Body `{"id_token":"Google ID token"}`，驗證登入。
- `POST /api/projects/generate`：Bearer Google ID Token；Body `{"prompt":"需求" }`；回傳完整專案 files。
- `POST /api/build`：Bearer Google ID Token；Body `{"files":{...}}`；回傳 job_id。
- `GET /api/build/{job_id}`：查詢 queued/building/success/failed。
- `GET /api/build/{job_id}/download`：成功後下載 APK。

## 重要限制
這個提交補上後端的 Google Token 驗證、AI 專案生成 API 與真正的雲端 Gradle Worker。Android APK 本身目前仍是聊天客戶端；Google Credential Manager 登入按鈕、專案檔案瀏覽/保存、編譯進度與 APK 下載 UI 尚待接入。雲端服務也尚未部署；僅把程式碼推到 GitHub 不會自動啟動 Ollama/Worker。Google OAuth Client ID、HTTPS 網域與伺服器資源需由部署環境提供。
