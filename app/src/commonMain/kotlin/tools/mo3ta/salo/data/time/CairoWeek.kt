package tools.mo3ta.salo.data.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Personal challenge-week helpers. A week is Saturday 00:00 through end of Friday
 * (the instant before next Saturday 00:00) in [CAIRO_ZONE].
 *
 * Distinct from [buildCompetitionWindow], which is the Mohamed Lovers round that
 * closes Friday 19:00 Cairo.
 */
val CAIRO_ZONE: TimeZone = TimeZone.of("Africa/Cairo")

/** Saturday that opens the week containing [date]. */
fun weekStartSaturday(date: LocalDate): LocalDate {
    val daysSinceSaturday = (date.dayOfWeek.ordinal - DayOfWeek.SATURDAY.ordinal + 7) % 7
    return LocalDate.fromEpochDays(date.toEpochDays() - daysSinceSaturday)
}

/** Friday that closes the week containing [date]. */
fun weekEndFriday(date: LocalDate): LocalDate =
    LocalDate.fromEpochDays(weekStartSaturday(date).toEpochDays() + 6)

/** `yyyy-MM-dd` of the Saturday that opens the week containing [date]. */
fun weekKey(date: LocalDate): String = weekStartSaturday(date).toString()

/**
 * Whole days remaining after [date] until Friday (0 on Friday itself).
 * Saturday → 6, Sunday → 5, …, Friday → 0.
 */
fun daysUntilFriday(date: LocalDate): Int =
    (weekEndFriday(date).toEpochDays() - date.toEpochDays()).coerceAtLeast(0)
