package tools.mo3ta.salo.domain

data class BaqiyatLeaderboardEntry(
    val uid: String,
    val countryCode: String,
    val count: Int,
    val rank: Int,
    val rankChange: String = "same",
    val nickname: String = "",
)

data class BaqiyatDayStats(
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayBaqiyat: Int = 0,
)
