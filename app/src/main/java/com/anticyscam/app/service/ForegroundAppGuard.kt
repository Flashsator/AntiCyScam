package com.anticyscam.app.service

import androidx.annotation.VisibleForTesting
import com.anticyscam.app.data.repository.BoundAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decision core for the AccessibilityService.
 *
 * Holds a cached, hot snapshot of bound apps (packageName → label) so the
 * foreground callback (which runs on the system's event thread) does not
 * hit Room for every single window transition — DB on that thread would
 * tank device-wide UI responsiveness.
 *
 * Contract:
 *  - [start] subscribes to the repository's flow + populates the snapshot.
 *  - [evaluate] returns a [Decision] given a freshly-foregrounded
 *    package. It is fast, allocation-light, and safe to call from the
 *    AccessibilityService callback thread.
 */
@Singleton
class ForegroundAppGuard @Inject constructor(
    private val boundAppRepository: BoundAppRepository,
    private val authorizedLaunchTracker: AuthorizedLaunchTracker
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow<Map<String, String>>(emptyMap())
    val snapshot: StateFlow<Map<String, String>> = _snapshot.asStateFlow()

    private val _diagnostic = MutableStateFlow(Diagnostic())
    val diagnostic: StateFlow<Diagnostic> = _diagnostic.asStateFlow()

    @Volatile
    private var lastObservedPackage: String? = null

    fun start() {
        boundAppRepository.observeBoundApps()
            .onEach { apps ->
                _snapshot.value = apps.associate { it.packageName to it.label }
            }
            .launchIn(scope)
    }

    /**
     * Clear the dedup gate so the next [evaluate] for the previously-seen
     * package re-triggers the warning. Call after the user dismisses an
     * overlay block, otherwise repeatedly tapping the same bound app from
     * launcher results in Ignore (same-package back-to-back).
     */
    fun resetLastObserved() {
        lastObservedPackage = null
    }

    /**
     * Test-only hook that bypasses [start] so unit tests can exercise the
     * [evaluate] decision tree without spinning up Room or a real coroutine
     * scope. Never called from production.
     */
    @VisibleForTesting
    internal fun primeSnapshotForTest(snapshot: Map<String, String>) {
        _snapshot.value = snapshot
    }

    /**
     * Decide whether [foregroundPkg] is allowed to stay in the foreground.
     *
     * - Ignores duplicate-transition events (same package back-to-back).
     * - Ignores our own UI (we are always allowed).
     * - Ignores anything outside the user's bound set.
     * - For bound packages: consumes a pending authorization. No grant ⇒
     *   the warning must be shown and the user pulled back.
     */
    fun evaluate(foregroundPkg: String, ownPkg: String): Decision {
        val last = lastObservedPackage
        lastObservedPackage = foregroundPkg
        val decision: Decision = when {
            foregroundPkg == last -> Decision.Ignore
            foregroundPkg == ownPkg -> Decision.Ignore
            foregroundPkg !in _snapshot.value -> Decision.Ignore
            authorizedLaunchTracker.consumeAuthorization(foregroundPkg) ->
                Decision.AllowAuthorized(foregroundPkg)
            else -> Decision.BlockUnauthorized(
                packageName = foregroundPkg,
                label = _snapshot.value[foregroundPkg] ?: foregroundPkg
            )
        }
        recordDiagnostic(foregroundPkg, decision)
        return decision
    }

    private fun recordDiagnostic(foregroundPkg: String, decision: Decision) {
        _diagnostic.update { d ->
            d.copy(
                totalEvents = d.totalEvents + 1,
                ignoredCount = d.ignoredCount + if (decision is Decision.Ignore) 1 else 0,
                allowedCount = d.allowedCount + if (decision is Decision.AllowAuthorized) 1 else 0,
                blockedCount = d.blockedCount + if (decision is Decision.BlockUnauthorized) 1 else 0,
                lastForegroundPackage = foregroundPkg,
                lastDecisionLabel = decision.toLabel(),
                lastEventEpochMs = System.currentTimeMillis()
            )
        }
    }

    sealed interface Decision {
        data object Ignore : Decision
        data class AllowAuthorized(val packageName: String) : Decision
        data class BlockUnauthorized(val packageName: String, val label: String) : Decision
    }

    /**
     * Observable counters + most-recent event metadata for the Settings page
     * diagnostic card. Pure side-effect of [evaluate] — never affects routing.
     */
    data class Diagnostic(
        val totalEvents: Long = 0,
        val ignoredCount: Long = 0,
        val allowedCount: Long = 0,
        val blockedCount: Long = 0,
        val lastForegroundPackage: String? = null,
        val lastDecisionLabel: String? = null,
        val lastEventEpochMs: Long? = null
    )

    private fun Decision.toLabel(): String = when (this) {
        is Decision.Ignore -> "Ignore"
        is Decision.AllowAuthorized -> "AllowAuthorized($packageName)"
        is Decision.BlockUnauthorized -> "BlockUnauthorized($packageName)"
    }
}
