package com.anticyscam.app.ui.recognition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anticyscam.app.ui.recognition.engine.PcmAudioDecoder
import com.anticyscam.app.ui.recognition.engine.VoskModelManager
import com.anticyscam.app.ui.recognition.engine.VoskSttEngine
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.DividerGray
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextDisabled
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedLight

private val AUDIO_MIME_TYPES = arrayOf(
    "audio/*",
    "audio/mp4",
    "audio/aac",
    "audio/mpeg",
    "audio/amr",
    "audio/3gpp",
    "audio/ogg",
    "audio/wav",
    "audio/x-wav"
)

@Composable
fun VoiceRecognitionScreen(
    errorMessage: String?,
    onProcessing: (String) -> Unit,
    onError: (String) -> Unit,
    onAnalyze: (String) -> Unit
) {
    val context = LocalContext.current
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
        }
    }

    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        onProcessing("準備中文語音模型…")
        val outcome = runCatching {
            val model = VoskModelManager.ensureModel(context) { phase, _ ->
                onProcessing(phase)
            }
            onProcessing("解碼音檔中…")
            val pcm = PcmAudioDecoder.decodeToPcm16kMono(context, uri)
            onProcessing("語音辨識中…（檔案越長越久）")
            VoskSttEngine.transcribe(model, pcm)
        }
        pickedUri = null
        outcome.fold(
            onSuccess = { text ->
                if (text.isBlank()) {
                    onError("語音中沒有辨識到內容，請確認檔案有清晰人聲。")
                } else {
                    onAnalyze(text)
                }
            },
            onFailure = { e ->
                onError("語音辨識失敗：${e.message ?: "未知錯誤"}")
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IntroCard(
            title = "電話語音辨識",
            body = "上傳通話錄音檔（M4A / AAC / MP3 / AMR / WAV）。App 會用內建的中文語音辨識把對話轉成文字，再比對詐騙資料庫。\n\n🔒 完全離線運作，不需網路、不會上傳，安裝完即可使用。"
        )

        EnableRecordingHowto()

        Button(
            onClick = { pickAudio.launch(AUDIO_MIME_TYPES) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WarningRed,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Filled.AudioFile,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "選擇錄音檔開始辨識",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = WarningRedLight,
                style = MaterialTheme.typography.bodySmall
            )
        }

        TipsBlock(
            tips = listOf(
                "首次使用會先解壓內建語音模型（約 5~10 秒），之後直接使用。",
                "Android 系統不允許 App 直接擷取通話聲音，請使用手機內建的通話錄音功能。",
                "辨識速度取決於錄音長度與裝置效能，10 分鐘的通話約 1~2 分鐘可完成。"
            )
        )
    }
}

@Composable
private fun EnableRecordingHowto() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, DividerGray)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = AlertYellow,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "如何取得通話錄音檔？",
                    color = AlertYellow,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Android 為了保護隱私，禁止第三方 App 擷取通話聲音。請先在手機內建撥號 App 開啟「通話自動錄音」，掛斷後再回到防詐器上傳檔案。",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            HowtoLine(brand = "小米 / Redmi", path = "電話 → 右上角選單 → 通話設定 → 通話錄音 → 全部通話自動錄音")
            HowtoLine(brand = "三星 Samsung", path = "電話 → 設定 → 錄製通話 → 自動錄製通話")
            HowtoLine(brand = "OPPO / realme", path = "電話 → 右下角更多 → 通話錄音 → 全部通話自動錄音")
            HowtoLine(brand = "Vivo", path = "電話 → 設定 → 通話錄音 → 全部通話")
            HowtoLine(brand = "Google Pixel", path = "原生系統未提供，請改用第三方錄音 App 對著喇叭錄音")
            Text(
                text = "錄音檔位置一般在「我的檔案 → Call recordings / Recordings / MIUI/sound_recorder」資料夾。",
                color = TextDisabled,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HowtoLine(brand: String, path: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "・$brand",
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "　　$path",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
