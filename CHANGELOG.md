# Changelog

本檔記錄反詐器的版本變更。格式參考 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)
並遵循 [Semantic Versioning](https://semver.org/lang/zh-TW/)。

## [0.1.0] - 2026-05-12

### Added — 首版功能

**強制無障礙閘門**
- 首次啟動強制顯示無障礙服務啟用畫面
- 未啟用前無法進入主畫面
- 偵測使用者開啟系統設定後自動回到 App 並重新檢查

**主功能（反詐器）**
- 「綁定／解除 APP」按鈕（紅框黑底）
- 列出手機已安裝 App 供使用者勾選綁定
- 主畫面顯示已綁定 App tile（橫向 LazyRow）
- 紅色 ➕ FAB 新增轉帳帳號（最多 5 筆）
- 預設「臨時用」帳號自動種子化、不可刪除
- 兩段式啟動流程：點擊綁定 App → 顯示帳號選擇 Sheet → 點擊帳號自動複製 + 啟動 App
- 60 秒授權視窗：點擊 App 後 60 秒內進入該 App 視為已授權

**全屏警告**
- AccessibilityService 監聽 `TYPE_WINDOW_STATE_CHANGED` 事件
- 未授權開啟綁定 App 時：
  1. 立即執行 `GLOBAL_ACTION_HOME` 拉回桌面
  2. 顯示全屏紅色 `BlockingWarningActivity`
  3. 攔截系統返回鍵
  4. 鎖屏覆蓋顯示
  5. 確認按鈕 → 重新拉起 MainActivity

**詐騙專區**
- 內建 6 種台灣常見詐騙手法說明（假冒檢警／假網銀／投資詐騙／ATM 解除分期／假交友／假購物退款）
- 165 反詐騙專線資訊卡

**設定**
- 無障礙服務啟用狀態（onResume 動態檢查）
- 統計：已綁定 App 數量、已建立轉帳帳號數量
- 165 撥號 Intent
- 清除所有資料（含確認對話框）
- App 版本資訊

### Security

- 轉帳帳號號碼欄位使用 Android Keystore + AES-GCM 加密儲存
- DataStore 用於非機密的 first-launch flag
- 所有資料僅儲存於本機，無任何網路請求
- 無 `INTERNET` 權限

### Tests

- `AuthorizedLaunchTracker` 單元測試（9 case）—— 涵蓋授權、消費、過期、邊界、per-package 隔離
- `ForegroundAppGuard` 單元測試（8 case）—— 涵蓋決策樹所有分支

### Known Limitations

- 部分中國定製 ROM（MIUI／EMUI／OriginOS）需要手動關閉「自啟動」與「電池優化」限制
- Android 14+ 對背景 Activity start 有額外限制 — 已加 GLOBAL_ACTION_HOME 作為防線
- 60 秒授權視窗 — 點擊 App 後 1 分鐘內未實際進入則需重新從反詐器啟動
