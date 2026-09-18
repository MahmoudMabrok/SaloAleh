package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.ROUND_STREAK_TARGET
import tools.mo3ta.salo.domain.RoundStreakResult
import tools.mo3ta.salo.domain.RoundStreakSnapshot
import tools.mo3ta.salo.domain.STREAK_FREEZE_MAX_DAYS

/**
 * Tracks the salawat activity streak: the number of consecutive days on which the user
 * sent at least one salawat (zikr).
 *
 * The streak is continuous — it carries across competition rounds. Missing 1–2 days does
 * not break it (those days consume the freeze window). Three consecutive Cairo days
 * with no zikr reset the streak to 0. Users can also freeze upcoming days, up to
 * [STREAK_FREEZE_MAX_DAYS] total in the current absence window.
 */
class RoundStreakStore(private val settings: Settings) {

    /**
     * Records salawat activity for [today]. Idempotent within a day. The streak persists
     * across rounds and only breaks after [STREAK_FREEZE_MAX_DAYS] consecutive missed days;
     * [roundKey] is used solely to award at most one [Achievement.RoundStreakBadge] per round.
     * Returns the up-to-date streak and any badge earned by this call.
     */
    fun recordActivity(roundKey: String, today: LocalDate): RoundStreakResult {
        val lastActive = lastActive()
        val storedCount = settings.getInt(KEY_COUNT, 0)

        if (lastActive == today) {
            return RoundStreakResult(storedCount, null)
        }

        val missed = missedDaysBetween(lastActive, today)
        val newStreak = when {
            lastActive == null -> 1
            missed >= STREAK_FREEZE_MAX_DAYS -> 1
            else -> storedCount + 1
        }

        settings.putString(KEY_LAST_ACTIVE, today.toString())
        settings.putInt(KEY_COUNT, newStreak)
        settings.remove(KEY_FREEZE_UNTIL)
        settings.remove(KEY_RESET_SHOWN)

        val newBadge = if (newStreak >= ROUND_STREAK_TARGET && !hasEarnedForRound(roundKey)) {
            val badge = Achievement.RoundStreakBadge(roundKey, today)
            addEarnedBadge(BadgeEntry(roundKey, today))
            badge
        } else {
            null
        }

        return RoundStreakResult(newStreak, newBadge)
    }

    /**
     * The current live streak as of [today]. Returns 0 when no activity has been recorded
     * or [STREAK_FREEZE_MAX_DAYS] consecutive days have already been missed. The
     * [roundKey] is accepted for call-site symmetry but does not gate the streak, which is
     * continuous across rounds.
     */
    fun getCurrentStreak(roundKey: String, today: LocalDate): Int = snapshot(today).currentStreak

    fun snapshot(today: LocalDate): RoundStreakSnapshot {
        val last = lastActive()
        val stored = settings.getInt(KEY_COUNT, 0)
        val missed = missedDaysBetween(last, today)
        val freezeUntil = freezeUntil()
        val broken = last != null && missed >= STREAK_FREEZE_MAX_DAYS
        val streak = when {
            last == null || stored <= 0 -> 0
            broken -> 0
            else -> stored
        }
        val remaining = if (last == null || broken || stored <= 0) {
            0
        } else {
            (STREAK_FREEZE_MAX_DAYS - missed).coerceAtLeast(0)
        }
        val coveredByFreeze = freezeUntil != null && freezeUntil >= today
        val atRisk = streak > 0 && missed in 1 until STREAK_FREEZE_MAX_DAYS && !coveredByFreeze
        return RoundStreakSnapshot(
            currentStreak = streak,
            missedDays = missed,
            freezeRemaining = remaining,
            isAtRisk = atRisk,
            freezeUntil = freezeUntil,
        )
    }

    /**
     * Persist a 3-day miss as a broken streak (count = 0) so display and the next
     * [recordActivity] agree. Returns [RoundStreakSnapshot.justReset] once per Cairo day.
     */
    fun reconcile(today: LocalDate): RoundStreakSnapshot {
        val snap = snapshot(today)
        val stored = settings.getInt(KEY_COUNT, 0)
        if (snap.currentStreak == 0 && stored > 0 && snap.missedDays >= STREAK_FREEZE_MAX_DAYS) {
            settings.putInt(KEY_COUNT, 0)
            val shown = settings.getStringOrNull(KEY_RESET_SHOWN)
            val justReset = shown != today.toString()
            if (justReset) settings.putString(KEY_RESET_SHOWN, today.toString())
            return snap.copy(justReset = justReset)
        }
        return snap
    }

    /**
     * Freeze the streak for [days] (1–[STREAK_FREEZE_MAX_DAYS]), capped by remaining freeze
     * in the current absence window. Frozen days do not put the streak at risk until the
     * freeze expires; three consecutive days with no zikr still reset it.
     */
    fun activateFreeze(days: Int, today: LocalDate): RoundStreakSnapshot {
        val snap = snapshot(today)
        if (snap.currentStreak <= 0) return snap
        val remaining = snap.freezeRemaining.coerceAtLeast(0)
        if (remaining <= 0) return snap
        val n = days.coerceIn(1, remaining)
        val last = lastActive() ?: today
        val maxUntil = last.plusDays(STREAK_FREEZE_MAX_DAYS)
        val start = if (last == today) today.plusDays(1) else today
        val requestedUntil = start.plusDays(n - 1)
        val until = if (requestedUntil.toEpochDays() <= maxUntil.toEpochDays()) requestedUntil else maxUntil
        settings.putString(KEY_FREEZE_UNTIL, until.toString())
        return snapshot(today)
    }

    /**
     * Adopt the streak a restored account had published to its player node. The server carries
     * the count but not the day it was last extended, so [lastActive] is supplied by the caller:
     * today when the account already sent salawat today (the count already includes today), and
     * yesterday otherwise (so today's first salawat extends it and a silent day still breaks it).
     *
     * Earned badges are not restored — they are local-only history that the server never held.
     */
    fun restoreStreak(streak: Int, lastActive: LocalDate) {
        if (streak <= 0) return
        settings.putInt(KEY_COUNT, streak)
        settings.putString(KEY_LAST_ACTIVE, lastActive.toString())
        settings.remove(KEY_FREEZE_UNTIL)
        settings.remove(KEY_RESET_SHOWN)
    }

    fun getEarnedBadges(): List<Achievement.RoundStreakBadge> =
        getEarnedBadgesRaw().map { Achievement.RoundStreakBadge(it.roundKey, it.date) }

    fun hasEarnedForRound(roundKey: String): Boolean =
        getEarnedBadgesRaw().any { it.roundKey == roundKey }

    // ── internal helpers ──────────────────────────────────────────────────────────

    @Serializable
    private data class BadgeEntry(val roundKey: String, val date: LocalDate)

    private fun lastActive(): LocalDate? =
        settings.getStringOrNull(KEY_LAST_ACTIVE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun freezeUntil(): LocalDate? =
        settings.getStringOrNull(KEY_FREEZE_UNTIL)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun missedDaysBetween(lastActive: LocalDate?, today: LocalDate): Int {
        if (lastActive == null) return 0
        val gap = (today.toEpochDays() - lastActive.toEpochDays()).toInt()
        return maxOf(0, gap - 1)
    }

    private fun getEarnedBadgesRaw(): List<BadgeEntry> {
        val raw = settings.getStringOrNull(KEY_BADGES) ?: return emptyList()
        return runCatching { json.decodeFromString<List<BadgeEntry>>(raw) }.getOrDefault(emptyList())
    }

    private fun addEarnedBadge(entry: BadgeEntry) {
        val updated = getEarnedBadgesRaw() + entry
        settings.putString(KEY_BADGES, json.encodeToString(updated))
    }

    private fun LocalDate.plusDays(n: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() + n)

    private companion object {
        const val KEY_LAST_ACTIVE = "round_streak_last_active"
        const val KEY_COUNT = "round_streak_count"
        const val KEY_BADGES = "round_streak_badges"
        const val KEY_FREEZE_UNTIL = "round_streak_freeze_until"
        const val KEY_RESET_SHOWN = "round_streak_reset_shown"
        val json = Json { ignoreUnknownKeys = true }
    }
}
