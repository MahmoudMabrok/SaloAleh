package tools.mo3ta.salo.domain

import kotlinx.datetime.LocalDate

data class EngagementData(
    val openCount: Int,
    val currentStreak: Int,
    val newlyEarnedBadge: BadgeType?,
    val shouldRequestNotifPermission: Boolean,
)

enum class BadgeType { STREAK_7, STREAK_30 }

sealed class Achievement {
    data class StreakBadge(val type: BadgeType, val earnedDate: LocalDate) : Achievement()
    data class RankAchievement(val roundKey: String, val rank: Int, val earnedDate: LocalDate) : Achievement()
}
