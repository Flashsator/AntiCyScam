package com.anticyscam.app.domain.model

/**
 * Computed state of a bound row, derived from accumulated millis + the
 * presence/absence of an unbind request. Pure value type — no I/O, no time
 * sources. Owned by [BindingSettleEngine.deriveState].
 *
 * UI uses this to pick the row footer; ViewModels use it to decide whether
 * an uncheck triggers the cooldown dialog or is a free instant-unbind.
 */
sealed interface BindingState {
    /** Row does not exist (or has been auto-purged after cooldown). */
    data object Unbound : BindingState

    /**
     * Still inside the 24h maturation window after first bind.
     * [remainingMs] is how much more accumulated time is needed before the
     * row turns Matured; UI uses this to render a countdown.
     */
    data class PendingMaturation(val remainingMs: Long) : BindingState

    /** 24h matured — uncheck now requires a 48h cooldown. */
    data object Matured : BindingState

    /**
     * Unbind has been requested and is counting down to auto-removal.
     * [remainingMs] is time left until the row is hard-deleted.
     */
    data class PendingUnbind(val remainingMs: Long) : BindingState
}
