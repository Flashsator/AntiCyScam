# 防詐器 (AntiCyScam)

反詐騙 Android App — 在進入網銀／支付類 App 前多一道轉帳帳號確認手續，
並提供可疑訊息辨識工具與台灣常見詐騙手法衛教，防止使用者被詐騙集團誘導轉帳。

> 測試版（v0.1.0）。安裝後可直接使用三個分頁，**沒有強制設定流程**；
> 偵測與進階保護皆於「設定」頁由使用者自行開啟。

---

## 為什麼需要防詐器？

詐騙集團最常見的手法是「誘導受害者匯款到指定人頭帳戶」。受害者打開網銀／轉帳 App 後，
往往在情急之下直接照詐騙集團的指示輸入帳號，跳過所有確認動作。

防詐器把「使用者預先建立的安全帳號清單」插入到網銀 App 啟動的流程裡：

- 從防詐器內點擊網銀 → 顯示帳號清單 → 必須點擊清單裡的一筆 → 自動複製 → 進入網銀
- 跳過防詐器（從手機桌面直接點網銀）→ 跳出全屏警告，把使用者拉回防詐器
- 若清單裡沒有「現在要轉的帳號」→ 多一個強制思考的緩衝點

---

## 三大功能

1. **防詐器**（主功能）— 已綁定 App 啟動器 + 轉帳帳號清單（含預設「臨時用」），
   搭配每日新增上限封鎖，防止使用者短時間內被誘導建立多筆人頭帳號。
2. **防詐專區** — 台灣常見詐騙手法卡片 + 官方來源連結，內建：
   - **辨識工具** — 圖片／文字／語音辨識可疑內容（皆為裝置端離線運算）
   - **詐騙資料庫自動更新** — 每週從公開副倉比對版本，可一鍵更新
3. **設定** — 防詐器保護設定（偵測必需的兩項權限 + 兩項選用項目）、資料統計、
   官方來源與資料版本、165 專線、意見回饋，以及「關於」卡（App 版本、詐騙資料庫
   版本、資料更新日）

---

## 辨識工具

防詐專區上方工具列提供三種裝置端離線辨識，幫助使用者判斷收到的訊息是否可疑：

| 模式 | 說明 | 技術 |
|---|---|---|
| 圖片辨識 | 對截圖做 OCR，比對詐騙關鍵字與可疑名單 | ML Kit 中文文字辨識（離線） |
| 文字辨識 | 直接貼上可疑訊息文字進行比對 | 內建規則引擎 |
| 語音辨識 | 把通話錄音／語音檔轉文字後比對 | Vosk 離線語音辨識（中文模型首次使用時下載） |

- 通話結束後會自動偵測 OEM 通話錄音，主動詢問是否要辨識該段錄音。
- 也可從 LINE／FB／Chrome／截圖 App 透過系統「分享」直接把可疑內容送入辨識流程。
- 所有辨識皆在裝置本機完成，不上傳任何內容。
- 語音辨識的中文模型**不隨 APK 打包**；首次使用語音辨識時才從公開副倉下載並解壓
  到裝置內，讓 APK 體積維持輕量。

---

## 使用流程

### 第一次安裝

1. 安裝 APK → 開啟「防詐器」，三個分頁（防詐器／防詐專區／設定）可直接使用。
2. 進入「設定」頁，依「防詐器保護設定」卡開啟偵測所需的兩項權限：
   - **使用情況存取權** — 偵測你是否開啟了綁定 App（系統「使用情況存取權」設定頁）
   - **上層顯示** — 偵測到風險時蓋出全屏警告（系統「在其他應用程式上層顯示」設定頁）
3. （選用）開啟「電池白名單」與「通知」，提升背景存活率與保護狀態可見性。
   這兩項可在 App 內直接彈系統對話框授權，免進設定頁。

> **若開啟權限時出現「應用程式存取權已遭拒絕」**：Android 14+ 的「受限設定 /
> 增強確認模式（Enhanced Confirmation Mode）」會阻擋以瀏覽器下載 APK 手動安裝的
> App 取得敏感權限。解法：到「設定 → 應用程式 → 防詐器 → 右上角 ⋮ → 允許受限設定」，
> 再回到「使用情況存取權」/「上層顯示」設定頁開啟即可。此為系統機制，App 無法繞過。

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

- 前景服務透過「使用情況存取權」偵測到「不是從防詐器授權」的綁定 App 進入前景
- 立即透過上層顯示蓋出全屏紅色警告「請從防詐器進入」
- 警告會持續出現，直到使用者回到防詐器 — 在此狀態下無法停留於網銀 App 操作轉帳

---

## 偵測與守護機制

### 偵測

- 防詐器以**前景服務常駐**，透過 `UsageStatsManager` 輪詢目前的前景 App。
- 偵測到綁定 App 在「未經防詐器授權」下進入前景 → 透過 `SYSTEM_ALERT_WINDOW`
  蓋出全屏警告。
- 偵測需要「使用情況存取權」+「上層顯示」兩項權限，缺任一項偵測即停用 ——
  因此設定頁把這兩項列為偵測必需。

### 守護

防詐器設計為「除非手機關機，否則不該被輕易關掉」：

- **前景服務 watchdog** — 在嚴格背景限制機型上保持偵測引擎存活。
- **開機 / 更新後自動復活** — `BootReceiver` 在開機、解鎖、App 更新後重新啟動服務。
- **通知復活** — Android 14+ 允許使用者滑掉前景服務通知；滑掉時
  `NotificationRevivalReceiver` 會立即重啟服務，讓通知重新出現。
- **Device Admin（選用）** — 提供可選的裝置管理員模組，需使用者自行在系統設定中啟用。

> 計時類保護（綁定熟成／解綁冷卻、臨時轉帳冷卻）皆以時間戳記錄，
> 即使 App 被背景清除或程序被殺，重新啟動後仍能正確接續。

---

## 技術棧

- Kotlin 2.0.21 + Jetpack Compose + Material 3
- minSdk 31（Android 12）/ targetSdk 36（Android 16）/ compileSdk 36
- 建置工具：AGP 8.8.2、Gradle 8.10.2、JDK 17
- ABI：release 僅打包 `arm64-v8a`；debug 另含 `x86_64` 供模擬器，符合 Android 15+
  的 16 KB 記憶體分頁需求
- DI：Hilt
- 持久層：Room + DataStore Preferences
- 加密：Android Keystore + AES/GCM（轉帳帳號號碼欄位加密）
- 導航：Navigation Compose
- 序列化：kotlinx.serialization（assets 內詐騙資料庫 JSON）
- 影像：Coil（App icon、防詐專區圖片）
- 文字辨識：ML Kit 中文 OCR（離線）
- 語音辨識：Vosk Android（離線，中文模型首次使用時下載，不打包於 APK）

---

## 架構分層

```
ui/
  ├── main/           — Bottom Nav + 路由（NavRoute）
  ├── mainfunction/   — 防詐器主功能（綁定 App 列、轉帳帳號 CRUD、Picker Sheet）
  ├── bind/           — 綁定 App 選擇頁
  ├── scaminfo/       — 防詐專區（詐騙手法卡片、手法圖片）
  ├── recognition/    — 圖片／文字／語音辨識
  │     └── engine/   — OcrEngine、VoskSttEngine、VoskModelManager、
  │                     PcmAudioDecoder、ChineseScriptConverter
  ├── catalog/        — 詐騙資料庫更新對話框
  ├── appupdate/      — App 內建 APK 更新對話框
  ├── setting/        — 設定頁
  ├── tempuse/        — 「臨時用」三段式閘門
  ├── lockdown/       — 每日新增上限封鎖頁
  ├── warning/        — 全屏警告 Activity
  ├── components/     — 共用元件
  └── theme/          — Compose 主題
service/              — 前景服務、UsageStats 前景偵測、前景守衛、授權追蹤、
                        開機／通知復活 Receiver、Device Admin、通話錄音偵測
data/
  ├── crypto/         — FieldCipher（AES-GCM via Android Keystore）
  ├── local/          — Room DAO + Entity
  ├── repository/     — TransferAccount / BoundApp / ScamInfo Repository
  ├── prefs/          — DataStore（更新檢查、每日新增追蹤、時鐘等）
  ├── catalog/        — CatalogUpdateChecker（線上詐騙資料庫更新）
  ├── appupdate/      — AppUpdateChecker（線上 APK 更新）
  └── system/         — InstalledAppsProvider、CallRecordingScanner
domain/
  ├── model/          — TransferAccount / BoundApp / ScamCatalog
  ├── binding/        — BindingSettleEngine（綁定熟成／解綁冷卻）
  ├── transfer/       — TransferAccountSettleEngine
  └── recognition/    — ScamDetector、HardRuleEngine、FuzzyMatch、PinyinNormalizer
di/                   — Hilt modules
utils/                — SystemAccessChecker、CallRecordingLauncher
```

### 三個關鍵組件

1. **AuthorizedLaunchTracker**（`service/`）— 短期授權記憶。當使用者從防詐器內
   點擊綁定 App，授權該 package 在短時間內可進入前景一次；超過視窗或被消費後
   失效。前景偵測引擎在每次前景切換時消費此授權。

2. **ForegroundAppGuard**（`service/`）— 決策核心。維護一份綁定 App 的 in-memory
   snapshot（避免在偵測 callback thread 上打 Room），對每個前景切換事件回傳
   `Ignore / AllowAuthorized / BlockUnauthorized` 之一。

3. **CatalogUpdateChecker**（`data/catalog/`）— 詐騙資料庫更新協調器。比對線上
   `version.json`，下載並以 sha256 驗證後原子替換本機覆蓋檔，再使資料庫快取失效。

---

## 詐騙資料庫

防詐專區的詐騙手法、警示帳戶與可疑名單來自一份伴生資料庫（catalog）：

- APK 內建一份 `assets/scam_catalog.json` 作為出廠基準。
- App 會定期從公開副倉檢查 `version.json`，發現新版時提示使用者一鍵更新；
  下載的覆蓋檔存於 `filesDir`，優先於內建版本。
- 顯示版號採雙軌：機器整數 `version`（驅動更新偵測）與人類可讀的
  `displayVersion`（如 `v1.0.0`，類比 Android versionCode / versionName）。

公開副倉：[anticyscam-catalog](https://github.com/Flashsator/anticyscam-catalog)
（僅放資料與 APK，無原始碼）。

---

## App 內建更新

App 啟動時（最多每 24h 一次）會檢查公開副倉的 `app_version.json`：

- `versionCode` 比目前安裝版本大 → 跳對話框詢問是否更新。
- 下載 APK 後以 `sha256` 驗證，再交給系統安裝程式（使用者仍需於系統確認安裝）。

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
| `PACKAGE_USAGE_STATS`（使用情況存取權） | 輪詢前景 App，偵測使用者是否開啟綁定 App（核心防護） |
| `SYSTEM_ALERT_WINDOW`（上層顯示） | 全屏警告覆蓋 |
| `QUERY_ALL_PACKAGES` | 列出已安裝 App 供綁定選擇 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 在嚴格背景限制機型上保持偵測服務運作 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 請求電池優化白名單，避免 doze 殺掉服務（選用） |
| `RECEIVE_BOOT_COMPLETED` | 開機 / App 更新後重啟守護服務 |
| `POST_NOTIFICATIONS` | Android 13+ 通知（選用） |
| `VIBRATE` | 警告觸覺回饋 |
| `READ_PHONE_STATE` | 偵測通話結束以便詢問是否辨識通話錄音 |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`(≤SDK 32) | 讀取 OEM 通話錄音檔供語音辨識 |
| `INTERNET` | 防詐專區圖片載入、詐騙資料庫與 APK 更新檢查、語音模型下載 |
| `REQUEST_INSTALL_PACKAGES` | App 內建更新：把下載的更新 APK 交給系統安裝程式 |

> 防詐器**不會**收集、上傳、或分享任何使用者資料。所有資料（綁定 App 清單、轉帳帳號、
> 辨識內容）皆在本機處理；轉帳帳號號碼透過 Android Keystore AES-GCM 加密儲存。
> 對外網路僅用於下載公開的詐騙資料庫、語音模型、防詐專區圖片與更新檢查。

---

## 發布注意事項

### Sideload（測試階段）

1. 在 Android Studio Build menu → **Generate Signed Bundle / APK** → APK，
   或終端機 `./gradlew :app:assembleRelease`
2. 將 APK 透過 USB / adb / 共享連結傳到測試裝置
3. 在裝置上「設定 → 應用程式 → 特殊存取 → 安裝未知 App」開啟對應來源
4. 安裝 APK 後依照「使用流程」一節操作

> 目前 release 建置沿用 debug 簽章金鑰，僅供測試發佈；正式上架前需改用獨立的
> upload keystore。

### 已知限制

- 部分中國 ROM（MIUI / EMUI / OriginOS）會額外加上「自啟動」「電池優化白名單」
  的限制，使用者必須手動關閉這些限制，防詐器才能持續守衛背景前景切換事件
- 偵測依賴「使用情況存取權」+「上層顯示」；若機型對 `SYSTEM_ALERT_WINDOW` 限制
  較嚴，警告可能無法即時跳出
- 短期授權視窗對絕大多數使用情境足夠；如果使用者在防詐器點擊 App 後暫離過久
  才實際進入，會被當成未授權並被攔截 — 這是有意的（防止授權被詐騙集團繞過）
- 通話錄音辨識依賴 OEM 內建錄音功能；未開啟錄音或機型不支援時，該功能靜默略過

---

## License

見 [LICENSE](LICENSE)
