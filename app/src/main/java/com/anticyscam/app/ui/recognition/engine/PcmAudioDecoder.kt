package com.anticyscam.app.ui.recognition.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Decodes a user-picked audio file (M4A / AAC / MP3 / WAV / OGG / AMR) to raw
 * 16-bit signed PCM at 16 kHz mono — the input format Vosk speech models
 * expect. Uses Android's built-in MediaExtractor + MediaCodec; no JNI deps.
 *
 * The output is a `ByteArray` of little-endian 16-bit samples (so a 1-second
 * clip is 32_000 bytes). For long audio this can be large; the analyzer
 * doesn't buffer the entire file at once, but we trade a bit of RAM for code
 * simplicity here.
 */
object PcmAudioDecoder {

    private const val TARGET_SAMPLE_RATE = 16_000
    private const val TIMEOUT_US = 10_000L

    suspend fun decodeToPcm16kMono(context: Context, uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
                requireNotNull(pfd) { "無法開啟音檔" }
                extractor.setDataSource(pfd.fileDescriptor)
            }

            val audioTrack = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: run {
                extractor.release()
                error("檔案中找不到音訊軌道")
            }
            extractor.selectTrack(audioTrack)
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("音訊格式不明")
            val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val pcmStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            try {
                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inIx = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIx >= 0) {
                            val buf = codec.getInputBuffer(inIx) ?: continue
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIx = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outIx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                val chunk = ByteArray(bufferInfo.size)
                                outBuf.position(bufferInfo.offset)
                                outBuf.get(chunk, 0, bufferInfo.size)
                                val downmixed = downmixAndResample(
                                    pcm = chunk,
                                    sourceSampleRate = sourceSampleRate,
                                    sourceChannels = sourceChannels
                                )
                                pcmStream.write(downmixed)
                            }
                            codec.releaseOutputBuffer(outIx, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEos = true
                            }
                        }
                        outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // ignore — we'll keep using the source-format sample rate
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                runCatching { extractor.release() }
            }
            pcmStream.toByteArray()
        }

    /**
     * Convert source PCM bytes → mono 16kHz little-endian short PCM.
     * - Stereo/multi-channel: average to mono.
     * - Resample: nearest-neighbor (good enough for STT; trades quality for
     *   simplicity, Vosk is robust to it).
     */
    private fun downmixAndResample(
        pcm: ByteArray,
        sourceSampleRate: Int,
        sourceChannels: Int
    ): ByteArray {
        val srcShorts = ShortBuffer.allocate(pcm.size / 2)
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(srcShorts.array())
        val srcFrameCount = srcShorts.capacity() / sourceChannels
        if (srcFrameCount == 0) return ByteArray(0)

        // Downmix to mono
        val mono = ShortArray(srcFrameCount)
        for (frame in 0 until srcFrameCount) {
            var sum = 0
            for (ch in 0 until sourceChannels) {
                sum += srcShorts.get(frame * sourceChannels + ch)
            }
            mono[frame] = (sum / sourceChannels).toShort()
        }

        if (sourceSampleRate == TARGET_SAMPLE_RATE) {
            return mono.toLittleEndianBytes()
        }

        // Nearest-neighbor resample to 16kHz
        val ratio = sourceSampleRate.toDouble() / TARGET_SAMPLE_RATE
        val outFrames = (srcFrameCount / ratio).toInt()
        val out = ShortArray(outFrames)
        for (i in 0 until outFrames) {
            val srcIx = (i * ratio).toInt().coerceAtMost(srcFrameCount - 1)
            out[i] = mono[srcIx]
        }
        return out.toLittleEndianBytes()
    }

    private fun ShortArray.toLittleEndianBytes(): ByteArray {
        val out = ByteArray(size * 2)
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        forEach { bb.putShort(it) }
        return out
    }
}
