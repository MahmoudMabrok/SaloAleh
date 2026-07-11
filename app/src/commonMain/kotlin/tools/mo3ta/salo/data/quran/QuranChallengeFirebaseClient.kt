package tools.mo3ta.salo.data.quran

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.first
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.logFirebaseError
import tools.mo3ta.salo.data.firebase.FirestoreMirror
import tools.mo3ta.salo.domain.QURAN_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.QuranChallengeDayStats
import tools.mo3ta.salo.domain.QuranLeaderboardEntry

private const val ROOT_PATH = "quran_challenge"
private const val USERS_PATH = "users"
private const val LEADERBOARD_PATH = "leaderboard"
private const val COUNT_KEY = "count"
private const val RANK_KEY = "rank"
private const val RANK_CHANGE_KEY = "rankChange"
private const val DATA_KEY = "data"
private const val UID_KEY = "uid"
private const val DATE_KEY = "date"
private const val COUNTRY_CODE_KEY = "countryCode"
private const val NICKNAME_KEY = "nickname"
private const val GOAL_KEY = "goal"
private const val COMPLETED_KEY = "completed"
private const val UPDATED_AT_KEY = "updatedAt"
private const val PARTICIPANT_COUNT_KEY = "participantCount"
private const val TOTAL_TODAY_QURAN_KEY = "totalTodayQuran"

class QuranChallengeFirebaseClient(
    private val mirror: FirestoreMirror,
    private val analyticsManager: AnalyticsManager,
) {

    private val log = Logger.withTag("QuranChallengeFirebase")

    fun isConfigured(): Boolean = runCatching { Firebase.database }.isSuccess

    suspend fun writeUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
        nickname: String = "",
    ): Result<Unit> {
        val safeNickname = nickname.trim().take(20)
        log.d { "writeUserDay[$dateKey/$uid] count=$count hasNickname=${safeNickname.isNotBlank()}" }
        return runCatching {
            Firebase.database.reference(userPath(dateKey, uid)).updateChildren(
                mapOf(
                    COUNT_KEY to count.coerceAtLeast(0),
                    DATA_KEY to mapOf(
                        UID_KEY to uid,
                        DATE_KEY to dateKey,
                        COUNTRY_CODE_KEY to countryCode,
                        NICKNAME_KEY to safeNickname,
                        GOAL_KEY to QURAN_CHALLENGE_DAILY_GOAL,
                        COMPLETED_KEY to (count >= QURAN_CHALLENGE_DAILY_GOAL),
                        UPDATED_AT_KEY to ServerValue.TIMESTAMP,
                    ),
                ),
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeUserDay[$dateKey/$uid] ok" }
                    mirror.mirrorQuranUserDay(
                        dateKey, uid, count, countryCode, safeNickname,
                        QURAN_CHALLENGE_DAILY_GOAL, count >= QURAN_CHALLENGE_DAILY_GOAL,
                    )
                },
                onFailure = { error ->
                    log.e(error) { "writeUserDay[$dateKey/$uid] failed" }
                    trackWriteFailure("write_user_day", error)
                },
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
                onFailure = { error ->
                    log.e(error) { "fetchUserCount[$dateKey/$uid] failed" }
                    trackReadFailure("fetch_user_count", error)
                },
            )
        }
    }

    suspend fun fetchDayStats(dateKey: String, uid: String): Result<QuranChallengeDayStats> {
        log.d { "fetchDayStats[$dateKey/$uid]" }
        return runCatching {
            val dayRef = Firebase.database.reference(dayPath(dateKey))
            val rankSnapshot = dayRef.child(USERS_PATH).child(uid).child(RANK_KEY)
                .valueEvents
                .first()
            val participantCountSnapshot = dayRef.child(PARTICIPANT_COUNT_KEY)
                .valueEvents
                .first()
            val totalTodayQuranSnapshot = dayRef.child(TOTAL_TODAY_QURAN_KEY)
                .valueEvents
                .first()

            QuranChallengeDayStats(
                rank = (rankSnapshot.value as? Number)?.toInt() ?: 0,
                participantCount = (participantCountSnapshot.value as? Number)?.toInt() ?: 0,
                totalTodayQuran = (totalTodayQuranSnapshot.value as? Number)?.toInt() ?: 0,
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchDayStats[$dateKey/$uid] rank=${it.rank} participants=${it.participantCount} totalToday=${it.totalTodayQuran}" } },
                onFailure = { error ->
                    log.e(error) { "fetchDayStats[$dateKey/$uid] failed" }
                    trackReadFailure("fetch_day_stats", error)
                },
            )
        }
    }

    suspend fun fetchLeaderboard(dateKey: String): Result<List<QuranLeaderboardEntry>> {
        log.d { "fetchLeaderboard[$dateKey]" }
        return runCatching {
            val snap = Firebase.database.reference("$ROOT_PATH/$dateKey/$LEADERBOARD_PATH")
                .valueEvents
                .first()
            if (!snap.exists) return@runCatching emptyList()
            val rawValue = snap.value
            val entries = parseQuranLeaderboardEntries(rawValue)
            log.d { "fetchLeaderboard[$dateKey] rawType=${rawValue.leaderboardContainerType()} parsed=${entries.size}" }
            entries
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchLeaderboard[$dateKey] ${it.size} entries" } },
                onFailure = { error ->
                    log.e(error) { "fetchLeaderboard[$dateKey] failed" }
                    trackReadFailure("fetch_leaderboard", error)
                },
            )
        }
    }

    private fun trackReadFailure(operation: String, error: Throwable) {
        analyticsManager.logFirebaseError(
            surface = "quran_challenge",
            operation = operation,
            access = "read",
            error = error,
        )
    }

    private fun trackWriteFailure(operation: String, error: Throwable) {
        analyticsManager.logFirebaseError(
            surface = "quran_challenge",
            operation = operation,
            access = "write",
            error = error,
        )
    }

    private fun dayPath(dateKey: String) = "$ROOT_PATH/$dateKey"
    private fun usersPath(dateKey: String) = "$ROOT_PATH/$dateKey/$USERS_PATH"
    private fun userPath(dateKey: String, uid: String) = "${usersPath(dateKey)}/$uid"
}

internal fun parseQuranLeaderboardEntries(value: Any?): List<QuranLeaderboardEntry> {
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
        .mapNotNull { (_, rawEntry) -> rawEntry.toQuranLeaderboardEntry() }
}

private fun Any?.toQuranLeaderboardEntry(): QuranLeaderboardEntry? {
    val entry = this as? Map<*, *> ?: return null
    val uid = entry[UID_KEY] as? String ?: return null
    return QuranLeaderboardEntry(
        uid = uid,
        countryCode = entry[COUNTRY_CODE_KEY] as? String ?: "",
        count = (entry[COUNT_KEY] as? Number)?.toInt() ?: 0,
        rank = (entry[RANK_KEY] as? Number)?.toInt() ?: 0,
        rankChange = entry[RANK_CHANGE_KEY] as? String ?: "same",
        nickname = entry[NICKNAME_KEY] as? String ?: "",
    )
}

private fun Any?.leaderboardContainerType(): String = when (this) {
    null -> "null"
    is Map<*, *> -> "map"
    is List<*> -> "list"
    else -> "other"
}
