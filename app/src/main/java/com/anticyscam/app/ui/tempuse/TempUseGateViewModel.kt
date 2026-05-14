package com.anticyscam.app.ui.tempuse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.prefs.ForcedCalmTimer
import com.anticyscam.app.data.prefs.TempUseTracker
import com.anticyscam.app.data.repository.TransferAccountRepository
import com.anticyscam.app.service.AppLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the [TempUseGateActivity]:
 *  - Reads the current [TempUseTracker] snapshot on bootstrap and decides
 *    which stage UI to show.
 *  - For stage 2, starts (or rejoins) the [ForcedCalmTimer] and emits a
 *    second-resolution countdown until it hits zero.
 *  - For stage 3, persists the lockout via [TempUseTracker.consume] up-front
 *    so the user can't bypass the lockdown by killing the activity.
 *  - On user-confirmed proceed, copies the account number to clipboard
 *    (if any), marks the account used (so the 90-day dormancy clock resets)
 *    and authorizes the launch.
 */
@HiltViewModel
class TempUseGateViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tracker: TempUseTracker,
    private val calmTimer: ForcedCalmTimer,
    private val clock: AntiScamClock,
    private val appLauncher: AppLauncher,
    private val transferRepo: TransferAccountRepository
) : ViewModel() {

    data class UiState(
        val stage: TempUseTracker.Stage = TempUseTracker.Stage.FIRST,
        val calmRemainingMs: Long = 0L,
        val check1: Boolean = false,
        val check2: Boolean = false,
        val check3: Boolean = false,
        val lockoutRemainingMs: Long = 0L,
        val lockedDown: Boolean = false
    ) {
        val canProceedStage2: Boolean
            get() = calmRemainingMs <= 0L && check1 && check2 && check3
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Plan v4 Item 7: when Stage 3 / LOCKED_OUT lockout finishes counting
     * down, fire a single event so the hosting Activity can finish itself
     * and bring MainActivity back to the front. Without this the user is
     * left staring at a 0:00 timer with nothing happening.
     */
    private val _returnToHome = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val returnToHome: SharedFlow<Unit> = _returnToHome.asSharedFlow()

    private var targetPackage: String? = null
    private var accountId: Long? = null
    private var accountNumber: String = ""
    private var tickJob: Job? = null
    private var alreadyBootstrapped = false

    fun bootstrap(targetPackage: String?, accountId: Long?, accountNumber: String) {
        if (alreadyBootstrapped) return
        alreadyBootstrapped = true
        this.targetPackage = targetPackage
        this.accountId = accountId
        this.accountNumber = accountNumber

        tracker.clearLockoutIfElapsed()
        val snap = tracker.snapshot()
        when (snap.stage) {
            TempUseTracker.Stage.FIRST -> {
                _state.value = UiState(stage = TempUseTracker.Stage.FIRST)
            }
            TempUseTracker.Stage.SECOND -> {
                // Persist deadline on first entry so killing the activity
                // doesn't restart the calm window.
                if (calmTimer.deadline() == 0L || calmTimer.isFinished()) {
                    calmTimer.start()
                }
                _state.value = UiState(
                    stage = TempUseTracker.Stage.SECOND,
                    calmRemainingMs = calmTimer.remainingMs()
                )
                tickCalmTimer()
            }
            TempUseTracker.Stage.THIRD -> {
                // Commit the lockout immediately — the user has already
                // burned the third attempt simply by reaching this screen.
                tracker.consume()
                _state.value = UiState(
                    stage = TempUseTracker.Stage.THIRD,
                    lockedDown = true,
                    lockoutRemainingMs = tracker.snapshot().lockoutUntil - clock.now()
                )
                tickLockoutTimer()
            }
            TempUseTracker.Stage.LOCKED_OUT -> {
                _state.value = UiState(
                    stage = TempUseTracker.Stage.LOCKED_OUT,
                    lockedDown = true,
                    lockoutRemainingMs = (snap.lockoutUntil - clock.now()).coerceAtLeast(0L)
                )
                tickLockoutTimer()
            }
        }
    }

    fun toggleCheck(index: Int) {
        val s = _state.value
        _state.value = when (index) {
            1 -> s.copy(check1 = !s.check1)
            2 -> s.copy(check2 = !s.check2)
            3 -> s.copy(check3 = !s.check3)
            else -> s
        }
    }

    /**
     * Stage-aware proceed handler. Returns true if the activity should finish
     * (launch fired or stage 1 completion); false if the UI should stay open
     * (lockout, missing checks, etc.).
     */
    fun confirmProceed(activityContext: Context): Boolean {
        val s = _state.value
        when (s.stage) {
            TempUseTracker.Stage.FIRST -> {
                // Phase G: only the user-confirmed proceed path may call
                // consume(). Cancel / back-press (canDismiss == true on FIRST)
                // intentionally bypasses this branch so the attempt does not
                // count against the 10-minute window.
                tracker.consume()
                fireLaunch(activityContext)
                return true
            }
            TempUseTracker.Stage.SECOND -> {
                if (!s.canProceedStage2) return false
                tracker.consume()
                calmTimer.clear()
                fireLaunch(activityContext)
                return true
            }
            TempUseTracker.Stage.THIRD, TempUseTracker.Stage.LOCKED_OUT -> {
                // No proceed path. UI shows 165 + dismiss only.
                return false
            }
        }
    }

    /**
     * Only Stage 1 ("提醒") may be dismissed by back-press / cancel button.
     * Stage 2 keeps the calm window honest; Stage 3 / LOCKED_OUT have no
     * "我知道" escape hatch per the post-test design — the user must
     * either dial 165 or wait out the 5-minute lockdown.
     */
    fun canDismiss(): Boolean = _state.value.stage == TempUseTracker.Stage.FIRST

    private fun fireLaunch(activityContext: Context) {
        if (accountNumber.isNotBlank()) {
            val cm = activityContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("transfer_account", accountNumber))
        }
        accountId?.let { id ->
            viewModelScope.launch { transferRepo.markUsed(id) }
        }
        targetPackage?.let { pkg -> appLauncher.launchAuthorized(pkg) }
    }

    private fun tickCalmTimer() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            calmTimer.observeRemaining(tickMs = 200L).collectLatest { remaining ->
                _state.value = _state.value.copy(calmRemainingMs = remaining)
            }
        }
    }

    private fun tickLockoutTimer() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val remaining = (tracker.snapshot().lockoutUntil - clock.now()).coerceAtLeast(0L)
                _state.value = _state.value.copy(lockoutRemainingMs = remaining)
                if (remaining <= 0L) {
                    _returnToHome.tryEmit(Unit)
                    break
                }
                kotlinx.coroutines.delay(1_000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
    }
}
