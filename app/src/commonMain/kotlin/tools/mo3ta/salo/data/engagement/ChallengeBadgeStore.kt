package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.ChallengeType

/**
 * Tracks per-challenge achievement badges. Each time the user wins a daily challenge
 * (reaches its daily goal) the badge count for that challenge increments by 1 —
 * at most once per day per challenge.
 */
class ChallengeBadgeStore(private val settings: Settings) {

    /**
     * Records a win for [challenge] on [date]. Idempotent within a day: recording
     * the same day again is a no-op. Returns the new badge count when a win was
     * recorded, or `null` when today's win was already counted.
     */
    fun recordWin(challenge: ChallengeType, date: LocalDate): Int? {
        val day = date.toString()
        if (settings.getStringOrNull(lastWinKey(challenge)) == day) return null
        val updated = settings.getInt(countKey(challenge), 0) + 1
        settings.putString(lastWinKey(challenge), day)
        settings.putInt(countKey(challenge), updated)
        return updated
    }

    /** Total wins recorded for [challenge] — the number shown on its badge. */
    fun getWinCount(challenge: ChallengeType): Int =
        settings.getInt(countKey(challenge), 0)

    /** Win counts for every challenge, for the achievements screen. */
    fun getWinCounts(): Map<ChallengeType, Int> =
        ChallengeType.entries.associateWith { getWinCount(it) }

    private fun lastWinKey(challenge: ChallengeType) = "challenge_badge_last_win_${challenge.id}"
    private fun countKey(challenge: ChallengeType) = "challenge_badge_count_${challenge.id}"
}
