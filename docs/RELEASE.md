# 發布準備指南

本文件涵蓋防詐器從 sideload 測試到正式上架 Google Play 的發布流程與審查資料準備。

---

## 階段一：Sideload 測試

### 1. 產生 debug APK

Android Studio → **Build → Build Bundle(s) / APK(s) → Build APK(s)**

產生位置：`app/build/outputs/apk/debug/app-debug.apk`

> debug 版本的 `applicationId` 結尾為 `.debug`，可以與正式版併存於同一支裝置。

### 2. 安裝到測試機

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

或直接把 APK 透過 USB、雲端、AirDroid 傳到測試機，使用裝置內檔案管理員開啟安裝。

### 3. 測試清單

對應 `CHANGELOG.md` 列出的功能，按以下順序驗證：

| # | 測試項 | 預期結果 |
|---|---|---|
| 1 | 第一次安裝後開啟 App | 顯示無障礙閘門 |
| 2 | 點擊「前往無障礙設定」 | 跳到系統設定 |
| 3 | 開啟「防詐器保護服務」後返回 | 進入主畫面 |
| 4 | 點擊「綁定／解除 APP」 | 顯示已安裝 App 清單 |
| 5 | 勾選任一 App（如 Line）→ 儲存 | 主畫面 tile 列顯示 Line |
| 6 | 點擊主畫面 ➕ → 新增「媽媽 / 0123456789」 | 帳號出現於清單 |
| 7 | 再連續新增 5 筆 | 第 6 筆按 ➕ 出現「最多只能新增 5 筆」snackbar |
| 8 | 點擊主畫面 Line tile | 跳出帳號選擇 Sheet |
| 9 | 選擇「媽媽」 | 帳號複製到剪貼簿 + Line 開啟 |
| 10 | 在 Line 內貼上 | 內容為 0123456789 |
| 11 | 直接從桌面開啟 Line | 全屏紅色警告 + 回到防詐器 |
| 12 | 警告畫面按下系統返回鍵 | 無反應（被攔截） |
| 13 | 警告畫面按「我知道了」 | 進入防詐器主畫面 |
| 14 | 設定頁顯示「已綁定 App: 1 / 轉帳帳號: 5 / 5」 | ✓ |
| 15 | 設定頁點擊「清除所有資料」→ 確認 | 綁定 App 與自訂帳號清空 |
| 16 | 主畫面只剩「臨時用」 | ✓ |

---

## 階段二：Google Play 上架準備

### 簽署金鑰

```bash
keytool -genkey -v -keystore anticyscam-release.keystore \
        -alias anticyscam -keyalg RSA -keysize 2048 -validity 10000
```

將 keystore 路徑 / 密碼 / alias / alias 密碼填入 `local.properties`（**不可進 git**）：

```properties
RELEASE_STORE_FILE=/path/to/anticyscam-release.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=anticyscam
RELEASE_KEY_PASSWORD=...
```

並在 `app/build.gradle.kts` 加入 `signingConfigs`（目前尚未配置，發布前再加）。

### 產生 Release Bundle

Android Studio → **Build → Generate Signed Bundle / APK → Android App Bundle (.aab)**

### Play Store 列表素材

| 素材 | 規格 |
|---|---|
| App icon | 512x512 PNG，無透明背景 |
| Feature graphic | 1024x500 PNG |
| 截圖 | 至少 2 張，建議 4-8 張，1080x1920 |
| 短說明 | 80 字內 |
| 詳細說明 | 4000 字內 |

#### 短說明（建議文案）

> 防詐器：強制在進入網銀前確認轉帳帳號，防止詐騙轉錯帳。

#### 詳細說明（建議結構）

1. 痛點：詐騙集團如何誘導匯款
2. 防詐器怎麼運作：強制三段式啟動流程 + 全屏警告
3. 使用方式：4 個步驟（綁定 App → 新增帳號 → 從防詐器進 App → 貼上帳號）
4. 隱私說明：本機儲存、無網路、無資料上傳

---

## 階段三：敏感權限申請文件

### `BIND_ACCESSIBILITY_SERVICE` 申請說明

> 防詐器使用無障礙服務監測使用者是否從本 App 之外的入口（如手機桌面、最近使用清單）
> 開啟已綁定的銀行類 App。當偵測到此情境，防詐器會立即顯示全屏警告畫面，提醒使用者
> 「請從防詐器進入」，並協助使用者回到防詐器確認轉帳對象。
>
> 本 App 使用無障礙服務的唯一行為是：
> - 監聽 `TYPE_WINDOW_STATE_CHANGED` 事件
> - 取得當前前景 App 的 package name
> - 比對是否屬於使用者預先綁定的清單
>
> 本服務**不會**：
> - 讀取畫面上的任何文字內容
> - 擷取畫面截圖
> - 攔截任何輸入
> - 收集、上傳或分享任何使用者資料
>
> 防詐器無 `INTERNET` 權限，技術上不可能將任何資料傳出裝置。

### `SYSTEM_ALERT_WINDOW` 申請說明

> 防詐器在偵測到使用者繞過保護流程開啟銀行 App 時，需要立即顯示一個無法被忽略的
> 全屏警告（包含「請從防詐器進入」訊息與確認按鈕），以避免使用者在情急下被詐騙集團
> 引導完成轉帳。`SYSTEM_ALERT_WINDOW` 用於確保此警告畫面在系統層級之上，且可覆蓋
> 任何銀行 App 畫面或鎖屏。

### `QUERY_ALL_PACKAGES` 申請說明

> 防詐器需要讓使用者從手機已安裝 App 中選擇要綁定保護的對象（通常是銀行 App、
> 支付 App、訊息 App）。`QUERY_ALL_PACKAGES` 用於 `PackageManager.queryIntentActivities`
> 列出所有可啟動的 App。本權限只用於 App 選擇 UI，未用於背景掃描或回報任何 App
> 清單到外部伺服器。

---

## 階段四：審查影片腳本

Play Console 通常會要求示範影片（30 秒以內）：

1. (0-3s) App icon 出現，文字「防詐器 — 強制反詐 App」
2. (3-8s) 開啟 App → 啟用無障礙服務畫面 → 點擊啟用
3. (8-13s) 主畫面 → 綁定 Line + 新增「媽媽」帳號
4. (13-20s) 從防詐器點擊 Line → 選擇「媽媽」→ 帳號複製 + Line 開啟
5. (20-27s) 直接從桌面開 Line → 全屏紅色警告 → 拉回防詐器
6. (27-30s) Logo + 文字「保護自己，保護家人」

---

## 階段五：審查問題回應準備

常見可能被問的問題：

- **Q: 為什麼需要強制無障礙服務？** → 因為這是 Android 唯一可以可靠監聽前景 App 切換
  的 API；BIND_EVENT API 受限太多無法即時反應
- **Q: 是否有讀取畫面文字？** → 否，只取 `event.packageName`
- **Q: 資料儲存？** → 全部本機 Room + Keystore；無網路權限
- **Q: 是否會干擾其他無障礙工具？** → 否，本服務只標註監聽 `typeWindowStateChanged`
  事件，不申請 `canRetrieveWindowContent` 或 `canPerformGestures`
