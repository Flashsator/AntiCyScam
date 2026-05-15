package com.anticyscam.app.domain.recognition

import com.anticyscam.app.data.repository.ScamInfoRepository
import com.anticyscam.app.domain.model.ScamSeverity
import com.anticyscam.app.domain.model.ScamTactic
import com.anticyscam.app.domain.model.SuspiciousName
import com.anticyscam.app.domain.model.WarnedAccount
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Three-layer detector: catalog keyword scoring, fuzzy redFlag matching, and
 * (voice mode only) pinyin-space matching for homophone errors. The
 * [HardRuleEngine] runs in parallel and contributes both to the risk verdict
 * and to a dedicated UI section.
 *
 * Per-tactic scoring (unchanged from v1, but matching is now fuzzy):
 *   - Each tag matched → +30
 *   - Each quoted phrase from a redFlag matched → +20
 *   - Title segments (split by / 、 space) matched → +15
 *
 * Score is normalized to 0..100 by max-possible-weight.
 *
 * Risk level (rolls in hard-rule weight so brand-new scams without catalog
 * coverage still surface as HIGH/MEDIUM):
 *   - HIGH: any CRITICAL tactic ≥ 25, any tactic ≥ 55, OR hard-rule sum ≥ 60
 *   - MEDIUM: any tactic ≥ 30 OR hard-rule sum ≥ 30
 *   - LOW: anything > 0
 *   - SAFE: no hits at all
 */
@Singleton
class ScamDetector @Inject constructor(
    private val repository: ScamInfoRepository
) {
    suspend fun analyze(input: String, mode: RecognitionMode): RecognitionResult {
        val normalized = input.trim()
        if (normalized.isEmpty()) {
            return RecognitionResult(
                inputText = input,
                mode = mode,
                riskLevel = RiskLevel.SAFE,
                maxScore = 0,
                matches = emptyList(),
                hardRuleHits = emptyList()
            )
        }
        val catalog = repository.load()
        val lowered = normalized.lowercase()
        val pinyinInput = if (mode == RecognitionMode.VOICE && PinyinNormalizer.isAvailable()) {
            PinyinNormalizer.toPinyin(normalized)
        } else {
            null
        }

        val hardRuleHits = HardRuleEngine.detect(normalized)
        val hardRuleWeight = hardRuleHits.sumOf { it.weight }

        val nameHits = detectNameHits(normalized, lowered, catalog.suspiciousNames)
        val accountHits = detectAccountHits(hardRuleHits, catalog.warnedAccounts)

        val scored = catalog.tactics.map { tactic ->
            val keywords = tactic.signalKeywords()
            val hits = keywords.filter { kw ->
                kw.text.isNotEmpty() && matches(kw, lowered, pinyinInput)
            }
            val totalWeight = keywords.sumOf { it.weight }.coerceAtLeast(1)
            val matchedWeight = hits.sumOf { it.weight }
            val score = (matchedWeight * 100 / totalWeight).coerceIn(0, 100)
            MatchedTactic(
                tactic = tactic,
                score = score,
                matchedKeywords = hits.map { it.text }
            )
        }
            .filter { it.score > 0 }
            .sortedByDescending { weighedRank(it) }

        val maxScore = scored.firstOrNull()?.score ?: 0
        val anyCriticalHit = scored.any {
            it.tactic.severity == ScamSeverity.CRITICAL && it.score >= 25
        }
        // Blacklist hits (curated警示帳戶 / 已知詐騙別名) are ground truth, not
        // heuristics — any single hit forces HIGH regardless of score math.
        val riskLevel = when {
            accountHits.isNotEmpty() || nameHits.isNotEmpty() -> RiskLevel.HIGH
            anyCriticalHit || maxScore >= 55 || hardRuleWeight >= 60 -> RiskLevel.HIGH
            maxScore >= 30 || hardRuleWeight >= 30 -> RiskLevel.MEDIUM
            maxScore > 0 || hardRuleWeight > 0 -> RiskLevel.LOW
            else -> RiskLevel.SAFE
        }

        return RecognitionResult(
            inputText = input,
            mode = mode,
            riskLevel = riskLevel,
            maxScore = maxScore,
            matches = scored.take(MAX_MATCHES),
            hardRuleHits = hardRuleHits,
            nameHits = nameHits,
            accountHits = accountHits
        )
    }

    private fun matches(keyword: Keyword, loweredHaystack: String, pinyinHaystack: String?): Boolean {
        val needle = keyword.text.lowercase()
        if (loweredHaystack.contains(needle)) return true

        // Fuzzy only meaningful for 3+ char needles — protects against noise.
        if (needle.length >= 3 && FuzzyMatch.containsFuzzy(loweredHaystack, needle, maxEdits = 1)) {
            return true
        }

        // Voice-mode homophone fallback: compare in pinyin space.
        if (pinyinHaystack != null && needle.length >= 2) {
            val pinyinNeedle = PinyinNormalizer.toPinyin(needle)
            if (pinyinNeedle.isNotBlank() && pinyinNeedle != needle) {
                if (pinyinHaystack.contains(pinyinNeedle)) return true
                if (pinyinNeedle.length >= 4 &&
                    FuzzyMatch.containsFuzzy(pinyinHaystack, pinyinNeedle, maxEdits = 1)
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun weighedRank(match: MatchedTactic): Int {
        val severityBoost = when (match.tactic.severity) {
            ScamSeverity.CRITICAL -> 30
            ScamSeverity.HIGH -> 15
            ScamSeverity.MEDIUM -> 0
        }
        return match.score + severityBoost
    }

    private fun detectNameHits(
        original: String,
        lowered: String,
        catalog: List<SuspiciousName>
    ): List<SuspiciousNameHit> {
        if (catalog.isEmpty()) return emptyList()
        return catalog.mapNotNull { entry ->
            val needle = entry.name.trim()
            if (needle.length < 2) return@mapNotNull null
            val haystack = if (needle.any { it.isLetter() && it.code < 128 }) lowered else original
            val target = if (needle.any { it.isLetter() && it.code < 128 }) needle.lowercase() else needle
            if (haystack.contains(target)) SuspiciousNameHit(entry, entry.name) else null
        }
    }

    private fun detectAccountHits(
        hardRuleHits: List<HardRuleHit>,
        catalog: List<WarnedAccount>
    ): List<WarnedAccountHit> {
        if (catalog.isEmpty()) return emptyList()
        val detectedDigits = hardRuleHits
            .filter { it.ruleId == BANK_ACCOUNT_RULE_ID }
            .map { it.matchedText.filter(Char::isDigit) }
            .filter { it.isNotEmpty() }
        if (detectedDigits.isEmpty()) return emptyList()
        return catalog.mapNotNull { warned ->
            val candidate = warned.account.filter(Char::isDigit)
            val matched = detectedDigits.firstOrNull { it == candidate }
            if (matched != null) WarnedAccountHit(warned, matched) else null
        }
    }

    private data class Keyword(val text: String, val weight: Int)

    private fun ScamTactic.signalKeywords(): List<Keyword> = buildList {
        tags.forEach { add(Keyword(it, 30)) }
        redFlags.forEach { flag ->
            extractQuotedSegments(flag).forEach { seg ->
                if (seg.length >= 2) add(Keyword(seg, 20))
            }
        }
        title.split('／', '/', ' ', '、', '：', ':').forEach { seg ->
            val clean = seg.trim()
            if (clean.length >= 2) add(Keyword(clean, 15))
        }
    }

    /**
     * Pulls 「…」 / 『…』 / "..." segments out of a red-flag sentence — those
     * are the distinctive scam phrases (e.g. 「監管帳戶」, 「保證獲利」).
     */
    private fun extractQuotedSegments(sentence: String): List<String> {
        val out = mutableListOf<String>()
        val regex = Regex("[「『\"](.+?)[」』\"]")
        regex.findAll(sentence).forEach { m ->
            out += m.groupValues[1].trim()
        }
        return out
    }

    private companion object {
        const val MAX_MATCHES = 5
        const val BANK_ACCOUNT_RULE_ID = "BANK_ACCOUNT"
    }
}
