"""
seed/scam_catalog.json 結構驗證 — 全自動發布流程的安全閘。

Claude 整理手法後、build_catalog 發布前跑這支。任何違規 → 退出碼 1 →
GitHub Actions 失敗 → 不 commit、不 push,壞資料不會送到使用者手機。

把 LLM 的爆炸半徑限制在:結構合法、欄位齊全、分類/嚴重度在白名單、
sourceUrl 來自可信網域。內容語意對不對不在此驗證(全自動的固有取捨)。
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "seed" / "scam_catalog.json"

REQUIRED_KEYS = {"id", "categoryId", "title", "severity", "tags", "description", "redFlags", "protection", "sourceUrl"}
ALLOWED_SEVERITY = {"CRITICAL", "HIGH", "MEDIUM"}
# sourceUrl 只允許這些政府/官方來源,擋掉 LLM 自行編造的網址。
ALLOWED_HOSTS = {"www.cib.npa.gov.tw", "cib.npa.gov.tw", "165.npa.gov.tw", "moneywise.fsc.gov.tw"}
ID_RE = re.compile(r"^[a-z][a-z0-9_]{2,48}$")
DESC_MIN, DESC_MAX = 10, 2000


def _err(errors: list[str], msg: str) -> None:
    errors.append(msg)


def main() -> int:
    try:
        seed = json.loads(SEED.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as exc:
        print(f"[validate_seed] cannot read seed: {exc}", file=sys.stderr)
        return 1

    errors: list[str] = []
    category_ids = {c["id"] for c in seed.get("categories", [])}
    tactics = seed.get("tactics", [])
    if not isinstance(tactics, list) or not tactics:
        print("[validate_seed] tactics missing or empty", file=sys.stderr)
        return 1

    seen_ids: set[str] = set()
    for i, t in enumerate(tactics):
        where = f"tactics[{i}] id={t.get('id', '?')!r}"
        missing = REQUIRED_KEYS - set(t)
        if missing:
            _err(errors, f"{where}: missing keys {sorted(missing)}")
            continue

        tid = t["id"]
        if not ID_RE.match(tid):
            _err(errors, f"{where}: bad id format")
        if tid in seen_ids:
            _err(errors, f"{where}: duplicate id")
        seen_ids.add(tid)

        if t["categoryId"] not in category_ids:
            _err(errors, f"{where}: categoryId {t['categoryId']!r} not in categories")
        if t["severity"] not in ALLOWED_SEVERITY:
            _err(errors, f"{where}: severity {t['severity']!r} not in {ALLOWED_SEVERITY}")

        for list_key in ("tags", "redFlags"):
            v = t[list_key]
            if not isinstance(v, list) or not v or not all(isinstance(x, str) and x.strip() for x in v):
                _err(errors, f"{where}: {list_key} must be a non-empty list of non-empty strings")

        for str_key in ("title", "description", "protection"):
            if not isinstance(t[str_key], str) or not t[str_key].strip():
                _err(errors, f"{where}: {str_key} must be a non-empty string")
        desc = t.get("description", "")
        if isinstance(desc, str) and not (DESC_MIN <= len(desc) <= DESC_MAX):
            _err(errors, f"{where}: description length {len(desc)} out of [{DESC_MIN},{DESC_MAX}]")

        url = t["sourceUrl"]
        host = urlparse(url).netloc.lower() if isinstance(url, str) else ""
        if not (isinstance(url, str) and url.startswith("https://") and host in ALLOWED_HOSTS):
            _err(errors, f"{where}: sourceUrl host {host!r} not in allowlist {ALLOWED_HOSTS}")

    if errors:
        print(f"[validate_seed] FAIL — {len(errors)} error(s):", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1

    print(f"[validate_seed] OK — {len(tactics)} tactics, {len(category_ids)} categories")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
