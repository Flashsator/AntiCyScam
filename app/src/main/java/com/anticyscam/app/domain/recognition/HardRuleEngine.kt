package com.anticyscam.app.domain.recognition

/**
 * High-precision regex rules layered on top of catalog keyword matching.
 *
 * Catalog keyword/redFlag matching covers "詐騙慣用語" — but scammers can
 * disguise those. The patterns here detect **operational artifacts** that any
 * scam phone-call, screenshot, or SMS still has to contain: a bank account
 * number, a money amount + 匯款 verb, a shortened URL, a government-agency +
 * 監管帳戶 combo, ATM weird-mode instructions.
 *
 * These artifacts are hard for the scam script to remove because they're the
 * *thing the scam needs from the victim* (匯款 + 帳號) rather than padding
 * phrases like 「您好」/「請問」.
 *
 * Hits stack with the catalog score — 60+ here alone is enough to surface a
 * HIGH risk verdict even when no catalog tactic matches yet (covers brand-new
 * scam variants).
 */
object HardRuleEngine {

    fun detect(input: String): List<HardRuleHit> {
        if (input.isBlank()) return emptyList()
        return RULES.flatMap { rule ->
            rule.pattern.findAll(input).map { match ->
                HardRuleHit(
                    ruleId = rule.id,
                    label = rule.label,
                    matchedText = match.value.take(40),
                    weight = rule.weight,
                    explanation = rule.explanation
                )
            }
        }.distinctBy { it.ruleId to it.matchedText }
    }

    private data class Rule(
        val id: String,
        val label: String,
        val weight: Int,
        val pattern: Regex,
        val explanation: String
    )

    // Ordered by typical signal strength — strongest first.
    private val RULES = listOf(
        Rule(
            id = "AUTHORITY_IMPERSONATION",
            label = "假冒公務／金融機關",
            weight = 45,
            pattern = Regex(
                "(?:警局|警察局|警察|警員|刑警|地檢署|檢察官|法務部|法官|" +
                    "金管會|郵局|中華郵政|銀行|台銀|國泰|玉山|中信)" +
                    "(?:.{0,12})(?:保證金|監管|安全帳戶|清查|凍結|帳戶資料|配合調查)"
            ),
            explanation = "出現「公務／金融機關 + 監管／清查／保證金」組合。實際公務機關不會要求民眾匯款或交付帳戶資料。"
        ),
        Rule(
            id = "SHORT_URL",
            label = "可疑短網址",
            weight = 40,
            pattern = Regex(
                "(?:bit\\.ly|reurl\\.cc|pse\\.is|lihi\\.cc|lihi[0-9]?\\.cc|" +
                    "lin\\.ee|line\\.me/ti/|tinyurl\\.com|t\\.cn|gg\\.gg|" +
                    "ppt\\.cc|0rz\\.tw)/[A-Za-z0-9_\\-]+",
                RegexOption.IGNORE_CASE
            ),
            explanation = "出現短網址，詐騙集團常以短網址隱藏釣魚頁面或假冒網銀。正當機關不會用個人短網址連結。"
        ),
        Rule(
            id = "SUSPICIOUS_TLD",
            label = "可疑頂級網域",
            weight = 30,
            pattern = Regex(
                "https?://[A-Za-z0-9.-]+\\." +
                    "(?:xyz|top|click|live|vip|icu|info|cc|gq|ml|tk|cf|ga|" +
                    "biz|win|loan|trade|date|stream|review|fit|men|country|" +
                    "kim|work|party|science)(?:[/?#:]|\\b)",
                RegexOption.IGNORE_CASE
            ),
            explanation = "網址使用 .xyz/.top/.click/.icu 等冷門便宜 TLD。詐騙集團常用這類匿名／秒發 TLD 架釣魚頁。台灣金融／政府機關官方網域只用 .gov.tw／.com.tw／.net.tw。"
        ),
        Rule(
            id = "BRAND_TYPOSQUAT",
            label = "仿冒機關域名",
            weight = 35,
            pattern = Regex(
                "(?<![A-Za-z0-9])" +
                    "(?:fetc|etag|esun|esunbank|ctbc|cathay|cathaybk|taishin|hncb|" +
                    "megabank|fubon|firstbank|landbank|hnbcbank|sinopac|kgi|tcb|" +
                    "npa|fsc|cib|gov|post|chunghwapost|line|bank|streaming|netflix|" +
                    "disney|pchome|momo|shopee|7-eleven|amazon|apple|icloud)" +
                    "[-_.]" +
                    "(?:tw|secure|safe|safety|bank|banking|verify|verification|" +
                    "login|signin|signup|account|service|services|gov|govt|info|" +
                    "notice|alert|update|center|support|customer|help|recovery|" +
                    "official|portal|payment|pay|fastpay|refund)" +
                    "(?![A-Za-z0-9])",
                RegexOption.IGNORE_CASE
            ),
            explanation = "網址出現「機關／品牌名 + 連字符 + 動作」組合（如 line-secure、fetc-verify、bank-login）。真實品牌域名不會在 host 用這種拼法 — 是仿冒釣魚頁的常見命名模式。"
        ),
        Rule(
            id = "ATM_OPERATION",
            label = "ATM 異常操作指示",
            weight = 40,
            pattern = Regex(
                "(?:英文(?:版|模式|介面)|轉(?:成|為|至)英文|外幣帳戶|" +
                    "按.{0,3}[9零０]|信用卡(?:[++]|加)現金卡|無摺存款)"
            ),
            explanation = "詐騙慣用「ATM 切英文模式 / 外幣帳戶」隱藏真正的轉出金額。一般銀行交易絕對不需切換英文介面。"
        ),
        Rule(
            id = "BANK_ACCOUNT",
            label = "疑似銀行帳號",
            weight = 30,
            pattern = Regex("(?<!\\d)\\d{10,16}(?!\\d)"),
            explanation = "出現 10~16 碼連續數字（疑似銀行帳號）。對方若要求把錢匯到此類號碼，建議撥 165 確認。"
        ),
        Rule(
            id = "MONEY_TRANSFER",
            label = "金額 + 匯款／轉帳",
            weight = 25,
            pattern = Regex(
                "(?:匯|轉|繳|存|匯款|轉帳|繳費|存款)" +
                    "(?:.{0,8})" +
                    "(?:\\d{1,3}(?:,\\d{3})+|\\d{4,})" +
                    "\\s*(?:元|塊|萬|圓)?"
            ),
            explanation = "出現「動詞 + 金額」組合，疑似對方要求匯款。請務必先撥 165 核實。"
        ),
        Rule(
            id = "URGENCY",
            label = "催促語 + 行動",
            weight = 20,
            pattern = Regex(
                "(?:立刻|馬上|現在|盡快|趕快|趕緊|快點|限時|逾期|" +
                    "今(?:天|日)(?:之內|內|前)|24小時內|半小時內|1小時內)" +
                    "(?:.{0,10})" +
                    "(?:匯|轉|繳|存|操作|配合|處理|前往|提供|告訴)"
            ),
            explanation = "出現「立刻／馬上 + 行動」的催促組合。詐騙腳本核心手法之一，目的是讓受害人沒時間查證。"
        ),
        Rule(
            id = "ISOLATION_TACTIC",
            label = "孤立化話術（要求保密）",
            weight = 25,
            pattern = Regex(
                "(?:不要(?:跟|和|對|讓)|不能(?:跟|和|對|讓)|別(?:跟|和|對|讓)|" +
                    "千萬不要(?:跟|和|對|讓)|保密|不要說|不能說|不要告訴)" +
                    "(?:任何人|家人|父母|爸媽|朋友|別人|其他人|警察|" +
                    "ATM(?:旁|附近)的人|行員)"
            ),
            explanation = "出現「要求對家人／警察／行員保密」話術。這是電話詐騙特有腳本，正當機關絕不會要求保密。"
        ),
        Rule(
            id = "CREDENTIAL_REQUEST",
            label = "要求提供帳密／個資",
            weight = 35,
            pattern = Regex(
                "(?:提供|告訴|輸入|傳給?我|傳過來|拍.{0,2}給我|寫給我|報給我)" +
                    "(?:.{0,8})" +
                    "(?:身分證(?:字)?號?|帳號|密碼|驗證碼|信用卡(?:號|背面)?|" +
                    "CVV|OTP|簡訊驗證|金融卡|提款密碼|網銀密碼)"
            ),
            explanation = "對方要求提供身分證／帳號／密碼／驗證碼。任何機構（含銀行、警方）都不會以電話或訊息索取這些資訊。"
        ),
    )
}

data class HardRuleHit(
    val ruleId: String,
    val label: String,
    val matchedText: String,
    val weight: Int,
    val explanation: String
)
