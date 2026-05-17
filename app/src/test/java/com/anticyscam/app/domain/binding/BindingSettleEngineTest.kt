package com.anticyscam.app.domain.binding

import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.prefs.NowSnapshot
import com.anticyscam.app.domain.model.BindingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for [BindingSettleEngine].
 *
 * Focus areas (the only things worth getting wrong):
 *  - The min(wallDelta, elapsedDelta) clock-tampering rule
 *  - The "either anchor is 0 → 0 advance" first-settle guard
 *  - bootSettle's MAX_BOOT_WALL_DELTA_MS clamp
 *  - deriveState classification at the 24h / 48h boundaries
 *  - deriveState is non-mutating (purity)
 */
class BindingSettleEngineTest {

    private val H = 60L * 60L * 1000L
    private val NANOS_PER_MS = 1_000_000L

    // Express test deltas as fractions of the live constants so the suite
    // stays green whether MATURATION_MS is 24h (prod) or 2min (temp test).
    private val M = BindingSettleEngine.MATURATION_MS
    private val U = BindingSettleEngine.UNBIND_COOLDOWN_MS
    private val MAX_BOOT = BindingSettleEngine.MAX_BOOT_WALL_DELTA_MS

    private fun freshRow(
        wallNow: Long,
        elapsedNanosNow: Long,
        accumulatedBoundMillis: Long = 0L,
        accumulatedUnbindMillis: Long = 0L,
        unbindRequestedAtWall: Long? = null
    ) = BoundAppEntity(
        packageName = "com.example.bank",
        label = "Bank",
        boundAt = wallNow,
        boundAtElapsedNanos = elapsedNanosNow,
        accumulatedBoundMillis = accumulatedBoundMillis,
        lastSettledWall = wallNow,
        lastSettledElapsedNanos = elapsedNanosNow,
        unbindRequestedAtWall = unbindRequestedAtWall,
        unbindRequestedAtElapsedNanos = unbindRequestedAtWall?.let { elapsedNanosNow },
        accumulatedUnbindMillis = accumulatedUnbindMillis
    )

    // ──────────────────────────────────────────────────────────────────────
    // settle() — normal flow + tampering immunity
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `settle with first-settle anchors zero returns no advance`() {
        // Migration path: both anchors NULL → 0 → we skip advancement on the
        // first tick so a row migrated from v3 doesn't "catch up" by claiming
        // wallMillis worth of progress since epoch.
        val row = BoundAppEntity(
            packageName = "p", label = "L",
            lastSettledWall = 0L,
            lastSettledElapsedNanos = 0L
        )
        val now = NowSnapshot(wallMillis = 1_700_000_000_000L, elapsedNanos = 500_000_000L)
        val out = BindingSettleEngine.settle(row, now)
        assertEquals(0L, out.accumulatedBoundMillis)
        // Anchors must move forward so the next settle has a baseline.
        assertEquals(now.wallMillis, out.lastSettledWall)
        assertEquals(now.elapsedNanos, out.lastSettledElapsedNanos)
    }

    @Test
    fun `settle normal flow adds min of wall and elapsed delta`() {
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(baseWall, baseElapsed)
        val advance = M / 2
        val now = NowSnapshot(
            wallMillis = baseWall + advance,
            elapsedNanos = baseElapsed + advance * NANOS_PER_MS
        )
        val out = BindingSettleEngine.settle(row, now)
        assertEquals(advance, out.accumulatedBoundMillis)
    }

    @Test
    fun `settle ignores wall rewind`() {
        // User tries to extend their cooldown by rewinding system clock.
        // wallDelta goes negative; min(negative, positive) → still ≥0 after
        // the max(0, _) clamp.
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(baseWall, baseElapsed)
        val now = NowSnapshot(
            wallMillis = baseWall - H, // rewound 1h
            elapsedNanos = baseElapsed + H * NANOS_PER_MS
        )
        val out = BindingSettleEngine.settle(row, now)
        assertEquals(0L, out.accumulatedBoundMillis)
    }

    @Test
    fun `settle ignores wall fast-forward (bounded by elapsed)`() {
        // User tries to skip the timer by setting system clock far forward.
        // elapsedDelta is the bounding clock — only that much gets credited.
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(baseWall, baseElapsed)
        val elapsedAdvance = M / 2
        val now = NowSnapshot(
            wallMillis = baseWall + 30L * H, // huge wall jump, ignored
            elapsedNanos = baseElapsed + elapsedAdvance * NANOS_PER_MS
        )
        val out = BindingSettleEngine.settle(row, now)
        assertEquals(elapsedAdvance, out.accumulatedBoundMillis)
    }

    @Test
    fun `settle caps accumulatedBoundMillis at MATURATION_MS`() {
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(
            wallNow = baseWall,
            elapsedNanosNow = baseElapsed,
            accumulatedBoundMillis = 23L * H
        )
        val now = NowSnapshot(
            wallMillis = baseWall + 5L * H,
            elapsedNanos = baseElapsed + 5L * H * NANOS_PER_MS
        )
        val out = BindingSettleEngine.settle(row, now)
        assertEquals(BindingSettleEngine.MATURATION_MS, out.accumulatedBoundMillis)
    }

    @Test
    fun `settle in cooldown advances unbind counter not bound counter`() {
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(
            wallNow = baseWall,
            elapsedNanosNow = baseElapsed,
            accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
            unbindRequestedAtWall = baseWall,
            accumulatedUnbindMillis = 0L
        )
        val advance = U / 2
        val now = NowSnapshot(
            wallMillis = baseWall + advance,
            elapsedNanos = baseElapsed + advance * NANOS_PER_MS
        )
        val out = BindingSettleEngine.settle(row, now)
        // Bound counter must NOT advance during cooldown.
        assertEquals(BindingSettleEngine.MATURATION_MS, out.accumulatedBoundMillis)
        assertEquals(advance, out.accumulatedUnbindMillis)
    }

    @Test
    fun `settle caps accumulatedUnbindMillis at UNBIND_COOLDOWN_MS`() {
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(
            wallNow = baseWall,
            elapsedNanosNow = baseElapsed,
            accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
            unbindRequestedAtWall = baseWall,
            accumulatedUnbindMillis = 47L * H
        )
        val now = NowSnapshot(
            wallMillis = baseWall + 10L * H,
            elapsedNanos = baseElapsed + 10L * H * NANOS_PER_MS
        )
        val out = BindingSettleEngine.settle(row, now)
        assertEquals(BindingSettleEngine.UNBIND_COOLDOWN_MS, out.accumulatedUnbindMillis)
    }

    // ──────────────────────────────────────────────────────────────────────
    // bootSettle() — wall-only with 24h clamp
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `bootSettle credits wall delta within clamp`() {
        val baseWall = 1_700_000_000_000L
        val row = freshRow(wallNow = baseWall, elapsedNanosNow = 999L)
        val wallAdvance = M / 2 // safely under both clamps
        val now = NowSnapshot(wallMillis = baseWall + wallAdvance, elapsedNanos = 0L)
        val out = BindingSettleEngine.bootSettle(row, now)
        assertEquals(wallAdvance, out.accumulatedBoundMillis)
    }

    @Test
    fun `bootSettle clamps wall delta at MAX_BOOT_WALL_DELTA_MS`() {
        // Attack: power off, wind clock forward by 1 year, power on. We must
        // not credit a year of progress — bootSettle clamps at MAX_BOOT_WALL_DELTA_MS,
        // then applyAdvance further clamps the bound counter at MATURATION_MS.
        // Visible upper bound = min of the two.
        val baseWall = 1_700_000_000_000L
        val row = freshRow(wallNow = baseWall, elapsedNanosNow = 999L)
        val now = NowSnapshot(
            wallMillis = baseWall + 365L * 24L * H,
            elapsedNanos = 0L
        )
        val out = BindingSettleEngine.bootSettle(row, now)
        assertEquals(minOf(MAX_BOOT, M), out.accumulatedBoundMillis)
    }

    @Test
    fun `bootSettle ignores wall rewind`() {
        val baseWall = 1_700_000_000_000L
        val row = freshRow(wallNow = baseWall, elapsedNanosNow = 999L)
        val now = NowSnapshot(wallMillis = baseWall - 3L * H, elapsedNanos = 0L)
        val out = BindingSettleEngine.bootSettle(row, now)
        assertEquals(0L, out.accumulatedBoundMillis)
    }

    @Test
    fun `bootSettle respects custom clamp value`() {
        val baseWall = 1_700_000_000_000L
        val row = freshRow(wallNow = baseWall, elapsedNanosNow = 999L)
        // Custom clamp must be < MATURATION_MS so the bound counter cap
        // doesn't also kick in and mask the custom clamp.
        val customClamp = M / 4
        val now = NowSnapshot(
            wallMillis = baseWall + customClamp * 10L, // far exceeds clamp
            elapsedNanos = 0L
        )
        val out = BindingSettleEngine.bootSettle(row, now, maxBootDelta = customClamp)
        assertEquals(customClamp, out.accumulatedBoundMillis)
    }

    // ──────────────────────────────────────────────────────────────────────
    // deriveState() — classification + virtual advance
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `deriveState fresh row reports full maturation remaining`() {
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(baseWall, baseElapsed)
        val now = NowSnapshot(baseWall, baseElapsed) // no time advance
        val state = BindingSettleEngine.deriveState(row, now)
        assertTrue(state is BindingState.PendingMaturation)
        val pm = state as BindingState.PendingMaturation
        assertEquals(BindingSettleEngine.MATURATION_MS, pm.remainingMs)
    }

    @Test
    fun `deriveState advances virtually without mutating row`() {
        // The 1s UI ticker calls deriveState; we MUST NOT write to the row
        // per tick. Confirm the row is logically untouched (deep eq of the
        // input field set) and the state reflects accumulated + virtualAdvance.
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val saved = M / 2
        val virtual = M / 4
        val row = freshRow(
            wallNow = baseWall,
            elapsedNanosNow = baseElapsed,
            accumulatedBoundMillis = saved
        )
        val now = NowSnapshot(
            wallMillis = baseWall + virtual,
            elapsedNanos = baseElapsed + virtual * NANOS_PER_MS
        )
        val state = BindingSettleEngine.deriveState(row, now)
        // saved + virtual = 3M/4 → remaining = M/4.
        assertTrue(state is BindingState.PendingMaturation)
        assertEquals(M - saved - virtual, (state as BindingState.PendingMaturation).remainingMs)
        // Row's persisted counter is still `saved` — deriveState did not write.
        assertEquals(saved, row.accumulatedBoundMillis)
    }

    @Test
    fun `deriveState reports Matured at the 24h boundary`() {
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(
            wallNow = baseWall,
            elapsedNanosNow = baseElapsed,
            accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS
        )
        val now = NowSnapshot(baseWall, baseElapsed)
        assertSame(BindingState.Matured, BindingSettleEngine.deriveState(row, now))
    }

    @Test
    fun `deriveState reports PendingUnbind when cooldown active and under 48h`() {
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val saved = U / 4
        val virtual = U / 4
        val row = freshRow(
            wallNow = baseWall,
            elapsedNanosNow = baseElapsed,
            accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
            unbindRequestedAtWall = baseWall,
            accumulatedUnbindMillis = saved
        )
        val now = NowSnapshot(
            wallMillis = baseWall + virtual,
            elapsedNanos = baseElapsed + virtual * NANOS_PER_MS
        )
        val state = BindingSettleEngine.deriveState(row, now)
        assertTrue(state is BindingState.PendingUnbind)
        // saved + virtual = U/2 → remaining = U/2.
        assertEquals(U - saved - virtual, (state as BindingState.PendingUnbind).remainingMs)
    }

    @Test
    fun `deriveState reports Unbound when cooldown elapsed past 48h`() {
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(
            wallNow = baseWall,
            elapsedNanosNow = baseElapsed,
            accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
            unbindRequestedAtWall = baseWall,
            accumulatedUnbindMillis = BindingSettleEngine.UNBIND_COOLDOWN_MS
        )
        val now = NowSnapshot(baseWall, baseElapsed)
        assertSame(BindingState.Unbound, BindingSettleEngine.deriveState(row, now))
    }

    @Test
    fun `deriveState in cooldown does not credit bound counter`() {
        // While cooling down, the row is conceptually "still bound" until
        // 48h elapses — the bound counter freezes (no virtual advance) so
        // canceling the cooldown returns the row to Matured, not back to
        // PendingMaturation.
        val baseWall = 1_700_000_000_000L
        val baseElapsed = 100L * NANOS_PER_MS
        val row = freshRow(
            wallNow = baseWall,
            elapsedNanosNow = baseElapsed,
            accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
            unbindRequestedAtWall = baseWall,
            accumulatedUnbindMillis = 0L
        )
        val now = NowSnapshot(
            wallMillis = baseWall + 100L * H, // huge fictional advance
            elapsedNanos = baseElapsed + 100L * H * NANOS_PER_MS
        )
        val state = BindingSettleEngine.deriveState(row, now)
        // Should be Unbound (100h ≥ 48h cooldown), NOT something inflated
        // through the bound channel.
        assertSame(BindingState.Unbound, state)
    }

    @Test
    fun `constants match product spec`() {
        assertEquals(24L * 60L * 60L * 1000L, BindingSettleEngine.MATURATION_MS)
        assertEquals(48L * 60L * 60L * 1000L, BindingSettleEngine.UNBIND_COOLDOWN_MS)
        assertEquals(24L * 60L * 60L * 1000L, BindingSettleEngine.MAX_BOOT_WALL_DELTA_MS)
    }
}
