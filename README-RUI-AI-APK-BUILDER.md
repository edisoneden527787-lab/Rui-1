# Rui AI APK Builder

Android client plus a starter self-hosted AI API.

## Android client
Open `generated/` as an Android Gradle project, or let the repository GitHub Actions workflow build the debug APK. In the app, open **服務設定** and enter the public HTTPS URL of the API.

## AI API
The API forwards chat requests to an Ollama server. It does not ask the mobile user for an AI API key.

```bash
docker compose up -d --build
docker compose exec ollama ollama pull qwen2.5-coder:7b
```

The API is available on port 8000. Put it behind HTTPS before exposing it publicly.

## Current implementation boundary
This commit provides the Android client, a working chat API adapter, and a self-hosted Ollama deployment starter. It does **not** yet implement Google OAuth, project ZIP upload/version storage, isolated Android build workers, automated code repair, or APK delivery endpoints. The Android client does not pretend those unfinished services are live.
