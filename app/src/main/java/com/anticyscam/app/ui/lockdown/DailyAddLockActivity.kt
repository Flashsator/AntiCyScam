package com.anticyscam.app.ui.lockdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.anticyscam.app.MainActivity
import com.anticyscam.app.R
import com.anticyscam.app.ui.theme.AntiCyScamTheme
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.WarningRedDark

/**
 * Fullscreen lockdown shown when the user has added their 3rd transfer
 * account within a single calendar day (PRD 畫面 10 / § 3.2). Back is
 * intercepted — the only paths out are "撥打 165" or "返回防詐器".
 */
class DailyAddLockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* swallow */ }
        })
        setContent {
            AntiCyScamTheme {
                LockScreen(
                    onCall165 = ::call165,
                    onReturn = ::returnHome
                )
            }
        }
    }

    private fun call165() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:165"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:165"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun returnHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finishAndRemoveTask()
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, DailyAddLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}

@Composable
private fun LockScreen(onCall165: () -> Unit, onReturn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarningRedDark)
            .padding(24.dp)
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
                    text = "⚠",
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = stringResource(R.string.daily_add_lock_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.daily_add_lock_body),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        text = stringResource(R.string.daily_add_lock_call),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                OutlinedButton(onClick = onReturn, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.daily_add_lock_return),
                        color = Color.White
                    )
                }
            }
        }
    }
}
