# 反詐器 (AntiCyScam)

強制反詐 Android App — 在進入網銀／支付類 App 前多一道轉帳帳號確認手續，
防止使用者被詐騙集團誘導轉帳至錯誤帳號。

> ⚠️ **本 App 採強制無障礙服務 + 全屏覆蓋警告機制，安裝後請務必先閱讀「使用流程」一節**。

---

## 為什麼需要反詐器？

詐騙集團最常見的手法是「誘導受害者匯款到指定人頭帳戶」。受害者打開網銀／轉帳 App 後，
往往在情急之下直接照詐騙集團的指示輸入帳號，跳過所有確認動作。

反詐器強制把「使用者預先建立的安全帳號清單」插入到網銀 App 啟動的流程裡：

- 從反詐器內點擊網銀 → 顯示帳號清單 → 必須點擊清單裡的一筆 → 自動複製 → 進入網銀
- 跳過反詐器（從手機桌面直接點網銀）→ 自動全屏警告 + 拉回反詐器
- 若清單裡沒有「現在要轉的帳號」→ 多一個強制思考的緩衝點

---

## 三大功能

1. **反詐器**（主功能）— 已綁定 App 啟動器 + 轉帳帳號清單（含預設「臨時用」）
2. **詐騙專區** — 6 種台灣常見詐騙手法 + 165 反詐騙專線
3. **設定** — 無障礙服務狀態、資料統計、清除所有資料、版本資訊

---

## 使用流程

### 第一次安裝

1. 安裝 APK → 開啟「反詐器」
2. App 強制顯示「請啟用無障礙服務」閘門 → 點擊「前往無障礙設定」
3. 在系統的「無障礙」設定中找到「反詐器保護服務」並開啟
4. 回到反詐器 → 自動偵測並進入主畫面

### 日常使用

1. 點擊主畫面紅色框「綁定／解除 APP」→ 勾選你要保護的 App（網銀、Line、轉帳 App）
2. 點擊主畫面右下角紅色 ➕ → 新增轉帳帳號（最多 5 筆，例如「媽媽 / 0123456789」）
3. 主畫面顯示已綁定的 App tile → 點擊 → 跳出帳號清單
4. 在清單中點擊一筆帳號（例如「媽媽」）→ 帳號自動複製 + 自動進入網銀 App
5. 在網銀中貼上即可

### 「臨時用」預設帳號

- 系統預裝、無法刪除、不會複製任何號碼
- 用途：當你真的有臨時轉帳需求、不想新增到清單時 → 選擇「臨時用」可直接進入網銀
- 仍然有「先點反詐器再進網銀」的儀式感，提供反射性思考緩衝

### 從桌面直接打開已綁定 App 會發生什麼？

- 反詐器的無障礙服務偵測到「不是從反詐器授權」的綁定 App 開啟
- 立即執行 GLOBAL_ACTION_HOME 拉回桌面
- 顯示全屏紅色警告「請從反詐器進入」（覆蓋鎖屏、攔截返回鍵）
- 使用者必須點擊「我知道了」回到反詐器主畫面

---

## 技術棧

- Kotlin 1.9.24 + Jetpack Compose + Material 3
- minSdk 26（Android 8.0）/ targetSdk 34
- DI: Hilt 2.51.1
- 持久層: Room 2.6.1 + DataStore Preferences 1.1.1
- 加密: Android Keystore + AES/GCM（轉帳帳號號碼欄位加密）
- 導航: Navigation Compose 2.8.0
- 影像: Coil（App icon）

---

## 架構分層

```
ui/                   — Compose UI + ViewModel
  ├── gate/           — 首次啟動無障礙閘門
  ├── main/           — Bottom Nav + 路由
  ├── mainfunction/   — 反詐器主功能（綁定 App 列、轉帳帳號 CRUD、Picker Sheet）
  ├── bind/           — 綁定 App 選擇頁
  ├── scaminfo/       — 詐騙專區
  ├── setting/        — 設定頁
  └── warning/        — 全屏警告 Activity
service/              — AccessibilityService + 前景守衛 + 授權追蹤
data/
  ├── crypto/         — FieldCipher (AES-GCM via Android Keystore)
  ├── local/          — Room DAO + Entity
  ├── repository/     — TransferAccountRepository / BoundAppRepository
  ├── prefs/          — DataStore (first-launch flag etc.)
  └── system/         — InstalledAppsProvider (PackageManager)
domain/model/         — TransferAccount / BoundApp
di/                   — Hilt modules
utils/                — AccessibilityChecker
```

### 兩個關鍵組件

1. **AuthorizedLaunchTracker**（`service/`）— 短期授權記憶。當使用者從反詐器內
   點擊綁定 App，授權該 package 在 60 秒內可進入前景一次；超過視窗或被消費後
   失效。AccessibilityService 在每次 `TYPE_WINDOW_STATE_CHANGED` 事件時消費。

2. **ForegroundAppGuard**（`service/`）— 決策核心。維護一份綁定 App 的 in-memory
   snapshot（避免在 AccessibilityService callback thread 上打 Room），對每個前景
   切換事件回傳 `Ignore / AllowAuthorized / BlockUnauthorized` 之一。

---

## 開發狀態

| Phase | 內容 | 狀態 |
|---|---|---|
| 1 | 專案骨架 + Gradle + Manifest + 主題 | ✅ |
| 2 | 強制無障礙閘門畫面 | ✅ |
| 3 | Bottom Navigation + 三功能殼 | ✅ |
| 4 | 綁定／解除 APP 流程 | ✅ |
| 5 | 轉帳帳號 CRUD（加密） | ✅ |
| 6 | 授權啟動 + 帳號複製 | ✅ |
| 7 | Accessibility Service 前景監聽 | ✅ |
| 8 | 全屏警告 + 強制拉回 | ✅ |
| 9 | 詐騙專區 + 設定頁 | ✅ |
| 10 | 單元測試（Tracker + Guard） | ✅ |
| 11 | 發布準備 + 文件 | ✅ |

---

## 開啟專案

1. 用 **Android Studio Hedgehog (2023.1.1) 或更新版** 開啟此資料夾
2. Android Studio 會自動執行 Gradle Sync 並下載 wrapper
3. 建立 `local.properties` 寫入 `sdk.dir=<你的 Android SDK 路徑>` (Studio 通常會自動產生)
4. Run → app

### 從終端機跑測試

```bash
./gradlew :app:testDebugUnitTest
```

---

## 權限

| 權限 | 用途 |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | 監聽前景 App 切換（核心防護） |
| `SYSTEM_ALERT_WINDOW` | 全屏警告覆蓋（覆蓋鎖屏） |
| `QUERY_ALL_PACKAGES` | 列出已安裝 App 供綁定選擇 |
| `FOREGROUND_SERVICE` | 在嚴格背景限制機型上保持服務運作 |
| `POST_NOTIFICATIONS` | Android 13+ 通知 |

> 反詐器**不會**收集、上傳、或分享任何使用者資料。所有資料（綁定 App 清單、轉帳帳號）
> 皆儲存於本機 Room 資料庫；帳號號碼透過 Android Keystore AES-GCM 加密。

---

## 發布注意事項

### Sideload（測試階段）

1. 在 Android Studio Build menu → **Generate Signed Bundle / APK** → APK → debug 或 release
2. 將 APK 透過 USB / adb / 共享連結傳到測試裝置
3. 在裝置上「設定 → 應用程式 → 特殊存取 → 安裝未知 App」開啟對應來源
4. 安裝 APK 後依照「使用流程」一節操作

### 上架 Google Play 注意

- `BIND_ACCESSIBILITY_SERVICE` 屬於敏感權限，Play 商店審查時必須清楚說明用途：
  「我們使用無障礙服務監聽使用者開啟已綁定銀行 App 的時機，並在使用者不是
  從本 App 進入時顯示警告，防止詐騙。本服務不會讀取畫面內容、不會擷取任何
  個人資料。」
- 需要在 Play Console 提供「Permissions Declaration → Use of Accessibility API」
  說明，並提供示範影片
- `SYSTEM_ALERT_WINDOW` 也需在隱私說明中載明
- 建議先在 Closed Testing track 跑一輪審查

### 已知限制

- 部分中國 ROM（MIUI / EMUI / OriginOS）會額外加上「自啟動」「電池優化白名單」
  的限制，使用者必須手動關閉這些限制，反詐器才能持續守衛背景前景切換事件
- Android 14+ 限制背景 Activity start，若機型對 `SYSTEM_ALERT_WINDOW` 限制較嚴，
  警告 Activity 可能無法即時跳出 — 我們仍然先觸發 `GLOBAL_ACTION_HOME` 把使用者
  拉回桌面，作為防線最後一步
- 一個 60 秒授權視窗對絕大多數使用情境足夠；如果使用者在反詐器點擊 App 後
  暫離超過 1 分鐘才實際進入，會被當成未授權並被攔截 — 這是有意的（防止
  反詐器啟動授權被詐騙集團繞過）

---

## License

見 [LICENSE](LICENSE)
