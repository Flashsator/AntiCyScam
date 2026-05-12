package com.anticyscam.app.ui.scaminfo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed

/**
 * 詐騙專區 — 列出常見台灣詐騙手法 + 反詐器的對應防護方式。
 * 內容為固定文案，由本地維護，不打網路（避免 PII / 廣告風險）。
 */
@Composable
fun ScamInfoScreen() {
    val items = remember { ScamTip.DEFAULTS }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "詐騙專區",
                    color = WarningRed,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "了解常見詐騙手法，遇到可疑情況請務必撥打 165 反詐騙專線。",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(items = items, key = { it.title }) { tip ->
            ScamTipCard(tip)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDim),
                border = BorderStroke(1.dp, AlertYellow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "緊急時請撥打 165",
                        color = AlertYellow,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "165 反詐騙諮詢專線（24 小時服務）：可諮詢、報案、查詢可疑帳號。",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ScamTipCard(tip: ScamTip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, WarningRed)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = WarningRed
                )
                Text(
                    text = tip.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = tip.description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "保護方式：${tip.protection}",
                color = AlertYellow,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private data class ScamTip(
    val title: String,
    val description: String,
    val protection: String
) {
    companion object {
        val DEFAULTS = listOf(
            ScamTip(
                title = "假冒檢警／公務員",
                description = "歹徒冒充檢察官、警察或法院人員，告知您涉及洗錢、刑案，要求監管帳戶或匯款。",
                protection = "真正的檢警「絕對不會」要求您匯款或提供存簿密碼。立即撥打 165 確認。"
            ),
            ScamTip(
                title = "假網銀／釣魚連結",
                description = "簡訊、Line 訊息或 Email 附帶網銀登入連結，網址近似但非真實網域，騙取帳密。",
                protection = "切勿從訊息點擊連結。請使用反詐器內已綁定的網銀 App 開啟，避免進入假網站。"
            ),
            ScamTip(
                title = "投資詐騙",
                description = "標榜「保證獲利」、「內線消息」、「飆股群組」，要求加入私密群組或下載假投資 App。",
                protection = "任何標榜保證獲利皆為詐騙。投資前請查證該機構是否為金管會合法業者。"
            ),
            ScamTip(
                title = "ATM／網銀解除分期付款",
                description = "歹徒謊稱您誤訂分期付款，要求至 ATM 或網銀「依語音指示」操作。",
                protection = "ATM 與網銀絕無「解除分期」功能，任何指示操作 ATM 的電話皆為詐騙。"
            ),
            ScamTip(
                title = "假交友／投資戀人",
                description = "在交友 App 認識的對象迅速建立感情，誘導投資「他懂的飆股」或匯款救急。",
                protection = "網路交友從未見面就談錢，幾乎必為詐騙。任何匯款要求請立即停止互動。"
            ),
            ScamTip(
                title = "假購物退款",
                description = "冒充電商客服稱訂單異常、需協助退款，要求提供信用卡或操作 ATM。",
                protection = "電商真的退款只會退回原付款工具，絕不需要您操作 ATM 或提供卡片資訊。"
            )
        )
    }
}
