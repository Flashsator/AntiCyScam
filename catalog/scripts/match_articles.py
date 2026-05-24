"""
Score CIB wg117 article titles against each tactic in the seed catalog,
and emit data/proposed_tactic_sources.json — a per-tactic ranked list of
candidate articles with their scores. A human reviews this before we
apply the mapping to seed/scam_catalog.json.

Scoring is intentionally simple: each tactic carries a list of weighted
keywords; an article scores by summing weights for every keyword that
appears as a substring of its title. We keep tactics that score >= a
floor and report the top candidate plus runner-up so humans can
spot-check confidence.
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "seed" / "scam_catalog.json"
ARTICLES = ROOT / "data" / "cib_wg117_articles.json"
OUT = ROOT / "data" / "proposed_tactic_sources.json"

# Per-tactic keywords. Keys are tactic.id. Each entry is a list of
# (keyword, weight). Higher weight = more discriminating. Chinese
# substring match is fine (no tokenisation needed for short titles).
TACTIC_KEYWORDS: dict[str, list[tuple[str, int]]] = {
    # investment
    "investment_group_master": [("假投資", 3), ("投資詐騙", 3), ("飆股", 4), ("投資老師", 4), ("股市憲哥", 5), ("跟著網友學投資", 5)],
    "investment_usdt": [("USDT", 5), ("虛擬貨幣", 4), ("挖礦", 3)],
    "investment_forex": [("外匯", 5), ("差價合約", 5), ("保證金", 3)],
    "investment_ai_robot": [("AI", 3), ("機器人", 4), ("量化交易", 5)],
    "investment_ponzi": [("吸金", 5), ("老鼠會", 5), ("傳銷", 4)],
    "investment_stock_pump": [("內線", 4), ("明牌", 3), ("拉抬", 4), ("出貨", 3)],
    "investment_ipo_pre": [("未上市", 5), ("IPO", 5), ("原始股", 5)],
    "investment_offshore_fund": [("境外基金", 5), ("結構商品", 5)],
    # fake_authority
    "fake_prosecutor": [("假檢警", 5), ("檢警", 4), ("假警察", 4), ("健保卡遭盜用", 4), ("猜猜我是誰", 0)],
    "fake_health_bureau": [("健保", 4), ("健保卡", 5), ("衛福部", 4)],
    "fake_authority_postal": [("郵局", 4), ("快遞", 3), ("包裹涉案", 5)],
    "fake_tax_bureau": [("國稅局", 5), ("退稅", 4), ("補稅", 4)],
    "fake_taipower": [("台電", 5), ("自來水", 4), ("欠費停電", 5)],
    "fake_immigration": [("移民署", 5), ("護照", 4)],
    "fake_customs": [("關務", 5), ("通關費", 5)],
    # phishing
    "phishing_sms_bank": [("釣魚簡訊", 5), ("假網銀", 5), ("網銀", 3)],
    "phishing_delivery_sms": [("物流", 3), ("海關", 3), ("取貨簡訊", 5)],
    "phishing_qr_parking": [("停車單", 5), ("違規", 3), ("QR", 3)],
    "phishing_streaming": [("串流", 4), ("訂閱", 3), ("Netflix", 5)],
    "phishing_etag": [("ETC", 5), ("通行費", 5), ("ETag", 5)],
    "phishing_invoice": [("電子發票", 5), ("發票中獎", 5)],
    "phishing_apple_id": [("Apple ID", 5), ("Apple", 2), ("iCloud", 3)],
    "phishing_facebook_login": [("FB", 2), ("Facebook", 3), ("IG", 2), ("安全警告", 4)],
    "phishing_post_redelivery": [("中華郵政", 5), ("重新派送", 5)],
    # romance
    "romance_dating_app": [("交友App", 5), ("交友", 3), ("假愛情", 4), ("假感情", 4), ("假交友", 4)],
    "romance_strangers_dm": [("陌生", 3), ("陌生來訊", 5), ("LINE", 2), ("Instagram", 2)],
    "romance_foreign_military": [("外國軍人", 5), ("軍人", 3), ("工程師", 2)],
    "romance_doctor_war": [("海外醫師", 5), ("醫師", 3), ("戰地", 4), ("義工", 3)],
    "romance_pig_butchering": [("殺豬盤", 5), ("養豬", 4), ("以愛之名", 5)],
    "romance_overseas_taishang": [("台商", 4), ("海外投資", 4)],
    # fake_purchase
    "fake_shop_refund": [("解除分期", 5), ("假客服", 4), ("分期付款", 4), ("取消設定", 5)],
    "fake_shop_facebook": [("FB", 1), ("社團", 3), ("低價", 3), ("購物社團", 4), ("一頁式", 4)],
    "fake_concert_ticket": [("演唱會", 5), ("黃牛", 4), ("讓票", 4)],
    "fake_pet_seller": [("寵物", 5), ("訂金", 2)],
    "fake_used_car": [("二手車", 5)],
    "fake_overseas_buyer": [("代購", 5), ("海外幫購", 5)],
    # social_impersonation
    "social_line_hijack": [("LINE被盜", 5), ("FB被盜", 5), ("帳號被盜", 4), ("盜借錢", 5)],
    "social_ceo_fraud": [("假冒老闆", 5), ("CEO", 4), ("BEC", 5)],
    "social_official_account": [("假冒官方", 4), ("假小編", 4), ("粉專", 3)],
    "social_family_emergency": [("家人車禍", 5), ("醫院急用", 5), ("猜猜我是誰", 5)],
    # employment
    "employment_money_mule": [("車手", 4), ("人頭帳戶", 5), ("求職詐騙", 4), ("人頭", 3)],
    "employment_id_collection": [("徵才", 4), ("個資", 2), ("證件", 3)],
    "employment_model": [("模特兒", 5), ("試鏡", 4), ("網紅", 3)],
    "employment_typing": [("打字員", 5), ("在家手作", 5)],
    "employment_delivery_advance": [("外送員", 4), ("預付款", 4)],
    # fake_aid
    "fake_aid_subsidy": [("紓困", 5), ("補助", 3), ("振興", 4), ("紓困振興", 5)],
    "fake_aid_lottery": [("中獎", 4), ("抽獎", 4), ("穩贏頭獎", 5), ("中獎號碼", 4)],
    "fake_aid_youth_dream": [("青年圓夢", 5), ("創業補助", 4)],
    "fake_aid_pension": [("勞退", 5), ("老人津貼", 5)],
    "fake_aid_credit_reward": [("信用卡紅利", 5), ("紅利兌換", 4)],
    # sextortion
    "sextortion_naked_chat": [("視訊裸聊", 5), ("裸聊", 5)],
    "sextortion_deepfake": [("AI合成", 5), ("不雅照", 4), ("deepfake", 5)],
    "sextortion_compensated_dating": [("援交", 5), ("仙人跳", 5)],
    "sextortion_sugar_baby": [("包養", 5), ("甜心", 4)],
    "sextortion_porn_membership": [("色情網站", 5), ("會員紀錄", 4)],
    # gambling
    "gambling_baccarat": [("百家樂", 5), ("必殺技", 4)],
    "gambling_lottery_lucky_number": [("六合彩", 5), ("明牌", 4), ("包牌", 4)],
    "gambling_sports_betting": [("運彩", 5), ("簽賭", 4), ("職棒", 3)],
    # crypto_wallet
    "crypto_wallet_phishing": [("冷錢包", 5), ("簽名授權", 5)],
    "crypto_airdrop_drainer": [("空投", 5), ("免費領幣", 5)],
    "crypto_nft_marketplace": [("NFT", 5)],
    # tech_support
    "tech_support_microsoft": [("微軟", 5), ("Microsoft", 5), ("遠端控制", 4)],
    "tech_support_apple": [("Apple客服", 5), ("iCloud", 4)],
    "tech_support_router": [("路由器", 5)],
}

# Minimum score for a candidate to be considered. Below this we leave
# the category-default URL in place.
SCORE_FLOOR = 4


def main() -> int:
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    articles = json.loads(ARTICLES.read_text(encoding="utf-8"))

    proposals = []
    for t in seed["tactics"]:
        tid = t["id"]
        kw = TACTIC_KEYWORDS.get(tid, [])
        scored: list[tuple[int, dict]] = []
        for art in articles:
            title = art["title"]
            score = sum(w for k, w in kw if k in title)
            if score > 0:
                scored.append((score, art))
        scored.sort(key=lambda x: -x[0])
        top = scored[:3]
        proposals.append({
            "id": tid,
            "categoryId": t["categoryId"],
            "title": t["title"],
            "currentSourceUrl": t.get("sourceUrl"),
            "topCandidates": [
                {"score": s, "title": a["title"], "url": a["url"]}
                for s, a in top
            ],
            "recommended": (
                top[0][1]["url"]
                if top and top[0][0] >= SCORE_FLOOR
                else None
            ),
        })

    OUT.write_bytes(
        json.dumps(proposals, ensure_ascii=False, indent=2).encode("utf-8")
    )
    matched = sum(1 for p in proposals if p["recommended"])
    print(f"[match] tactics={len(proposals)} matched={matched} floor={SCORE_FLOOR}")
    print(f"[match] wrote {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
