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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.anticyscam.app.R
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.domain.model.TransferAccountState
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SuccessGreen
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.UserAccentBlue
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedDark

// File-level constants so the per-second tick on the main screen does NOT
// realloc shapes/strokes every recomposition for every visible card. See
// memory: scroll-perf-rule.
private val CardShape = RoundedCornerShape(12.dp)
private val FooterShape = RoundedCornerShape(6.dp)

/**
 * Single transfer-account row, driven by [TransferAccountState]:
 *   - Default                : yellow star, no delete, tap is a no-op.
 *   - PendingMaturation       : yellow border + "綁定中" + hh:mm:ss countdown.
 *                              Edit allowed iff editsRemaining > 0.
 *   - Matured                 : green border + "已綁定". Delete only.
 *   - PendingDeletion         : red border + "解除中" + countdown. Cancel only.
 */
@Composable
fun TransferAccountCard(
    account: TransferAccount,
    state: TransferAccountState,
    onClick: (TransferAccount) -> Unit,
    onRequestDelete: (TransferAccount) -> Unit,
    onCancelDelete: (TransferAccount) -> Unit,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    onEdit: ((TransferAccount) -> Unit)? = null
) {
    val accent = when (state) {
        TransferAccountState.Default -> AlertYellow
        is TransferAccountState.PendingMaturation -> AlertYellow
        TransferAccountState.Matured -> SuccessGreen
        is TransferAccountState.PendingDeletion -> WarningRed
    }
    // BorderStroke only changes when the *class* of state changes (rare).
    // Without this, the per-second countdown tick allocates a new stroke
    // for every visible card every second.
    val cardBorder = remember(accent) { BorderStroke(width = 2.dp, color = accent) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = cardBorder,
        onClick = { onClick(account) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account.isDefault) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = AlertYellow,
                    modifier = Modifier.padding(end = 12.dp)
                )
                // Default 「臨時用」 title is rendered in AlertYellow so it
                // visually reads as "system-provided", distinguishing it
                // from the user-added rows below (which keep TextPrimary
                // bright white) — per spec #1.
                Text(
                    text = stringResource(R.string.transfer_default_purpose),
                    color = AlertYellow,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                return@Row
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Field rows: label prefix in UserAccentBlue, user-entered
                // value in TextPrimary white. Each AnnotatedString is
                // `remember`-keyed so the per-second tick that drives the
                // countdown does not realloc them every recomposition —
                // see memory: scroll-perf-rule.
                val nameLabel = stringResource(R.string.transfer_field_name)
                val nameLine = remember(account.name, nameLabel) {
                    labelValue(label = nameLabel, value = account.name)
                }
                Text(text = nameLine, style = MaterialTheme.typography.titleMedium)

                val accountLabel = stringResource(R.string.transfer_field_account)
                val accountLine = remember(account.accountNumber, accountLabel) {
                    labelValue(
                        label = accountLabel,
                        value = maskAccount(account.accountNumber)
                    )
                }
                Text(text = accountLine, style = MaterialTheme.typography.bodyMedium)

                val bank = account.bankCode
                if (!bank.isNullOrBlank()) {
                    val bankLabel = stringResource(R.string.transfer_card_bank_code_prefix)
                    val bankLine = remember(bank, bankLabel) {
                        labelValue(label = bankLabel, value = bank)
                    }
                    Text(text = bankLine, style = MaterialTheme.typography.bodyMedium)
                }
                StateFooter(state = state, accent = accent)
            }

            val editAllowed = state is TransferAccountState.PendingMaturation &&
                account.editsRemaining > 0
            if (showActions && onEdit != null && editAllowed) {
                IconButton(onClick = { onEdit(account) }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "編輯",
                        tint = AlertYellow
                    )
                }
            }
            if (showActions) {
                if (state is TransferAccountState.PendingDeletion) {
                    TextButton(onClick = { onCancelDelete(account) }) {
                        Text(
                            text = stringResource(R.string.transfer_cancel_delete),
                            color = AlertYellow,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    IconButton(onClick = { onRequestDelete(account) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.transfer_request_delete),
                            tint = WarningRedDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StateFooter(state: TransferAccountState, accent: androidx.compose.ui.graphics.Color) {
    val label: String
    val countdown: String?
    when (state) {
        TransferAccountState.Default -> {
            label = stringResource(R.string.account_status_default)
            countdown = null
        }
        is TransferAccountState.PendingMaturation -> {
            label = stringResource(R.string.transfer_state_pending_maturation)
            countdown = formatHms(state.remainingMs)
        }
        TransferAccountState.Matured -> {
            label = stringResource(R.string.transfer_state_matured)
            countdown = null
        }
        is TransferAccountState.PendingDeletion -> {
            label = stringResource(R.string.transfer_state_pending_deletion)
            countdown = formatHms(state.remainingMs)
        }
    }
    Box(
        modifier = Modifier
            .background(SurfaceDim, FooterShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Text(
                text = label,
                color = accent,
                style = MaterialTheme.typography.labelMedium
            )
            if (countdown != null) {
                Text(
                    text = countdown,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatHms(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun labelValue(label: String, value: String): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = UserAccentBlue)) { append("$label：") }
        withStyle(SpanStyle(color = TextPrimary)) { append(value) }
    }

private fun maskAccount(number: String): String {
    if (number.length <= 8) return number
    val head = number.substring(0, 4)
    val tail = number.substring(number.length - 4)
    val maskedMiddle = "*".repeat(number.length - 8)
    return "$head$maskedMiddle$tail"
}
