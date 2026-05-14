package com.anticyscam.app.data.repository

import com.anticyscam.app.data.local.dao.BoundAppDao
import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.domain.model.BoundApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the set of user-bound third-party apps (e.g. Line, banking apps).
 *
 * Phase 4 will wire this into the binding UI; Phase 7 will query
 * [isBound]/[allPackageNamesSnapshot] from the AccessibilityService to
 * decide whether to raise the blocking warning.
 */
@Singleton
open class BoundAppRepository @Inject constructor(
    private val dao: BoundAppDao
) {

    fun observeBoundApps(): Flow<List<BoundApp>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    suspend fun allPackageNamesSnapshot(): Set<String> =
        dao.allPackageNames().toSet()

    suspend fun snapshot(): List<BoundApp> = dao.all().map(::toDomain)

    suspend fun isBound(packageName: String): Boolean =
        dao.isBound(packageName)

    /**
     * Diff-aware save that preserves `bound_at` for rows that survive across
     * the save. New rows get `now` as their bind timestamp; removed rows are
     * deleted; rows present in both states keep their original `bound_at`.
     *
     * Plan v4 Item 6: any caller that wants to remove a row MUST verify the
     * 24h unbind gate at the UI layer (or via [canUnbind]); this method does
     * NOT enforce the gate — it just persists the requested target set.
     */
    suspend fun saveDiff(target: List<BoundApp>, now: Long) {
        val existing = dao.all().associateBy { it.packageName }
        val targetByPkg = target.associateBy { it.packageName }

        val toDelete = existing.keys - targetByPkg.keys
        if (toDelete.isNotEmpty()) dao.deleteByPackages(toDelete.toList())

        val toInsert = targetByPkg.values
            .filter { it.packageName !in existing.keys }
            .map {
                BoundAppEntity(
                    packageName = it.packageName,
                    label = it.label,
                    boundAt = now
                )
            }
        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        // Existing rows are intentionally left alone — preserving `bound_at`.
    }

    suspend fun bind(apps: List<BoundApp>) {
        if (apps.isEmpty()) return
        dao.insertAll(apps.map { BoundAppEntity(packageName = it.packageName, label = it.label) })
    }

    suspend fun unbind(packageName: String) = dao.deleteByPackage(packageName)

    /**
     * Settings "clear all data" entry point. Wipes every bound row without
     * any 24h gate check — destructive resets bypass [BoundApp.UNBIND_LOCK_MS]
     * because the user has already confirmed the destructive intent at the UI
     * layer.
     */
    suspend fun clearAll() {
        val all = dao.allPackageNames()
        if (all.isNotEmpty()) dao.deleteByPackages(all)
    }

    private fun toDomain(entity: BoundAppEntity): BoundApp =
        BoundApp(
            packageName = entity.packageName,
            label = entity.label,
            boundAt = entity.boundAt
        )
}
