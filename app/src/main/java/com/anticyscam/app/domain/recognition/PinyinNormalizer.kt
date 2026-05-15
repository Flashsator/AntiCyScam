package com.anticyscam.app.domain.recognition

import android.icu.text.Transliterator

/**
 * Wraps Android's ICU `Transliterator` to convert Han characters → ASCII pinyin
 * for voice-mode homophone matching.
 *
 * Vosk small-cn STT frequently confuses homophones (監管 ↔ 鑒管, 帳戶 ↔ 賬戶,
 * 警察 ↔ 警查). Pinyin-space matching collapses those into the same key. We
 * pair this with [FuzzyMatch] (edit distance 1) so single-syllable noise still
 * lands.
 *
 * Tones are stripped (`Latin-ASCII`) and whitespace removed so "jiānguǎn" and
 * "jian guan" both normalize to "jianguan".
 */
internal object PinyinNormalizer {

    private val transliterator: Transliterator? by lazy {
        runCatching {
            Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
        }.getOrNull()
    }

    fun isAvailable(): Boolean = transliterator != null

    fun toPinyin(input: String): String {
        val t = transliterator ?: return input.lowercase()
        return t.transliterate(input).replace(WHITESPACE, "")
    }

    private val WHITESPACE = Regex("\\s+")
}
