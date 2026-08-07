package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SalawatDailyCapTest {

    @Test
    fun server_day_total_is_the_round_total_minus_the_nightly_baseline() {
        assertEquals(3_000, SalawatDailyCap.serverDayTotal(totalCount = 12_000, yesterdayTotalScore = 9_000))
    }

    @Test
    fun server_day_total_of_a_player_with_no_baseline_is_their_whole_total() {
        // No baseline yet: they joined today, or the round node just rolled over — either way
        // everything on the node was earned today.
        assertEquals(450, SalawatDailyCap.serverDayTotal(totalCount = 450, yesterdayTotalScore = 0))
    }

    @Test
    fun server_day_total_never_goes_negative() {
        // A correction can drop the total below the baseline the cron stamped last night.
        assertEquals(0, SalawatDailyCap.serverDayTotal(totalCount = 800, yesterdayTotalScore = 1_200))
    }

    @Test
    fun remaining_counts_down_from_the_cap() {
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, SalawatDailyCap.remaining(pushedToday = 0))
        assertEquals(5_000, SalawatDailyCap.remaining(pushedToday = 20_000))
        assertEquals(0, SalawatDailyCap.remaining(pushedToday = MOHAMED_LOVERS_DAILY_PUSH_CAP))
        assertEquals(0, SalawatDailyCap.remaining(pushedToday = 90_000))
    }

    @Test
    fun allowed_push_clamps_to_what_is_left() {
        assertEquals(120, SalawatDailyCap.allowedPush(requested = 120, pushedToday = 0))
        assertEquals(5_000, SalawatDailyCap.allowedPush(requested = 9_000, pushedToday = 20_000))
        assertEquals(0, SalawatDailyCap.allowedPush(requested = 9_000, pushedToday = MOHAMED_LOVERS_DAILY_PUSH_CAP))
    }

    @Test
    fun the_very_first_push_of_a_day_is_capped_too() {
        assertEquals(
            MOHAMED_LOVERS_DAILY_PUSH_CAP,
            SalawatDailyCap.allowedPush(requested = 1_000_000, pushedToday = 0),
        )
    }
}
