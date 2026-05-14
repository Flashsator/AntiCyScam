package com.anticyscam.app.ui.bind

import com.anticyscam.app.data.local.entity.BoundAppEntity
import com.anticyscam.app.data.repository.BoundAppRepository
import com.anticyscam.app.data.system.InstalledAppsProvider.InstalledAppInfo
import com.anticyscam.app.domain.binding.BindingSettleEngine
import com.anticyscam.app.domain.model.BindingState
import com.anticyscam.app.testing.FakeAntiScamClock
import com.anticyscam.app.testing.FakeBoundAppDao
import com.anticyscam.app.testing.FakeInstalledAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BindAppsViewModel]. Exercises the batch-save UX and the
 * cooldown dialog flow described in the screen's KDoc.
 *
 * Strategy: real [BoundAppRepository] backed by [FakeBoundAppDao] so the
 * VM-to-repo logic is exercised transitively (one bug surface, not two).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BindAppsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeBoundAppDao
    private lateinit var repo: BoundAppRepository
    private lateinit var clock: FakeAntiScamClock

    private val bankPkg = "com.example.bank"
    private val linePkg = "com.example.line"
    private val chromePkg = "com.android.chrome"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakeBoundAppDao()
        repo = BoundAppRepository(dao)
        clock = FakeAntiScamClock(initialWallMillis = 1_000_000L, initialElapsedNanos = 0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(installed: List<InstalledAppInfo> = defaultInstalled()) =
        BindAppsViewModel(
            installedApps = FakeInstalledAppsProvider(installed),
            repository = repo,
            clock = clock
        ).also {
            // Tests assert single-shot state; the 1s ticker would loop
            // forever under runTest's virtual time. Kill it before any
            // dispatcher advancement.
            it.cancelTickerForTest()
        }

    private fun defaultInstalled(): List<InstalledAppInfo> = listOf(
        InstalledAppInfo(bankPkg, "Bank"),
        InstalledAppInfo(linePkg, "Line"),
        InstalledAppInfo(chromePkg, "Chrome")
    )

    /** Helper: seed a Matured row (>= 24h accumulated). */
    private fun seedMatured(pkg: String, label: String = pkg) {
        dao.seed(
            BoundAppEntity(
                packageName = pkg,
                label = label,
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
                lastSettledWall = clock.snapshot().wallMillis,
                lastSettledElapsedNanos = clock.snapshot().elapsedNanos,
                unbindRequestedAtWall = null,
                unbindRequestedAtElapsedNanos = null,
                accumulatedUnbindMillis = 0L
            )
        )
    }

    /** Helper: seed a PendingMaturation row (< 24h accumulated). */
    private fun seedPending(pkg: String, label: String = pkg) {
        dao.seed(
            BoundAppEntity(
                packageName = pkg,
                label = label,
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = 60_000L, // 1 minute
                lastSettledWall = clock.snapshot().wallMillis,
                lastSettledElapsedNanos = clock.snapshot().elapsedNanos,
                unbindRequestedAtWall = null,
                unbindRequestedAtElapsedNanos = null,
                accumulatedUnbindMillis = 0L
            )
        )
    }

    /** Helper: seed a PendingUnbind row (cooldown active, < 48h). */
    private fun seedCoolingDown(pkg: String, label: String = pkg) {
        val now = clock.snapshot()
        dao.seed(
            BoundAppEntity(
                packageName = pkg,
                label = label,
                boundAt = 100L,
                boundAtElapsedNanos = 100L,
                accumulatedBoundMillis = BindingSettleEngine.MATURATION_MS,
                lastSettledWall = now.wallMillis,
                lastSettledElapsedNanos = now.elapsedNanos,
                unbindRequestedAtWall = now.wallMillis,
                unbindRequestedAtElapsedNanos = now.elapsedNanos,
                accumulatedUnbindMillis = 0L
            )
        )
    }

    @Test
    fun `init loads installed apps with bound state derived per row`() = runTest(dispatcher) {
        seedMatured(bankPkg, "Bank")
        seedPending(linePkg, "Line")

        val vm = newVm()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(3, state.apps.size)

        val bank = state.apps.first { it.packageName == bankPkg }
        assertTrue(bank.isBound)
        assertEquals(BindingState.Matured, bank.state)

        val line = state.apps.first { it.packageName == linePkg }
        assertTrue(line.isBound)
        assertTrue(line.state is BindingState.PendingMaturation)

        val chrome = state.apps.first { it.packageName == chromePkg }
        assertFalse(chrome.isBound)
        assertEquals(BindingState.Unbound, chrome.state)

        assertEquals(setOf(bankPkg, linePkg), state.selected)
        assertEquals(setOf(bankPkg, linePkg), state.initialBound)
    }

    @Test
    fun `toggle on PendingUnbind row is a no-op`() = runTest(dispatcher) {
        seedCoolingDown(bankPkg, "Bank")
        val vm = newVm()
        advanceUntilIdle()

        val before = vm.state.value.selected
        vm.toggle(bankPkg)
        val after = vm.state.value.selected

        assertEquals(before, after)
    }

    @Test
    fun `toggle on unbound row adds it to selection`() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.toggle(chromePkg)
        val state = vm.state.value
        assertTrue(chromePkg in state.selected)
        assertTrue(state.apps.first { it.packageName == chromePkg }.isBound)
    }

    @Test
    fun `toggle off pending maturation row removes it locally without DB write`() =
        runTest(dispatcher) {
            seedPending(linePkg, "Line")
            val vm = newVm()
            advanceUntilIdle()

            vm.toggle(linePkg)
            val state = vm.state.value
            assertFalse(linePkg in state.selected)
            // DB unchanged — save hasn't been pressed yet.
            assertTrue(linePkg in dao.allPackageNames())
        }

    @Test
    fun `save with no matured unchecks commits diff and calls onDone`() =
        runTest(dispatcher) {
            seedPending(linePkg, "Line") // uncheck this — pending = free unbind
            val vm = newVm()
            advanceUntilIdle()

            vm.toggle(linePkg) // uncheck pending row
            vm.toggle(chromePkg) // check new row

            var doneCalled = false
            vm.onSaveClicked { doneCalled = true }
            advanceUntilIdle()

            assertTrue(doneCalled)
            assertFalse(vm.state.value.showCooldownDialog)
            val pkgs = dao.allPackageNames().toSet()
            assertEquals(setOf(chromePkg), pkgs)
        }

    @Test
    fun `save with matured uncheck opens cooldown dialog and does not commit`() =
        runTest(dispatcher) {
            seedMatured(bankPkg, "Bank")
            val vm = newVm()
            advanceUntilIdle()

            vm.toggle(bankPkg) // uncheck a Matured row

            var doneCalled = false
            vm.onSaveClicked { doneCalled = true }
            advanceUntilIdle()

            assertFalse(doneCalled)
            val state = vm.state.value
            assertTrue(state.showCooldownDialog)
            assertEquals(listOf(bankPkg), state.pendingMaturedUnbinds)
            // DB still has bank row, unmodified.
            val row = dao.rowsSnapshot().first { it.packageName == bankPkg }
            assertNull(row.unbindRequestedAtWall)
        }

    @Test
    fun `confirm cooldown dialog requests unbind, saves diff, closes dialog`() =
        runTest(dispatcher) {
            seedMatured(bankPkg, "Bank")
            seedPending(linePkg, "Line")
            val vm = newVm()
            advanceUntilIdle()

            vm.toggle(bankPkg) // matured uncheck → cooldown
            vm.toggle(linePkg) // pending uncheck → free
            vm.toggle(chromePkg) // new bind → insert

            vm.onSaveClicked { /* not used in this path */ }
            advanceUntilIdle()
            assertTrue(vm.state.value.showCooldownDialog)

            var doneCalled = false
            vm.onConfirmCooldownDialog { doneCalled = true }
            advanceUntilIdle()

            assertTrue(doneCalled)
            val state = vm.state.value
            assertFalse(state.showCooldownDialog)
            assertTrue(state.pendingMaturedUnbinds.isEmpty())

            val pkgs = dao.allPackageNames().toSet()
            // bank kept (now cooling down), line deleted, chrome inserted.
            assertEquals(setOf(bankPkg, chromePkg), pkgs)
            val bankRow = dao.rowsSnapshot().first { it.packageName == bankPkg }
            assertNotNull(bankRow.unbindRequestedAtWall)
        }

    @Test
    fun `dismiss cooldown dialog leaves state intact`() = runTest(dispatcher) {
        seedMatured(bankPkg, "Bank")
        val vm = newVm()
        advanceUntilIdle()

        vm.toggle(bankPkg)
        vm.onSaveClicked { }
        advanceUntilIdle()
        assertTrue(vm.state.value.showCooldownDialog)

        vm.onDismissCooldownDialog()
        val state = vm.state.value
        assertFalse(state.showCooldownDialog)
        assertTrue(state.pendingMaturedUnbinds.isEmpty())
        // Row untouched.
        val row = dao.rowsSnapshot().first { it.packageName == bankPkg }
        assertNull(row.unbindRequestedAtWall)
    }

    @Test
    fun `cancel cooldown clears unbind request and reloads state`() =
        runTest(dispatcher) {
            seedCoolingDown(bankPkg, "Bank")
            val vm = newVm()
            advanceUntilIdle()

            val initial = vm.state.value.apps.first { it.packageName == bankPkg }
            assertTrue(initial.state is BindingState.PendingUnbind)

            vm.onCancelCooldown(bankPkg)
            advanceUntilIdle()

            val row = dao.rowsSnapshot().first { it.packageName == bankPkg }
            assertNull(row.unbindRequestedAtWall)
            assertEquals(0L, row.accumulatedUnbindMillis)

            val after = vm.state.value.apps.first { it.packageName == bankPkg }
            assertEquals(BindingState.Matured, after.state)
        }
}
