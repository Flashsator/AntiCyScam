package com.anticyscam.app.ui.recognition.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Feeds a complete 16 kHz mono PCM byte array through a Vosk [Recognizer] in
 * chunks and returns the concatenated transcript.
 *
 * Vosk's Java/JNI binding wants `short[]` chunks; we cap chunk size to keep
 * native calls reasonable. The cn model emits Simplified Chinese, so the
 * transcript is converted to Traditional via [ChineseScriptConverter] before
 * being returned.
 */
object VoskSttEngine {

    private const val CHUNK_BYTES = 8_000  // 0.25s @ 16kHz mono
    private const val SAMPLE_RATE_F = 16_000f

    suspend fun transcribe(model: Model, pcm16kMono: ByteArray): String =
        withContext(Dispatchers.Default) {
            val recognizer = Recognizer(model, SAMPLE_RATE_F)
            val transcript = StringBuilder()
            try {
                var offset = 0
                while (offset < pcm16kMono.size) {
                    val len = minOf(CHUNK_BYTES, pcm16kMono.size - offset)
                    // Vosk's acceptWaveForm(byte[], int) always reads from index
                    // 0, so feed it a fresh slice — passing the whole array would
                    // re-feed the first chunk every iteration.
                    val chunk = pcm16kMono.copyOfRange(offset, offset + len)
                    val ended = recognizer.acceptWaveForm(chunk, len)
                    if (ended) {
                        appendPartialResult(transcript, recognizer.result)
                    } else {
                        // not finalized — discard partial here, only commit on finalization
                    }
                    offset += len
                }
                appendPartialResult(transcript, recognizer.finalResult)
            } finally {
                runCatching { recognizer.close() }
            }
            ChineseScriptConverter.toTraditional(transcript.toString().trim())
        }

    private fun appendPartialResult(sb: StringBuilder, raw: String) {
        val text = runCatching {
            JSONObject(raw).optString("text", "")
        }.getOrDefault("")
        if (text.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(text)
        }
    }
}
