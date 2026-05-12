package com.anticyscam.app.ui.mainfunction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.domain.model.BoundApp
import com.anticyscam.app.service.AppLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Owns:
 *  - the bound-app list shown on the main screen
 *  - the "currently pending app" state used by the two-step flow:
 *    1. user taps a bound app → pendingApp is set, account sheet shows
 *    2. user taps an account → launchAuthorized + clear pending
 */
@HiltViewModel
class MainFunctionViewModel @Inject constructor(
    private val boundAppRepository: BoundAppRepository,
    private val appLauncher: AppLauncher
) : ViewModel() {

    val boundApps: StateFlow<List<BoundApp>> = boundAppRepository.observeBoundApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
}
