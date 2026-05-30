# 任務:整理 CIB 新文章成防詐手法條目(全自動)

你正在維護台灣反詐 App 的「詐騙手法」資料庫。此為**全自動發布**流程,無人工複核,
請保守、寧缺勿濫。所有路徑相對於 `catalog/` 目錄。

## 你要做的事

1. 讀 `data/cib_new_articles.json` — 這是本月 CIB「最新犯罪手法宣導」的新文章
   陣列,每篇含 `serno` / `title` / `url` / `content`。
2. 讀 `seed/scam_catalog.json` — 現有資料庫。`tactics` 已有數十筆手法、
   `categories` 有 12 個固定分類。
3. 對**每篇**新文章判斷:它描述的詐騙手法,是否為現有 `tactics` 尚未涵蓋的**全新**手法?
   - **是全新手法** → 在 `tactics` 陣列**末端附加一個**新手法物件(schema 見下)。
   - **是現有手法的新案例**,且該手法目前的 `sourceUrl` 是通用清單頁
     (`.../list?...` 或 `moneywise.fsc.gov.tw/home...`)→ 可只把該手法的
     `sourceUrl` 更新成這篇文章的 `url`,**不新增**。
   - **判斷不出明確手法、或只是宣導/活動花絮** → **跳過**,不要硬湊。

## 新手法物件 schema(9 個欄位全部必填)

```json
{
  "id": "snake_case_唯一英文識別碼",
  "categoryId": "必須是下列 12 個之一",
  "title": "繁體中文手法名稱(簡短)",
  "severity": "CRITICAL | HIGH | MEDIUM 三選一",
  "tags": ["3 到 5 個", "繁中關鍵詞"],
  "description": "繁體中文 1–3 句白話,說明手法怎麼運作(10–300 字)",
  "redFlags": ["3 到 5 條", "可辨識的警訊", "民眾看得懂的話"],
  "protection": "繁體中文,具體防護/查證做法(例如撥 165、官方 App 查詢)",
  "sourceUrl": "該篇文章的 url(必須照抄 data 裡的 url)"
}
```

`categoryId` 只能是:`investment` `fake_authority` `phishing` `romance`
`fake_purchase` `social_impersonation` `employment` `fake_aid` `sextortion`
`gambling` `crypto_wallet` `tech_support`。
找不到貼切分類就用最接近的,**不要自創新分類**。

## 硬性規則

- **只能新增手法 / 更新 sourceUrl。絕對不可刪除或改寫任何現有手法的其他欄位。**
- 不要動 `suspiciousNames`、`warnedAccounts`、`version`、`displayVersion`、
  `categories`、`channels`。
- `id` 必須全域唯一、不可與現有 id 重複。
- `sourceUrl` 必須是 `https://` 開頭、且來自 cib.npa.gov.tw / 165.npa.gov.tw /
  moneywise.fsc.gov.tw。**嚴禁編造網址。**
- 全程用繁體中文撰寫內容。
- 寫回 `seed/scam_catalog.json` 時保持 UTF-8、2 空格縮排、**保留中文字元**
  (不可 escape 成 \uXXXX)。
- 若評估後沒有任何文章構成新手法 → **不要做任何修改**,直接結束。
- 你**不需要**執行任何指令(build / git / 驗證由 workflow 負責)。只編輯 JSON 檔即可。

## 完成後

簡述你新增了哪幾筆手法(或為何全部跳過),不需其他動作。
