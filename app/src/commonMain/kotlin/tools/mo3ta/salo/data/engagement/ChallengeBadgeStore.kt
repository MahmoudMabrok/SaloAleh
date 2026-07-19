package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.ChallengeType

/**
 * Tracks per-challenge achievement badges. Each time the user wins a daily challenge
 * (reaches its daily goal) the badge count for that challenge increments by 1 —
 * at most once per day per challenge.
 *
 * Independently of the goal-gated win count, each challenge keeps a **daily streak**: the
 * number of consecutive Cairo days on which the user did *any* activity for that challenge
 * (a single tap/count is enough — reaching the goal is not required). A fresh active day
 * starts the streak at 1, activity on the next day extends it, and skipping a day restarts
 * it at 1. The longest streak ever reached is remembered as the best streak.
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
     * the daily goal is not required. Idempotent within a day: recording the same day again is
     * a no-op. Returns the new streak when activity was recorded, or `null` when today's
     * activity was already counted.
     *
     * Activity on the day after the previous active day extends that challenge's streak; any
     * gap restarts it at 1. The best (longest) streak is updated whenever it is beaten.
     */
    fun recordActivity(challenge: ChallengeType, date: LocalDate): Int? {
        val lastActive = settings.getStringOrNull(lastActiveKey(challenge))?.let { LocalDate.parse(it) }
        if (lastActive == date) return null

        val newStreak = if (lastActive == date.minusDays(1)) {
            settings.getInt(streakKey(challenge), 0) + 1
        } else {
            1
        }

        settings.putString(lastActiveKey(challenge), date.toString())
        settings.putInt(streakKey(challenge), newStreak)
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
     * The live daily streak for [challenge] as of [today]: consecutive active days that
     * have not yet been broken. Returns 0 when there was never any activity, or when the last
     * active day is older than yesterday (the streak has since lapsed).
     */
    fun getCurrentStreak(challenge: ChallengeType, today: LocalDate): Int {
        val lastActive = settings.getStringOrNull(lastActiveKey(challenge))?.let { LocalDate.parse(it) } ?: return 0
        return if (lastActive == today || lastActive == today.minusDays(1)) {
            settings.getInt(streakKey(challenge), 0)
        } else {
            0
        }
    }

    /** The longest daily streak [challenge] has ever reached. */
    fun getBestStreak(challenge: ChallengeType): Int =
        settings.getInt(bestStreakKey(challenge), 0)

    /** Whether the user did any activity for [challenge] on [date] — i.e. participated that day. */
    fun wasActiveOn(challenge: ChallengeType, date: LocalDate): Boolean =
        settings.getStringOrNull(lastActiveKey(challenge))?.let { LocalDate.parse(it) } == date

    /** The challenges the user participated in on [today], for marking their cards. */
    fun getActiveChallenges(today: LocalDate): Set<ChallengeType> =
        ChallengeType.entries.filterTo(mutableSetOf()) { wasActiveOn(it, today) }

    /** Live daily streaks for every challenge as of [today], for the achievements screen. */
    fun getCurrentStreaks(today: LocalDate): Map<ChallengeType, Int> =
        ChallengeType.entries.associateWith { getCurrentStreak(it, today) }

    /** Best daily streaks for every challenge. */
    fun getBestStreaks(): Map<ChallengeType, Int> =
        ChallengeType.entries.associateWith { getBestStreak(it) }

    private fun lastWinKey(challenge: ChallengeType) = "challenge_badge_last_win_${challenge.id}"
    private fun countKey(challenge: ChallengeType) = "challenge_badge_count_${challenge.id}"
    private fun lastActiveKey(challenge: ChallengeType) = "challenge_streak_last_active_${challenge.id}"
    private fun streakKey(challenge: ChallengeType) = "challenge_streak_count_${challenge.id}"
    private fun bestStreakKey(challenge: ChallengeType) = "challenge_streak_best_${challenge.id}"

    private fun LocalDate.minusDays(n: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() - n)
}
