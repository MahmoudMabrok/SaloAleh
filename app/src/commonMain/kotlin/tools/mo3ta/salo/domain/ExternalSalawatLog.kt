package tools.mo3ta.salo.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Audit trail for large externally-recorded salawat batches (manual "record external" entries and
 * Chrome-extension syncs). Regular in-app taps are never logged — only bulk amounts a user claims.
 *
 * A batch strictly above [MIN_LOGGED_COUNT] is appended to the player node as
 * `players/{uid}/externalLog/{entryKey} = count`, so an oversized claim leaves a timestamped record
 * next to the score it inflated. Record-only: nothing about the score changes because of it.
 */
object ExternalSalawatLog {

    /** Batches strictly greater than this are recorded; anything at or below it is ignored. */
    const val MIN_LOGGED_COUNT = 2_000

    private val CAIRO = TimeZone.of("Africa/Cairo")

    fun shouldLog(count: Int): Boolean = count > MIN_LOGGED_COUNT

    /**
     * The log key for [instant]: `yyyy-MM-dd HH;mm` in `Africa/Cairo` (minute precision).
     * `;` separates the time parts because `:` reads as a path separator in some tooling; the
     * remaining characters are all legal in an RTDB key (no `.`, `$`, `#`, `[`, `]` or `/`).
     */
    fun entryKey(instant: Instant): String {
        val local = instant.toLocalDateTime(CAIRO)
        val month = local.monthNumber.pad()
        val day = local.dayOfMonth.pad()
        val hour = local.hour.pad()
        val minute = local.minute.pad()
        return "${local.year}-$month-$day $hour;$minute"
    }

    private fun Int.pad(): String = toString().padStart(2, '0')
}
