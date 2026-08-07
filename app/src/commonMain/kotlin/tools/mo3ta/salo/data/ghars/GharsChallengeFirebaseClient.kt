package tools.mo3ta.salo.data.ghars

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.first
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.logFirebaseError
import tools.mo3ta.salo.data.firebase.FirestoreMirror
import tools.mo3ta.salo.domain.GHARS_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.GharsChallengeDayStats
import tools.mo3ta.salo.domain.GharsLeaderboardEntry

private const val ROOT_PATH = "ghars_challenge"
private const val USERS_PATH = "users"
private const val LEADERBOARD_PATH = "leaderboard"
private const val COUNT_KEY = "count"
private const val TOTAL_COUNT_KEY = "totalCount"
private const val STREAK_KEY = "streak"
private const val RANK_KEY = "rank"
private const val RANK_CHANGE_KEY = "rankChange"
private const val DATA_KEY = "data"
private const val UID_KEY = "uid"
private const val DATE_KEY = "date"
private const val COUNTRY_CODE_KEY = "countryCode"
private const val NICKNAME_KEY = "nickname"
private const val GOLD_MEDALS_KEY = "goldMedals"
private const val SILVER_MEDALS_KEY = "silverMedals"
private const val BRONZE_MEDALS_KEY = "bronzeMedals"
private const val GOAL_KEY = "goal"
private const val COMPLETED_KEY = "completed"
private const val UPDATED_AT_KEY = "updatedAt"
private const val PARTICIPANT_COUNT_KEY = "participantCount"
private const val TOTAL_TODAY_GHARS_KEY = "totalTodayGhars"

class GharsChallengeFirebaseClient(
    private val mirror: FirestoreMirror,
    private val analyticsManager: AnalyticsManager,
) {

    private val log = Logger.withTag("GharsChallengeFirebase")

    fun isConfigured(): Boolean = runCatching { Firebase.database }.isSuccess

    suspend fun writeUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
        nickname: String = "",
        streak: Int = 0,
    ): Result<Unit> {
        val safeNickname = nickname.trim().take(20)
        log.d { "writeUserDay[$dateKey/$uid] count=$count hasNickname=${safeNickname.isNotBlank()}" }
        return runCatching {
            Firebase.database.reference(userPath(dateKey, uid)).updateChildren(
                mapOf(
                    COUNT_KEY to count.coerceAtLeast(0),
                    STREAK_KEY to streak.coerceAtLeast(0),
                    DATA_KEY to mapOf(
                        UID_KEY to uid,
                        DATE_KEY to dateKey,
                        COUNTRY_CODE_KEY to countryCode,
                        NICKNAME_KEY to safeNickname,
                        GOAL_KEY to GHARS_CHALLENGE_DAILY_GOAL,
                        COMPLETED_KEY to (count >= GHARS_CHALLENGE_DAILY_GOAL),
                        UPDATED_AT_KEY to ServerValue.TIMESTAMP,
                    ),
                ),
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeUserDay[$dateKey/$uid] ok" }
                    mirror.mirrorGharsUserDay(
                        dateKey, uid, count, countryCode, safeNickname,
                        GHARS_CHALLENGE_DAILY_GOAL, count >= GHARS_CHALLENGE_DAILY_GOAL, streak,
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

    suspend fun fetchDayStats(dateKey: String, uid: String): Result<GharsChallengeDayStats> {
        log.d { "fetchDayStats[$dateKey/$uid]" }
        return runCatching {
            val dayRef = Firebase.database.reference(dayPath(dateKey))
            val rankSnapshot = dayRef.child(USERS_PATH).child(uid).child(RANK_KEY)
                .valueEvents
                .first()
            val participantCountSnapshot = dayRef.child(PARTICIPANT_COUNT_KEY)
                .valueEvents
                .first()
            val totalTodayGharsSnapshot = dayRef.child(TOTAL_TODAY_GHARS_KEY)
                .valueEvents
                .first()

            GharsChallengeDayStats(
                rank = (rankSnapshot.value as? Number)?.toInt() ?: 0,
                participantCount = (participantCountSnapshot.value as? Number)?.toInt() ?: 0,
                totalTodayGhars = (totalTodayGharsSnapshot.value as? Number)?.toInt() ?: 0,
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchDayStats[$dateKey/$uid] rank=${it.rank} participants=${it.participantCount} totalToday=${it.totalTodayGhars}" } },
                onFailure = { error ->
                    log.e(error) { "fetchDayStats[$dateKey/$uid] failed" }
                    trackReadFailure("fetch_day_stats", error)
                },
            )
        }
    }

    suspend fun fetchLeaderboard(dateKey: String): Result<List<GharsLeaderboardEntry>> {
        log.d { "fetchLeaderboard[$dateKey]" }
        return runCatching {
            val snap = Firebase.database.reference("$ROOT_PATH/$dateKey/$LEADERBOARD_PATH")
                .valueEvents
                .first()
            if (!snap.exists) return@runCatching emptyList()
            val rawValue = snap.value
            val entries = parseGharsLeaderboardEntries(rawValue)
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
            surface = "ghars_challenge",
            operation = operation,
            access = "read",
            error = error,
        )
    }

    /**
     * Publish the device's lifetime total (all-time palms grown across every day) to the persistent
     * user node at {root}/users/{uid}/totalCount. Fire-and-forget: a failure never affects the
     * daily-count sync. The value is absolute — the server baseline is not read back.
     */
    suspend fun writeUserTotal(uid: String, total: Int): Result<Unit> {
        log.d { "writeUserTotal[$uid] total=$total" }
        return runCatching {
            Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid")
                .updateChildren(mapOf(TOTAL_COUNT_KEY to total.coerceAtLeast(0)))
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "writeUserTotal[$uid] ok" } },
                onFailure = { error ->
                    log.e(error) { "writeUserTotal[$uid] failed" }
                    trackWriteFailure("write_user_total", error)
                },
            )
        }
    }

    /**
     * Read the lifetime total the server holds for [uid]. Used by account restore: the publish is
     * absolute, so a restored device must adopt this value before it can publish its own.
     * Returns 0 when the node is absent.
     */
    suspend fun fetchUserTotal(uid: String): Result<Int> {
        log.d { "fetchUserTotal[$uid]" }
        return runCatching {
            val snapshot = Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid")
                .child(TOTAL_COUNT_KEY)
                .valueEvents
                .first()
            (snapshot.value as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchUserTotal[$uid]=$it" } },
                onFailure = { error ->
                    log.e(error) { "fetchUserTotal[$uid] failed" }
                    trackReadFailure("fetch_user_total", error)
                },
            )
        }
    }

    private fun trackWriteFailure(operation: String, error: Throwable) {
        analyticsManager.logFirebaseError(
            surface = "ghars_challenge",
            operation = operation,
            access = "write",
            error = error,
        )
    }

    private fun dayPath(dateKey: String) = "$ROOT_PATH/$dateKey"
    private fun usersPath(dateKey: String) = "$ROOT_PATH/$dateKey/$USERS_PATH"
    private fun userPath(dateKey: String, uid: String) = "${usersPath(dateKey)}/$uid"
}

internal fun parseGharsLeaderboardEntries(value: Any?): List<GharsLeaderboardEntry> {
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
        .mapNotNull { (_, rawEntry) -> rawEntry.toGharsLeaderboardEntry() }
}

private fun Any?.toGharsLeaderboardEntry(): GharsLeaderboardEntry? {
    val entry = this as? Map<*, *> ?: return null
    val uid = entry[UID_KEY] as? String ?: return null
    return GharsLeaderboardEntry(
        uid = uid,
        countryCode = entry[COUNTRY_CODE_KEY] as? String ?: "",
        count = (entry[COUNT_KEY] as? Number)?.toInt() ?: 0,
        rank = (entry[RANK_KEY] as? Number)?.toInt() ?: 0,
        rankChange = entry[RANK_CHANGE_KEY] as? String ?: "same",
        nickname = entry[NICKNAME_KEY] as? String ?: "",
        streak = (entry[STREAK_KEY] as? Number)?.toInt() ?: 0,
        goldMedals = (entry[GOLD_MEDALS_KEY] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
        silverMedals = (entry[SILVER_MEDALS_KEY] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
        bronzeMedals = (entry[BRONZE_MEDALS_KEY] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
    )
}

private fun Any?.leaderboardContainerType(): String = when (this) {
    null -> "null"
    is Map<*, *> -> "map"
    is List<*> -> "list"
    else -> "other"
}
