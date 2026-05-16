package com.anticyscam.app.ui.recognition

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.domain.recognition.RecognitionMode
import com.anticyscam.app.domain.recognition.RecognitionResult
import com.anticyscam.app.domain.recognition.ScamDetector
import com.anticyscam.app.ui.recognition.engine.OcrEngine
import com.anticyscam.app.ui.recognition.engine.PcmAudioDecoder
import com.anticyscam.app.ui.recognition.engine.VoskModelManager
import com.anticyscam.app.ui.recognition.engine.VoskSttEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State container for the 3 recognition modalities. The same VM hosts text
 * input, OCR-extracted text, and Vosk-transcribed text — all of them feed the
 * single `analyze(...)` entry point, which routes to [ScamDetector].
 *
 * Phase machine:
 *   - INPUT       : showing the input UI (typing / picking image / picking audio)
 *   - PROCESSING  : OCR or Vosk running; statusMessage is "辨識中…" + progress
 *   - RESULT      : detector finished; `result` is non-null
 *
 * Share-intent flow ([startTextFromShare] / [startScreenshotFromShare] /
 * [startVoiceFromShare]) is invoked from [RecognitionActivity] when launched
 * via `Intent.ACTION_SEND` from another app (LINE, FB, Chrome…). The VM auto
 * runs the matching engine + detector so the user lands directly on the
 * RESULT screen — one tap from share sheet to verdict.
 */
@HiltViewModel
class RecognitionViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val detector: ScamDetector
) : ViewModel() {

    data class State(
        val mode: RecognitionMode = RecognitionMode.TEXT,
        val phase: Phase = Phase.INPUT,
        val draftText: String = "",
        val statusMessage: String = "",
        val errorMessage: String? = null,
        val result: RecognitionResult? = null
    )

    enum class Phase { INPUT, PROCESSING, RESULT }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var shareHandled = false

    fun setMode(mode: RecognitionMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun updateDraft(text: String) {
        _state.update { it.copy(draftText = text, errorMessage = null) }
    }

    fun setError(message: String) {
        _state.update {
            it.copy(phase = Phase.INPUT, errorMessage = message, statusMessage = "")
        }
    }

    fun analyze(text: String) {
        if (text.isBlank()) {
            setError("請輸入或匯入要辨識的內容")
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    draftText = text,
                    phase = Phase.PROCESSING,
                    statusMessage = "比對詐騙資料庫中…",
                    errorMessage = null
                )
            }
            val analysis = detector.analyze(text, _state.value.mode)
            _state.update {
                it.copy(
                    phase = Phase.RESULT,
                    result = analysis,
                    statusMessage = ""
                )
            }
        }
    }

    fun resetToInput() {
        _state.update {
            State(mode = it.mode)
        }
    }

    /**
     * Entry from share sheet for `text/plain` content (LINE / FB / Chrome
     * text selection). Sets TEXT mode + runs detector immediately.
     */
    fun startTextFromShare(text: String) {
        if (shareHandled) return
        shareHandled = true
        setMode(RecognitionMode.TEXT)
        analyze(text)
    }

    /**
     * Entry from in-app Photo Picker. Accepts 1..N images so a long
     * conversation that the user had to split into several screenshots can be
     * OCR'd in one go and analyzed as a single document. OCR runs in
     * [viewModelScope] so it survives the input screen unmounting when phase
     * transitions to PROCESSING.
     */
    fun runScreenshot(uris: List<Uri>) {
        runOcrPipeline(uris)
    }

    /**
     * Entry from share sheet for image content (screenshot from any app).
     * Runs OCR then detector and ends in PROCESSING -> RESULT phase.
     */
    fun startScreenshotFromShare(uri: Uri) {
        if (shareHandled) return
        shareHandled = true
        setMode(RecognitionMode.SCREENSHOT)
        runOcrPipeline(listOf(uri))
    }

    private fun runOcrPipeline(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val total = uris.size
            val texts = mutableListOf<String>()
            uris.forEachIndexed { index, uri ->
                _state.update {
                    it.copy(
                        phase = Phase.PROCESSING,
                        statusMessage = if (total == 1) "辨識圖片文字中…"
                        else "辨識圖片文字中（${index + 1}/$total）…",
                        errorMessage = null
                    )
                }
                val outcome = runCatching { OcrEngine.recognize(appContext, uri) }
                outcome.fold(
                    onSuccess = { text -> if (text.isNotBlank()) texts += text },
                    onFailure = { e ->
                        setError(
                            if (total == 1) "圖片辨識失敗：${e.message ?: "未知錯誤"}"
                            else "圖片辨識失敗（第 ${index + 1} 張）：${e.message ?: "未知錯誤"}"
                        )
                        return@launch
                    }
                )
            }
            val combined = texts.joinToString(separator = "\n\n---\n\n")
            if (combined.isBlank()) {
                setError("圖片中沒有偵測到文字，請改用文字模式手動輸入。")
            } else {
                analyze(combined)
            }
        }
    }

    /**
     * Entry from the in-app audio picker. Runs the Vosk pipeline in
     * [viewModelScope] so it survives [VoiceRecognitionScreen] unmounting when
     * phase transitions to PROCESSING — a composable-scoped coroutine would be
     * cancelled the instant the input screen leaves the composition.
     */
    fun runVoice(uri: Uri) {
        runVoicePipeline(uri)
    }

    /**
     * Entry from share sheet for audio content (recording-app share).
     */
    fun startVoiceFromShare(uri: Uri) {
        if (shareHandled) return
        shareHandled = true
        setMode(RecognitionMode.VOICE)
        runVoicePipeline(uri)
    }

    private fun runVoicePipeline(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = Phase.PROCESSING,
                    statusMessage = "準備中文語音模型…",
                    errorMessage = null
                )
            }
            val outcome = runCatching {
                val model = VoskModelManager.ensureModel(appContext) { phase, _ ->
                    _state.update { s -> s.copy(statusMessage = phase) }
                }
                _state.update { s -> s.copy(statusMessage = "解碼音檔中…") }
                val pcm = PcmAudioDecoder.decodeToPcm16kMono(appContext, uri)
                _state.update { s -> s.copy(statusMessage = "語音辨識中…（檔案越長越久）") }
                VoskSttEngine.transcribe(model, pcm)
            }
            outcome.fold(
                onSuccess = { text ->
                    if (text.isBlank()) {
                        setError("語音中沒有辨識到內容，請確認檔案有清晰人聲。")
                    } else {
                        analyze(text)
                    }
                },
                onFailure = { e ->
                    setError("語音辨識失敗：${e.message ?: "未知錯誤"}")
                }
            )
        }
    }
}
