package tools.mo3ta.salo.domain

// The Prophet ﷺ said the four words three times ("أربع كلمات ثلاث مرات"), so a day's
// goal is to complete the four-word tasbih three times. Resets each Cairo day.
const val KALIMAT_CHALLENGE_DAILY_GOAL = 3

data class KalimatChallengeDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayKalimat: Int = 0,
)

data class KalimatLeaderboardEntry(
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
