package com.anticyscam.app.ui.mainfunction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anticyscam.app.R
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextDisabled
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedDark

/**
 * Single transfer-account row.
 *
 * - 預設 "臨時用" 顯示為黃色星星，不可刪除，點擊時直接放行（不複製帳號）
 * - 一般帳號顯示為紅框，點擊複製帳號至剪貼簿
 */
@Composable
fun TransferAccountCard(
    account: TransferAccount,
    onClick: (TransferAccount) -> Unit,
    onDelete: (TransferAccount) -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(
            width = if (account.isDefault) 1.5.dp else 2.dp,
            color = if (account.isDefault) AlertYellow else WarningRed
        ),
        onClick = { onClick(account) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account.isDefault) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = AlertYellow,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (account.isDefault) {
                        "${stringResource(R.string.transfer_field_name)}：${account.name}"
                    } else {
                        "${stringResource(R.string.transfer_field_name)}：${account.name}"
                    },
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (account.isDefault) {
                        stringResource(R.string.transfer_default_purpose)
                    } else {
                        "${stringResource(R.string.transfer_field_account)}：${maskAccount(account.accountNumber)}"
                    },
                    color = if (account.isDefault) TextDisabled else TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!account.isDefault && showDelete) {
                IconButton(onClick = { onDelete(account) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "刪除",
                        tint = WarningRedDark
                    )
                }
            } else {
                Box(modifier = Modifier) // keep row height stable
            }
        }
    }
}

/**
 * Show first 4 and last 4 digits to discourage shoulder-surfing while still
 * letting the user confirm the right account before tapping.
 */
private fun maskAccount(number: String): String {
    if (number.length <= 8) return number
    val head = number.substring(0, 4)
    val tail = number.substring(number.length - 4)
    val maskedMiddle = "*".repeat(number.length - 8)
    return "$head$maskedMiddle$tail"
}
