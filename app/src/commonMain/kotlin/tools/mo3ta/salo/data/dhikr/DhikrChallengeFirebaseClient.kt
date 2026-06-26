package tools.mo3ta.salo.data.dhikr

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.first
import tools.mo3ta.salo.domain.DHIKR_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.DhikrChallengeDayStats
import tools.mo3ta.salo.domain.DhikrLeaderboardEntry

private const val ROOT_PATH = "100_challenge"
private const val USERS_PATH = "users"
private const val LEADERBOARD_PATH = "leaderboard"
private const val COUNT_KEY = "count"
private const val RANK_KEY = "rank"
private const val RANK_CHANGE_KEY = "rankChange"
private const val DATA_KEY = "data"
private const val UID_KEY = "uid"
private const val DATE_KEY = "date"
private const val COUNTRY_CODE_KEY = "countryCode"
private const val GOAL_KEY = "goal"
private const val COMPLETED_KEY = "completed"
private const val UPDATED_AT_KEY = "updatedAt"
private const val PARTICIPANT_COUNT_KEY = "participantCount"
private const val TOTAL_TODAY_DHIKR_KEY = "totalTodayDhikr"

class DhikrChallengeFirebaseClient {

    private val log = Logger.withTag("DhikrChallengeFirebase")

    fun isConfigured(): Boolean = runCatching { Firebase.database }.isSuccess

    suspend fun writeUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
    ): Result<Unit> {
        log.d { "writeUserDay[$dateKey/$uid] count=$count" }
        return runCatching {
            Firebase.database.reference(userPath(dateKey, uid)).updateChildren(
                mapOf(
                    COUNT_KEY to count.coerceAtLeast(0),
                    DATA_KEY to mapOf(
                        UID_KEY to uid,
                        DATE_KEY to dateKey,
                        COUNTRY_CODE_KEY to countryCode,
                        GOAL_KEY to DHIKR_CHALLENGE_DAILY_GOAL,
                        COMPLETED_KEY to (count >= DHIKR_CHALLENGE_DAILY_GOAL),
                        UPDATED_AT_KEY to ServerValue.TIMESTAMP,
                    ),
                ),
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "writeUserDay[$dateKey/$uid] ok" } },
                onFailure = { log.e(it) { "writeUserDay[$dateKey/$uid] failed" } },
            )
        }
    }

    suspend fun fetchUserCount(dateKey: String, uid: String): Result<Int?> {
        log.d { "fetchUserCount[$dateKey/$uid]" }
        return runCatching {
            val snapshot = Firebase.database.reference(userPath(dateKey, uid))
                .child(COUNT_KEY)
                .valueEvents
                .first()
            if (!snapshot.exists) null else (snapshot.value as? Number)?.toInt()
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchUserCount[$dateKey/$uid]=$it" } },
                onFailure = { log.e(it) { "fetchUserCount[$dateKey/$uid] failed" } },
            )
        }
    }

    suspend fun fetchDayStats(dateKey: String, uid: String): Result<DhikrChallengeDayStats> {
        log.d { "fetchDayStats[$dateKey/$uid]" }
        return runCatching {
            val dayRef = Firebase.database.reference(dayPath(dateKey))
            val rankSnapshot = dayRef.child(USERS_PATH).child(uid).child(RANK_KEY)
                .valueEvents
                .first()
            val participantCountSnapshot = dayRef.child(PARTICIPANT_COUNT_KEY)
                .valueEvents
                .first()
            val totalTodayDhikrSnapshot = dayRef.child(TOTAL_TODAY_DHIKR_KEY)
                .valueEvents
                .first()

            DhikrChallengeDayStats(
                rank = (rankSnapshot.value as? Number)?.toInt() ?: 0,
                participantCount = (participantCountSnapshot.value as? Number)?.toInt() ?: 0,
                totalTodayDhikr = (totalTodayDhikrSnapshot.value as? Number)?.toInt() ?: 0,
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchDayStats[$dateKey/$uid] rank=${it.rank} participants=${it.participantCount} totalToday=${it.totalTodayDhikr}" } },
                onFailure = { log.e(it) { "fetchDayStats[$dateKey/$uid] failed" } },
            )
        }
    }

    suspend fun fetchLeaderboard(dateKey: String): Result<List<DhikrLeaderboardEntry>> {
        log.d { "fetchLeaderboard[$dateKey]" }
        return runCatching {
            val snap = Firebase.database.reference("$ROOT_PATH/$dateKey/$LEADERBOARD_PATH")
                .valueEvents
                .first()
            if (!snap.exists) return@runCatching emptyList()
            val rawValue = snap.value
            val entries = parseDhikrLeaderboardEntries(rawValue)
            log.d { "fetchLeaderboard[$dateKey] rawType=${rawValue.leaderboardContainerType()} parsed=${entries.size}" }
            entries
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchLeaderboard[$dateKey] ${it.size} entries" } },
                onFailure = { log.e(it) { "fetchLeaderboard[$dateKey] failed" } },
            )
        }
    }

    private fun dayPath(dateKey: String) = "$ROOT_PATH/$dateKey"
    private fun usersPath(dateKey: String) = "$ROOT_PATH/$dateKey/$USERS_PATH"
    private fun userPath(dateKey: String, uid: String) = "${usersPath(dateKey)}/$uid"
}

internal fun parseDhikrLeaderboardEntries(value: Any?): List<DhikrLeaderboardEntry> {
    val indexedValues = when (value) {
        is Map<*, *> -> value.entries.map { entry ->
            (entry.key.toString().toIntOrNull() ?: Int.MAX_VALUE) to entry.value
        }
        is List<*> -> value.withIndex().map { indexedValue ->
            indexedValue.index to indexedValue.value
        }
        else -> emptyList()
    }

    return indexedValues
        .sortedBy { it.first }
        .mapNotNull { (_, rawEntry) -> rawEntry.toDhikrLeaderboardEntry() }
}

private fun Any?.toDhikrLeaderboardEntry(): DhikrLeaderboardEntry? {
    val entry = this as? Map<*, *> ?: return null
    val uid = entry[UID_KEY] as? String ?: return null
    return DhikrLeaderboardEntry(
        uid = uid,
        countryCode = entry[COUNTRY_CODE_KEY] as? String ?: "",
        count = (entry[COUNT_KEY] as? Number)?.toInt() ?: 0,
        rank = (entry[RANK_KEY] as? Number)?.toInt() ?: 0,
        rankChange = entry[RANK_CHANGE_KEY] as? String ?: "same",
    )
}

private fun Any?.leaderboardContainerType(): String = when (this) {
    null -> "null"
    is Map<*, *> -> "map"
    is List<*> -> "list"
    else -> "other"
}
