package com.anticyscam.app.ui.recognition

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.anticyscam.app.R
import com.anticyscam.app.utils.CallRecordingLauncher
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

/**
 * 語音辨識輸入畫面。選檔後把 uri 交給 [onPicked]（接到
 * `RecognitionViewModel.runVoice`），辨識流程一律跑在 viewModelScope。
 *
 * 不可在此 composable 內用 LaunchedEffect 跑辨識：流程第一步就會把 phase
 * 切到 PROCESSING，本畫面隨即離開 composition，composable-scoped 協程會被
 * 立即取消（"the coroutine scope left the composition"）。
 */
@Composable
fun VoiceRecognitionScreen(
    errorMessage: String?,
    onPicked: (Uri) -> Unit
) {
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onPicked(uri)
        }
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
            body = "上傳通話錄音檔（M4A / AAC / MP3 / AMR / WAV）。App 會用內建的中文語音辨識把對話轉成文字，再比對詐騙資料庫。\n\n⚠️ 通話結束自動偵測功能僅部分機種適用（小米／三星／OPPO／Vivo 等原廠支援自動錄音的機型）。\n\n🔒 完全離線運作，不需網路、不會上傳，安裝完即可使用。"
        )

        EnableRecordingHowto()

        AutoDetectCard()

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
    val context = LocalContext.current
    val directHint = stringResource(R.string.voice_open_call_recording_hint)
    val fallbackHint = stringResource(R.string.voice_open_call_recording_fallback_hint)
    val launchFailedTemplate = stringResource(R.string.gate_launch_failed)
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
            Button(
                onClick = {
                    val result = CallRecordingLauncher.launch(context)
                    val msg = when {
                        !result.launched -> launchFailedTemplate.format(
                            result.error?.javaClass?.simpleName ?: "Unknown"
                        )
                        result.isDirect -> directHint
                        else -> fallbackHint
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlertYellow,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PhoneInTalk,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.voice_open_call_recording_settings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "若按鈕沒帶你到「通話錄音」頁，請依下方各品牌路徑手動進入：",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
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
private fun AutoDetectCard() {
    val context = LocalContext.current
    val mediaPerm = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val requiredPerms = remember(mediaPerm) {
        arrayOf(Manifest.permission.READ_PHONE_STATE, mediaPerm)
    }
    fun granted(): Boolean = requiredPerms.all { p ->
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }
    var allGranted by remember { mutableStateOf(granted()) }
    val deniedHint = stringResource(R.string.voice_autodetect_denied_hint)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ok = result.values.all { it }
        allGranted = ok || granted()
        if (!ok && !allGranted) {
            Toast.makeText(context, deniedHint, Toast.LENGTH_LONG).show()
        }
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Filled.AutoMode,
                    contentDescription = null,
                    tint = AlertYellow,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.voice_autodetect_title),
                    color = AlertYellow,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = stringResource(R.string.voice_autodetect_body),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            if (allGranted) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AlertYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.voice_autodetect_enabled),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = { permLauncher.launch(requiredPerms) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertYellow,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoMode,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.voice_autodetect_enable),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
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
