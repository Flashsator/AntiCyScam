"""
One-shot: backfill `sourceUrl` on every tactic in seed/scam_catalog.json
that doesn't already have one, using category-level authoritative sources
from Taiwanese official agencies (165 / CIB / FSC). Preserves all
existing `sourceUrl` values verbatim.
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "seed" / "scam_catalog.json"

# Category -> authoritative official source page.
# Picked by which agency owns the topic: FSC for investment/crypto,
# CIB for impersonation + employment + sextortion, 165 for the rest.
CATEGORY_DEFAULT_SOURCE = {
    "investment": "https://moneywise.fsc.gov.tw/home.jsp?id=24&parentpath=0",
    "fake_authority": "https://www.cib.npa.gov.tw/",
    "phishing": "https://165.npa.gov.tw/",
    "romance": "https://165.npa.gov.tw/",
    "fake_purchase": "https://165.npa.gov.tw/",
    "social_impersonation": "https://165.npa.gov.tw/",
    "employment": "https://www.cib.npa.gov.tw/",
    "fake_aid": "https://165.npa.gov.tw/",
    "sextortion": "https://www.cib.npa.gov.tw/",
    "gambling": "https://165.npa.gov.tw/",
    "crypto_wallet": "https://moneywise.fsc.gov.tw/home.jsp?id=24&parentpath=0",
    "tech_support": "https://165.npa.gov.tw/",
}

# Per-tactic precise overrides where a specific official article exists.
# Keep this small — only add when we are confident the URL is durable.
TACTIC_OVERRIDE_SOURCE: dict[str, str] = {
    # (left intentionally minimal; precise links can be added later)
}


def main() -> int:
    data = json.loads(SEED.read_text(encoding="utf-8"))
    tactics = data.get("tactics", [])
    filled = 0
    skipped_existing = 0
    missing_category = 0
    for t in tactics:
        if t.get("sourceUrl"):
            skipped_existing += 1
            continue
        override = TACTIC_OVERRIDE_SOURCE.get(t["id"])
        if override:
            t["sourceUrl"] = override
            filled += 1
            continue
        default = CATEGORY_DEFAULT_SOURCE.get(t.get("categoryId", ""))
        if not default:
            print(f"[patch] no default for category={t.get('categoryId')!r} tactic={t['id']!r}")
            missing_category += 1
            continue
        t["sourceUrl"] = default
        filled += 1

    SEED.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"[patch] filled={filled} skipped_existing={skipped_existing} "
          f"missing_category={missing_category} total={len(tactics)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
