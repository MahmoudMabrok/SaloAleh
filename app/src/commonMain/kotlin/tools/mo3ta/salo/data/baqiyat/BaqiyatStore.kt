package tools.mo3ta.salo.data.baqiyat

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import tools.mo3ta.salo.domain.BAQIYAT_MANUAL_DAILY_CAP
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.baqiyat_phrase_alhamdulillah
import tools.mo3ta.salo.generated.resources.baqiyat_phrase_allahuakbar
import tools.mo3ta.salo.generated.resources.baqiyat_phrase_lahawla
import tools.mo3ta.salo.generated.resources.baqiyat_phrase_lailaha
import tools.mo3ta.salo.generated.resources.baqiyat_phrase_subhanallah

enum class BaqiyatPhrase(val key: String, val labelRes: StringResource) {
    SubhanAllah("subhanallah", Res.string.baqiyat_phrase_subhanallah),
    Alhamdulillah("alhamdulillah", Res.string.baqiyat_phrase_alhamdulillah),
    AllahuAkbar("allahuakbar", Res.string.baqiyat_phrase_allahuakbar),
    LaIlahaIllallah("lailaha", Res.string.baqiyat_phrase_lailaha),
    LaHawla("lahawla", Res.string.baqiyat_phrase_lahawla),
}

/** Local counter of completed Baqiyat Salihat cycles (one full pass over all [BaqiyatPhrase] values = +1). */
class BaqiyatStore(private val settings: Settings) {

    /** Total to display = remote baseline + unsynced pending cycles. */
    fun todayCount(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_REMOTE, 0) + settings.getInt(KEY_PENDING, 0)
    }

    /**
     * Returns (dateKey, total) for a previous day that still has unsynced pending > 0.
     * Only fires when there are actually un-flushed local cycles — not just a stale remote baseline.
     */
    fun previousEntry(today: LocalDate): Pair<String, Int>? {
        val storedDate = settings.getStringOrNull(KEY_DATE) ?: return null
        if (storedDate == today.toString()) return null
        val pending = settings.getInt(KEY_PENDING, 0)
        if (pending == 0) return null
        return storedDate to (settings.getInt(KEY_REMOTE, 0) + pending)
    }

    /** Increment pending cycle count and return new total (remote + pending). Never touches network. */
    fun incrementToday(today: LocalDate): Int {
        ensureToday(today)
        val newPending = settings.getInt(KEY_PENDING, 0) + 1
        settings.putInt(KEY_PENDING, newPending)
        return settings.getInt(KEY_REMOTE, 0) + newPending
    }

    /**
     * Add [count] pending cycles at once (manual external entry) and return the new total
     * (remote + pending). Never touches network. Non-positive counts are ignored, and the
     * amount actually applied is clamped so cumulative manual entry never exceeds
     * [BAQIYAT_MANUAL_DAILY_CAP] for the day. Regular cycles ([incrementToday]) are not counted
     * against this cap.
     */
    fun addToday(today: LocalDate, count: Int): Int {
        ensureToday(today)
        if (count > 0) {
            val usedManual = settings.getInt(KEY_MANUAL, 0)
            val applied = count.coerceAtMost((BAQIYAT_MANUAL_DAILY_CAP - usedManual).coerceAtLeast(0))
            if (applied > 0) {
                settings.putInt(KEY_PENDING, settings.getInt(KEY_PENDING, 0) + applied)
                settings.putInt(KEY_MANUAL, usedManual + applied)
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
     * the correction. Any amount removed is also refunded to the manual-entry cap ledger so a
     * corrected mistake frees the daily allowance again.
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
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return BAQIYAT_MANUAL_DAILY_CAP
        return (BAQIYAT_MANUAL_DAILY_CAP - settings.getInt(KEY_MANUAL, 0)).coerceAtLeast(0)
    }

    /**
     * Update the remote baseline after loading it from Firebase.
     * Only advances the baseline — never reduces it.
     * Pending is untouched so any local cycles completed during the fetch are preserved.
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

    private fun ensureToday(today: LocalDate) {
        val date = today.toString()
        if (settings.getStringOrNull(KEY_DATE) == date) return
        settings.putString(KEY_DATE, date)
        settings.putInt(KEY_REMOTE, 0)
        settings.putInt(KEY_PENDING, 0)
        settings.putInt(KEY_MANUAL, 0)
    }

    private companion object {
        const val KEY_DATE = "baqiyat_challenge_date"
        const val KEY_REMOTE = "baqiyat_challenge_count"
        const val KEY_PENDING = "baqiyat_challenge_pending"
        // Cumulative manual ("external") entry today — the daily-cap ledger.
        const val KEY_MANUAL = "baqiyat_challenge_manual"
    }
}
