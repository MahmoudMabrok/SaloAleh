package tools.mo3ta.salo.data.time

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CairoWeekTest {

    @Test
    fun saturdayIsItsOwnWeekStart() {
        // 2026-09-12 is a Saturday
        val sat = LocalDate(2026, 9, 12)
        assertEquals(sat, weekStartSaturday(sat))
        assertEquals("2026-09-12", weekKey(sat))
        assertEquals(LocalDate(2026, 9, 18), weekEndFriday(sat))
        assertEquals(6, daysUntilFriday(sat))
    }

    @Test
    fun fridayClosesTheWeekThatStartedPreviousSaturday() {
        // 2026-09-18 is a Friday
        val fri = LocalDate(2026, 9, 18)
        assertEquals(LocalDate(2026, 9, 12), weekStartSaturday(fri))
        assertEquals(fri, weekEndFriday(fri))
        assertEquals(0, daysUntilFriday(fri))
        assertEquals("2026-09-12", weekKey(fri))
    }

    @Test
    fun nextSaturdayOpensANewWeek() {
        val nextSat = LocalDate(2026, 9, 19)
        assertEquals(nextSat, weekStartSaturday(nextSat))
        assertEquals("2026-09-19", weekKey(nextSat))
        assertEquals(6, daysUntilFriday(nextSat))
    }

    @Test
    fun sundaySitsInTheSaturdayWeek() {
        val sun = LocalDate(2026, 9, 13)
        assertEquals(LocalDate(2026, 9, 12), weekStartSaturday(sun))
        assertEquals(5, daysUntilFriday(sun))
    }
}
