package com.anticyscam.app.ui.mainfunction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.domain.binding.BindingSettleEngine
import com.anticyscam.app.domain.model.BoundApp
import com.anticyscam.app.domain.model.BoundAppWithState
import com.anticyscam.app.service.AppLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Owns:
 *  - the bound-app list paired with computed [BindingState] (1s display tick)
 *  - the "currently pending app" state for the two-step launch flow
 *  - background settle trigger (60s) so DB stays roughly current without
 *    writing per-tick
 */
@HiltViewModel
class MainFunctionViewModel @Inject constructor(
    private val boundAppRepository: BoundAppRepository,
    private val appLauncher: AppLauncher,
    private val clock: AntiScamClock
) : ViewModel() {

    /**
     * Bound apps with their derived state at the latest tick. UI subscribes
     * here to render countdowns; the underlying row is *not* written every
     * second — see the 60s settle trigger below.
     */
    val boundApps: StateFlow<List<BoundAppWithState>> = combine(
        boundAppRepository.observeBoundApps(),
        bindingTickerFlow(clock, DISPLAY_TICK_MS)
    ) { apps, now ->
        apps.map { app ->
            BoundAppWithState(
                app = app,
                state = BindingSettleEngine.deriveState(app.toEntity(), now)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Persist accumulated millis at 60s cadence. UI sees fresh values
        // every 1s via the display tick above; the 60s write amortizes Room
        // I/O — under a 24h maturation, 60s of slip is rounding noise.
        bindingTickerFlow(clock, SETTLE_TICK_MS)
            .onEach { now -> boundAppRepository.settleAll(now) }
            .launchIn(viewModelScope)
    }

    private val _pendingApp = MutableStateFlow<BoundApp?>(null)
    val pendingApp: StateFlow<BoundApp?> = _pendingApp.asStateFlow()

    fun onBoundAppClicked(app: BoundApp) {
        _pendingApp.value = app
    }

    fun cancelPending() {
        _pendingApp.value = null
    }

    /**
     * Authorize + launch the [pendingApp]. Returns false if there was no
     * pending app or the launch could not be resolved (e.g. uninstalled).
     */
    fun authorizeAndLaunch(): LaunchOutcome {
        val app = _pendingApp.value ?: return LaunchOutcome.NoPending
        val launched = appLauncher.launchAuthorized(app.packageName)
        _pendingApp.value = null
        return if (launched) LaunchOutcome.Launched(app) else LaunchOutcome.NotInstalled(app)
    }

    sealed interface LaunchOutcome {
        data object NoPending : LaunchOutcome
        data class Launched(val app: BoundApp) : LaunchOutcome
        data class NotInstalled(val app: BoundApp) : LaunchOutcome
    }

    /**
     * Map back to the entity shape consumed by the settle engine.
     * BindingSettleEngine operates on entities to keep its boundary inside
     * the persistence layer; the domain BoundApp mirrors the same fields.
     */
    private fun BoundApp.toEntity(): com.anticyscam.app.data.local.entity.BoundAppEntity =
        com.anticyscam.app.data.local.entity.BoundAppEntity(
            packageName = packageName,
            label = label,
            boundAt = boundAt,
            boundAtElapsedNanos = boundAtElapsedNanos,
            accumulatedBoundMillis = accumulatedBoundMillis,
            lastSettledWall = lastSettledWall,
            lastSettledElapsedNanos = lastSettledElapsedNanos,
            unbindRequestedAtWall = unbindRequestedAtWall,
            unbindRequestedAtElapsedNanos = unbindRequestedAtElapsedNanos,
            accumulatedUnbindMillis = accumulatedUnbindMillis
        )

    private companion object {
        const val DISPLAY_TICK_MS = 1_000L
        const val SETTLE_TICK_MS = 60_000L
    }
}
