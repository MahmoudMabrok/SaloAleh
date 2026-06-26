package tools.mo3ta.salo.domain

const val DHIKR_CHALLENGE_DAILY_GOAL = 100

data class DhikrChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayDhikr: Int = 0,
)

data class DhikrLeaderboardEntry(
    val uid: String,
    val countryCode: String,
    val count: Int,
    val rank: Int,
    val rankChange: String, // "up" | "down" | "same" | "new"
    val nickname: String = "",
)
