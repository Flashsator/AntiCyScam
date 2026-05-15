package com.anticyscam.app.ui.recognition

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anticyscam.app.domain.recognition.RecognitionMode
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import dagger.hilt.android.AndroidEntryPoint

/**
 * Host activity for the 3 recognition modalities (text / screenshot / voice).
 * Launched from 防詐專區 top toolbar via [intent].
 *
 * Routing rule:
 *   - Phase.INPUT      → mode-specific input screen
 *   - Phase.PROCESSING → centered spinner + status message overlay
 *   - Phase.RESULT     → shared [RecognitionResultScreen]
 */
@AndroidEntryPoint
class RecognitionActivity : ComponentActivity() {

    private val viewModel: RecognitionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!handleShareIntent(intent)) {
            val modeName = intent.getStringExtra(EXTRA_MODE) ?: RecognitionMode.TEXT.name
            val mode = runCatching { RecognitionMode.valueOf(modeName) }
                .getOrDefault(RecognitionMode.TEXT)
            viewModel.setMode(mode)
        }

        enableEdgeToEdge()
        setContent {
            AntiCyScamTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceBlack),
                    color = SurfaceBlack
                ) {
                    RecognitionHost(
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    /**
     * Dispatches `Intent.ACTION_SEND` from other apps (LINE / FB / Chrome /
     * screenshot apps / recording apps) to the matching recognition pipeline.
     * Returns true when the intent was a share, so the caller skips the
     * normal mode-from-extras setup.
     */
    private fun handleShareIntent(intent: Intent?): Boolean {
        if (intent == null || intent.action != Intent.ACTION_SEND) return false
        val type = intent.type.orEmpty()
        return when {
            type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text.isNullOrBlank()) false
                else { viewModel.startTextFromShare(text); true }
            }
            type.startsWith("image/") -> {
                val uri = extractStreamUri(intent)
                if (uri == null) false
                else { viewModel.startScreenshotFromShare(uri); true }
            }
            type.startsWith("audio/") -> {
                val uri = extractStreamUri(intent)
                if (uri == null) false
                else { viewModel.startVoiceFromShare(uri); true }
            }
            else -> false
        }
    }

    private fun extractStreamUri(intent: Intent): Uri? =
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

    companion object {
        private const val EXTRA_MODE = "extra_mode"

        fun intent(context: Context, mode: RecognitionMode): Intent =
            Intent(context, RecognitionActivity::class.java)
                .putExtra(EXTRA_MODE, mode.name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecognitionHost(
    viewModel: RecognitionViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val title = when (state.mode) {
        RecognitionMode.TEXT -> "文字辨識"
        RecognitionMode.SCREENSHOT -> "圖片辨識"
        RecognitionMode.VOICE -> "語音辨識"
    }
    Scaffold(
        containerColor = SurfaceBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceBlack,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.phase) {
                RecognitionViewModel.Phase.INPUT -> InputBody(state, viewModel)
                RecognitionViewModel.Phase.PROCESSING -> ProcessingOverlay(state.statusMessage)
                RecognitionViewModel.Phase.RESULT -> {
                    val result = state.result
                    if (result != null) {
                        RecognitionResultScreen(
                            result = result,
                            onReset = viewModel::resetToInput
                        )
                    } else {
                        InputBody(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBody(
    state: RecognitionViewModel.State,
    viewModel: RecognitionViewModel
) {
    when (state.mode) {
        RecognitionMode.TEXT -> TextRecognitionScreen(
            draft = state.draftText,
            errorMessage = state.errorMessage,
            onDraftChange = viewModel::updateDraft,
            onAnalyze = viewModel::analyze
        )
        RecognitionMode.SCREENSHOT -> ScreenshotRecognitionScreen(
            errorMessage = state.errorMessage,
            onPicked = viewModel::runScreenshot
        )
        RecognitionMode.VOICE -> VoiceRecognitionScreen(
            errorMessage = state.errorMessage,
            onProcessing = viewModel::setProcessing,
            onError = viewModel::setError,
            onAnalyze = viewModel::analyze
        )
    }
}

@Composable
private fun ProcessingOverlay(statusMessage: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                color = WarningRed,
                modifier = Modifier.size(54.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusMessage.ifBlank { "處理中…" },
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "辨識在手機本地進行，不會上傳任何內容。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
