package com.anticyscam.app.ui.recognition.engine

import com.github.houbb.opencc4j.util.ZhConverterUtil

/**
 * Converts Simplified Chinese to Traditional Chinese.
 *
 * The bundled Vosk model (`vosk-model-small-cn-0.22`) is trained on Mandarin
 * and emits Simplified output. The scam catalog and the whole app UI are
 * Traditional (Taiwan), and [ScamDetector] matches keywords with `contains`,
 * so a Simplified transcript silently fails every catalog lookup. Converting
 * the transcript to Traditional fixes both the displayed text and matching.
 *
 * OpenCC4j does phrase-level conversion, so context-sensitive characters
 * (后→後/后, 发→髮/發, 里→裡/里…) resolve correctly. Conversion is best-effort:
 * on any failure the original text is returned rather than throwing.
 */
object ChineseScriptConverter {

    fun toTraditional(text: String): String {
        if (text.isBlank()) return text
        return runCatching { ZhConverterUtil.toTraditional(text) }
            .getOrDefault(text)
    }
}
