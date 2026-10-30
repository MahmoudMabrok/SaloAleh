package tools.mo3ta.salo.data.time

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CompetitionWindowUtilsTest {

    @Test
    fun roundKey_is_next_friday_date_string() {
        // Monday 2026-04-27 10:00 Cairo (UTC+2)
        val instant = Instant.parse("2026-04-27T08:00:00Z")
        val window = buildCompetitionWindow(instant)
        assertEquals("2026-05-01", window.roundKey)
    }

    @Test
    fun friday_before_17h_same_day_round() {
        // Friday 2026-05-01 11:00 Cairo (UTC 08:00)
        val instant = Instant.parse("2026-05-01T08:00:00Z")
        val window = buildCompetitionWindow(instant)
        assertEquals("2026-05-01", window.roundKey)
    }

    @Test
    fun friday_after_17h_next_week_round() {
        // Friday 2026-05-01 20:00 Cairo (UTC 17:00)
        val instant = Instant.parse("2026-05-01T17:00:00Z")
        val window = buildCompetitionWindow(instant)
        assertEquals("2026-05-08", window.roundKey)
    }

    @Test
    fun winter_friday_round_ends_at_17h_cairo() {
        // Friday 2026-11-06, Cairo is UTC+2: 16:59 Cairo = 14:59Z still this round.
        assertEquals("2026-11-06", buildCompetitionWindow(Instant.parse("2026-11-06T14:59:00Z")).roundKey)
        // 17:00 Cairo = 15:00Z belongs to the next round.
        val atClose = buildCompetitionWindow(Instant.parse("2026-11-06T15:00:00Z"))
        assertEquals("2026-11-13", atClose.roundKey)
        assertEquals(Instant.parse("2026-11-13T15:00:00Z"), atClose.roundEnd)
    }

    @Test
    fun summer_friday_round_ends_at_17h_cairo() {
        // Friday 2026-07-03, Cairo is UTC+3 (DST): 17:00 Cairo = 14:00Z.
        assertEquals("2026-07-03", buildCompetitionWindow(Instant.parse("2026-07-03T13:59:00Z")).roundKey)
        assertEquals("2026-07-10", buildCompetitionWindow(Instant.parse("2026-07-03T14:00:00Z")).roundKey)
        assertEquals(
            Instant.parse("2026-07-03T14:00:00Z"),
            buildCompetitionWindow(Instant.parse("2026-07-01T08:00:00Z")).roundEnd,
        )
    }
}
