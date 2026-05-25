package tools.mo3ta.salo.domain

import kotlinx.datetime.Instant

data class MohamedLoversPlayer(
    val uid: String = "",
    val totalCount: Int = 0,
    val totalExternal: Int = 0,
    val rank: Int = 0,
    val isWinner: Boolean = false,
    val winnerCode: String = "",
    val countryCode: String = "",
    val updatedAt: Long = 0L,
    val yesterdayTotalScore: Int = 0,
)

data class MohamedLoversPendingSession(
    val roundKey: String? = null,
    val clickCount: Int = 0,
)

data class MohamedLoversCompetitionWindow(
    val networkNow: Instant? = null,
    val roundKey: String? = null,
    val roundEnd: Instant? = null,
    val message: String? = null,
)

data class FirebaseLeaderboardEntry(
    val rank: Int,
    val uid: String,
    val score: Int,
    val countryCode: String = "",
    val rankChange: String = "",
    val scoreMasked: Boolean = false,
    val isSupporter: Boolean = false,
    val dailyBadge: String? = null,
)

data class FirebaseLeaderboard(
    val entries: List<FirebaseLeaderboardEntry>,
    val isFinal: Boolean,
)

data class MohamedLoversBootstrap(
    val firebaseConfigured: Boolean,
    val countryCode: String,
    val competitionWindow: MohamedLoversCompetitionWindow,
    val pendingSession: MohamedLoversPendingSession,
)

const val MOHAMED_LOVERS_TOP_LIMIT = 10
const val MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE = "NA"

fun buildMohamedLoversDisplayTag(uid: String, countryCode: String): String {
    val tag = uid.takeLast(6).uppercase().ifBlank { "------" }
    val country = countryCode.uppercase().ifBlank { MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE }
    return "$country • $tag"
}
