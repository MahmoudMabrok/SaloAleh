package tools.mo3ta.salo.desktop.qr

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val ROUND_BOUNDARY_HOUR = 18
private val cairoZone = TimeZone.of("Africa/Cairo")

fun currentRoundKey(now: Instant): String {
    val local = now.toLocalDateTime(cairoZone)
    val daysUntilFriday = ((DayOfWeek.FRIDAY.ordinal - local.dayOfWeek.ordinal + 7) % 7).toLong()
    val candidate = local.date
        .plus(daysUntilFriday, DateTimeUnit.DAY)
        .atTime(ROUND_BOUNDARY_HOUR, 0)
        .toInstant(cairoZone)
    val boundary = if (now < candidate) candidate else candidate.plus(7, DateTimeUnit.DAY, cairoZone)
    return boundary.toLocalDateTime(cairoZone).date.toString()
}
