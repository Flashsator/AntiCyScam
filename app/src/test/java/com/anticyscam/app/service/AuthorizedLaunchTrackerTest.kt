package com.anticyscam.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AuthorizedLaunchTracker].
 *
 * The tracker's only non-trivial state is its expiry semantics. We
 * override [AuthorizedLaunchTracker.clockProvider] so the test thread
 * controls "now" directly — no Thread.sleep, no flakiness.
 */
class AuthorizedLaunchTrackerTest {

    private lateinit var tracker: AuthorizedLaunchTracker
    private var now: Long = 0L

    @Before
    fun setup() {
        tracker = AuthorizedLaunchTracker()
        tracker.clockProvider = { now }
    }

    @Test
    fun `authorize then consume returns true once`() {
        tracker.authorize("com.example.bank")
        assertTrue(tracker.consumeAuthorization("com.example.bank"))
    }

    @Test
    fun `second consume after first returns false`() {
        tracker.authorize("com.example.bank")
        tracker.consumeAuthorization("com.example.bank") // first
        assertFalse(tracker.consumeAuthorization("com.example.bank"))
    }

    @Test
    fun `consume without prior authorize returns false`() {
        assertFalse(tracker.consumeAuthorization("com.example.bank"))
    }

    @Test
    fun `expired grant is not consumable`() {
        tracker.authorize("com.example.bank")
        now += AuthorizedLaunchTracker.AUTHORIZATION_WINDOW_MS + 1L
        assertFalse(tracker.consumeAuthorization("com.example.bank"))
    }

    @Test
    fun `grant remains valid at exact boundary`() {
        tracker.authorize("com.example.bank")
        now += AuthorizedLaunchTracker.AUTHORIZATION_WINDOW_MS
        assertTrue(tracker.consumeAuthorization("com.example.bank"))
    }

    @Test
    fun `isAuthorized is non-destructive`() {
        tracker.authorize("com.example.bank")
        assertTrue(tracker.isAuthorized("com.example.bank"))
        assertTrue(tracker.isAuthorized("com.example.bank"))
        // The grant is still consumable after peeking.
        assertTrue(tracker.consumeAuthorization("com.example.bank"))
    }

    @Test
    fun `authorize is per-package`() {
        tracker.authorize("com.example.bank")
        assertFalse(tracker.consumeAuthorization("com.example.line"))
        // The bank grant is untouched.
        assertTrue(tracker.consumeAuthorization("com.example.bank"))
    }

    @Test
    fun `clearAll removes every grant`() {
        tracker.authorize("com.example.bank")
        tracker.authorize("com.example.line")
        tracker.clearAll()
        assertFalse(tracker.consumeAuthorization("com.example.bank"))
        assertFalse(tracker.consumeAuthorization("com.example.line"))
    }

    @Test
    fun `re-authorizing extends the window`() {
        tracker.authorize("com.example.bank")
        now += AuthorizedLaunchTracker.AUTHORIZATION_WINDOW_MS / 2
        tracker.authorize("com.example.bank") // refresh
        now += AuthorizedLaunchTracker.AUTHORIZATION_WINDOW_MS - 1
        assertTrue(tracker.consumeAuthorization("com.example.bank"))
    }
}
