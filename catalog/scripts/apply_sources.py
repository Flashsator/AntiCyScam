"""
Apply the curated tactic -> sourceUrl mapping to seed/scam_catalog.json.

Two passes:
1. For tactics in CONFIRMED_MATCHES, set sourceUrl to that specific CIB
   article. These were hand-picked from data/proposed_tactic_sources.json.
2. For every other tactic, set sourceUrl to a category-level default
   that is at least browse-able (CIB wg117 list, 165 home, FSC home)
   rather than a generic agency landing page.

Rationale: shipping a specific article when we have one, and a usable
browse list otherwise, is strictly better than the v6 mix of agency
homepages (some of which 404 in the SPA shell).
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "seed" / "scam_catalog.json"

CIB_VIEW = "https://www.cib.npa.gov.tw/ch/app/data/view?module=wg117&id=1893&serno="
CIB_LIST = "https://www.cib.npa.gov.tw/ch/app/data/list?module=wg117&id=1893&pageSize=100"
NPA_165 = "https://165.npa.gov.tw/"
FSC_INVESTOR = "https://moneywise.fsc.gov.tw/home.jsp?id=24&parentpath=0"

# Hand-curated mappings, derived from running scripts/match_articles.py
# and reviewing the top candidates by hand. employment_delivery_advance
# was rejected (article was about a victim who was a delivery worker,
# not the "預付款套利" tactic itself).
CONFIRMED_MATCHES: dict[str, str] = {
    # serno values match articles in data/cib_wg117_articles.json
    "investment_group_master":    CIB_VIEW + "50df8ea2-6394-44f4-a397-edc95b3effd0",  # 防詐咖啡廳-假投資真詐騙(股市憲哥)
    "fake_prosecutor":            CIB_VIEW + "f2cf480b-e012-4567-98b4-9e575c858f3d",  # 165防詐學堂-假檢警(陳樹菊阿嬤)
    "fake_health_bureau":         CIB_VIEW + "4084f6e6-ac65-4446-86a9-763febc56021",  # 健保卡遭盜用詐騙老梗
    "phishing_sms_bank":          CIB_VIEW + "14b7cbe3-9cc2-4167-856f-c0ab5c7d9c01",  # 防詐森友會-虎媽中了釣魚簡訊
    "phishing_etag":              CIB_VIEW + "2c6333b4-f539-41bc-8267-a25902717ad5",  # 通行費簡訊釣魚
    "romance_dating_app":         CIB_VIEW + "c54cbf52-5f8a-4651-8724-c86aa5fd1204",  # 165防詐學堂-假愛情交友(宅女小紅)
    "romance_pig_butchering":     CIB_VIEW + "7d53c3be-0c86-4147-8201-52d530de2e62",  # 以愛之名假投資(李玉璽)
    "fake_shop_refund":           CIB_VIEW + "db6d0daf-5f4d-4c2b-96ab-2a1248c5ab50",  # 防詐咖啡廳第3話-解除分期付款
    "fake_shop_facebook":         CIB_VIEW + "2f1dde4f-d278-4449-a194-9f082919026e",  # 防詐咖啡廳第4話-一頁式廣告
    "fake_concert_ticket":        CIB_VIEW + "b9ec89bd-3fc4-4580-8d97-d2a1a82c2ae7",  # 網購社團求讓票
    "social_family_emergency":    CIB_VIEW + "98efb277-8a76-4416-bc24-fae04f7e9282",  # 刑事Bear-猜猜我是誰
    "employment_money_mule":      CIB_VIEW + "b7b6c528-3624-4649-ab96-d12031ebc3e6",  # 求職誤入人頭帳戶集中營
    "fake_aid_subsidy":           CIB_VIEW + "cb93196c-1d0a-432a-a181-f5552a6bbd89",  # 紓困振興話題正夯
    "fake_aid_lottery":           CIB_VIEW + "1bca2d5f-b216-4ce9-adc5-cd86246a204d",  # 預告中獎號碼穩贏頭獎
    "sextortion_naked_chat":      CIB_VIEW + "5c6c82da-c2b1-4610-88a8-83679877fa54",  # 視訊裸聊脫光
}

# Category fallback when no confirmed match. CIB list is a real
# browse-able page; the FSC investor education page covers
# investment-style fraud across SFB jurisdiction.
CATEGORY_FALLBACK = {
    "investment":           FSC_INVESTOR,
    "fake_authority":       CIB_LIST,
    "phishing":             CIB_LIST,
    "romance":              CIB_LIST,
    "fake_purchase":        CIB_LIST,
    "social_impersonation": CIB_LIST,
    "employment":           CIB_LIST,
    "fake_aid":             CIB_LIST,
    "sextortion":           CIB_LIST,
    "gambling":             NPA_165,
    "crypto_wallet":        FSC_INVESTOR,
    "tech_support":         CIB_LIST,
}


def main() -> int:
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    updated_specific = 0
    updated_fallback = 0
    for t in seed["tactics"]:
        tid = t["id"]
        if tid in CONFIRMED_MATCHES:
            url = CONFIRMED_MATCHES[tid]
            if t.get("sourceUrl") != url:
                t["sourceUrl"] = url
                updated_specific += 1
            continue
        fallback = CATEGORY_FALLBACK.get(t.get("categoryId", ""))
        if fallback and t.get("sourceUrl") != fallback:
            t["sourceUrl"] = fallback
            updated_fallback += 1

    SEED.write_bytes(
        json.dumps(seed, ensure_ascii=False, indent=2).encode("utf-8")
    )
    print(f"[apply] specific_updated={updated_specific} "
          f"fallback_updated={updated_fallback} "
          f"total_tactics={len(seed['tactics'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
