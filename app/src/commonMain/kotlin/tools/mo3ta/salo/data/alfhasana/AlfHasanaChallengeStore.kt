package tools.mo3ta.salo.data.alfhasana

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP

/**
 * Local ledger for the "ألف حسنة" tasbih challenge — mirrors [tools.mo3ta.salo.data.dhikr.DhikrChallengeStore].
 * Total to display = remote baseline + unsynced pending taps. Never touches the network.
 */
class AlfHasanaChallengeStore(private val settings: Settings) {

    fun todayCount(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_REMOTE, 0) + settings.getInt(KEY_PENDING, 0)
    }

    fun todayPending(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_PENDING, 0)
    }

    /** (dateKey, total) for a previous day that still has unsynced pending > 0. */
    fun previousEntry(today: LocalDate): Pair<String, Int>? {
        val storedDate = settings.getStringOrNull(KEY_DATE) ?: return null
        if (storedDate == today.toString()) return null
        val pending = settings.getInt(KEY_PENDING, 0)
        if (pending == 0) return null
        return storedDate to (settings.getInt(KEY_REMOTE, 0) + pending)
    }

    fun incrementToday(today: LocalDate): Int {
        ensureToday(today)
        val newPending = settings.getInt(KEY_PENDING, 0) + 1
        settings.putInt(KEY_PENDING, newPending)
        return settings.getInt(KEY_REMOTE, 0) + newPending
    }

    /** Add [count] pending taps at once (manual entry), clamped to the daily manual cap. */
    fun addToday(today: LocalDate, count: Int): Int {
        ensureToday(today)
        if (count > 0) {
            val usedManual = settings.getInt(KEY_MANUAL, 0)
            val applied = count.coerceAtMost((CHALLENGE_MANUAL_DAILY_CAP - usedManual).coerceAtLeast(0))
            if (applied > 0) {
                settings.putInt(KEY_PENDING, settings.getInt(KEY_PENDING, 0) + applied)
                settings.putInt(KEY_MANUAL, usedManual + applied)
            }
        }
        return settings.getInt(KEY_REMOTE, 0) + settings.getInt(KEY_PENDING, 0)
    }

    /** Subtract [count] to correct a mistaken entry. Floored at 0; refunds the manual cap ledger. */
    fun subtractToday(today: LocalDate, count: Int): Int {
        ensureToday(today)
        if (count > 0) {
            val remote = settings.getInt(KEY_REMOTE, 0)
            val current = remote + settings.getInt(KEY_PENDING, 0)
            val newTotal = (current - count).coerceAtLeast(0)
            settings.putInt(KEY_PENDING, newTotal - remote)
            val removed = current - newTotal
            val usedManual = settings.getInt(KEY_MANUAL, 0)
            settings.putInt(KEY_MANUAL, (usedManual - removed).coerceAtLeast(0))
        }
        return settings.getInt(KEY_REMOTE, 0) + settings.getInt(KEY_PENDING, 0)
    }

    fun manualRemainingToday(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return CHALLENGE_MANUAL_DAILY_CAP
        return (CHALLENGE_MANUAL_DAILY_CAP - settings.getInt(KEY_MANUAL, 0)).coerceAtLeast(0)
    }

    /** Advance the remote baseline after a Firebase read. Only advances — never reduces. */
    fun updateRemoteBaseline(today: LocalDate, remoteCount: Int) {
        ensureToday(today)
        if (remoteCount > settings.getInt(KEY_REMOTE, 0)) {
            settings.putInt(KEY_REMOTE, remoteCount)
        }
    }

    /** After a successful Firebase write: advance baseline to total and clear pending. */
    fun onSyncSuccess(today: LocalDate, total: Int) {
        ensureToday(today)
        settings.putInt(KEY_REMOTE, total)
        settings.putInt(KEY_PENDING, 0)
    }

    fun clearPreviousPending() {
        settings.putInt(KEY_PENDING, 0)
    }

    fun resetToday(today: LocalDate): Int {
        ensureToday(today)
        settings.putInt(KEY_REMOTE, 0)
        settings.putInt(KEY_PENDING, 0)
        settings.putInt(KEY_MANUAL, 0)
        return 0
    }

    private fun ensureToday(today: LocalDate) {
        val date = today.toString()
        if (settings.getStringOrNull(KEY_DATE) == date) return
        settings.putString(KEY_DATE, date)
        settings.putInt(KEY_REMOTE, 0)
        settings.putInt(KEY_PENDING, 0)
        settings.putInt(KEY_MANUAL, 0)
    }

    private companion object {
        const val KEY_DATE = "alf_hasana_challenge_date"
        const val KEY_REMOTE = "alf_hasana_challenge_count"
        const val KEY_PENDING = "alf_hasana_challenge_pending"
        const val KEY_MANUAL = "alf_hasana_challenge_manual"
    }
}
