package com.anticyscam.app.testing

import com.anticyscam.app.data.local.dao.BoundAppDao
import com.anticyscam.app.data.local.entity.BoundAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [BoundAppDao] used across pure-JVM unit tests. Tracks rows by
 * packageName so update/insert semantics mirror Room's REPLACE behavior.
 *
 * Emits via a StateFlow so VM tests can collect observeAll if needed.
 */
class FakeBoundAppDao : BoundAppDao {

    private val rows = mutableMapOf<String, BoundAppEntity>()
    private val flow = MutableStateFlow<List<BoundAppEntity>>(emptyList())

    fun seed(vararg entities: BoundAppEntity) {
        entities.forEach { rows[it.packageName] = it }
        emit()
    }

    fun rowsSnapshot(): List<BoundAppEntity> = rows.values.toList()

    private fun emit() {
        flow.value = rows.values.sortedBy { it.boundAt }
    }

    override fun observeAll(): Flow<List<BoundAppEntity>> = flow.asStateFlow()

    override suspend fun all(): List<BoundAppEntity> =
        rows.values.sortedBy { it.boundAt }

    override suspend fun allPackageNames(): List<String> = rows.keys.toList()

    override suspend fun isBound(pkg: String): Boolean = pkg in rows

    override suspend fun insertAll(apps: List<BoundAppEntity>) {
        apps.forEach { rows[it.packageName] = it }
        emit()
    }

    override suspend fun update(row: BoundAppEntity) {
        rows[row.packageName] = row
        emit()
    }

    override suspend fun updateAll(rows: List<BoundAppEntity>) {
        rows.forEach { this.rows[it.packageName] = it }
        emit()
    }

    override suspend fun deleteByPackage(pkg: String) {
        rows.remove(pkg)
        emit()
    }

    override suspend fun deleteByPackages(pkgs: List<String>) {
        pkgs.forEach { rows.remove(it) }
        emit()
    }

    override suspend fun clear() {
        rows.clear()
        emit()
    }
}
