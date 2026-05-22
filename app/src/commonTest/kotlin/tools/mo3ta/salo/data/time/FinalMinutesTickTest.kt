package tools.mo3ta.salo.data.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinalMinutesTickTest {

    @Test
    fun remaining_zero_flushes_and_shows_new_round() {
        val tick = computeFinalMinutesTick(0)
        assertTrue(tick.shouldFlush)
        assertTrue(tick.showNewRound)
        assertEquals(0L, tick.nextDelayMillis)
    }

    @Test
    fun negative_remaining_flushes_and_shows_new_round() {
        val tick = computeFinalMinutesTick(-5)
        assertTrue(tick.shouldFlush)
        assertTrue(tick.showNewRound)
        assertEquals(0L, tick.nextDelayMillis)
    }

    @Test
    fun exactly_600s_flushes_without_new_round() {
        val tick = computeFinalMinutesTick(600)
        assertTrue(tick.shouldFlush)
        assertFalse(tick.showNewRound)
        assertEquals(60_000L, tick.nextDelayMillis)
    }

    @Test
    fun deep_inside_final_minutes_flushes_every_60s() {
        val tick = computeFinalMinutesTick(300)
        assertTrue(tick.shouldFlush)
        assertFalse(tick.showNewRound)
        assertEquals(60_000L, tick.nextDelayMillis)
    }

    @Test
    fun just_over_boundary_schedules_remaining_gap() {
        val tick = computeFinalMinutesTick(601)
        assertFalse(tick.shouldFlush)
        assertFalse(tick.showNewRound)
        assertEquals(1_000L, tick.nextDelayMillis)
    }

    @Test
    fun slightly_over_boundary_schedules_exact_gap() {
        val tick = computeFinalMinutesTick(605)
        assertFalse(tick.shouldFlush)
        assertFalse(tick.showNewRound)
        assertEquals(5_000L, tick.nextDelayMillis)
    }

    @Test
    fun far_away_caps_delay_at_60s() {
        val tick = computeFinalMinutesTick(3600)
        assertFalse(tick.shouldFlush)
        assertFalse(tick.showNewRound)
        assertEquals(60_000L, tick.nextDelayMillis)
    }
}
