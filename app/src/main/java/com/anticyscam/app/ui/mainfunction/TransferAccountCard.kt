package com.anticyscam.app.ui.mainfunction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anticyscam.app.R
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SuccessGreen
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextDisabled
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedDark

/**
 * Single transfer-account row.
 *
 * Status-driven styling (PRD § 3.1):
 *   - Default 臨時用 — yellow star, no delete, no copy on tap.
 *   - 🟢 Normal      — red border, copies on tap.
 *   - 🟡 InCooldown  — yellow border + countdown badge, treated as 臨時用.
 *   - 💤 Dormant     — gray border + dormant badge, requires confirmation.
 */
@Composable
fun TransferAccountCard(
    account: TransferAccount,
    status: TransferAccount.Status,
    onClick: (TransferAccount) -> Unit,
    onDelete: (TransferAccount) -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean = true,
    onEdit: ((TransferAccount) -> Unit)? = null
) {
    val accentColor = when (status) {
        TransferAccount.Status.Default -> AlertYellow
        TransferAccount.Status.Normal -> WarningRed
        is TransferAccount.Status.InCooldown -> AlertYellow
        TransferAccount.Status.Dormant -> TextDisabled
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(width = 2.dp, color = accentColor),
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
                Text(
                    text = stringResource(R.string.transfer_default_purpose),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.transfer_field_name)}：${account.name}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${stringResource(R.string.transfer_field_account)}：${maskAccount(account.accountNumber)}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    StatusBadge(status = status)
                }
                if (onEdit != null && account.editsRemaining > 0) {
                    IconButton(onClick = { onEdit(account) }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "編輯",
                            tint = AlertYellow
                        )
                    }
                }
                if (showDelete) {
                    IconButton(onClick = { onDelete(account) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "刪除",
                            tint = WarningRedDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TransferAccount.Status) {
    val (label, fg, bg) = when (status) {
        TransferAccount.Status.Default ->
            Triple(stringResource(R.string.account_status_default), AlertYellow, SurfaceDim)
        TransferAccount.Status.Normal ->
            Triple(stringResource(R.string.account_status_normal), SuccessGreen, SurfaceDim)
        is TransferAccount.Status.InCooldown ->
            Triple(cooldownLabel(status), AlertYellow, SurfaceDim)
        TransferAccount.Status.Dormant ->
            Triple(stringResource(R.string.account_status_dormant), TextDisabled, SurfaceDim)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun cooldownLabel(status: TransferAccount.Status.InCooldown): String {
    val hours = (status.remainingMs / (60L * 60 * 1000)).coerceAtLeast(0)
    val mins = ((status.remainingMs / (60L * 1000)) % 60).coerceAtLeast(0)
    val timeLabel = if (hours > 0) "${hours}h" else "${mins}m"
    return stringResource(R.string.account_status_cooldown) +
        " · " +
        stringResource(R.string.account_cooldown_remaining, timeLabel)
}

@Suppress("unused")
private fun unusedColorRef(): Color = Color.Transparent

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
