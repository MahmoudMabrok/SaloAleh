package tools.mo3ta.salo.domain

const val GHARS_CHALLENGE_DAILY_GOAL = 100 // two complete groves
const val GHARS_GROVE_SIZE = 50 // palms per grove; hard drawing ceiling

data class GharsChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayGhars: Int = 0,
)

data class GharsLeaderboardEntry(
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
