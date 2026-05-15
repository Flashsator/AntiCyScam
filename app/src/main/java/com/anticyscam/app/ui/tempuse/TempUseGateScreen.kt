package com.anticyscam.app.ui.tempuse

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anticyscam.app.R
import com.anticyscam.app.data.prefs.TempUseTracker
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SuccessGreen
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.SurfaceElevated
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedDark

@Composable
fun TempUseGateScreen(
    state: TempUseGateViewModel.UiState,
    onProceed: () -> Unit,
    onCall165: () -> Unit,
    onCheckBox: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val backgroundColor = when (state.stage) {
        TempUseTracker.Stage.FIRST -> SurfaceBlack
        TempUseTracker.Stage.SECOND -> SurfaceBlack
        TempUseTracker.Stage.THIRD,
        TempUseTracker.Stage.LOCKED_OUT,
        TempUseTracker.Stage.BANNED -> WarningRedDark
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        when (state.stage) {
            TempUseTracker.Stage.FIRST -> Stage1(onProceed, onDismiss)
            TempUseTracker.Stage.SECOND -> Stage2(state, onProceed, onCheckBox, onCall165)
            TempUseTracker.Stage.THIRD -> Stage3(state, onCall165, onDismiss)
            TempUseTracker.Stage.LOCKED_OUT -> StageLocked(state, onCall165, onDismiss)
            TempUseTracker.Stage.BANNED -> StageBanned(state, onCall165)
        }
    }
}

@Composable
private fun Stage1(
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🟢",
                color = SuccessGreen,
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = stringResource(R.string.temp_use_stage1_title),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.temp_use_stage1_body),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.temp_use_stage1_cta),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.transfer_action_cancel),
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun Stage2(
    state: TempUseGateViewModel.UiState,
    onProceed: () -> Unit,
    onCheck: (Int) -> Unit,
    onCall165: () -> Unit
) {
    val countdownSec = ((state.calmRemainingMs + 999) / 1000).toInt().coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.temp_use_stage2_title),
            color = AlertYellow,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.temp_use_stage2_body),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
        StageCheckRow(
            checked = state.check1,
            enabled = true,
            text = stringResource(R.string.temp_use_stage2_check1),
            onClick = { onCheck(1) }
        )
        StageCheckRow(
            checked = state.check2,
            enabled = true,
            text = stringResource(R.string.temp_use_stage2_check2),
            onClick = { onCheck(2) }
        )
        StageCheckRow(
            checked = state.check3,
            enabled = true,
            text = stringResource(R.string.temp_use_stage2_check3),
            onClick = { onCheck(3) }
        )
        Spacer(Modifier.height(8.dp))
        MorphingConfirmBlock(
            countdownSec = countdownSec,
            canProceed = state.canProceedStage2,
            onProceed = onProceed
        )
        OutlinedButton(
            onClick = onCall165,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.temp_use_stage2_call_165),
                color = WarningRed
            )
        }
    }
}

@Composable
private fun MorphingConfirmBlock(
    countdownSec: Int,
    canProceed: Boolean,
    onProceed: () -> Unit
) {
    if (countdownSec <= 0) {
        Button(
            onClick = onProceed,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AlertYellow,
                contentColor = SurfaceBlack,
                disabledContainerColor = SurfaceElevated,
                disabledContentColor = TextSecondary
            )
        ) {
            Text(
                text = if (canProceed) {
                    stringResource(R.string.temp_use_stage2_cta)
                } else {
                    "請先完成上方確認"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(SurfaceElevated, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.temp_use_stage2_countdown, countdownSec),
                color = AlertYellow,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun Stage3(
    state: TempUseGateViewModel.UiState,
    onCall165: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit
) {
    // Phase J: 第三次警告無「我知道」逃生口 — 只能撥 165 或乖乖等 5 分鐘。
    // 加上冷靜口訣，讓使用者在被詐騙催促時有文字錨點。
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚠",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = stringResource(R.string.temp_use_stage3_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.temp_use_stage3_body),
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.temp_use_calm_mantra),
                color = AlertYellow,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            // Phase O1: 倒數計時補回。tickLockoutTimer 在 ViewModel 已填好
            // lockoutRemainingMs；這裡格式化為 mm:ss 與 locked_note 一併顯示。
            Text(
                text = stringResource(
                    R.string.temp_use_stage3_locked_note,
                    formatLockout(state.lockoutRemainingMs)
                ),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onCall165,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceBlack,
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(R.string.temp_use_stage3_call_cta),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun StageLocked(
    state: TempUseGateViewModel.UiState,
    onCall165: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit
) {
    // Phase J: LOCKED_OUT 期間使用者只能撥 165 或等冷卻時間結束 — 無「我知道」按鈕。
    val remaining = formatLockout(state.lockoutRemainingMs)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🚫",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = stringResource(R.string.temp_use_locked_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.temp_use_locked_body, remaining),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.temp_use_calm_mantra),
                color = AlertYellow,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onCall165,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceBlack,
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(R.string.temp_use_locked_call),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun StageBanned(
    state: TempUseGateViewModel.UiState,
    onCall165: () -> Unit
) {
    // Escalation tier: 10-min watchful window + 3rd warning = 1-hour total
    // ban on every transfer feature. No 「我知道」 escape, no proceed path.
    val remaining = formatLockoutHms(state.lockoutRemainingMs)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "⛔",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = stringResource(R.string.temp_use_banned_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.temp_use_banned_body, remaining),
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.temp_use_calm_mantra),
                color = AlertYellow,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onCall165,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceBlack,
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(R.string.temp_use_stage3_call_cta),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun StageCheckRow(
    checked: Boolean,
    enabled: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDim, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = AlertYellow,
                uncheckedColor = TextSecondary
            )
        )
        Text(
            text = text,
            color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun formatLockout(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * hh:mm:ss formatter for the 1-hour ban — minute-only would compress to
 * "60:00" at start which reads as a typo. We display HH:MM:SS so the
 * remaining hour is obvious.
 */
private fun formatLockoutHms(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
