package com.anticyscam.app.ui.warning

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anticyscam.app.MainActivity
import com.anticyscam.app.R
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.WarningRedDark

/**
 * Fullscreen warning shown when a bound app is launched without going
 * through the anti-fraud entry.
 *
 * - 蓋滿整個螢幕（manifest 已設定 windowFullscreen + showWhenLocked）
 * - 攔截返回鍵：使用者只能透過底部「我知道了」按鈕離開
 * - 點擊「我知道了」後：finish + 重新拉起 MainActivity（強制回到防詐器）
 * - launchMode=singleInstance + noHistory：避免重複的警告 Activity 疊起來
 *
 * 使用 [newIntent] 建立啟動意圖，會帶入被攔截的 packageName / label。
 */
class BlockingWarningActivity : ComponentActivity() {

    private val blockedPackage: String?
        get() = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)

    private val blockedLabel: String?
        get() = intent.getStringExtra(EXTRA_BLOCKED_LABEL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOverlayWindowFlags()
        installBackBlocker()
        setContent {
            AntiCyScamTheme {
                WarningScreen(
                    blockedLabel = blockedLabel,
                    onConfirm = ::onConfirmReturnToHome
                )
            }
        }
    }

    /**
     * Show even over the lock screen so the user cannot escape the warning
     * by locking the phone — the warning stays visible and interactive on
     * top of the keyguard.
     *
     * We deliberately do NOT call requestDismissKeyguard / FLAG_DISMISS_KEYGUARD:
     * forcing the fingerprint/PIN prompt the instant the warning appears made
     * every trigger feel like the device had locked. With showWhenLocked alone
     * the warning is readable and the confirm button is tappable straight away;
     * the keyguard prompt is deferred to the return-to-MainActivity step
     * (MainActivity is not showWhenLocked), which is far less jarring.
     * API-version-aware to keep deprecation warnings off the active code path.
     */
    private fun applyOverlayWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Keep this Activity on top of recents / multi-window snap.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun installBackBlocker() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Swallow back. User must tap the confirm button.
                }
            }
        )
    }

    private fun onConfirmReturnToHome() {
        // Bring the anti-fraud Activity to the front, then finish ourselves.
        //
        // Belt-and-braces: when the warning is triggered from the launcher
        // (user tapped a bound app icon), Android often keeps the bound
        // app's task in the foreground after we finish, dropping the user
        // back to the bound app instead of MainActivity. We mitigate that
        // with three layers:
        //   1. NEW_TASK | CLEAR_TOP | SINGLE_TOP — standard reuse path
        //   2. REORDER_TO_FRONT — explicitly reorder MainActivity's task
        //   3. ActivityManager.AppTask.moveToFront() — direct task move
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        startActivity(intent)
        bringAntiScamTaskToFront()
        finishAndRemoveTask()
    }

    private fun bringAntiScamTaskToFront() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val mainClassName = MainActivity::class.java.name
        for (task in am.appTasks) {
            val baseActivity = task.taskInfo.baseActivity ?: continue
            if (baseActivity.className == mainClassName) {
                runCatching { task.moveToFront() }
                return
            }
        }
    }

    companion object {
        private const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        private const val EXTRA_BLOCKED_LABEL = "extra_blocked_label"

        fun newIntent(context: Context, blockedPackage: String, blockedLabel: String): Intent =
            Intent(context, BlockingWarningActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_PACKAGE, blockedPackage)
                putExtra(EXTRA_BLOCKED_LABEL, blockedLabel)
            }
    }
}

@Composable
private fun WarningScreen(blockedLabel: String?, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarningRedDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚠",
                color = Color.White,
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = stringResource(R.string.warning_title),
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (!blockedLabel.isNullOrBlank()) {
                Text(
                    text = "被攔截：$blockedLabel",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Text(
                text = stringResource(R.string.warning_body),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
            )
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceBlack,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.warning_confirm),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
