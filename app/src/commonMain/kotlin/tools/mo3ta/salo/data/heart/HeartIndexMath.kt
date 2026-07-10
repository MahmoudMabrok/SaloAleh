package tools.mo3ta.salo.data.heart

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal const val HEART_TAP_BONUS = 10
internal const val HEART_DECAY_INTERVAL_MS = 10_000L
internal const val HEART_DECAY_PER_INTERVAL = 1
internal const val HEART_LOW_THRESHOLD = 0
internal const val HEART_RESET_INTERVAL_DAYS = 2
internal const val HEART_RESET_HOUR = 22

// Fixed grid reference (epoch day 1 = 1970-01-02): reset boundaries fall at 22:00 Cairo
// every HEART_RESET_INTERVAL_DAYS days counted from this date.
private const val HEART_RESET_REFERENCE_EPOCH_DAY = 1L

internal data class HeartSettleResult(
    val score: Int,
    val anchorTs: Long,
    val didReset: Boolean,
)

internal fun lastHeartResetBoundary(
    nowTs: Long,
    zone: TimeZone = TimeZone.of("Africa/Cairo"),
): Long {
    val nowInstant = Instant.fromEpochMilliseconds(nowTs)
    val localNow = nowInstant.toLocalDateTime(zone)
    val daysSinceReference = localNow.date.toEpochDays() - HEART_RESET_REFERENCE_EPOCH_DAY
    val daysSinceResetDay = daysSinceReference.mod(HEART_RESET_INTERVAL_DAYS.toLong())

    fun boundary(daysBack: Long): Instant =
        localNow.date
            .plus(-daysBack, DateTimeUnit.DAY)
            .atTime(HEART_RESET_HOUR, 0)
            .toInstant(zone)

    val candidate = boundary(daysSinceResetDay)
    return if (nowInstant >= candidate) {
        candidate.toEpochMilliseconds()
    } else {
        boundary(daysSinceResetDay + HEART_RESET_INTERVAL_DAYS).toEpochMilliseconds()
    }
}

internal fun settleHeart(
    storedScore: Int,
    anchorTs: Long,
    nowTs: Long,
    resetBoundaryTs: Long,
): HeartSettleResult {
    if (anchorTs <= 0L) return HeartSettleResult(storedScore, anchorTs, false)
    if (anchorTs < resetBoundaryTs) return HeartSettleResult(0, nowTs, true)
    if (nowTs <= anchorTs) return HeartSettleResult(storedScore, anchorTs, false)

    val intervals = (nowTs - anchorTs) / HEART_DECAY_INTERVAL_MS
    if (intervals == 0L) return HeartSettleResult(storedScore, anchorTs, false)

    return HeartSettleResult(
        score = (storedScore.toLong() - intervals * HEART_DECAY_PER_INTERVAL).toInt(),
        anchorTs = anchorTs + intervals * HEART_DECAY_INTERVAL_MS,
        didReset = false,
    )
}
