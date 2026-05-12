package com.anticyscam.app.domain.model

/**
 * Domain representation of a transfer account. The [accountNumber] is plain
 * text at this layer — encryption is an implementation detail of the data
 * layer.
 */
data class TransferAccount(
    val id: Long,
    val name: String,
    val accountNumber: String,
    val isDefault: Boolean
) {
    companion object {
        const val MAX_ACCOUNTS = 5
        const val DEFAULT_ACCOUNT_NAME_KEY = "__default__"
    }
}
