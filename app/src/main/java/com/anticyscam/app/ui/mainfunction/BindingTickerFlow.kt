package com.anticyscam.app.ui.mainfunction

import com.anticyscam.app.data.prefs.AntiScamClock
import com.anticyscam.app.data.prefs.NowSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Cold flow that emits a fresh [NowSnapshot] every [intervalMs] ms while
 * collected. Foreground-scoped via WhileSubscribed in the ViewModel — this
 * flow itself does no lifecycle work.
 *
 * Why a custom flow rather than tickerFlow / channelFlow:
 *   - We need the consumer to be able to derive countdowns from a fresh
 *     snapshot every tick; emitting a pure tick signal would force every
 *     consumer to re-read the clock.
 *   - Emits immediately on subscribe so the first frame has data without
 *     waiting [intervalMs].
 */
fun bindingTickerFlow(
    clock: AntiScamClock,
    intervalMs: Long
): Flow<NowSnapshot> = flow {
    while (true) {
        emit(clock.snapshot())
        delay(intervalMs)
    }
}
