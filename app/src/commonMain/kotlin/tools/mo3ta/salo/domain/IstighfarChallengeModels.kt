package tools.mo3ta.salo.domain

const val ISTIGHFAR_CHALLENGE_DAILY_GOAL = 70

data class IstighfarChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayIstighfar: Int = 0,
)

data class IstighfarLeaderboardEntry(
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
