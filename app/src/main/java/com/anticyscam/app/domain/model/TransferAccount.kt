package com.anticyscam.app.domain.model

/**
 * Domain representation of a transfer account.
 *
 * `accountNumber` is plain text at this layer — encryption is an implementation
 * detail of the data layer.
 *
 * Plan v4 simplifies the cooldown rule to pure 24h wall-clock — a freshly
 * added account stays in 冷卻 (cooldown) until [cooldownEndsAt]. The previous
 * "+3 app opens" anti-tamper gate has been removed. `cooldownOpenCountTarget`
 * is retained on the entity for schema stability but ignored by [status].
 *
 * `lastUsedAt` powers the 90-day dormancy rule.
 */
data class TransferAccount(
    val id: Long,
    val name: String,
    val accountNumber: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val cooldownEndsAt: Long,
    val cooldownOpenCountTarget: Int,
    val lastUsedAt: Long,
    val dormantConsumed: Boolean,
    val editsRemaining: Int
) {
    /**
     * Compute the runtime status from current clocks. Pure function — no
     * side effects. Plan v4: cooldown is now pure 24h wall-clock; the
     * `openCount` parameter is kept for callsite stability but unused.
     */
    @Suppress("UNUSED_PARAMETER")
    fun status(now: Long, openCount: Int): Status {
        if (isDefault) return Status.Default
        if (now < cooldownEndsAt) {
            val remaining = (cooldownEndsAt - now).coerceAtLeast(0L)
            return Status.InCooldown(remainingMs = remaining)
        }
        val lastUsed = lastUsedAt
        if (lastUsed > 0L && now - lastUsed >= DORMANT_AFTER_MS && !dormantConsumed) {
            return Status.Dormant
        }
        return Status.Normal
    }

    sealed interface Status {
        data object Default : Status
        data object Normal : Status
        data class InCooldown(val remainingMs: Long) : Status
        data object Dormant : Status
    }

    companion object {
        const val MAX_ACCOUNTS = 5
        const val COOLDOWN_DURATION_MS: Long = 24L * 60 * 60 * 1000
        const val DORMANT_AFTER_MS: Long = 90L * 24 * 60 * 60 * 1000
        // Plan v4: a freshly added account may be edited exactly once. After
        // the edit consumes this slot the only remaining action is deletion,
        // making "wait, transfer to *this* number instead" spoofing via
        // repeated edits impossible.
        const val INITIAL_EDITS_REMAINING: Int = 1
    }
}
