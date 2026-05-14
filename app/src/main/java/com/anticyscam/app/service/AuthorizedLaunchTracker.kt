package com.anticyscam.app.service

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks short-lived launch authorizations. When the user explicitly
 * chooses to launch a bound app from inside 防詐器, we mark its package
 * as "authorized for the next N seconds". The AccessibilityService
 * checks this tracker on each foreground-app transition — if the app
 * appears WITHOUT a fresh authorization, the blocking warning fires.
 *
 * Design choices:
 *  - Uses [SystemClock.elapsedRealtime] (monotonic) — wall-clock changes
 *    cannot bypass the gate.
 *  - The grant is single-use within its window: [consumeAuthorization]
 *    clears the entry on first hit, so backgrounding-then-relaunching
 *    the app (e.g. from recents) still triggers the warning.
 *  - Thread-safe — accessed from Activity (UI thread) and the
 *    AccessibilityService (system event thread).
 */
@Singleton
class AuthorizedLaunchTracker @Inject constructor() {

    private val grants = ConcurrentHashMap<String, Long>()

    /**
     * Pluggable monotonic clock. Production uses [SystemClock.elapsedRealtime];
     * tests substitute a controllable lambda to exercise expiry without
     * sleeping the test thread.
     */
    @VisibleForTesting
    internal var clockProvider: () -> Long = { SystemClock.elapsedRealtime() }

    /**
     * Authorize a single launch of [packageName]. The grant expires
     * [AUTHORIZATION_WINDOW_MS] milliseconds after this call.
     */
    fun authorize(packageName: String) {
        grants[packageName] = clockProvider() + AUTHORIZATION_WINDOW_MS
    }

    /**
     * Atomically check + consume a pending grant for [packageName].
     * Returns true exactly once per call to [authorize]; subsequent
     * checks until the next [authorize] return false.
     */
    fun consumeAuthorization(packageName: String): Boolean {
        val expiry = grants.remove(packageName) ?: return false
        return clockProvider() <= expiry
    }

    /**
     * Non-destructive peek. Useful for tests + diagnostics; the production
     * path should call [consumeAuthorization] so each launch is verified
     * exactly once.
     */
    fun isAuthorized(packageName: String): Boolean {
        val expiry = grants[packageName] ?: return false
        return clockProvider() <= expiry
    }

    /** Drop all authorizations — useful when the user toggles binding off. */
    fun clearAll() = grants.clear()

    companion object {
        const val AUTHORIZATION_WINDOW_MS: Long = 60_000L
    }
}
