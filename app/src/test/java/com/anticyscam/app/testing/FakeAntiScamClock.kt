package com.anticyscam.app.testing

import android.content.ContextWrapper
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.prefs.NowSnapshot

/**
 * Test double for [AntiScamClock] that returns whatever [NowSnapshot] the
 * test sets via [setNow]. Bypasses Context by passing a null-backed
 * [ContextWrapper] — safe because [AntiScamClock] lazily initializes
 * SharedPreferences and tests never touch the count-related methods.
 */
class FakeAntiScamClock(
    initialWallMillis: Long = 0L,
    initialElapsedNanos: Long = 0L
) : AntiScamClock(ContextWrapper(null)) {

    private var current = NowSnapshot(initialWallMillis, initialElapsedNanos)

    fun setNow(wallMillis: Long, elapsedNanos: Long) {
        current = NowSnapshot(wallMillis, elapsedNanos)
    }

    fun advanceMillis(deltaMillis: Long) {
        current = NowSnapshot(
            wallMillis = current.wallMillis + deltaMillis,
            elapsedNanos = current.elapsedNanos + deltaMillis * 1_000_000L
        )
    }

    override fun snapshot(): NowSnapshot = current
}
