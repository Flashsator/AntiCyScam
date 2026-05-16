# 防詐器 (AntiCyScam)

強制反詐 Android App — 在進入網銀／支付類 App 前多一道轉帳帳號確認手續，
並提供可疑訊息辨識工具與台灣常見詐騙手法衛教，防止使用者被詐騙集團誘導轉帳。

> ⚠️ **本 App 採強制無障礙服務 + 全屏覆蓋警告機制，安裝後請務必先閱讀「使用流程」一節**。

---

## 為什麼需要防詐器？

詐騙集團最常見的手法是「誘導受害者匯款到指定人頭帳戶」。受害者打開網銀／轉帳 App 後，
往往在情急之下直接照詐騙集團的指示輸入帳號，跳過所有確認動作。

防詐器強制把「使用者預先建立的安全帳號清單」插入到網銀 App 啟動的流程裡：

- 從防詐器內點擊網銀 → 顯示帳號清單 → 必須點擊清單裡的一筆 → 自動複製 → 進入網銀
- 跳過防詐器（從手機桌面直接點網銀）→ 自動全屏警告 + 拉回防詐器
- 若清單裡沒有「現在要轉的帳號」→ 多一個強制思考的緩衝點

---

## 三大功能

1. **防詐器**（主功能）— 已綁定 App 啟動器 + 轉帳帳號清單（含預設「臨時用」），
   搭配每日新增上限封鎖，防止使用者短時間內被誘導建立多筆人頭帳號。
2. **防詐專區** — 12 類台灣常見詐騙手法卡片 + 官方來源連結，內建：
   - **辨識工具** — 圖片／文字／語音辨識可疑內容（皆為裝置端離線運算）
   - **詐騙資料庫自動更新** — 每週從伴生資料庫比對版本，可一鍵更新
3. **設定** — 四項保護狀態、資料統計、官方來源與資料版本、165 專線、意見回饋

---

## 辨識工具

防詐專區上方工具列提供三種裝置端離線辨識，幫助使用者判斷收到的訊息是否可疑：

| 模式 | 說明 | 技術 |
|---|---|---|
| 圖片辨識 | 對截圖做 OCR，比對詐騙關鍵字與可疑名單 | ML Kit 中文文字辨識（離線） |
| 文字辨識 | 直接貼上可疑訊息文字進行比對 | 內建規則引擎 |
| 語音辨識 | 把通話錄音／語音檔轉文字後比對 | Vosk 離線語音辨識 |

- 通話結束後會自動偵測 OEM 通話錄音，主動詢問是否要辨識該段錄音。
- 也可從 LINE／FB／Chrome／截圖 App 透過系統「分享」直接把可疑內容送入辨識流程。
- 所有辨識皆在裝置本機完成，不上傳任何內容。

---

## 使用流程

### 第一次安裝

1. 安裝 APK → 開啟「防詐器」
2. App 強制顯示「請啟用無障礙服務」閘門 → 點擊「前往無障礙設定」
3. 在系統的「無障礙」設定中找到「防詐器保護服務」並開啟
4. 回到防詐器 → 自動偵測並進入主畫面
5. 依設定頁「四項保護狀態」提示，補開電池優化白名單等其餘項目

### 日常使用

1. 點擊主畫面紅色框「綁定／解除 APP」→ 勾選你要保護的 App（網銀、Line、轉帳 App）
2. 點擊主畫面右下角紅色 ➕ → 新增轉帳帳號（最多 5 筆自訂帳號，例如「媽媽 / 0123456789」）
3. 主畫面顯示已綁定的 App tile → 點擊 → 跳出帳號清單
4. 在清單中點擊一筆帳號（例如「媽媽」）→ 帳號自動複製 + 自動進入網銀 App
5. 在網銀中貼上即可

### 「臨時用」預設帳號

- 系統預裝、無法刪除、不會複製任何號碼
- 用途：當你真的有臨時轉帳需求、不想新增到清單時 → 選擇「臨時用」可直接進入網銀
- 仍然有「先點防詐器再進網銀」的儀式感，提供反射性思考緩衝

### 每日新增上限

- 同一天新增轉帳帳號達到上限後，新增功能會被封鎖 24 小時並顯示防詐提醒。
- 這是針對「詐騙集團要求一次建立多筆收款帳號」情境的緩衝設計。

### 從桌面直接打開已綁定 App 會發生什麼？

- 防詐器的無障礙服務偵測到「不是從防詐器授權」的綁定 App 開啟
- 立即執行 GLOBAL_ACTION_HOME 拉回桌面
- 顯示全屏紅色警告「請從防詐器進入」（覆蓋鎖屏、攔截返回鍵）
- 使用者必須點擊「我知道了」回到防詐器主畫面

---

## 持續守護機制

防詐器設計為「除非手機關機，否則不該被輕易關掉」：

- **前景服務 watchdog** — 在嚴格背景限制機型上保持無障礙服務存活。
- **開機 / 更新後自動復活** — `BootReceiver` 在開機、解鎖、App 更新後重新啟動服務。
- **通知復活** — Android 14+ 允許使用者滑掉前景服務通知；滑掉時 `NotificationRevivalReceiver`
  會立即重啟服務，讓通知重新出現。
- **Device Admin（選用）** — 提供可選的裝置管理員模組，需使用者自行在系統設定中啟用。

---

## 技術棧

- Kotlin 2.0.21 + Jetpack Compose + Material 3
- minSdk 31（Android 12）/ targetSdk 36（Android 16）/ compileSdk 36
- 建置工具：AGP 8.8.2、Gradle 8.10.2、JDK 17
- DI：Hilt 2.52
- 持久層：Room 2.6.1 + DataStore Preferences 1.1.1
- 加密：Android Keystore + AES/GCM（轉帳帳號號碼欄位加密）
- 導航：Navigation Compose 2.8.0
- 序列化：kotlinx.serialization（assets 內詐騙資料庫 JSON）
- 影像：Coil（App icon、防詐專區圖片）
- 文字辨識：ML Kit 中文 OCR（離線）
- 語音辨識：Vosk Android（離線，模型隨 APK 打包於 assets）

---

## 架構分層

```
ui/
  ├── gate/           — 首次啟動無障礙閘門
  ├── main/           — Bottom Nav + 路由（NavRoute）
  ├── mainfunction/   — 防詐器主功能（綁定 App 列、轉帳帳號 CRUD、Picker Sheet）
  ├── bind/           — 綁定 App 選擇頁
  ├── scaminfo/       — 防詐專區（詐騙手法卡片、手法圖片）
  ├── recognition/    — 圖片／文字／語音辨識（engine/：OcrEngine、VoskSttEngine、PcmAudioDecoder）
  ├── catalog/        — 詐騙資料庫更新對話框
  ├── setting/        — 設定頁
  ├── tempuse/        — 「臨時用」三段式閘門
  ├── lockdown/       — 每日新增上限封鎖頁
  ├── warning/        — 全屏警告 Activity
  ├── components/     — 共用元件
  └── theme/          — Compose 主題
service/              — AccessibilityService、前景守衛、前景服務 watchdog、
                        開機／通知復活 Receiver、Device Admin、通話錄音偵測
data/
  ├── crypto/         — FieldCipher（AES-GCM via Android Keystore）
  ├── local/          — Room DAO + Entity
  ├── repository/     — TransferAccount / BoundApp / ScamInfo Repository
  ├── prefs/          — DataStore（首次啟動旗標、更新檢查、每日新增追蹤等）
  ├── catalog/        — CatalogUpdateChecker（線上詐騙資料庫更新）
  └── system/         — InstalledAppsProvider、CallRecordingScanner
domain/
  ├── model/          — TransferAccount / BoundApp / ScamCatalog
  ├── binding/        — BindingSettleEngine（綁定熟成／解綁冷卻）
  ├── transfer/       — TransferAccountSettleEngine
  └── recognition/    — ScamDetector、HardRuleEngine、FuzzyMatch、PinyinNormalizer
di/                   — Hilt modules
utils/                — AccessibilityChecker、CallRecordingLauncher
```

### 三個關鍵組件

1. **AuthorizedLaunchTracker**（`service/`）— 短期授權記憶。當使用者從防詐器內
   點擊綁定 App，授權該 package 在 60 秒內可進入前景一次；超過視窗或被消費後
   失效。AccessibilityService 在每次 `TYPE_WINDOW_STATE_CHANGED` 事件時消費。

2. **ForegroundAppGuard**（`service/`）— 決策核心。維護一份綁定 App 的 in-memory
   snapshot（避免在 AccessibilityService callback thread 上打 Room），對每個前景
   切換事件回傳 `Ignore / AllowAuthorized / BlockUnauthorized` 之一。

3. **CatalogUpdateChecker**（`data/catalog/`）— 詐騙資料庫更新協調器。比對線上
   `version.json`，下載並以 sha256 驗證後原子替換本機覆蓋檔，再使資料庫快取失效。

---

## 詐騙資料庫

防詐專區的詐騙手法、警示帳戶與可疑名單來自一份伴生資料庫（catalog）：

- APK 內建一份 `assets/scam_catalog.json` 作為出廠基準。
- App 會定期從伴生 Git repo 檢查 `version.json`，發現新版時提示使用者一鍵更新；
  下載的覆蓋檔存於 `filesDir`，優先於內建版本。
- 顯示版號採雙軌：機器整數 `version`（驅動更新偵測）與人類可讀的
  `displayVersion`（如 `v1.0.0`，類比 Android versionCode / versionName）。

---

## 開啟專案

1. 用 **Android Studio Ladybug (2024.2.1) 或更新版** 開啟此資料夾（需 JDK 17）
2. Android Studio 會自動執行 Gradle Sync 並下載 wrapper
3. 建立 `local.properties` 寫入 `sdk.dir=<你的 Android SDK 路徑>`（Studio 通常會自動產生）
4. Run → app

### 從終端機跑測試

```bash
./gradlew :app:testDebugUnitTest
```

---

## 權限

| 權限 | 用途 |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE`（服務綁定） | 監聽前景 App 切換（核心防護） |
| `SYSTEM_ALERT_WINDOW` | 全屏警告覆蓋（覆蓋鎖屏） |
| `QUERY_ALL_PACKAGES` | 列出已安裝 App 供綁定選擇 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 在嚴格背景限制機型上保持服務運作 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 請求電池優化白名單，避免 doze 殺掉服務 |
| `RECEIVE_BOOT_COMPLETED` | 開機 / App 更新後重啟守護服務 |
| `POST_NOTIFICATIONS` | Android 13+ 通知 |
| `VIBRATE` | 警告觸覺回饋 |
| `READ_PHONE_STATE` | 偵測通話結束以便詢問是否辨識通話錄音 |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`(≤SDK 32) | 讀取 OEM 通話錄音檔供語音辨識 |
| `INTERNET` | 防詐專區圖片載入與詐騙資料庫更新檢查 |

> 防詐器**不會**收集、上傳、或分享任何使用者資料。所有資料（綁定 App 清單、轉帳帳號、
> 辨識內容）皆在本機處理；轉帳帳號號碼透過 Android Keystore AES-GCM 加密儲存。
> 對外網路僅用於下載公開的詐騙資料庫與防詐專區圖片。

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
- `SYSTEM_ALERT_WINDOW`、`QUERY_ALL_PACKAGES`、通話錄音相關權限也需在隱私說明中載明
- 建議先在 Closed Testing track 跑一輪審查

### 已知限制

- 部分中國 ROM（MIUI / EMUI / OriginOS）會額外加上「自啟動」「電池優化白名單」
  的限制，使用者必須手動關閉這些限制，防詐器才能持續守衛背景前景切換事件
- Android 14+ 限制背景 Activity start，若機型對 `SYSTEM_ALERT_WINDOW` 限制較嚴，
  警告 Activity 可能無法即時跳出 — 我們仍然先觸發 `GLOBAL_ACTION_HOME` 把使用者
  拉回桌面，作為防線最後一步
- 一個 60 秒授權視窗對絕大多數使用情境足夠；如果使用者在防詐器點擊 App 後
  暫離超過 1 分鐘才實際進入，會被當成未授權並被攔截 — 這是有意的（防止
  防詐器啟動授權被詐騙集團繞過）
- 通話錄音辨識依賴 OEM 內建錄音功能；未開啟錄音或機型不支援時，該功能靜默略過

---

## License

見 [LICENSE](LICENSE)
