package tools.mo3ta.salo.data.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

internal const val ROUND_BOUNDARY_HOUR = 18
private val cairoZone = TimeZone.of("Africa/Cairo")

internal fun buildCompetitionWindow(networkNow: Instant): MohamedLoversCompetitionWindow {
    val localNow = networkNow.toLocalDateTime(cairoZone)
    val isFridayBonus = localNow.dayOfWeek == DayOfWeek.FRIDAY && localNow.hour < ROUND_BOUNDARY_HOUR
    val roundEnd = nextRoundBoundary(localNow)
    return MohamedLoversCompetitionWindow(
        networkNow = networkNow,
        isFridayBonus = isFridayBonus,
        roundKey = roundEnd.toLocalDateTime(cairoZone).date.toString(),
        roundEnd = roundEnd,
        message = null,
    )
}

private fun nextRoundBoundary(now: LocalDateTime): Instant {
    // ordinal: MONDAY=0 … FRIDAY=4 … SUNDAY=6
    val daysUntilFriday = ((DayOfWeek.FRIDAY.ordinal - now.dayOfWeek.ordinal + 7) % 7).toLong()
    val candidate = now.date
        .plus(daysUntilFriday, DateTimeUnit.DAY)
        .atTime(ROUND_BOUNDARY_HOUR, 0)
        .toInstant(cairoZone)
    val nowInstant = now.toInstant(cairoZone)
    return if (nowInstant < candidate) candidate else candidate.plus(7, DateTimeUnit.DAY, cairoZone)
}
