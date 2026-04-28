package tools.mo3ta.salo.data.time

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompetitionWindowUtilsTest {

    @Test
    fun roundKey_is_next_friday_date_string() {
        // Monday 2026-04-27 10:00 Cairo (UTC+2)
        val instant = Instant.parse("2026-04-27T08:00:00Z")
        val window = buildCompetitionWindow(instant)
        assertEquals("2026-05-01", window.roundKey)
    }

    @Test
    fun friday_before_18h_is_bonus_and_same_day_round() {
        // Friday 2026-05-01 10:00 Cairo (UTC 08:00)
        val instant = Instant.parse("2026-05-01T08:00:00Z")
        val window = buildCompetitionWindow(instant)
        assertTrue(window.isFridayBonus)
        assertEquals("2026-05-01", window.roundKey)
    }

    @Test
    fun friday_after_18h_is_not_bonus_and_next_week_round() {
        // Friday 2026-05-01 19:00 Cairo (UTC 17:00)
        val instant = Instant.parse("2026-05-01T17:00:00Z")
        val window = buildCompetitionWindow(instant)
        assertFalse(window.isFridayBonus)
        assertEquals("2026-05-08", window.roundKey)
    }
}
