package tools.mo3ta.salo.data.heart

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeartIndexMathTest {

    @Test
    fun anchor_zero_is_unchanged() {
        val result = settleHeart(12, 0L, 50_000L, 0L)
        assertEquals(12, result.score)
        assertEquals(0L, result.anchorTs)
        assertFalse(result.didReset)
    }

    @Test
    fun now_at_or_before_anchor_is_unchanged() {
        val result = settleHeart(12, 10_000L, 10_000L, 0L)
        assertEquals(12, result.score)
        assertEquals(10_000L, result.anchorTs)
    }

    @Test
    fun less_than_one_interval_is_unchanged() {
        val result = settleHeart(12, 10_000L, 19_999L, 0L)
        assertEquals(12, result.score)
        assertEquals(10_000L, result.anchorTs)
    }

    @Test
    fun one_interval_decays_once_and_advances_anchor() {
        val result = settleHeart(12, 10_000L, 20_000L, 0L)
        assertEquals(11, result.score)
        assertEquals(20_000L, result.anchorTs)
    }

    @Test
    fun partial_interval_remainder_is_carried() {
        val result = settleHeart(12, 10_000L, 35_000L, 0L)
        assertEquals(10, result.score)
        assertEquals(30_000L, result.anchorTs)
    }

    @Test
    fun composition_matches_single_settle() {
        val anchor = 10_000L
        val partial = settleHeart(12, anchor, anchor + 25_000L, 0L)
        val composed = settleHeart(partial.score, partial.anchorTs, anchor + 35_000L, 0L)
        val single = settleHeart(12, anchor, anchor + 35_000L, 0L)

        assertEquals(single, composed)
    }

    @Test
    fun decay_is_unbounded_below_zero() {
        val result = settleHeart(1, 10_000L, 40_000L, 0L)
        assertEquals(-2, result.score)
        assertEquals(40_000L, result.anchorTs)
    }

    @Test
    fun long_gap_decays_without_overflow() {
        val anchor = 10_000L
        val result = settleHeart(100, anchor, anchor + 30L * 24 * 60 * 60 * 1000, 0L)
        assertEquals(100 - 259_200, result.score)
    }

    @Test
    fun weekly_reset_sets_score_zero_and_anchor_to_now() {
        val result = settleHeart(50, 10_000L, 80_000L, 20_000L)
        assertEquals(0, result.score)
        assertEquals(80_000L, result.anchorTs)
        assertTrue(result.didReset)
    }

    @Test
    fun anchor_exactly_at_reset_boundary_decays_normally() {
        val result = settleHeart(50, 20_000L, 40_000L, 20_000L)
        assertEquals(48, result.score)
        assertEquals(40_000L, result.anchorTs)
        assertFalse(result.didReset)
    }

    @Test
    fun last_boundary_for_thursday_is_previous_friday_22_cairo() {
        assertEquals(
            cairoTs(2026, 6, 26, 22, 0),
            lastHeartResetBoundary(cairoTs(2026, 7, 2, 12, 0)),
        )
    }

    @Test
    fun last_boundary_for_friday_before_22_is_previous_week() {
        assertEquals(
            cairoTs(2026, 6, 26, 22, 0),
            lastHeartResetBoundary(cairoTs(2026, 7, 3, 21, 59)),
        )
    }

    @Test
    fun last_boundary_for_friday_at_22_is_same_day() {
        assertEquals(
            cairoTs(2026, 7, 3, 22, 0),
            lastHeartResetBoundary(cairoTs(2026, 7, 3, 22, 0)),
        )
    }

    @Test
    fun last_boundary_for_friday_after_22_is_same_day() {
        assertEquals(
            cairoTs(2026, 7, 3, 22, 0),
            lastHeartResetBoundary(cairoTs(2026, 7, 3, 22, 1)),
        )
    }

    private fun cairoTs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute)
            .toInstant(TimeZone.of("Africa/Cairo"))
            .toEpochMilliseconds()
}
