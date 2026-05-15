package com.anticyscam.app.domain.recognition

/**
 * Bounded sliding-window Levenshtein used to catch OCR / STT 1-char errors.
 *
 * Scam scripts are short, high-information strings ("監管帳戶", "保證金"); OCR
 * frequently mis-reads one Han character (e.g. 監→盤) and STT homophones
 * substitute look/sound-alikes. Pure substring matching misses these.
 *
 * Strategy:
 *   - For very short needles (≤ 2 chars), demand exact substring — fuzzy
 *     matching on 2 chars produces noise.
 *   - Otherwise slide a window across the haystack at sizes
 *     [needle.length - maxEdits .. needle.length + maxEdits] and accept the
 *     first window with edit distance ≤ maxEdits.
 *   - Bounded Levenshtein with early-exit when the row minimum exceeds the
 *     limit keeps this O(haystack * needle * maxEdits).
 */
internal object FuzzyMatch {

    fun containsFuzzy(haystack: String, needle: String, maxEdits: Int): Boolean {
        if (needle.isEmpty()) return false
        if (haystack.contains(needle)) return true
        if (needle.length <= 2 || maxEdits <= 0) return false

        val minLen = (needle.length - maxEdits).coerceAtLeast(1)
        val maxLen = needle.length + maxEdits
        if (haystack.length < minLen) return false

        for (start in 0..(haystack.length - minLen)) {
            val end = (start + maxLen).coerceAtMost(haystack.length)
            for (windowLen in minLen..(end - start)) {
                val window = haystack.substring(start, start + windowLen)
                if (boundedLevenshtein(window, needle, maxEdits) <= maxEdits) {
                    return true
                }
            }
        }
        return false
    }

    private fun boundedLevenshtein(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,        // deletion
                    curr[j - 1] + 1,    // insertion
                    prev[j - 1] + cost  // substitution
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > limit) return limit + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
