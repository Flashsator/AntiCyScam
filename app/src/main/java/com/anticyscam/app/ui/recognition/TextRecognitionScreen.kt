package com.anticyscam.app.ui.recognition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.DividerGray
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextDisabled
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedLight

@Composable
fun TextRecognitionScreen(
    draft: String,
    errorMessage: String?,
    onDraftChange: (String) -> Unit,
    onAnalyze: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val noRippleInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = noRippleInteraction,
                indication = null
            ) { focusManager.clearFocus() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IntroCard(
            title = "文字辨識",
            body = "貼上可疑的簡訊、LINE 訊息、Email 內容，系統會比對台灣常見詐騙手法資料庫，協助您判斷是否為詐騙。\n\n🔒 完全離線比對，不會上傳任何內容。"
        )

        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = {
                Text(
                    text = "貼上可疑訊息、簡訊、LINE 對話內容…",
                    color = TextDisabled
                )
            },
            trailingIcon = if (draft.isNotEmpty()) {
                {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除內容",
                        tint = TextSecondary,
                        modifier = Modifier.clickable {
                            onDraftChange("")
                            focusManager.clearFocus()
                        }
                    )
                }
            } else null,
            minLines = 6,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = WarningRed,
                unfocusedBorderColor = DividerGray,
                cursorColor = WarningRed,
                focusedContainerColor = SurfaceDim,
                unfocusedContainerColor = SurfaceDim
            )
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = WarningRedLight,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                onAnalyze(draft)
            },
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
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "開始辨識",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        TipsBlock(
            tips = listOf(
                "辨識結果僅供參考，仍請以撥打 165 諮詢為準。",
                "字越多越完整、判斷越精準（包含金額、帳號、時間、稱呼等）。",
                "判讀後若有任何疑慮，立即停止匯款動作。"
            )
        )
    }
}

@Composable
internal fun IntroCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, WarningRed)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = AlertYellow,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun TipsBlock(tips: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "使用提示",
            color = AlertYellow,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        tips.forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "・", color = TextDisabled)
                Text(
                    text = tip,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
