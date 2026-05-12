package com.anticyscam.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistence form of a transfer account.
 *
 * The account number is stored encrypted (base64 of AES-GCM ciphertext)
 * via [com.anticyscam.app.data.crypto.FieldCipher]. The repository layer
 * handles encryption / decryption so callers see plain Strings.
 *
 * [isDefault] = true marks the built-in "臨時用" entry which is created on
 * first run and cannot be deleted by the user.
 */
@Entity(tableName = "transfer_accounts")
data class TransferAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "account_cipher")
    val accountCipher: String,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
