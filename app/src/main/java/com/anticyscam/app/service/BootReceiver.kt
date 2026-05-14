package com.anticyscam.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.repository.BoundAppRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Re-arms the foreground service after a device boot or app upgrade so the
 * AccessibilityService stays alive even if the user has not yet opened the
 * app. Registered for BOOT_COMPLETED + MY_PACKAGE_REPLACED in the manifest.
 *
 * Also settles bind-maturation + cooldown-unbind progress so the user cannot
 * gain free time by power-cycling the device. Monotonic clock resets at boot,
 * so [BoundAppRepository.bootSettleAll] uses wall-only deltas with a 24h
 * clamp — see [com.anticyscam.app.domain.binding.BindingSettleEngine.bootSettle].
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var boundAppRepository: BoundAppRepository
    @Inject lateinit var clock: AntiScamClock

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        AntiScamForegroundService.start(context.applicationContext)

        // goAsync lets us settle Room outside the receiver's main-thread tick.
        // Room is forbidden on the main thread, and BroadcastReceiver.onReceive
        // is on the main thread by default.
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                    boundAppRepository.bootSettleAll(clock.snapshot())
                }
            } catch (t: Throwable) {
                Log.w(TAG, "bootSettleAll failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AntiScamBootRecv"
        const val SETTLE_TIMEOUT_MS = 5_000L
    }
}
