package tools.mo3ta.salo.data.quran

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP

class QuranChallengeStore(private val settings: Settings) {

    fun todayCount(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_REMOTE, 0) + settings.getInt(KEY_PENDING, 0)
    }

    fun todayPending(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_PENDING, 0)
    }

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
        addLifetime(1)
        return settings.getInt(KEY_REMOTE, 0) + newPending
    }

    /**
     * Manual ("external") entry. The applied amount is clamped so cumulative manual entry
     * never exceeds [CHALLENGE_MANUAL_DAILY_CAP] for the day. Taps ([incrementToday]) are uncapped.
     */
    fun addToday(today: LocalDate, count: Int): Int {
        ensureToday(today)
        if (count > 0) {
            val usedManual = settings.getInt(KEY_MANUAL, 0)
            val applied = count.coerceAtMost((CHALLENGE_MANUAL_DAILY_CAP - usedManual).coerceAtLeast(0))
            if (applied > 0) {
                settings.putInt(KEY_PENDING, settings.getInt(KEY_PENDING, 0) + applied)
                settings.putInt(KEY_MANUAL, usedManual + applied)
                addLifetime(applied)
            }
        }
        return settings.getInt(KEY_REMOTE, 0) + settings.getInt(KEY_PENDING, 0)
    }

    /**
     * Subtract [count] from today's total to correct a mistaken entry, and return the new total.
     * The total is floored at 0 — it never goes negative. Never touches network.
     *
     * The reduction is kept in the pending ledger (which is allowed to go negative) rather than the
     * remote baseline, so a later remote fetch that re-advances the baseline does not silently undo
     * the correction: the negative pending preserves the lowered total until a write succeeds.
     * Any amount removed is also refunded to the manual-entry cap ledger so a corrected mistake
     * frees the daily allowance again.
     */
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

    /** How much more may still be added via manual entry today (cap minus what's used). */
    fun manualRemainingToday(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return CHALLENGE_MANUAL_DAILY_CAP
        return (CHALLENGE_MANUAL_DAILY_CAP - settings.getInt(KEY_MANUAL, 0)).coerceAtLeast(0)
    }

    fun updateRemoteBaseline(today: LocalDate, remoteCount: Int) {
        ensureToday(today)
        if (remoteCount > settings.getInt(KEY_REMOTE, 0)) {
            settings.putInt(KEY_REMOTE, remoteCount)
        }
    }

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

    /**
     * Lifetime total counted on this device — never resets on day rollover. Accumulates every
     * locally-counted khatma unit (taps + applied manual entries) so the overall total-over-time is
     * published to the persistent DB user node.
     */
    fun lifetimeCount(): Int = settings.getInt(KEY_LIFETIME, 0)

    /**
     * Adopt the lifetime total the server holds for a restored account. The publish is absolute,
     * so a restored device that started from 0 would overwrite the account's real total on the
     * next screen visit; the higher of the two values wins to keep that one-way.
     */
    fun restoreLifetime(total: Int) {
        val restored = total.coerceAtLeast(0)
        if (restored > settings.getInt(KEY_LIFETIME, 0)) settings.putInt(KEY_LIFETIME, restored)
    }

    private fun addLifetime(delta: Int) {
        if (delta <= 0) return
        settings.putInt(KEY_LIFETIME, settings.getInt(KEY_LIFETIME, 0) + delta)
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
        const val KEY_DATE = "quran_challenge_date"
        const val KEY_REMOTE = "quran_challenge_count"
        const val KEY_PENDING = "quran_challenge_pending"
        // Cumulative manual ("external") entry today — the daily-cap ledger.
        const val KEY_MANUAL = "quran_challenge_manual"
        // Lifetime accumulator — survives day rollover; total counted across all days.
        const val KEY_LIFETIME = "quran_challenge_lifetime"
    }
}
