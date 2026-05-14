package com.anticyscam.app.data.repository

import com.anticyscam.app.data.local.dao.BoundAppDao
import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.prefs.NowSnapshot
import com.anticyscam.app.domain.binding.BindingSettleEngine
import com.anticyscam.app.domain.model.BoundApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the set of user-bound third-party apps (e.g. Line, banking apps).
 *
 * v4: each row now carries a maturation + cooldown-unbind state. Time is
 * advanced via [settleAll] / [bootSettleAll]; UI countdowns are derived
 * from the raw row + [NowSnapshot] without writing each tick.
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
     * Diff-aware save that preserves bind anchors for rows that survive.
     * New rows are anchored to [now] (both wall + monotonic). Removed rows
     * are deleted unconditionally — callers MUST classify by state
     * (MATURED unbinds go through [requestUnbind] instead).
     */
    suspend fun saveDiff(target: List<BoundApp>, now: NowSnapshot) {
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
                    boundAt = now.wallMillis,
                    boundAtElapsedNanos = now.elapsedNanos,
                    accumulatedBoundMillis = 0L,
                    lastSettledWall = now.wallMillis,
                    lastSettledElapsedNanos = now.elapsedNanos,
                    unbindRequestedAtWall = null,
                    unbindRequestedAtElapsedNanos = null,
                    accumulatedUnbindMillis = 0L
                )
            }
        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        // Existing rows preserved — their accumulated millis stay intact.
    }

    /**
     * Advance every row's accumulated counters using the settle engine.
     * Auto-purges rows whose unbind cooldown finished.
     *
     * Call from MainFunctionViewModel periodically (e.g. 60s) and from FGS
     * onCreate / BootReceiver as safety nets. Tick-frequency for UI display
     * is decoupled: UI derives state from raw rows + a 1s ticker.
     */
    suspend fun settleAll(now: NowSnapshot) {
        val rows = dao.all()
        if (rows.isEmpty()) return
        val (toUpdate, toDelete) = partitionAfterSettle(
            rows.map { BindingSettleEngine.settle(it, now) }
        )
        if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
        if (toDelete.isNotEmpty()) dao.deleteByPackages(toDelete.map { it.packageName })
    }

    /**
     * Boot-recovery settle: monotonic clock just reset. Uses wall-only with
     * 24h clamp via [BindingSettleEngine.bootSettle].
     */
    suspend fun bootSettleAll(now: NowSnapshot) {
        val rows = dao.all()
        if (rows.isEmpty()) return
        val (toUpdate, toDelete) = partitionAfterSettle(
            rows.map { BindingSettleEngine.bootSettle(it, now) }
        )
        if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
        if (toDelete.isNotEmpty()) dao.deleteByPackages(toDelete.map { it.packageName })
    }

    /**
     * Mark a row as cooling-down. Used by the BindAppsScreen save flow
     * after the user confirms the 48h dialog for a MATURED uncheck.
     *
     * Idempotent: if the row already has an unbind request, the request
     * timestamps are NOT reset — re-confirming during the cooldown should
     * not extend it.
     */
    suspend fun requestUnbind(packageName: String, now: NowSnapshot) {
        val row = dao.all().firstOrNull { it.packageName == packageName } ?: return
        if (row.unbindRequestedAtWall != null) return
        val settled = BindingSettleEngine.settle(row, now)
        dao.update(
            settled.copy(
                unbindRequestedAtWall = now.wallMillis,
                unbindRequestedAtElapsedNanos = now.elapsedNanos,
                accumulatedUnbindMillis = 0L
            )
        )
    }

    /**
     * Cancel an active 48h cooldown. Row returns to MATURED with
     * accumulatedUnbindMillis reset to 0 (next request restarts from scratch).
     */
    suspend fun cancelUnbind(packageName: String, now: NowSnapshot) {
        val row = dao.all().firstOrNull { it.packageName == packageName } ?: return
        if (row.unbindRequestedAtWall == null) return
        val settled = BindingSettleEngine.settle(row, now)
        dao.update(
            settled.copy(
                unbindRequestedAtWall = null,
                unbindRequestedAtElapsedNanos = null,
                accumulatedUnbindMillis = 0L
            )
        )
    }

    /**
     * Settings "clear all data" entry point. Wipes every bound row without
     * any cooldown check — destructive resets bypass the engine because the
     * user has already confirmed destructive intent at the UI layer.
     */
    suspend fun clearAll() {
        val all = dao.allPackageNames()
        if (all.isNotEmpty()) dao.deleteByPackages(all)
    }

    private fun partitionAfterSettle(
        settled: List<BoundAppEntity>
    ): Pair<List<BoundAppEntity>, List<BoundAppEntity>> {
        val toDelete = settled.filter {
            it.unbindRequestedAtWall != null &&
                it.accumulatedUnbindMillis >= BindingSettleEngine.UNBIND_COOLDOWN_MS
        }
        val toDeleteKeys = toDelete.map { it.packageName }.toSet()
        val toUpdate = settled.filter { it.packageName !in toDeleteKeys }
        return toUpdate to toDelete
    }

    private fun toDomain(entity: BoundAppEntity): BoundApp =
        BoundApp(
            packageName = entity.packageName,
            label = entity.label,
            boundAt = entity.boundAt,
            boundAtElapsedNanos = entity.boundAtElapsedNanos,
            accumulatedBoundMillis = entity.accumulatedBoundMillis,
            lastSettledWall = entity.lastSettledWall,
            lastSettledElapsedNanos = entity.lastSettledElapsedNanos,
            unbindRequestedAtWall = entity.unbindRequestedAtWall,
            unbindRequestedAtElapsedNanos = entity.unbindRequestedAtElapsedNanos,
            accumulatedUnbindMillis = entity.accumulatedUnbindMillis
        )
}
