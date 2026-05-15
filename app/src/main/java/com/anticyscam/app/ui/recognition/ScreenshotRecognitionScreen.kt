package com.anticyscam.app.ui.recognition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedLight

private const val MAX_PICK_IMAGES = 6

@Composable
fun ScreenshotRecognitionScreen(
    errorMessage: String?,
    onPicked: (List<Uri>) -> Unit
) {
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPicked(uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IntroCard(
            title = "圖片辨識",
            body = "選擇可疑的對話截圖、簡訊截圖、社群貼文截圖，App 會抓出圖片裡的中文字並比對詐騙資料庫。\n\n一次最多 ${MAX_PICK_IMAGES} 張，多張會合併成一段文字一起判讀，適合長對話被分多張截圖的情境。\n\n🔒 完全離線辨識，圖片不會上傳。"
        )

        Button(
            onClick = {
                pickImages.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WarningRed,
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, WarningRed)
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "選擇圖片開始辨識",
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
                "建議使用清晰、文字不被裁切的整張對話截圖。",
                "支援繁體與簡體中文，無法辨識手寫字。",
                "可一次選 1 ~ ${MAX_PICK_IMAGES} 張，多張會合併判讀。",
                "辨識完文字後仍會比對詐騙資料庫並給出風險等級。"
            )
        )
    }
}
