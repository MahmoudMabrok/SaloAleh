package tools.mo3ta.salo.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Audit trail for externally-recorded salawat batches (manual "record external" entries and
 * Chrome-extension syncs). Regular in-app taps are never logged — only bulk amounts a user claims.
 *
 * **Every** external push is appended to the player node as
 * `players/{uid}/externalLog/{entryKey} = count`, so each batch leaves a timestamped record next to
 * the score it produced. A correction (subtract) is written as a negative entry, so the log stays
 * the net truth of what was claimed. Record-only: nothing about the score changes because of it.
 *
 * The cap-consuming half of the same write lands on `players/{uid}/externalDaily/{dayKey}` — the
 * server-side mirror of the local per-Cairo-day manual allowance ledger, so a reinstall cannot hand
 * the user a fresh daily cap.
 */
object ExternalSalawatLog {

    private val CAIRO = TimeZone.of("Africa/Cairo")

    /** Every non-zero push is recorded; a correction arrives here as a negative [count]. */
    fun shouldLog(count: Int): Boolean = count != 0

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

    /**
     * The daily-ledger key for [instant]: `yyyy-MM-dd` in `Africa/Cairo` — the same Cairo day the
     * local manual-entry allowance is scoped to, so the two ledgers line up key for key.
     */
    fun dayKey(instant: Instant): String {
        val local = instant.toLocalDateTime(CAIRO)
        return "${local.year}-${local.monthNumber.pad()}-${local.dayOfMonth.pad()}"
    }

    private fun Int.pad(): String = toString().padStart(2, '0')
}
