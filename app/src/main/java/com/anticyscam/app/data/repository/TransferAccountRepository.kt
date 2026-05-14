package com.anticyscam.app.data.repository

import com.anticyscam.app.data.crypto.FieldCipher
import com.anticyscam.app.data.local.dao.TransferAccountDao
import com.anticyscam.app.data.local.entity.TransferAccountEntity
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.prefs.DailyAddTracker
import com.anticyscam.app.domain.model.TransferAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the persistence + crypto contract for transfer accounts.
 *
 * Storage uses [TransferAccountDao] which holds rows with an encrypted
 * account number (`account_cipher`). Repository decrypts on read and
 * encrypts on write via [FieldCipher] so the rest of the app sees plain
 * [TransferAccount] domain objects.
 *
 * Business rules enforced here (not in DAO):
 *  - At most [TransferAccount.MAX_ACCOUNTS] rows.
 *  - The default "臨時用" row is seeded once and cannot be deleted.
 *  - Every successful add (or number-change) is recorded against the
 *    [DailyAddTracker] and writes the 24h cooldown anchors so the row
 *    is treated as 臨時用-level during its freshness window.
 */
@Singleton
class TransferAccountRepository @Inject constructor(
    private val dao: TransferAccountDao,
    private val cipher: FieldCipher,
    private val clock: AntiScamClock,
    private val dailyAddTracker: DailyAddTracker
) {

    fun observeAccounts(): Flow<List<TransferAccount>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    suspend fun count(): Int = dao.count()

    suspend fun findById(id: Long): TransferAccount? =
        dao.findById(id)?.let(::toDomain)

    suspend fun add(name: String, accountNumber: String): AddResult {
        val trimmedName = name.trim()
        val trimmedAccount = accountNumber.trim()
        if (trimmedName.isEmpty() || trimmedAccount.isEmpty()) {
            return AddResult.InvalidInput
        }
        if (dao.count() >= TransferAccount.MAX_ACCOUNTS) {
            return AddResult.LimitReached
        }
        // Reserve a slot in the daily counter BEFORE writing to the DB so
        // that a crash between the two would leave the user with a smaller
        // count, not a phantom entry.
        return when (val outcome = dailyAddTracker.recordAddAttempt()) {
            is DailyAddTracker.Outcome.AlreadyLocked ->
                AddResult.DailyLocked(outcome.remainingMs)
            is DailyAddTracker.Outcome.HitLimit -> {
                // This attempt triggered the limit — DO NOT actually insert.
                AddResult.DailyLimitTriggered(outcome.lockUntil)
            }
            is DailyAddTracker.Outcome.Allowed -> {
                val now = clock.now()
                val entity = TransferAccountEntity(
                    name = trimmedName,
                    accountCipher = cipher.encrypt(trimmedAccount),
                    isDefault = false,
                    createdAt = now,
                    cooldownEndsAt = now + TransferAccount.COOLDOWN_DURATION_MS,
                    cooldownOpenCountTarget = 0,
                    lastUsedAt = 0L,
                    dormantConsumed = false
                )
                val newId = dao.insert(entity)
                AddResult.Success(
                    id = newId,
                    countToday = outcome.countToday,
                    warning = outcome.warning
                )
            }
        }
    }

    /**
     * Edit an existing row's name and/or account number. Consumes exactly
     * one slot from `edits_remaining` if anything actually changes — the
     * PRD models "新增完後編輯一次" as one slot total, not one per field,
     * so a name-only tweak still burns the slot.
     *
     * If the *account number* changes, the row is treated like a brand-new
     * add: cooldown anchors reset AND the daily counter ticks. A name-only
     * change is free of those rules but still consumes the slot.
     */
    suspend fun editAccount(
        id: Long,
        newName: String,
        newAccountNumber: String
    ): AddResult {
        val existing = dao.findById(id) ?: return AddResult.InvalidInput
        if (existing.isDefault) return AddResult.InvalidInput
        if (existing.editsRemaining <= 0) return AddResult.EditsExhausted

        val trimmedName = newName.trim()
        val trimmedNumber = newAccountNumber.trim()
        if (trimmedName.isEmpty() || trimmedNumber.isEmpty()) {
            return AddResult.InvalidInput
        }

        val existingNumber = runCatching { cipher.decrypt(existing.accountCipher) }
            .getOrDefault("")
        val numberChanged = trimmedNumber != existingNumber
        val nameChanged = trimmedName != existing.name

        if (!numberChanged && !nameChanged) {
            // No-op save — don't waste the slot.
            return AddResult.Success(id = id, countToday = 0, warning = false)
        }

        if (numberChanged) {
            return when (val outcome = dailyAddTracker.recordAddAttempt()) {
                is DailyAddTracker.Outcome.AlreadyLocked ->
                    AddResult.DailyLocked(outcome.remainingMs)
                is DailyAddTracker.Outcome.HitLimit ->
                    AddResult.DailyLimitTriggered(outcome.lockUntil)
                is DailyAddTracker.Outcome.Allowed -> {
                    val now = clock.now()
                    val rows = dao.replaceAccountCipher(
                        id = id,
                        cipher = cipher.encrypt(trimmedNumber),
                        createdAt = now,
                        cooldownEndsAt = now + TransferAccount.COOLDOWN_DURATION_MS,
                        openTarget = 0
                    )
                    if (rows == 0) return AddResult.EditsExhausted
                    if (nameChanged) dao.rename(id, trimmedName)
                    AddResult.Success(
                        id = id,
                        countToday = outcome.countToday,
                        warning = outcome.warning
                    )
                }
            }
        }

        // Name-only path: free of daily/cooldown rules, but still burns the slot.
        dao.rename(id, trimmedName)
        val rows = dao.decrementEditsRemaining(id)
        if (rows == 0) return AddResult.EditsExhausted
        return AddResult.Success(id = id, countToday = 0, warning = false)
    }

    /** Stamp a successful use — drives the 90-day dormancy clock. */
    suspend fun markUsed(id: Long) {
        dao.touchUsage(id, clock.now())
    }

    /** Called after a dormant account survives its one-time verification. */
    suspend fun consumeDormantVerification(id: Long) {
        dao.markDormantConsumed(id)
        dao.touchUsage(id, clock.now())
    }

    /**
     * Seed the built-in "臨時用" default account exactly once.
     * Safe to call on every app start — does nothing if already present.
     * The default account stores an empty string as its number (no copy
     * happens when the user selects it; it just bypasses the friction).
     */
    suspend fun ensureDefaultSeeded(defaultLabel: String) {
        if (dao.defaultCount() > 0) return
        dao.insert(
            TransferAccountEntity(
                name = defaultLabel,
                accountCipher = cipher.encrypt(""),
                isDefault = true,
                createdAt = 0L, // sort before any user-created entry
                cooldownEndsAt = 0L,
                cooldownOpenCountTarget = 0,
                lastUsedAt = 0L,
                dormantConsumed = false
            )
        )
    }

    suspend fun delete(id: Long): Boolean = dao.deleteIfNotDefault(id) > 0

    /**
     * Wipe user-created accounts only. The default 「臨時用」row is kept
     * forever — clearing app data must never remove it, otherwise the
     * picker sheet loses its always-available fallback.
     */
    suspend fun clear() = dao.clearUserCreated()

    private fun toDomain(entity: TransferAccountEntity): TransferAccount =
        TransferAccount(
            id = entity.id,
            name = entity.name,
            accountNumber = runCatching { cipher.decrypt(entity.accountCipher) }
                .getOrDefault(""),
            isDefault = entity.isDefault,
            createdAt = entity.createdAt,
            cooldownEndsAt = entity.cooldownEndsAt,
            cooldownOpenCountTarget = entity.cooldownOpenCountTarget,
            lastUsedAt = entity.lastUsedAt,
            dormantConsumed = entity.dormantConsumed,
            editsRemaining = entity.editsRemaining
        )

    sealed interface AddResult {
        data class Success(
            val id: Long,
            val countToday: Int,
            val warning: Boolean
        ) : AddResult
        data object LimitReached : AddResult
        data object InvalidInput : AddResult
        /** This attempt is the one that triggered the daily lockdown. */
        data class DailyLimitTriggered(val lockUntil: Long) : AddResult
        /** Lockdown already in effect from an earlier attempt. */
        data class DailyLocked(val remainingMs: Long) : AddResult
        /** The one-shot edit slot for this row is already spent. */
        data object EditsExhausted : AddResult
    }
}
