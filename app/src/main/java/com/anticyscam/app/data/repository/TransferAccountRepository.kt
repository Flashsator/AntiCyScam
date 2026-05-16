package com.anticyscam.app.data.repository

import com.anticyscam.app.data.crypto.FieldCipher
import com.anticyscam.app.data.local.dao.TransferAccountDao
import com.anticyscam.app.data.local.entity.TransferAccountEntity
import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.prefs.DailyAddTracker
import com.anticyscam.app.data.prefs.NowSnapshot
import com.anticyscam.app.domain.model.TransferAccount
import com.anticyscam.app.domain.model.TransferAccountState
import com.anticyscam.app.domain.transfer.TransferAccountSettleEngine
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
 *  - At most [TransferAccount.MAX_ACCOUNTS] rows including the default.
 *  - The default 「臨時用」 row is seeded once and is exempt from every
 *    cooldown and from the daily-add counter.
 *  - Every successful add (or number-change edit) is recorded against
 *    [DailyAddTracker] and writes maturation anchors so the row starts
 *    counting toward the 24h 已綁定 gate.
 *  - Edit is only allowed while a row is [TransferAccountState.PendingMaturation]
 *    and `editsRemaining > 0`. Matured / PendingDeletion rows refuse edits.
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

    /**
     * Derive the runtime state of a row at this instant — used by the UI
     * layer (cards, dialogs) without going through a full Flow recompose.
     */
    suspend fun stateOf(id: Long): TransferAccountState? {
        val row = dao.findById(id) ?: return null
        return TransferAccountSettleEngine.deriveState(row, clock.snapshot())
    }

    suspend fun add(
        name: String,
        accountNumber: String,
        bankCode: String? = null
    ): AddResult {
        val trimmedName = name.trim()
        val trimmedAccount = accountNumber.trim()
        val trimmedBankCode = bankCode?.trim()?.ifEmpty { null }
        if (trimmedName.isEmpty() || !isValidAccountNumber(trimmedAccount)) {
            return AddResult.InvalidInput
        }
        if (!isValidBankCode(trimmedBankCode)) {
            return AddResult.InvalidInput
        }
        if (dao.count() >= TransferAccount.MAX_ACCOUNTS) {
            return AddResult.LimitReached
        }
        // Reserve a slot in the daily counter BEFORE writing to the DB so
        // that a crash between the two leaves the user with a smaller
        // count, not a phantom entry.
        return when (val outcome = dailyAddTracker.recordAddAttempt()) {
            is DailyAddTracker.Outcome.AlreadyLocked ->
                AddResult.DailyLocked(outcome.remainingMs)
            is DailyAddTracker.Outcome.HitLimit ->
                AddResult.DailyLimitTriggered(outcome.remainingMs)
            is DailyAddTracker.Outcome.Allowed -> {
                val snap = clock.snapshot()
                val entity = TransferAccountEntity(
                    name = trimmedName,
                    accountCipher = cipher.encrypt(trimmedAccount),
                    bankCode = trimmedBankCode,
                    isDefault = false,
                    createdAt = snap.wallMillis,
                    boundAnchorWall = snap.wallMillis,
                    boundAnchorElapsedNanos = snap.elapsedNanos,
                    accumulatedBoundMillis = 0L,
                    deleteRequestedAtWall = null,
                    deleteRequestedAtElapsedNanos = null,
                    accumulatedDeleteMillis = 0L,
                    lastSettledWall = snap.wallMillis,
                    lastSettledElapsedNanos = snap.elapsedNanos
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
     * Edit an existing row. Allowed only while the row is in
     * [TransferAccountState.PendingMaturation] (< 24h) AND
     * `editsRemaining > 0`. A Matured / PendingDeletion / Default row
     * cannot be edited — that closes the 「等到使用者 24h 後再竄改」 attack.
     *
     * If the account number changes, the row is reset to a fresh
     * PendingMaturation with new anchors AND the daily counter ticks. A
     * name-only edit is free of daily/cooldown rules but still consumes
     * the slot.
     */
    suspend fun editAccount(
        id: Long,
        newName: String,
        newAccountNumber: String,
        newBankCode: String? = null
    ): AddResult {
        val existing = dao.findById(id) ?: return AddResult.InvalidInput
        if (existing.isDefault) return AddResult.InvalidInput
        if (existing.editsRemaining <= 0) return AddResult.EditsExhausted

        val state = TransferAccountSettleEngine.deriveState(existing, clock.snapshot())
        if (state !is TransferAccountState.PendingMaturation) {
            return AddResult.EditWindowClosed
        }

        val trimmedName = newName.trim()
        val trimmedNumber = newAccountNumber.trim()
        val trimmedBankCode = newBankCode?.trim()?.ifEmpty { null }
        if (trimmedName.isEmpty() || !isValidAccountNumber(trimmedNumber)) {
            return AddResult.InvalidInput
        }
        if (!isValidBankCode(trimmedBankCode)) {
            return AddResult.InvalidInput
        }

        // Spec #3: any 確認 burns the one-shot slot — even a true no-op
        // confirm permanently retires the edit button. We only branch on
        // whether the number changed (because that resets the 24h
        // maturation timer + ticks the daily counter); name / bank-code
        // diffs and pure no-ops both fall through to the slot-burn path.
        val existingNumber = runCatching { cipher.decrypt(existing.accountCipher) }
            .getOrDefault("")
        val numberChanged = trimmedNumber != existingNumber

        if (numberChanged) {
            return when (val outcome = dailyAddTracker.recordAddAttempt()) {
                is DailyAddTracker.Outcome.AlreadyLocked ->
                    AddResult.DailyLocked(outcome.remainingMs)
                is DailyAddTracker.Outcome.HitLimit ->
                    AddResult.DailyLimitTriggered(outcome.remainingMs)
                is DailyAddTracker.Outcome.Allowed -> {
                    val snap = clock.snapshot()
                    val rows = dao.replaceAccountCipher(
                        id = id,
                        cipher = cipher.encrypt(trimmedNumber),
                        bankCode = trimmedBankCode,
                        nowWall = snap.wallMillis,
                        nowElapsedNanos = snap.elapsedNanos
                    )
                    if (rows == 0) return AddResult.EditsExhausted
                    // replaceAccountCipher only touches the cipher + anchors;
                    // sync the label + bank code with a follow-up UPDATE.
                    dao.updateNameAndBankCode(id, trimmedName, trimmedBankCode)
                    AddResult.Success(
                        id = id,
                        countToday = outcome.countToday,
                        warning = outcome.warning
                    )
                }
            }
        }

        // Number unchanged → no daily-counter tick, no maturation reset.
        // We still rewrite name + bank code (cheap idempotent UPDATE) and
        // ALWAYS decrement editsRemaining so the edit icon disappears.
        dao.updateNameAndBankCode(id, trimmedName, trimmedBankCode)
        val rows = dao.decrementEditsRemaining(id)
        if (rows == 0) return AddResult.EditsExhausted
        return AddResult.Success(id = id, countToday = 0, warning = false)
    }

    /**
     * Request deletion of a row.
     *
     * Branching by current state (per user spec):
     *  - PendingMaturation (< 24h, 「臨時用」 phase) → hard-delete immediately.
     *    These rows haven't earned the 「已綁定」 trust gate yet, so there's
     *    no anti-tamper benefit in forcing a cooldown.
     *  - Matured (≥ 24h) → start the 48h cooldown so a hijacker who got
     *    past the lock screen still can't quietly burn a bound recipient.
     *  - PendingDeletion → no-op (already cooling down). Idempotent.
     *  - Default 「臨時用」 → no-op (DAO guards this anyway).
     */
    suspend fun requestDelete(id: Long): Boolean {
        val row = dao.findById(id) ?: return false
        if (row.isDefault) return false
        val state = TransferAccountSettleEngine.deriveState(row, clock.snapshot())
        return when (state) {
            is TransferAccountState.PendingMaturation ->
                dao.deleteIfNotDefault(id) > 0
            TransferAccountState.Matured -> {
                val snap = clock.snapshot()
                dao.requestDelete(
                    id = id,
                    nowWall = snap.wallMillis,
                    nowElapsedNanos = snap.elapsedNanos
                ) > 0
            }
            is TransferAccountState.PendingDeletion,
            TransferAccountState.Default -> false
        }
    }

    /** Cancel a pending-delete and return the row to its prior state. */
    suspend fun cancelDelete(id: Long): Boolean = dao.cancelDelete(id) > 0

    /**
     * Settle every non-default row against the current clock; hard-delete
     * any row whose 48h delete cooldown has expired. Persists settled
     * anchors so subsequent UI ticks don't replay the same advance.
     *
     * Called from the ViewModel's per-second ticker — cheap because the
     * settle engine is pure-functional + writes only on actual change.
     */
    suspend fun sweepAutoDeletes() {
        val snap = clock.snapshot()
        val rows = dao.allNonDefault()
        for (row in rows) {
            if (TransferAccountSettleEngine.isAutoDeleteDue(row, snap)) {
                dao.deleteIfNotDefault(row.id)
                continue
            }
            val settled = TransferAccountSettleEngine.settle(row, snap)
            if (settled != row) dao.update(settled)
        }
    }

    /**
     * Seed the built-in 「臨時用」 default account exactly once. Safe to
     * call on every app start — does nothing if already present. The
     * default account stores an empty string as its number (no copy
     * happens when the user selects it; it just bypasses friction).
     */
    suspend fun ensureDefaultSeeded(defaultLabel: String) {
        if (dao.defaultCount() > 0) return
        dao.insert(
            TransferAccountEntity(
                name = defaultLabel,
                accountCipher = cipher.encrypt(""),
                bankCode = null,
                isDefault = true,
                createdAt = 0L, // sort before any user-created entry
                boundAnchorWall = 0L,
                boundAnchorElapsedNanos = 0L,
                accumulatedBoundMillis = 0L,
                deleteRequestedAtWall = null,
                deleteRequestedAtElapsedNanos = null,
                accumulatedDeleteMillis = 0L,
                lastSettledWall = 0L,
                lastSettledElapsedNanos = 0L
            )
        )
    }

    /**
     * Hard-delete bypass — only used by debug/cleanup paths. The normal
     * deletion flow MUST go through [requestDelete] + [sweepAutoDeletes].
     */
    suspend fun forceDelete(id: Long): Boolean = dao.deleteIfNotDefault(id) > 0

    /**
     * Wipe user-created accounts only. The default 「臨時用」 row is kept
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
            boundAnchorWall = entity.boundAnchorWall,
            boundAnchorElapsedNanos = entity.boundAnchorElapsedNanos,
            accumulatedBoundMillis = entity.accumulatedBoundMillis,
            deleteRequestedAtWall = entity.deleteRequestedAtWall,
            deleteRequestedAtElapsedNanos = entity.deleteRequestedAtElapsedNanos,
            accumulatedDeleteMillis = entity.accumulatedDeleteMillis,
            lastSettledWall = entity.lastSettledWall,
            lastSettledElapsedNanos = entity.lastSettledElapsedNanos,
            editsRemaining = entity.editsRemaining,
            bankCode = entity.bankCode
        )

    /**
     * Returns the engine state of a domain row at a given clock snapshot —
     * convenient for view models that already have a [TransferAccount] in
     * hand. Calls the entity-free overload so the per-second tick on the
     * main screen does not allocate a throw-away entity per visible card
     * (see memory: scroll-perf-rule).
     */
    fun stateOf(account: TransferAccount, now: NowSnapshot): TransferAccountState =
        TransferAccountSettleEngine.deriveState(account, now)

    /**
     * 轉帳帳號規範：限純數字、不可為空。UI 端已用數字鍵盤 + filter 擋下，
     * 這裡是 repository 層的第二道閘，防止非 UI 路徑寫入髒資料。
     */
    private fun isValidAccountNumber(value: String): Boolean =
        value.isNotEmpty() && value.all { it.isDigit() }

    /**
     * 銀行代碼規範：選填；一旦填寫，必須剛好 [BANK_CODE_LENGTH] 位純數字
     * （例 007、012）。null 代表使用者未填，視為合規。
     */
    private fun isValidBankCode(value: String?): Boolean =
        value == null || (value.length == BANK_CODE_LENGTH && value.all { it.isDigit() })

    sealed interface AddResult {
        data class Success(
            val id: Long,
            val countToday: Int,
            val warning: Boolean
        ) : AddResult
        data object LimitReached : AddResult
        data object InvalidInput : AddResult
        /** This attempt is the one that triggered the daily lockdown. */
        data class DailyLimitTriggered(val remainingMs: Long) : AddResult
        /** Lockdown already in effect from an earlier attempt. */
        data class DailyLocked(val remainingMs: Long) : AddResult
        /** The one-shot edit slot for this row is already spent. */
        data object EditsExhausted : AddResult
        /** Row is past the 24h 編輯窗 (Matured / PendingDeletion). */
        data object EditWindowClosed : AddResult
    }

    private companion object {
        /** 國內銀行代碼固定 3 位數字。 */
        const val BANK_CODE_LENGTH = 3
    }
}
