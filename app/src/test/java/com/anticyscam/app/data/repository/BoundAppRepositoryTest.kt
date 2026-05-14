package com.anticyscam.app.data.repository

import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.prefs.NowSnapshot
import com.anticyscam.app.domain.binding.BindingSettleEngine
import com.anticyscam.app.domain.model.BoundApp
import com.anticyscam.app.testing.FakeBoundAppDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BoundAppRepository]. Focuses on the behaviors that
 * cannot be inferred from [BindingSettleEngineTest] alone — namely diff
 * semantics, cooldown lifecycle, and auto-purge at the 48h mark.
 */
class BoundAppRepositoryTest {

    private lateinit var dao: FakeBoundAppDao
    private lateinit var repo: BoundAppRepository

    private val bankPkg = "com.example.bank"
    private val linePkg = "com.example.line"
    private val chromePkg = "com.android.chrome"

    @Before
    fun setUp() {
        dao = FakeBoundAppDao()
        repo = BoundAppRepository(dao)
    }

    private fun now(wallMs: Long = 1_000_000L, elapsedNanos: Long = 0L) =
        NowSnapshot(wallMs, elapsedNanos)

    @Test
    fun `saveDiff inserts new rows with anchors set to now`() = runTest {
        val n = now(wallMs = 5_000_000L, elapsedNanos = 5_000_000_000_000L)
        repo.saveDiff(
            target = listOf(BoundApp(packageName = bankPkg, label = "Bank")),
            now = n
        )

        val rows = dao.rowsSnapshot()
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(bankPkg, row.packageName)
        assertEquals("Bank", row.label)
        assertEquals(n.wallMillis, row.boundAt)
        assertEquals(n.elapsedNanos, row.boundAtElapsedNanos)
        assertEquals(n.wallMillis, row.lastSettledWall)
        assertEquals(n.elapsedNanos, row.lastSettledElapsedNanos)
        assertEquals(0L, row.accumulatedBoundMillis)
        assertNull(row.unbindRequestedAtWall)
    }

    @Test
    fun `saveDiff deletes rows missing from target`() = runTest {
        repo.saveDiff(
            target = listOf(
                BoundApp(packageName = bankPkg, label = "Bank"),
                BoundApp(packageName = linePkg, label = "Line")
            ),
            now = now()
        )
        assertEquals(2, dao.rowsSnapshot().size)

        repo.saveDiff(
            target = listOf(BoundApp(packageName = bankPkg, label = "Bank")),
            now = now()
        )
        val pkgs = dao.rowsSnapshot().map { it.packageName }.toSet()
        assertEquals(setOf(bankPkg), pkgs)
    }

    @Test
    fun `saveDiff preserves existing rows accumulated state`() = runTest {
        dao.seed(
            BoundAppEntity(
                packageName = bankPkg,
                label = "Bank",
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = 60_000L,
                lastSettledWall = 1000L,
                lastSettledElapsedNanos = 1000L,
                unbindRequestedAtWall = null,
                unbindRequestedAtElapsedNanos = null,
                accumulatedUnbindMillis = 0L
            )
        )
        repo.saveDiff(
            target = listOf(
                BoundApp(packageName = bankPkg, label = "Bank"),
                BoundApp(packageName = chromePkg, label = "Chrome")
            ),
            now = now(wallMs = 9_999_999L, elapsedNanos = 9_999_999_000_000L)
        )

        val bank = dao.rowsSnapshot().first { it.packageName == bankPkg }
        // Anchors and accumulator preserved (NOT re-anchored to "now").
        assertEquals(100L, bank.boundAt)
        assertEquals(60_000L, bank.accumulatedBoundMillis)
        assertEquals(1000L, bank.lastSettledWall)

        val chrome = dao.rowsSnapshot().first { it.packageName == chromePkg }
        assertEquals(9_999_999L, chrome.boundAt)
        assertEquals(0L, chrome.accumulatedBoundMillis)
    }

    @Test
    fun `requestUnbind on matured row starts cooldown`() = runTest {
        val seedNow = now(wallMs = 1_000_000L, elapsedNanos = 1_000_000_000_000L)
        dao.seed(
            BoundAppEntity(
                packageName = bankPkg,
                label = "Bank",
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
                lastSettledWall = seedNow.wallMillis,
                lastSettledElapsedNanos = seedNow.elapsedNanos,
                unbindRequestedAtWall = null,
                unbindRequestedAtElapsedNanos = null,
                accumulatedUnbindMillis = 0L
            )
        )

        val laterNow = NowSnapshot(seedNow.wallMillis + 1000L, seedNow.elapsedNanos + 1_000_000_000L)
        repo.requestUnbind(bankPkg, laterNow)

        val row = dao.rowsSnapshot().first()
        assertEquals(laterNow.wallMillis, row.unbindRequestedAtWall)
        assertEquals(laterNow.elapsedNanos, row.unbindRequestedAtElapsedNanos)
        assertEquals(0L, row.accumulatedUnbindMillis)
    }

    @Test
    fun `requestUnbind is idempotent — does not reset cooldown timestamp`() = runTest {
        val seedNow = now(wallMs = 1_000_000L, elapsedNanos = 1_000_000_000_000L)
        val firstRequest = seedNow.wallMillis + 100L
        dao.seed(
            BoundAppEntity(
                packageName = bankPkg,
                label = "Bank",
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
                lastSettledWall = seedNow.wallMillis,
                lastSettledElapsedNanos = seedNow.elapsedNanos,
                unbindRequestedAtWall = firstRequest,
                unbindRequestedAtElapsedNanos = seedNow.elapsedNanos + 100_000_000L,
                accumulatedUnbindMillis = 60_000L // 1 minute into cooldown
            )
        )

        val muchLater = NowSnapshot(
            wallMillis = seedNow.wallMillis + 3_600_000L, // 1h later
            elapsedNanos = seedNow.elapsedNanos + 3_600_000_000_000L
        )
        repo.requestUnbind(bankPkg, muchLater)

        val row = dao.rowsSnapshot().first()
        // Timestamp unchanged — re-confirming should not restart the clock.
        assertEquals(firstRequest, row.unbindRequestedAtWall)
        // Accumulator preserved or advanced — but never reset.
        assertTrue(row.accumulatedUnbindMillis >= 60_000L)
    }

    @Test
    fun `cancelUnbind clears request and resets accumulator`() = runTest {
        val seedNow = now(wallMs = 1_000_000L, elapsedNanos = 1_000_000_000_000L)
        dao.seed(
            BoundAppEntity(
                packageName = bankPkg,
                label = "Bank",
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
                lastSettledWall = seedNow.wallMillis,
                lastSettledElapsedNanos = seedNow.elapsedNanos,
                unbindRequestedAtWall = seedNow.wallMillis,
                unbindRequestedAtElapsedNanos = seedNow.elapsedNanos,
                accumulatedUnbindMillis = 3_600_000L // 1 hour
            )
        )

        repo.cancelUnbind(bankPkg, seedNow)
        val row = dao.rowsSnapshot().first()
        assertNull(row.unbindRequestedAtWall)
        assertNull(row.unbindRequestedAtElapsedNanos)
        assertEquals(0L, row.accumulatedUnbindMillis)
    }

    @Test
    fun `cancelUnbind on a row that was never cooling down is a no-op`() = runTest {
        val seedNow = now()
        dao.seed(
            BoundAppEntity(
                packageName = bankPkg,
                label = "Bank",
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = 60_000L,
                lastSettledWall = seedNow.wallMillis,
                lastSettledElapsedNanos = seedNow.elapsedNanos,
                unbindRequestedAtWall = null,
                unbindRequestedAtElapsedNanos = null,
                accumulatedUnbindMillis = 0L
            )
        )
        val before = dao.rowsSnapshot().first()
        repo.cancelUnbind(bankPkg, seedNow)
        val after = dao.rowsSnapshot().first()
        assertEquals(before, after)
    }

    @Test
    fun `settleAll auto-purges rows whose cooldown reached 48h`() = runTest {
        val seedNow = now(wallMs = 1_000_000L, elapsedNanos = 1_000_000_000_000L)
        // Row 1: cooling down but only halfway through.
        dao.seed(
            BoundAppEntity(
                packageName = linePkg,
                label = "Line",
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
                lastSettledWall = seedNow.wallMillis,
                lastSettledElapsedNanos = seedNow.elapsedNanos,
                unbindRequestedAtWall = seedNow.wallMillis,
                unbindRequestedAtElapsedNanos = seedNow.elapsedNanos,
                accumulatedUnbindMillis = BindingSettleEngine.UNBIND_COOLDOWN_MS / 2
            ),
            // Row 2: cooldown completed (>= 48h).
            BoundAppEntity(
                packageName = bankPkg,
                label = "Bank",
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
                lastSettledWall = seedNow.wallMillis,
                lastSettledElapsedNanos = seedNow.elapsedNanos,
                unbindRequestedAtWall = seedNow.wallMillis,
                unbindRequestedAtElapsedNanos = seedNow.elapsedNanos,
                accumulatedUnbindMillis = BindingSettleEngine.UNBIND_COOLDOWN_MS
            )
        )

        repo.settleAll(seedNow) // no advance — bank is already at threshold.

        val pkgs = dao.rowsSnapshot().map { it.packageName }.toSet()
        assertFalse("Bank should have been auto-purged", bankPkg in pkgs)
        assertTrue(linePkg in pkgs)
    }

    @Test
    fun `clearAll wipes every row regardless of cooldown state`() = runTest {
        val seedNow = now()
        dao.seed(
            BoundAppEntity(
                packageName = bankPkg, label = "Bank",
                boundAt = 1L, boundAtElapsedNanos = 1L,
                accumulatedBoundMillis = 0L,
                lastSettledWall = seedNow.wallMillis,
                lastSettledElapsedNanos = seedNow.elapsedNanos,
                unbindRequestedAtWall = seedNow.wallMillis,
                unbindRequestedAtElapsedNanos = seedNow.elapsedNanos,
                accumulatedUnbindMillis = 0L
            )
        )
        repo.clearAll()
        assertTrue(dao.rowsSnapshot().isEmpty())
    }

    @Test
    fun `snapshot maps every entity column into BoundApp domain`() = runTest {
        dao.seed(
            BoundAppEntity(
                packageName = bankPkg, label = "Bank",
                boundAt = 7L, boundAtElapsedNanos = 9L,
                accumulatedBoundMillis = 11L,
                lastSettledWall = 13L, lastSettledElapsedNanos = 17L,
                unbindRequestedAtWall = 19L,
                unbindRequestedAtElapsedNanos = 23L,
                accumulatedUnbindMillis = 29L
            )
        )
        val snap = repo.snapshot().first()
        assertEquals(bankPkg, snap.packageName)
        assertEquals(7L, snap.boundAt)
        assertEquals(9L, snap.boundAtElapsedNanos)
        assertEquals(11L, snap.accumulatedBoundMillis)
        assertEquals(13L, snap.lastSettledWall)
        assertEquals(17L, snap.lastSettledElapsedNanos)
        assertEquals(19L, snap.unbindRequestedAtWall)
        assertEquals(23L, snap.unbindRequestedAtElapsedNanos)
        assertEquals(29L, snap.accumulatedUnbindMillis)
    }
}
