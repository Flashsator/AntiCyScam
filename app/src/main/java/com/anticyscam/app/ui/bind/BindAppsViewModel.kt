package com.anticyscam.app.ui.bind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.data.system.InstalledAppsProvider
import com.anticyscam.app.domain.model.BoundApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BindAppsViewModel @Inject constructor(
    private val installedApps: InstalledAppsProvider,
    private val repository: BoundAppRepository,
    private val clock: AntiScamClock
) : ViewModel() {

    private val _state = MutableStateFlow(BindAppsUiState())
    val state: StateFlow<BindAppsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val installed = installedApps.listLaunchableApps()
            val bound = repository.snapshot().associateBy { it.packageName }

            val apps = installed.map { app ->
                val boundEntry = bound[app.packageName]
                BindableApp(
                    packageName = app.packageName,
                    label = app.label,
                    isBound = boundEntry != null,
                    boundAt = boundEntry?.boundAt,
                    unlockableAt = boundEntry?.let { it.boundAt + BoundApp.UNBIND_LOCK_MS }
                )
            }

            _state.value = BindAppsUiState(
                isLoading = false,
                apps = apps,
                selected = bound.keys,
                initialBound = bound.keys
            )
        }
    }

    /**
     * Flip the bound/unbound state for [packageName] in the local UI buffer.
     *
     * Refuses to unbind a row that is still inside its 24h unbind lock
     * window (Plan v4 Item 6). The refusal surfaces via `toastMessage` so
     * the UI can announce why the toggle was a no-op.
     */
    fun toggle(packageName: String) {
        val current = _state.value
        val isCurrentlyChecked = packageName in current.selected
        val isInitiallyBound = packageName in current.initialBound

        if (isCurrentlyChecked && isInitiallyBound) {
            val row = current.apps.firstOrNull { it.packageName == packageName }
            val unlockableAt = row?.unlockableAt
            val now = clock.now()
            if (unlockableAt != null && now < unlockableAt) {
                val remainingMs = unlockableAt - now
                _state.update {
                    it.copy(toastMessage = unbindLockMessage(remainingMs))
                }
                return
            }
        }

        val next = if (isCurrentlyChecked) current.selected - packageName
                   else current.selected + packageName

        _state.update { state ->
            state.copy(
                selected = next,
                apps = state.apps.map { app ->
                    if (app.packageName == packageName) app.copy(isBound = packageName in next)
                    else app
                }
            )
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            val bindings = current.apps
                .filter { it.packageName in current.selected }
                .map { BoundApp(it.packageName, it.label) }
            repository.saveDiff(bindings, clock.now())
            onDone()
        }
    }

    private fun unbindLockMessage(remainingMs: Long): String {
        val hours = remainingMs / (60L * 60 * 1000)
        val mins = (remainingMs / (60L * 1000)) % 60
        val remaining = when {
            hours > 0 -> "${hours}小時${mins}分"
            mins > 0 -> "${mins}分"
            else -> "1 分以內"
        }
        return "此 App 綁定未滿 24 小時，還需 $remaining 才能解除。\n防詐器禁止臨時解除以阻止話術逼迫。"
    }
}

data class BindAppsUiState(
    val isLoading: Boolean = true,
    val apps: List<BindableApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val initialBound: Set<String> = emptySet(),
    val toastMessage: String? = null
)

data class BindableApp(
    val packageName: String,
    val label: String,
    val isBound: Boolean,
    val boundAt: Long? = null,
    val unlockableAt: Long? = null
)
