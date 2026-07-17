package tools.mo3ta.salo.domain

const val ZABAD_CHALLENGE_DAILY_GOAL = 100
const val ZABAD_ROUND_TARGET = 100

data class ZabadChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayZabad: Int = 0,
)

data class ZabadLeaderboardEntry(
    val uid: String,
    val countryCode: String,
    val count: Int,
    val rank: Int,
    val rankChange: String, // "up" | "down" | "same" | "new"
    val nickname: String = "",
    val streak: Int = 0,
)
