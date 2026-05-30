"""
偵測 CIB「最新犯罪手法宣導」(wg117) 是否有尚未收錄的新文章。

純 Python、無 LLM。每月 cron 先跑這支當「成本閘」:只有真的出現新文章
時，workflow 才會喚醒 Claude 去整理手法，沒新文章就整個略過、零 token。

流程:
  1. 下載 wg117 清單頁 (pageSize=100)，解析每篇 {serno, title, url}。
  2. 對照帳本 data/cib_seen_sernos.json(已見過的 serno)找出新文章。
  3. 對每篇新文章抓內文(best-effort，strip HTML、截斷)。
  4. 寫 data/cib_new_articles.json 供 Claude 步驟讀取。
  5. 把新 serno 併入帳本(僅寫檔；由 workflow 最後 commit 才持久化，
     任何步驟失敗都不會 push，下個月會重新偵測，不漏件)。

stdout 最後一行印新文章數；若環境有 GITHUB_OUTPUT 則寫 new_count=N。
"""

from __future__ import annotations

import json
import os
import re
import sys
from html import unescape
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent.parent
LEDGER_PATH = ROOT / "data" / "cib_seen_sernos.json"
OUT_PATH = ROOT / "data" / "cib_new_articles.json"

USER_AGENT = "Mozilla/5.0 (compatible; anticyscam-catalog/0.4; +https://github.com/Flashsator/AntiCyScam)"
TIMEOUT_SEC = 120

LIST_URL = "https://www.cib.npa.gov.tw/ch/app/data/list?module=wg117&id=1893&pageSize=100"
VIEW_BASE = "https://www.cib.npa.gov.tw/ch/app/data/view?module=wg117&id=1893&serno="

# 一次最多處理的新文章數，避免異常情況(例如帳本遺失)燒爆 LLM 成本。
MAX_NEW = 25
# 每篇內文截斷長度，標題才是主訊號、內文只是輔助。
CONTENT_MAX_CHARS = 3000

# 重用 parse_wg117 的 anchor 樣式:href 與 title 兩種前後順序都涵蓋。
ANCHOR_HREF_FIRST = re.compile(
    r'<a[^>]*href="[^"]*view\?module=wg117&amp;id=1893&amp;serno=([0-9a-f-]{36})"[^>]*title="([^"]+)"',
    re.IGNORECASE,
)
ANCHOR_TITLE_FIRST = re.compile(
    r'<a[^>]*title="([^"]+)"[^>]*href="[^"]*view\?module=wg117&amp;id=1893&amp;serno=([0-9a-f-]{36})"',
    re.IGNORECASE,
)
TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"\s+")


def _get(url: str) -> str:
    resp = requests.get(
        url,
        headers={"User-Agent": USER_AGENT},
        timeout=TIMEOUT_SEC,
    )
    resp.raise_for_status()
    return resp.text


def _strip_html(html: str) -> str:
    return WS_RE.sub(" ", unescape(TAG_RE.sub(" ", html))).strip()


def _parse_list(html: str) -> dict[str, str]:
    """Return {serno: title} for every article anchor on the list page."""
    seen: dict[str, str] = {}
    for m in ANCHOR_HREF_FIRST.finditer(html):
        serno, title = m.group(1), unescape(m.group(2)).strip()
        if title and serno not in seen:
            seen[serno] = title
    for m in ANCHOR_TITLE_FIRST.finditer(html):
        title, serno = unescape(m.group(1)).strip(), m.group(2)
        if title and serno not in seen:
            seen[serno] = title
    return seen


def _load_ledger() -> set[str]:
    if not LEDGER_PATH.exists():
        return set()
    try:
        return set(json.loads(LEDGER_PATH.read_text(encoding="utf-8")))
    except (json.JSONDecodeError, OSError):
        return set()


def _fetch_content(serno: str) -> str:
    try:
        html = _get(VIEW_BASE + serno)
    except requests.RequestException as exc:
        print(f"[fetch_cib_new] content fetch failed serno={serno}: {exc}", file=sys.stderr)
        return ""
    return _strip_html(html)[:CONTENT_MAX_CHARS]


def _emit_count(count: int) -> None:
    gh_out = os.environ.get("GITHUB_OUTPUT")
    if gh_out:
        with open(gh_out, "a", encoding="utf-8") as f:
            f.write(f"new_count={count}\n")
    print(f"new_count={count}")


def main() -> int:
    try:
        listing = _parse_list(_get(LIST_URL))
    except requests.RequestException as exc:
        print(f"[fetch_cib_new] list fetch failed: {exc}", file=sys.stderr)
        # 抓不到清單時當作沒有新文章，不阻斷 workflow。
        OUT_PATH.write_text("[]", encoding="utf-8")
        _emit_count(0)
        return 0

    seen = _load_ledger()
    new_sernos = [s for s in listing if s not in seen]
    print(f"[fetch_cib_new] list={len(listing)} known={len(seen)} new={len(new_sernos)}")

    if len(new_sernos) > MAX_NEW:
        print(f"[fetch_cib_new] capping {len(new_sernos)} new -> {MAX_NEW}", file=sys.stderr)
        new_sernos = new_sernos[:MAX_NEW]

    new_articles = []
    for serno in new_sernos:
        new_articles.append({
            "serno": serno,
            "title": listing[serno],
            "url": VIEW_BASE + serno,
            "content": _fetch_content(serno),
        })

    OUT_PATH.write_text(
        json.dumps(new_articles, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    # 帳本併入本次發出的 serno(僅寫檔；workflow 最後 commit 才持久化)。
    if new_articles:
        merged = sorted(seen | {a["serno"] for a in new_articles})
        LEDGER_PATH.write_text(
            json.dumps(merged, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    _emit_count(len(new_articles))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
