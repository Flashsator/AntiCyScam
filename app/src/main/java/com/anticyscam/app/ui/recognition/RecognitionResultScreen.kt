package com.anticyscam.app.ui.recognition

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anticyscam.app.domain.model.ScamSeverity
import com.anticyscam.app.domain.model.SuspiciousAliasType
import com.anticyscam.app.domain.recognition.HardRuleHit
import com.anticyscam.app.domain.recognition.MatchedTactic
import com.anticyscam.app.domain.recognition.RecognitionMode
import com.anticyscam.app.domain.recognition.RecognitionResult
import com.anticyscam.app.domain.recognition.RiskLevel
import com.anticyscam.app.domain.recognition.SuspiciousNameHit
import com.anticyscam.app.domain.recognition.WarnedAccountHit
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.DividerGray
import com.anticyscam.app.ui.theme.SuccessGreen
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.SurfaceElevated
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedLight

@Composable
fun RecognitionResultScreen(
    result: RecognitionResult,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { RiskHeader(result) }
        item { InputTranscript(result) }
        if (result.accountHits.isNotEmpty()) {
            item {
                Text(
                    text = "⛔ 命中已知警示帳戶 ${result.accountHits.size} 筆",
                    color = WarningRedLight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(result.accountHits.size) { ix ->
                AccountHitCard(result.accountHits[ix])
            }
        }
        if (result.nameHits.isNotEmpty()) {
            item {
                Text(
                    text = "⛔ 命中已知詐騙別名／群組 ${result.nameHits.size} 筆",
                    color = WarningRedLight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(result.nameHits.size) { ix ->
                NameHitCard(result.nameHits[ix])
            }
        }
        if (result.hardRuleHits.isNotEmpty()) {
            item {
                Text(
                    text = "偵測到 ${result.hardRuleHits.size} 項高風險特徵",
                    color = WarningRedLight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(result.hardRuleHits.size) { ix ->
                HardRuleCard(result.hardRuleHits[ix])
            }
        }
        if (result.matches.isEmpty()) {
            item { NoMatchCard() }
        } else {
            item {
                Text(
                    text = "命中 ${result.matches.size} 項詐騙手法",
                    color = AlertYellow,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(result.matches.size) { ix ->
                MatchCard(result.matches[ix])
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            ActionRow(
                showCall165 = result.riskLevel != RiskLevel.SAFE,
                onCall165 = { dial165(context) },
                onReset = onReset
            )
        }
    }
}

@Composable
private fun RiskHeader(result: RecognitionResult) {
    val (label, body, color, icon) = riskCopy(result)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(2.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SurfaceBlack,
                    modifier = Modifier.size(34.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    color = color,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = body,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "風險分數 ${result.maxScore} / 100　|　${modeLabel(result.mode)}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class RiskCopy(val label: String, val body: String, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private fun riskCopy(result: RecognitionResult): RiskCopy = when (result.riskLevel) {
    RiskLevel.HIGH -> RiskCopy(
        label = "⛔ 高度疑似詐騙",
        body = "強烈建議立刻停止與對方往來，並撥打 165 諮詢確認。",
        color = WarningRed,
        icon = Icons.Filled.Warning
    )
    RiskLevel.MEDIUM -> RiskCopy(
        label = "⚠️ 疑似詐騙特徵",
        body = "出現多個詐騙慣用話術，請暫停動作、與本人或家人查證。",
        color = AlertYellow,
        icon = Icons.Filled.WarningAmber
    )
    RiskLevel.LOW -> RiskCopy(
        label = "輕微可疑",
        body = "出現少量可疑詞彙，建議搭配下方手法說明再做判斷。",
        color = WarningRedLight,
        icon = Icons.Filled.WarningAmber
    )
    RiskLevel.SAFE -> RiskCopy(
        label = "未發現明顯詐騙特徵",
        body = "比對詐騙資料庫沒有命中。仍請保持警覺，異常情境可撥 165 諮詢。",
        color = SuccessGreen,
        icon = Icons.Filled.CheckCircle
    )
}

private fun modeLabel(mode: RecognitionMode): String = when (mode) {
    RecognitionMode.TEXT -> "文字辨識"
    RecognitionMode.SCREENSHOT -> "截圖辨識"
    RecognitionMode.VOICE -> "語音辨識"
}

@Composable
private fun InputTranscript(result: RecognitionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, DividerGray)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = when (result.mode) {
                    RecognitionMode.TEXT -> "辨識內容"
                    RecognitionMode.SCREENSHOT -> "圖片擷取文字"
                    RecognitionMode.VOICE -> "語音轉文字結果"
                },
                color = AlertYellow,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = result.inputText.ifBlank { "（沒有可比對的文字內容）" },
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun NoMatchCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, DividerGray)
    ) {
        Text(
            text = "資料庫內未比對到已知詐騙手法。\n但詐騙手法不斷推陳出新，若情境仍有可疑（例如要求匯款、催促行動、保密），請撥打 165 與家人確認。",
            color = TextSecondary,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MatchCard(match: MatchedTactic) {
    val borderColor = when (match.tactic.severity) {
        ScamSeverity.CRITICAL -> WarningRed
        ScamSeverity.HIGH -> AlertYellow
        ScamSeverity.MEDIUM -> DividerGray
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = borderColor
                )
                Text(
                    text = match.tactic.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                ScorePill(match.score, borderColor)
            }
            Text(
                text = match.tactic.description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            if (match.matchedKeywords.isNotEmpty()) {
                Text(
                    text = "命中關鍵字：${match.matchedKeywords.distinct().take(6).joinToString("、")}",
                    color = WarningRedLight,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "防範方式：${match.tactic.protection}",
                color = AlertYellow,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HardRuleCard(hit: HardRuleHit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WarningRed.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, WarningRed)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = WarningRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = hit.label,
                    color = WarningRedLight,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                ScorePill(hit.weight, WarningRed)
            }
            Text(
                text = "命中：「${hit.matchedText}」",
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = hit.explanation,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AccountHitCard(hit: WarnedAccountHit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WarningRed.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, WarningRed)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = WarningRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "已知警示帳戶",
                    color = WarningRedLight,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "帳號：${hit.match.account}" +
                    (if (hit.match.bank.isNotBlank()) "（${hit.match.bank}）" else ""),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (hit.match.note.isNotBlank()) {
                Text(
                    text = hit.match.note,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val attribution = listOf(hit.match.source, hit.match.reportedDate)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (attribution.isNotEmpty()) {
                Text(
                    text = "來源：$attribution",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun NameHitCard(hit: SuspiciousNameHit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WarningRed.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, WarningRed)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = WarningRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = aliasTypeLabel(hit.match.aliasType),
                    color = WarningRedLight,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "比對到：${hit.match.name}",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (hit.match.note.isNotBlank()) {
                Text(
                    text = hit.match.note,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val attribution = listOf(hit.match.source, hit.match.reportedDate)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (attribution.isNotEmpty()) {
                Text(
                    text = "來源：$attribution",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun aliasTypeLabel(type: SuspiciousAliasType): String = when (type) {
    SuspiciousAliasType.PERSON -> "已知詐騙人名"
    SuspiciousAliasType.LINE -> "已知詐騙 LINE 帳號"
    SuspiciousAliasType.IG -> "已知詐騙 IG 帳號"
    SuspiciousAliasType.FB -> "已知詐騙 FB 帳號"
    SuspiciousAliasType.GROUP -> "已知詐騙群組／投資社團"
    SuspiciousAliasType.FAKE_BROKERAGE -> "假冒券商／投顧名稱"
    SuspiciousAliasType.OTHER -> "已知詐騙別名"
}

@Composable
private fun ScorePill(score: Int, color: Color) {
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$score",
            color = SurfaceBlack,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionRow(
    showCall165: Boolean,
    onCall165: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showCall165) {
            Button(
                onClick = onCall165,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "立即撥打 165 諮詢",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DividerGray),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = SurfaceElevated,
                contentColor = TextPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(text = "重新辨識")
        }
    }
}

private fun dial165(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:165"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
