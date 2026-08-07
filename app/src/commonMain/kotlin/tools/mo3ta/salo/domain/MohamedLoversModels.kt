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
    val todayCount: Int = 0,
    val nickname: String = "",
    /** Server-published daily badge key for the current Cairo day (cleared nightly by the cron). */
    val dailyBadge: String = "",
)

/**
 * Outcome of one flush of the pending session to Firebase.
 *
 * [pushed] is what actually reached the server; [discarded] is the pending score that was dropped
 * because it did not fit under the day's [MOHAMED_LOVERS_DAILY_PUSH_CAP]. Discarded score is not
 * carried over to the next day — the local pending is cleared so the on-screen number goes back to
 * matching the remote one.
 */
data class MohamedLoversFlushResult(
    val pushed: Int = 0,
    val discarded: Int = 0,
)

/**
 * Cumulative podium medals for a single user, read from the server-authoritative
 * `mohamed_lovers/users/{uid}/medals` node. Used to render the current user's own
 * medals on the leaderboard even when they are outside the server-populated top-N
 * (where medals are only attached to the ranked entries).
 */
data class MohamedLoversMedals(
    val gold: Int = 0,
    val silver: Int = 0,
    val bronze: Int = 0,
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
    val isSupporter: Boolean = false,
    val dailyBadge: String? = null,
    val roundStreak: Int? = null,
    val goldMedals: Int? = null,
    val silverMedals: Int? = null,
    val bronzeMedals: Int? = null,
    val nickname: String = "",
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

fun buildMohamedLoversDisplayTag(uid: String, countryCode: String, nickname: String = ""): String {
    val country = countryCode.uppercase().ifBlank { MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE }
    if (nickname.isNotBlank()) return "$country • $nickname"
    val tag = uid.takeLast(6).uppercase().ifBlank { "------" }
    return "$country • $tag"
}
