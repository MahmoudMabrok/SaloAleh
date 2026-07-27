package tools.mo3ta.salo.domain

const val ALF_HASANA_CHALLENGE_DAILY_GOAL = 100

/** Each tasbiha ("سبحان الله") is worth 10 hasanat — 100 tasbihat = 1000 hasanat, as in the hadith. */
const val ALF_HASANA_HASANAT_PER_TASBIHA = 10

data class AlfHasanaChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayAlfHasana: Int = 0,
)

data class AlfHasanaLeaderboardEntry(
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
