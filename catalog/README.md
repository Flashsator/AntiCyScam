# catalog/

防詐器 App 用到的公開資料與更新中繼資料。腳本與 GitHub Actions 也住在這裡。
App 本體說明請見[主倉 README](../README.md)。

## 內容

### 資料檔

| 檔案 | 用途 |
| --- | --- |
| `scam_catalog.json` | App 端下載的合併版詐騙目錄 |
| `version.json` | 詐騙資料版本中繼資料：`version`（整數，驅動更新偵測）、`displayVersion`（人類可讀，如 `v1.0.0`）、`sha256`、`updatedAt` |
| `app_version.json` | APK 本體版本中繼資料：`versionCode`（整數，驅動更新偵測）、`versionName`、`apkUrl`、`sha256`、`notes`、`updatedAt` |
| `seed/scam_catalog.json` | 人工維護的基準目錄（cron 不會動） |

### 腳本

| 檔案 | 用途 |
| --- | --- |
| `scripts/scrape_165.py` | 165 官網內部 API + 政府開放資料平台 CSV 爬蟲（cron 使用） |
| `scripts/build_catalog.py` | 合併 seed + 抓取結果，產出 `scam_catalog.json` 與 `version.json`（cron 使用） |
| `scripts/parse_wg117.py` | 解析 CIB「最新犯罪手法宣導」文章列表（一次性維護） |
| `scripts/match_articles.py` | 將 CIB 文章標題比對到各 tactic，產出候選來源供人工複核（一次性維護） |
| `scripts/apply_sources.py`、`patch_sources.py` | 把複核後的 tactic → sourceUrl 對應寫回 seed（一次性維護） |
| `scripts/requirements.txt` | Python 相依套件 |
| `../.github/workflows/scrape-165.yml` | 每週六 08:00 台灣（週六 00:00 UTC）自動跑 scrape + build |

## App 下載端點

- `https://raw.githubusercontent.com/Flashsator/AntiCyScam/main/catalog/version.json`
- `https://raw.githubusercontent.com/Flashsator/AntiCyScam/main/catalog/scam_catalog.json`
- `https://raw.githubusercontent.com/Flashsator/AntiCyScam/main/catalog/app_version.json`

## 更新流程

1. Cron 每週六 08:00 台灣觸發
2. 跑 `scrape_165.py` → 產出 `data/scraped_165.json`
3. 跑 `build_catalog.py` → 將新發現去重後合進 seed，bump version，重算 sha256
4. 若有差異 → bot 自動 commit + push
5. App 啟動時檢查 `version.json`（最多每 24h 一次），`version` 整數變大 → 跳對話框問使用者要不要更新；下載後以 `sha256` 驗證

## 限制與誠實聲明

- 165 官網**沒有開放 API**，scraper 解析 HTML，網站改版會壞。失敗時自動回傳零筆，不影響舊資料。
- 自動抓取的條目都標 `source: 165 官網（自動抓取）` 與 `note: 未經人工複核`，App 端可依此標示。
- Regex 提取手機/帳號/LINE ID/URL 是粗略字串比對，**會有誤判**。建議定期人工檢視 seed 並把無價值條目刪掉。
- 所有條目來源僅限 165、警政署等台灣官方公開資訊，不接受未驗證來源。

## 手動觸發

到主倉 Actions 頁籤 → "Scrape 165 + rebuild catalog" → Run workflow。

## APK Releases

主倉本地 build 完的 APK 上傳到主倉的 [Releases](https://github.com/Flashsator/AntiCyScam/releases)。

### App 內建更新檢查

App 啟動時會檢查 `app_version.json`（最多每 24h 一次）：`versionCode` 比目前安裝的版本大 → 跳對話框問使用者要不要更新；下載 APK 後以 `sha256` 驗證，再交給系統安裝程式。

**發佈新版 App 流程：**

1. bump `versionCode` / `versionName`，build APK
2. 把 APK 上傳到新的 Release（或既有 Release 用 `gh release upload --clobber`）
3. 更新 `app_version.json`：`versionCode`、`versionName`、`apkUrl`、`sha256`（= 上傳 APK 的 SHA-256）、`notes`、`updatedAt`
4. commit + push → 使用者下次開 App 就會收到更新提示

> `sha256` 必須與 `apkUrl` 指向的 APK 完全一致，否則 App 端驗證會失敗並拒絕安裝。
