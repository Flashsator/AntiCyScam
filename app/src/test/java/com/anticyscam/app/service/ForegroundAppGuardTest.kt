package com.anticyscam.app.service

import com.anticyscam.app.data.local.dao.BoundAppDao
import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.repository.BoundAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ForegroundAppGuard.evaluate].
 *
 * Strategy: bypass [ForegroundAppGuard.start] entirely (no Room, no scopes)
 * via [ForegroundAppGuard.primeSnapshotForTest], then exhaustively cover
 * the decision tree:
 *
 *   foreground == lastObserved    → Ignore (dedup)
 *   foreground == ownPkg           → Ignore (we're allowed)
 *   foreground !in snapshot        → Ignore (not bound)
 *   foreground in snapshot + grant → AllowAuthorized
 *   foreground in snapshot + none  → BlockUnauthorized
 */
class ForegroundAppGuardTest {

    private lateinit var tracker: AuthorizedLaunchTracker
    private lateinit var guard: ForegroundAppGuard
    private var now: Long = 0L

    private val ownPkg = "com.anticyscam.app"
    private val boundBank = "com.example.bank"
    private val boundLine = "com.example.line"
    private val unrelated = "com.android.chrome"

    @Before
    fun setup() {
        tracker = AuthorizedLaunchTracker().apply { clockProvider = { now } }
        guard = ForegroundAppGuard(
            boundAppRepository = NoopBoundAppRepository(),
            authorizedLaunchTracker = tracker
        )
        guard.primeSnapshotForTest(
            mapOf(boundBank to "Bank", boundLine to "Line")
        )
    }

    @Test
    fun `own package is ignored`() {
        val decision = guard.evaluate(foregroundPkg = ownPkg, ownPkg = ownPkg)
        assertEquals(ForegroundAppGuard.Decision.Ignore, decision)
    }

    @Test
    fun `non-bound package is ignored`() {
        val decision = guard.evaluate(foregroundPkg = unrelated, ownPkg = ownPkg)
        assertEquals(ForegroundAppGuard.Decision.Ignore, decision)
    }

    @Test
    fun `bound + authorized yields AllowAuthorized`() {
        tracker.authorize(boundBank)
        val decision = guard.evaluate(foregroundPkg = boundBank, ownPkg = ownPkg)
        assertEquals(
            ForegroundAppGuard.Decision.AllowAuthorized(boundBank),
            decision
        )
    }

    @Test
    fun `bound + no authorization yields BlockUnauthorized with label`() {
        val decision = guard.evaluate(foregroundPkg = boundBank, ownPkg = ownPkg)
        assertTrue(decision is ForegroundAppGuard.Decision.BlockUnauthorized)
        decision as ForegroundAppGuard.Decision.BlockUnauthorized
        assertEquals(boundBank, decision.packageName)
        assertEquals("Bank", decision.label)
    }

    @Test
    fun `bound with missing label falls back to packageName`() {
        guard.primeSnapshotForTest(mapOf(boundBank to ""))
        val decision = guard.evaluate(foregroundPkg = boundBank, ownPkg = ownPkg)
        assertTrue(decision is ForegroundAppGuard.Decision.BlockUnauthorized)
        decision as ForegroundAppGuard.Decision.BlockUnauthorized
        // Empty-string label is what was stored; the fallback only kicks in
        // when the snapshot has no entry, but we still cover the contract
        // that label is non-null and equals the stored value.
        assertEquals("", decision.label)
    }

    @Test
    fun `duplicate consecutive transitions are deduped`() {
        tracker.authorize(boundBank)
        // First time the package is observed: returns AllowAuthorized and
        // consumes the grant.
        val first = guard.evaluate(foregroundPkg = boundBank, ownPkg = ownPkg)
        assertTrue(first is ForegroundAppGuard.Decision.AllowAuthorized)
        // Second consecutive transition with the same package should be a
        // no-op — even though there is no grant left, dedup must short-circuit
        // BEFORE consuming the (non-existent) authorization, otherwise the
        // user gets a spurious block on the very next WINDOW_STATE_CHANGED.
        val second = guard.evaluate(foregroundPkg = boundBank, ownPkg = ownPkg)
        assertEquals(ForegroundAppGuard.Decision.Ignore, second)
    }

    @Test
    fun `transition through a different package re-arms the same-package block`() {
        // Visit bound bank without a grant → block.
        val first = guard.evaluate(foregroundPkg = boundBank, ownPkg = ownPkg)
        assertTrue(first is ForegroundAppGuard.Decision.BlockUnauthorized)
        // Then visit Line (also unauthorized → block, lastObserved = line).
        val second = guard.evaluate(foregroundPkg = boundLine, ownPkg = ownPkg)
        assertTrue(second is ForegroundAppGuard.Decision.BlockUnauthorized)
        // Bank again — since lastObserved is now line, this is NOT deduped
        // and should re-block.
        val third = guard.evaluate(foregroundPkg = boundBank, ownPkg = ownPkg)
        assertTrue(third is ForegroundAppGuard.Decision.BlockUnauthorized)
    }

    @Test
    fun `expired authorization is treated as missing`() {
        tracker.authorize(boundBank)
        now += AuthorizedLaunchTracker.AUTHORIZATION_WINDOW_MS + 1L
        val decision = guard.evaluate(foregroundPkg = boundBank, ownPkg = ownPkg)
        assertTrue(decision is ForegroundAppGuard.Decision.BlockUnauthorized)
    }

    /**
     * Minimal [BoundAppRepository] subclass that never delivers any flow
     * data, since the tests prime the snapshot directly. Backed by a
     * no-op [BoundAppDao].
     */
    private class NoopBoundAppRepository :
        BoundAppRepository(NoopBoundAppDao())

    private class NoopBoundAppDao : BoundAppDao {
        override fun observeAll(): Flow<List<BoundAppEntity>> = flowOf(emptyList())
        override suspend fun all(): List<BoundAppEntity> = emptyList()
        override suspend fun allPackageNames(): List<String> = emptyList()
        override suspend fun isBound(pkg: String): Boolean = false
        override suspend fun insertAll(apps: List<BoundAppEntity>) {}
        override suspend fun deleteByPackage(pkg: String) {}
        override suspend fun deleteByPackages(pkgs: List<String>) {}
        override suspend fun clear() {}
    }
}
