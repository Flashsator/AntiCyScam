package com.anticyscam.app.domain.model

/**
 * Domain representation of a bound row. Mirrors [com.anticyscam.app.data.local.entity.BoundAppEntity]
 * but lives in the domain layer so UI/ViewModels don't import Room types.
 *
 * UI sees this paired with a [BindingState] via [BoundAppWithState]. The
 * raw counters are exposed for tests and the engine; ordinary callers should
 * read state, not the accumulated millis.
 */
data class BoundApp(
    val packageName: String,
    val label: String,
    val boundAt: Long = 0L,
    val boundAtElapsedNanos: Long = 0L,
    val accumulatedBoundMillis: Long = 0L,
    val lastSettledWall: Long = 0L,
    val lastSettledElapsedNanos: Long = 0L,
    val unbindRequestedAtWall: Long? = null,
    val unbindRequestedAtElapsedNanos: Long? = null,
    val accumulatedUnbindMillis: Long = 0L
)

/** UI-friendly bundle of the raw row + its derived state at a snapshot. */
data class BoundAppWithState(
    val app: BoundApp,
    val state: BindingState
)
