package tools.mo3ta.salo.domain

data class BaqiyatLeaderboardEntry(
    val uid: String,
    val countryCode: String,
    val count: Int,
    val rank: Int,
    val nickname: String = "",
)
