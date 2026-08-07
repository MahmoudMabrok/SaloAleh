package tools.mo3ta.salo.domain

const val HAWQALA_CHALLENGE_DAILY_GOAL = 100

data class HawqalaChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayHawqala: Int = 0,
)

data class HawqalaLeaderboardEntry(
    val uid: String,
    val countryCode: String,
    val count: Int,
    val rank: Int,
    val rankChange: String, // "up" | "down" | "same" | "new"
    val nickname: String = "",
    val streak: Int = 0,
    val goldMedals: Int = 0,
    val silverMedals: Int = 0,
    val bronzeMedals: Int = 0,
)
