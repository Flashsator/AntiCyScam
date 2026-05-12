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

    suspend fun isBound(packageName: String): Boolean =
        dao.isBound(packageName)

    suspend fun replaceAll(apps: List<BoundApp>) {
        dao.clear()
        if (apps.isEmpty()) return
        dao.insertAll(apps.map { BoundAppEntity(packageName = it.packageName, label = it.label) })
    }

    suspend fun bind(apps: List<BoundApp>) {
        if (apps.isEmpty()) return
        dao.insertAll(apps.map { BoundAppEntity(packageName = it.packageName, label = it.label) })
    }

    suspend fun unbind(packageName: String) = dao.deleteByPackage(packageName)

    private fun toDomain(entity: BoundAppEntity): BoundApp =
        BoundApp(packageName = entity.packageName, label = entity.label)
}
