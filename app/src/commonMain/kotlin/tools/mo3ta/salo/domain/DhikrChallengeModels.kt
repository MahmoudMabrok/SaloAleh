package tools.mo3ta.salo.domain

const val DHIKR_CHALLENGE_DAILY_GOAL = 100

data class DhikrChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayDhikr: Int = 0,
)
