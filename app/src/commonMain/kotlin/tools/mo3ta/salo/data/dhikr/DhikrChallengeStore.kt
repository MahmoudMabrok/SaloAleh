package tools.mo3ta.salo.data.dhikr

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP

class DhikrChallengeStore(private val settings: Settings) {

    /** Total to display = remote baseline + unsynced pending taps. */
    fun todayCount(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_REMOTE, 0) + settings.getInt(KEY_PENDING, 0)
    }

    /** Pending taps not yet flushed to Firebase. */
    fun todayPending(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_PENDING, 0)
    }

    /**
     * Returns (dateKey, total) for a previous day that still has unsynced pending > 0.
     * Only fires when there are actually un-flushed local taps — not just a stale remote baseline.
     */
    fun previousEntry(today: LocalDate): Pair<String, Int>? {
        val storedDate = settings.getStringOrNull(KEY_DATE) ?: return null
        if (storedDate == today.toString()) return null
        val pending = settings.getInt(KEY_PENDING, 0)
        if (pending == 0) return null
        return storedDate to (settings.getInt(KEY_REMOTE, 0) + pending)
    }

    /** Increment pending and return new total (remote + pending). Never touches network. */
    fun incrementToday(today: LocalDate): Int {
        ensureToday(today)
        val newPending = settings.getInt(KEY_PENDING, 0) + 1
        settings.putInt(KEY_PENDING, newPending)
        return settings.getInt(KEY_REMOTE, 0) + newPending
    }

    /**
     * Add [count] pending taps at once (manual external entry) and return the new total
     * (remote + pending). Never touches network. Non-positive counts are ignored, and the
     * amount actually applied is clamped so cumulative manual entry never exceeds
     * [CHALLENGE_MANUAL_DAILY_CAP] for the day. Regular taps ([incrementToday]) are not counted
     * against this cap.
     */
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

    /** How much more may still be added via manual entry today (cap minus what's used). */
    fun manualRemainingToday(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return CHALLENGE_MANUAL_DAILY_CAP
        return (CHALLENGE_MANUAL_DAILY_CAP - settings.getInt(KEY_MANUAL, 0)).coerceAtLeast(0)
    }

    /**
     * Update the remote baseline after loading it from Firebase.
     * Only advances the baseline — never reduces it.
     * Pending is untouched so any local taps made during the fetch are preserved.
     */
    fun updateRemoteBaseline(today: LocalDate, remoteCount: Int) {
        ensureToday(today)
        if (remoteCount > settings.getInt(KEY_REMOTE, 0)) {
            settings.putInt(KEY_REMOTE, remoteCount)
        }
    }

    /** Called after a successful Firebase write: advance baseline to total and clear pending. */
    fun onSyncSuccess(today: LocalDate, total: Int) {
        ensureToday(today)
        settings.putInt(KEY_REMOTE, total)
        settings.putInt(KEY_PENDING, 0)
    }

    /** Clear previous-day pending after a successful back-sync. */
    fun clearPreviousPending() {
        settings.putInt(KEY_PENDING, 0)
    }

    /** Hard reset (debug/admin only). */
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
        const val KEY_DATE = "dhikr_challenge_date"
        // Reuses the old "dhikr_challenge_count" key so existing users keep their synced value
        // as the remote baseline — no migration needed.
        const val KEY_REMOTE = "dhikr_challenge_count"
        const val KEY_PENDING = "dhikr_challenge_pending"
        // Cumulative manual ("external") entry today — the daily-cap ledger.
        const val KEY_MANUAL = "dhikr_challenge_manual"
    }
}
