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

    /**
     * The bound package currently treated as the user's active session.
     * Set when [evaluate] first consumes an authorization for a bound app;
     * cleared as soon as [evaluate] observes any *different* package (bound
     * or non-bound) surfacing in the foreground — that counts as the user
     * leaving the protected flow, so re-entering the bound app afterwards
     * must go through 防詐器 again or it is blocked.
     *
     * While it is set, repeated foreground events for the *same* package —
     * i.e. in-app window transitions within the bound app — short-circuit
     * to AllowAuthorized without consuming another grant, so mid-session
     * window churn cannot re-trigger the warning. True transient system
     * overlays (SystemUI, etc.) never reach [evaluate]: they are filtered
     * by IGNORED_PACKAGES in AntiScamAccessibilityService.
     */
    @Volatile
    private var authorizedSessionPackage: String? = null

    @Volatile
    private var started = false

    /**
     * Idempotent. Both [AntiScamAccessibilityService] (a11y-ON path) and
     * [AntiScamForegroundService]'s [UsageStatsForegroundDetector] (a11y-OFF
     * path) call this; whichever runs first populates the snapshot, the
     * second call is a no-op so the repository flow is collected only once.
     */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        boundAppRepository.observeBoundApps()
            .onEach { apps ->
                _snapshot.value = apps.associate { it.packageName to it.label }
            }
            .launchIn(scope)
    }

    /**
     * Reset the session-tracking state — the last-seen package and the
     * active authorized session. Called after the user dismisses an overlay
     * block so the next foreground event is evaluated from a clean slate:
     * no stale authorized session can carry the user back into a bound app
     * without re-authorizing through 防詐器.
     */
    fun resetLastObserved() {
        lastObservedPackage = null
        authorizedSessionPackage = null
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
     * Session model:
     *  - Our own UI is always Ignored — we never block ourselves.
     *  - Non-bound foregrounds (LINE / Launcher / system apps / OEM
     *    surfaces, etc.) are Ignored. A non-bound foreground that is also
     *    a *different package* from the last one we saw counts as the user
     *    truly leaving the bound app and ends the active session.
     *  - Bound foregrounds:
     *      * Same package as the active session → AllowAuthorized without
     *        consuming a new grant. This is what keeps mid-session window
     *        transitions / IME / OTP toasts from re-triggering the warning.
     *      * Otherwise → consume a pending authorization. Success opens a
     *        new session; failure clears any stale session and blocks.
     *
     * There is deliberately NO same-package dedup: an unauthorized re-entry
     * into a bound app (e.g. a gesture-nav quick-switch back to a bank app)
     * looks identical to the previous event, so deduping it would silently
     * skip the warning. Legitimate in-app transitions are already covered
     * by the authorized-session branch, and duplicate overlays are
     * prevented downstream by an overlayView null-check.
     */
    fun evaluate(foregroundPkg: String, ownPkg: String): Decision {
        if (foregroundPkg == ownPkg) {
            lastObservedPackage = foregroundPkg
            val d: Decision = Decision.Ignore
            recordDiagnostic(foregroundPkg, d)
            return d
        }

        val boundLabel = _snapshot.value[foregroundPkg]
        if (boundLabel == null) {
            // Non-bound app in the foreground. If this is genuinely a new
            // surface (not just a duplicate event), the user left the
            // bound app and the session ends.
            if (lastObservedPackage != foregroundPkg) {
                authorizedSessionPackage = null
            }
            lastObservedPackage = foregroundPkg
            val d: Decision = Decision.Ignore
            recordDiagnostic(foregroundPkg, d)
            return d
        }

        val decision: Decision = when {
            foregroundPkg == authorizedSessionPackage ->
                Decision.AllowAuthorized(foregroundPkg)
            authorizedLaunchTracker.consumeAuthorization(foregroundPkg) -> {
                authorizedSessionPackage = foregroundPkg
                Decision.AllowAuthorized(foregroundPkg)
            }
            else -> {
                authorizedSessionPackage = null
                Decision.BlockUnauthorized(
                    packageName = foregroundPkg,
                    label = boundLabel
                )
            }
        }
        lastObservedPackage = foregroundPkg
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
