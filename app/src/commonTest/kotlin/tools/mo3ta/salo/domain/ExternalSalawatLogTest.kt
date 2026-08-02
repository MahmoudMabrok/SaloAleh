package tools.mo3ta.salo.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalSalawatLogTest {

    private val cairo = TimeZone.of("Africa/Cairo")

    private fun cairoInstant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime(year, month, day, hour, minute).toInstant(cairo)

    @Test
    fun logs_only_batches_above_the_threshold() {
        assertFalse(ExternalSalawatLog.shouldLog(0))
        assertFalse(ExternalSalawatLog.shouldLog(1_999))
        assertFalse(ExternalSalawatLog.shouldLog(ExternalSalawatLog.MIN_LOGGED_COUNT))
        assertTrue(ExternalSalawatLog.shouldLog(ExternalSalawatLog.MIN_LOGGED_COUNT + 1))
        assertTrue(ExternalSalawatLog.shouldLog(10_000))
    }

    @Test
    fun entry_key_is_the_cairo_wall_clock_padded_to_minutes() {
        assertEquals("2026-08-02 12;05", ExternalSalawatLog.entryKey(cairoInstant(2026, 8, 2, 12, 5)))
        assertEquals("2026-01-09 02;00", ExternalSalawatLog.entryKey(cairoInstant(2026, 1, 9, 2, 0)))
        assertEquals("2026-11-30 23;59", ExternalSalawatLog.entryKey(cairoInstant(2026, 11, 30, 23, 59)))
    }

    @Test
    fun entry_key_uses_cairo_not_utc() {
        // 22:30 UTC in January is already 00:30 the next day in Cairo (UTC+2, no DST in winter).
        val key = ExternalSalawatLog.entryKey(Instant.parse("2026-01-09T22:30:00Z"))

        assertEquals("2026-01-10 00;30", key)
    }

    @Test
    fun entry_key_has_no_rtdb_illegal_characters() {
        val key = ExternalSalawatLog.entryKey(cairoInstant(2026, 8, 2, 12, 5))

        assertTrue(key.none { it in ".#$[]/" })
    }
}
