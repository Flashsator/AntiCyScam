package com.anticyscam.app.data.repository

import com.anticyscam.app.data.crypto.FieldCipher
import com.anticyscam.app.data.local.dao.TransferAccountDao
import com.anticyscam.app.data.local.entity.TransferAccountEntity
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
 */
@Singleton
class TransferAccountRepository @Inject constructor(
    private val dao: TransferAccountDao,
    private val cipher: FieldCipher
) {

    fun observeAccounts(): Flow<List<TransferAccount>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    suspend fun count(): Int = dao.count()

    suspend fun add(name: String, accountNumber: String): AddResult {
        val trimmedName = name.trim()
        val trimmedAccount = accountNumber.trim()
        if (trimmedName.isEmpty() || trimmedAccount.isEmpty()) {
            return AddResult.InvalidInput
        }
        if (dao.count() >= TransferAccount.MAX_ACCOUNTS) {
            return AddResult.LimitReached
        }
        val entity = TransferAccountEntity(
            name = trimmedName,
            accountCipher = cipher.encrypt(trimmedAccount),
            isDefault = false
        )
        val newId = dao.insert(entity)
        return AddResult.Success(newId)
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
                createdAt = 0L // sort before any user-created entry
            )
        )
    }

    suspend fun delete(id: Long): Boolean = dao.deleteIfNotDefault(id) > 0

    suspend fun clear() = dao.clear()

    private fun toDomain(entity: TransferAccountEntity): TransferAccount =
        TransferAccount(
            id = entity.id,
            name = entity.name,
            accountNumber = runCatching { cipher.decrypt(entity.accountCipher) }
                .getOrDefault(""),
            isDefault = entity.isDefault
        )

    sealed interface AddResult {
        data class Success(val id: Long) : AddResult
        data object LimitReached : AddResult
        data object InvalidInput : AddResult
    }
}
