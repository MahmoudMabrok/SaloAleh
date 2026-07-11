package tools.mo3ta.salo.domain

const val QURAN_CHALLENGE_DAILY_GOAL = 5

data class QuranChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayQuran: Int = 0,
)

data class QuranLeaderboardEntry(
    val uid: String,
    val countryCode: String,
    val count: Int,
    val rank: Int,
    val rankChange: String,
    val nickname: String = "",
)
