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
}

data class UserAchievement(
    val rank: Int,
    val score: Int? = null,
    val winnerCode: String = "",
)
