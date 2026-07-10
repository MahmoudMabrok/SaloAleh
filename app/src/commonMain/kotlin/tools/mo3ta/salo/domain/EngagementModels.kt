package tools.mo3ta.salo.domain

import kotlinx.datetime.LocalDate

data class EngagementData(
    val openCount: Int,
    val currentStreak: Int,
    val newlyEarnedBadge: BadgeType?,
    val shouldRequestNotifPermission: Boolean,
    val graceConsumedNow: Boolean = false,
    val shouldReshowFcmAlert: Boolean = false,
)

enum class BadgeType { STREAK_7, STREAK_30 }

sealed class Achievement {
    data class StreakBadge(val type: BadgeType, val earnedDate: LocalDate) : Achievement()
    data class RankAchievement(
        val roundKey: String,
        val rank: Int,
        val earnedDate: LocalDate,
        val score: Int? = null,
        val winnerCode: String = "",
    ) : Achievement()

    /**
     * Earned when the user sends salawat every day of a competition round without
     * missing a single day (a "perfect week"). Repeatable — one per round.
     */
    data class RoundStreakBadge(
        val roundKey: String,
        val earnedDate: LocalDate,
    ) : Achievement()
}

/** Number of consecutive active days within a round required to earn the round-streak badge. */
const val ROUND_STREAK_TARGET = 7

/** Outcome of recording a day's salawat activity for the round-streak tracker. */
data class RoundStreakResult(
    val currentStreak: Int,
    val newlyEarnedBadge: Achievement.RoundStreakBadge?,
)

data class UserAchievement(
    val rank: Int,
    val score: Int? = null,
    val winnerCode: String = "",
)
