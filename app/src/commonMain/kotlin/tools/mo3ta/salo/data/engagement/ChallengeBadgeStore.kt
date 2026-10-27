package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.domain.STREAK_FREEZE_MAX_DAYS
import tools.mo3ta.salo.domain.RoundStreakSnapshot

/**
 * Tracks per-challenge achievement badges. Each time the user wins a daily challenge
 * (reaches its daily goal) the badge count for that challenge increments by 1 —
 * at most once per day per challenge.
 *
 * Independently of the goal-gated win count, each challenge keeps a **daily streak**: the
 * number of consecutive Cairo days on which the user did *any* activity for that challenge
 * (a single tap/count is enough — reaching the goal is not required). Missing 1–2 days
 * consumes the freeze window and keeps the streak; 3 consecutive days with no activity
 * reset it. Users can also freeze remaining days in the window.
 */
class ChallengeBadgeStore(private val settings: Settings) {

    /**
     * Records a win for [challenge] on [date] — reaching the daily goal. Idempotent within
     * a day: recording the same day again is a no-op. Returns the new badge count when a win
     * was recorded, or `null` when today's win was already counted.
     *
     * The daily streak is **not** driven by wins — see [recordActivity].
     */
    fun recordWin(challenge: ChallengeType, date: LocalDate): Int? {
        val lastWin = settings.getStringOrNull(lastWinKey(challenge))?.let { LocalDate.parse(it) }
        if (lastWin == date) return null

        val updated = settings.getInt(countKey(challenge), 0) + 1
        settings.putString(lastWinKey(challenge), date.toString())
        settings.putInt(countKey(challenge), updated)
        return updated
    }

    /**
     * Records any activity for [challenge] on [date] — a single tap/count is enough; reaching
     * the daily goal is not required. Idempotent within a day. Returns the new streak when
     * activity was recorded, or `null` when today's activity was already counted.
     *
     * Missing 1–2 days keeps the streak (freeze window); 3 consecutive missed days reset it to 1.
     */
    fun recordActivity(challenge: ChallengeType, date: LocalDate): Int? {
        val lastActive = lastActive(challenge)
        if (lastActive == date) return null

        val missed = missedDaysBetween(lastActive, date)
        val stored = settings.getInt(streakKey(challenge), 0)
        val newStreak = when {
            lastActive == null -> 1
            missed >= STREAK_FREEZE_MAX_DAYS -> 1
            else -> stored + 1
        }

        settings.putString(lastActiveKey(challenge), date.toString())
        settings.putInt(streakKey(challenge), newStreak)
        settings.remove(freezeUntilKey(challenge))
        if (newStreak > settings.getInt(bestStreakKey(challenge), 0)) {
            settings.putInt(bestStreakKey(challenge), newStreak)
        }
        return newStreak
    }

    /** Total wins recorded for [challenge] — the number shown on its badge. */
    fun getWinCount(challenge: ChallengeType): Int =
        settings.getInt(countKey(challenge), 0)

    /** Win counts for every challenge, for the achievements screen. */
    fun getWinCounts(): Map<ChallengeType, Int> =
        ChallengeType.entries.associateWith { getWinCount(it) }

    /**
     * The live daily streak for [challenge] as of [today]. Returns 0 when no activity has been
     * recorded or [STREAK_FREEZE_MAX_DAYS] consecutive days have already been missed.
     */
    fun getCurrentStreak(challenge: ChallengeType, today: LocalDate): Int =
        snapshot(challenge, today).currentStreak

    /** Live view of a challenge's daily streak, including the 3-day freeze window. */
    fun snapshot(challenge: ChallengeType, today: LocalDate): RoundStreakSnapshot {
        val last = lastActive(challenge)
        val stored = settings.getInt(streakKey(challenge), 0)
        val missed = missedDaysBetween(last, today)
        val freezeUntil = freezeUntil(challenge)
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
     * Freeze the streak for [days] (1–[STREAK_FREEZE_MAX_DAYS]), capped by remaining freeze
     * in the current absence window. Frozen days do not put the streak at risk until the
     * freeze expires; three consecutive days with no activity still reset it.
     */
    fun activateFreeze(challenge: ChallengeType, days: Int, today: LocalDate): RoundStreakSnapshot {
        val snap = snapshot(challenge, today)
        if (snap.currentStreak <= 0) return snap
        val remaining = snap.freezeRemaining.coerceAtLeast(0)
        if (remaining <= 0) return snap
        val n = days.coerceIn(1, remaining)
        val last = lastActive(challenge) ?: today
        val maxUntil = last.plusDays(STREAK_FREEZE_MAX_DAYS)
        val start = if (last == today) today.plusDays(1) else today
        val requestedUntil = start.plusDays(n - 1)
        val until = if (requestedUntil.toEpochDays() <= maxUntil.toEpochDays()) requestedUntil else maxUntil
        settings.putString(freezeUntilKey(challenge), until.toString())
        return snapshot(challenge, today)
    }

    /** The longest daily streak [challenge] has ever reached. */
    fun getBestStreak(challenge: ChallengeType): Int =
        settings.getInt(bestStreakKey(challenge), 0)

    /** Whether the user did any activity for [challenge] on [date] — i.e. participated that day. */
    fun wasActiveOn(challenge: ChallengeType, date: LocalDate): Boolean =
        lastActive(challenge) == date

    /** The challenges the user participated in on [today], for marking their cards. */
    fun getActiveChallenges(today: LocalDate): Set<ChallengeType> =
        ChallengeType.entries.filterTo(mutableSetOf()) { wasActiveOn(it, today) }

    /** Live daily streaks for every challenge as of [today], for the achievements screen. */
    fun getCurrentStreaks(today: LocalDate): Map<ChallengeType, Int> =
        ChallengeType.entries.associateWith { getCurrentStreak(it, today) }

    /** Best daily streaks for every challenge. */
    fun getBestStreaks(): Map<ChallengeType, Int> =
        ChallengeType.entries.associateWith { getBestStreak(it) }

    // ── internal helpers ─────────────────────────────────────────────────────────────

    private fun lastActive(challenge: ChallengeType): LocalDate? =
        settings.getStringOrNull(lastActiveKey(challenge))?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun freezeUntil(challenge: ChallengeType): LocalDate? =
        settings.getStringOrNull(freezeUntilKey(challenge))?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun missedDaysBetween(lastActive: LocalDate?, today: LocalDate): Int {
        if (lastActive == null) return 0
        val gap = (today.toEpochDays() - lastActive.toEpochDays()).toInt()
        return maxOf(0, gap - 1)
    }

    private fun LocalDate.plusDays(n: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() + n)

    private fun lastWinKey(challenge: ChallengeType) = "challenge_badge_last_win_${challenge.id}"
    private fun countKey(challenge: ChallengeType) = "challenge_badge_count_${challenge.id}"
    private fun lastActiveKey(challenge: ChallengeType) = "challenge_streak_last_active_${challenge.id}"
    private fun streakKey(challenge: ChallengeType) = "challenge_streak_count_${challenge.id}"
    private fun bestStreakKey(challenge: ChallengeType) = "challenge_streak_best_${challenge.id}"
    private fun freezeUntilKey(challenge: ChallengeType) = "challenge_streak_freeze_until_${challenge.id}"
}
