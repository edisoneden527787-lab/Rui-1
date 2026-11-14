# AI App 產生器的編譯倉庫

這個倉庫只做一件事：接收手機 App 推上來的程式，用 GitHub Actions 編譯成 APK，再放到 Release 讓手機下載。

請把它跟「AI App 產生器」這個專案分開。不要把這個範本放進已經有別的程式的倉庫，也不要拿產生器的原始碼倉庫來編譯。

## 這個倉庫裡有什麼

- `.github/workflows/build.yml`：雲端編譯腳本。有人把檔案推到 `generated/` 時會自動執行，也可以在 Actions 頁面手動執行。
- `generated/`：手機 App 產生的 Android 專案會整個蓋掉這裡。請不要手動修改。
- 編譯成功後，Actions 會建立一個標籤像 `build-12-20261113...` 的 Release，並附上 `ai-app-debug.apk`。

## 用手機建立（建議）

1. 在 GitHub 新增一個**空白**倉庫（Public 或 Private 都可以）。
2. 建立細分權限的個人存取權杖，Contents 與 Actions 都要是 Read and write，而且只能選這個倉庫。
3. 打開倉庫的 Settings → Actions → General，在 Workflow permissions 選 **Read and write permissions**，按 Save。
4. 在手機 App 的設定裡填好金鑰、使用者名稱、權杖、倉庫名稱，按「儲存並驗證」。App 會把這個腳本傳上去。

逐步的點選說明在手機 App 的設定教學裡。

## 用電腦建立

若你手上有這個 `build-repo-template` 資料夾：

```bash
git init
git add .
git commit -m "加入雲端編譯腳本"
git branch -M main
git remote add origin https://github.com/你的帳號/倉庫名稱.git
git push -u origin main
```

推上去之後，一樣要到 Settings → Actions → General，把 Workflow permissions 改成 Read and write permissions。

## 編譯腳本會做什麼

1. 確認 `generated/` 裡有 Gradle 專案。
2. 安裝 JDK 17 與 Android SDK 34。
3. 如果專案有完整的 `gradlew` 和 `gradle-wrapper.jar` 就用它；沒有就改裝 Gradle 8.7 來執行 `assembleDebug`。
4. 找到 `app-debug.apk`，重新命名成 `ai-app-debug.apk`，放到一個新的 Release。

編譯紀錄都留在 GitHub 這個倉庫的 Actions 分頁。失敗時，手機 App 會盡量用中文說明常見原因。

## 注意

- 產生出來的程式會在**你的** GitHub 帳號上編譯。請只描述你信任的一般小工具。
- 這個流程不會做遊戲外掛、透視、讀取其他 App、注入或破解。
- 免費帳號的私有倉庫有 Actions 分鐘數上限。若常常失敗在額度，可以把倉庫改成 Public。
