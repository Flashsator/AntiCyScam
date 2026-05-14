package com.anticyscam.app.ui.bind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.data.system.InstalledAppsProvider
import com.anticyscam.app.domain.binding.BindingSettleEngine
import com.anticyscam.app.domain.model.BindingState
import com.anticyscam.app.domain.model.BoundApp
import androidx.annotation.VisibleForTesting
import com.anticyscam.app.ui.mainfunction.bindingTickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bind / unbind screen ViewModel.
 *
 * Save UX (per user spec: "勾完一堆按儲存+yes"):
 *  - User toggles checkboxes freely.
 *  - On 儲存 tap, [computeSaveIntent] classifies every pending unbind:
 *      • PendingMaturation row  → free instant-unbind (no dialog)
 *      • Matured row            → must go through CooldownUnbindDialog
 *      • PendingUnbind row      → can't be in pending-uncheck (checkbox is
 *        always rendered checked; user uses inline "取消解除" instead)
 *  - If any Matured unchecks present → show bulk dialog before committing.
 *  - On confirm → requestUnbind per Matured row + saveDiff for the rest +
 *    closes the screen.
 *  - On cancel-dialog → state unchanged, user back on the screen.
 *
 * PendingUnbind inline cancel: [onCancelCooldown] flips the row back to
 * Matured immediately (no separate save step — destructive intent reversal
 * does not need batching).
 */
@HiltViewModel
class BindAppsViewModel @Inject constructor(
    private val installedApps: InstalledAppsProvider,
    private val repository: BoundAppRepository,
    private val clock: AntiScamClock
) : ViewModel() {

    private val _state = MutableStateFlow(BindAppsUiState())
    val state: StateFlow<BindAppsUiState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    init {
        load()
        startCountdownTicker()
    }

    /**
     * Test seam: cancel the 1s ticker so `runTest` doesn't hang on the
     * infinite delay loop. Production never calls this — the ticker lives as
     * long as the ViewModel.
     */
    @VisibleForTesting
    internal fun cancelTickerForTest() {
        tickerJob?.cancel()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val installed = installedApps.listLaunchableApps()
            val bound = repository.snapshot().associateBy { it.packageName }
            val now = clock.snapshot()

            val apps = installed.map { app ->
                val boundEntry = bound[app.packageName]
                val anchor = boundEntry?.toEntity()
                val state = anchor?.let {
                    BindingSettleEngine.deriveState(it, now)
                } ?: BindingState.Unbound
                BindableApp(
                    packageName = app.packageName,
                    label = app.label,
                    isBound = boundEntry != null,
                    state = state,
                    anchor = anchor
                )
            }

            _state.value = BindAppsUiState(
                isLoading = false,
                apps = apps,
                selected = bound.keys,
                initialBound = bound.keys,
                pendingMaturedUnbinds = emptyList(),
                showCooldownDialog = false
            )
        }
    }

    /**
     * Drive countdown text refresh on the bind screen. Without this, the
     * `綁定中 hh:mm:ss` and `解除中 剩 hh:mm:ss` strings would be frozen at the
     * value computed when the screen opened.
     *
     * Mirrors [MainFunctionViewModel]'s DISPLAY_TICK_MS cadence. Only emits a
     * new state when at least one row's derived [BindingState] actually
     * changes — Matured/Unbound rows are stable and skip the copy.
     */
    private fun startCountdownTicker() {
        tickerJob = viewModelScope.launch {
            bindingTickerFlow(clock, DISPLAY_TICK_MS).collect { now ->
                _state.update { current ->
                    if (current.apps.isEmpty()) return@update current
                    var changed = false
                    val nextApps = current.apps.map { app ->
                        val anchor = app.anchor ?: return@map app
                        val derived = BindingSettleEngine.deriveState(anchor, now)
                        if (derived == app.state) {
                            app
                        } else {
                            changed = true
                            app.copy(state = derived)
                        }
                    }
                    if (changed) current.copy(apps = nextApps) else current
                }
            }
        }
    }

    /**
     * Toggle the checkbox for [packageName]. PendingUnbind rows are not
     * toggleable here — they render as "checkbox checked" + inline cancel —
     * so we early-return.
     */
    fun toggle(packageName: String) {
        val current = _state.value
        val row = current.apps.firstOrNull { it.packageName == packageName } ?: return
        if (row.state is BindingState.PendingUnbind) {
            // PendingUnbind rows can only be acted on via [onCancelCooldown].
            return
        }
        val isCurrentlyChecked = packageName in current.selected
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

    /**
     * 儲存 button entry point. Classifies pending unchecks and either shows
     * the bulk cooldown dialog (if any Matured row is being unbound) or
     * commits the save immediately.
     */
    fun onSaveClicked(onDone: () -> Unit) {
        val current = _state.value
        val maturedUnbinds = computeMaturedUnbinds(current)
        if (maturedUnbinds.isNotEmpty()) {
            _state.update {
                it.copy(
                    pendingMaturedUnbinds = maturedUnbinds,
                    showCooldownDialog = true
                )
            }
            return
        }
        commitSave(onDone)
    }

    /** User pressed 「啟動 48 小時冷靜期」in the dialog. */
    fun onConfirmCooldownDialog(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            val now = clock.snapshot()
            current.pendingMaturedUnbinds.forEach { pkg ->
                repository.requestUnbind(pkg, now)
            }
            // Now persist the remaining diff — non-matured unbinds (delete)
            // and new binds (insert). The matured rows are NOT in the target
            // set because the user unchecked them; without removing them
            // from the diff target they'd be deleted by saveDiff. Build the
            // target as: selected ∪ pendingMaturedUnbinds (keep matured rows
            // bound; their cooldown was just started).
            val target = buildTargetForSave(
                current,
                keepPackages = current.pendingMaturedUnbinds.toSet()
            )
            repository.saveDiff(target, now)
            _state.update {
                it.copy(showCooldownDialog = false, pendingMaturedUnbinds = emptyList())
            }
            onDone()
        }
    }

    fun onDismissCooldownDialog() {
        _state.update {
            it.copy(showCooldownDialog = false, pendingMaturedUnbinds = emptyList())
        }
    }

    /** Inline 取消解除 on a PendingUnbind row. */
    fun onCancelCooldown(packageName: String) {
        viewModelScope.launch {
            repository.cancelUnbind(packageName, clock.snapshot())
            reloadAfterCooldownChange()
        }
    }

    private suspend fun reloadAfterCooldownChange() {
        val installed = installedApps.listLaunchableApps()
        val bound = repository.snapshot().associateBy { it.packageName }
        val now = clock.snapshot()
        val apps = installed.map { app ->
            val boundEntry = bound[app.packageName]
            val anchor = boundEntry?.toEntity()
            val derived = anchor?.let {
                BindingSettleEngine.deriveState(it, now)
            } ?: BindingState.Unbound
            BindableApp(
                packageName = app.packageName,
                label = app.label,
                isBound = boundEntry != null,
                state = derived,
                anchor = anchor
            )
        }
        _state.update {
            it.copy(apps = apps, selected = bound.keys, initialBound = bound.keys)
        }
    }

    private fun commitSave(onDone: () -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            val target = buildTargetForSave(current, keepPackages = emptySet())
            repository.saveDiff(target, clock.snapshot())
            onDone()
        }
    }

    /**
     * Build the target list of rows to persist. By default, only currently
     * checked rows are kept. Use [keepPackages] to additionally retain rows
     * the dialog flow has redirected to cooldown — those should NOT be
     * deleted by saveDiff even though they are unchecked in the UI.
     */
    private fun buildTargetForSave(
        state: BindAppsUiState,
        keepPackages: Set<String>
    ): List<BoundApp> {
        val keepSet = state.selected + keepPackages
        return state.apps
            .filter { it.packageName in keepSet }
            .map { BoundApp(packageName = it.packageName, label = it.label) }
    }

    /**
     * Rows that are (a) currently bound, (b) unchecked by the user, and
     * (c) currently in Matured state. These are the ones that need to go
     * through the 48h cooldown.
     */
    private fun computeMaturedUnbinds(state: BindAppsUiState): List<String> =
        state.apps
            .filter { it.packageName in state.initialBound }
            .filter { it.packageName !in state.selected }
            .filter { it.state is BindingState.Matured }
            .map { it.packageName }

    private fun BoundApp.toEntity(): BoundAppEntity =
        BoundAppEntity(
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

    companion object {
        /** Mirrors MainFunctionViewModel.DISPLAY_TICK_MS. */
        private const val DISPLAY_TICK_MS = 1_000L
    }
}

data class BindAppsUiState(
    val isLoading: Boolean = true,
    val apps: List<BindableApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val initialBound: Set<String> = emptySet(),
    /** Packages selected for unbind that are in Matured state. */
    val pendingMaturedUnbinds: List<String> = emptyList(),
    val showCooldownDialog: Boolean = false
)

data class BindableApp(
    val packageName: String,
    val label: String,
    val isBound: Boolean,
    val state: BindingState,
    /**
     * Raw entity snapshot used by the 1s countdown ticker to re-derive
     * [state] each tick. Null for rows that aren't currently bound — those
     * stay [BindingState.Unbound] and skip re-derivation.
     */
    val anchor: BoundAppEntity? = null
)
