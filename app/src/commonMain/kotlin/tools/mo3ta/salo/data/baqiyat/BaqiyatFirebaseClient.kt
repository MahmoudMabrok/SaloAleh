package tools.mo3ta.salo.data.baqiyat

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.first
import tools.mo3ta.salo.data.firebase.FirestoreMirror
import tools.mo3ta.salo.domain.BaqiyatDayStats
import tools.mo3ta.salo.domain.BaqiyatLeaderboardEntry

private const val ROOT_PATH = "baqiyat_saliha"
private const val PLAYERS_PATH = "players"
private const val LEADERBOARD_PATH = "leaderboard"
private const val UID_KEY = "uid"
private const val COUNT_KEY = "count"
private const val RANK_KEY = "rank"
private const val RANK_CHANGE_KEY = "rankChange"
private const val COUNTRY_CODE_KEY = "countryCode"
private const val NICKNAME_KEY = "nickname"
private const val UPDATED_AT_KEY = "updatedAt"
private const val PARTICIPANT_COUNT_KEY = "participantCount"
private const val TOTAL_TODAY_BAQIYAT_KEY = "totalTodayBaqiyat"

class BaqiyatFirebaseClient(private val mirror: FirestoreMirror) {

    private val log = Logger.withTag("BaqiyatFirebase")

    fun isConfigured(): Boolean = runCatching { Firebase.database }.isSuccess

    suspend fun writeUserDay(
        dateKey: String,
        uid: String,
        count: Int,
        countryCode: String,
        nickname: String = "",
    ): Result<Unit> {
        val safeNickname = nickname.trim().take(20)
        log.d { "writeUserDay[$dateKey/$uid] count=$count" }
        return runCatching {
            Firebase.database.reference(playerPath(dateKey, uid)).updateChildren(
                mapOf(
                    UID_KEY to uid,
                    COUNT_KEY to count.coerceAtLeast(0),
                    COUNTRY_CODE_KEY to countryCode,
                    NICKNAME_KEY to safeNickname,
                    UPDATED_AT_KEY to ServerValue.TIMESTAMP,
                ),
            )
        }.also { result ->
            result.fold(
                onSuccess = {
                    log.d { "writeUserDay[$dateKey/$uid] ok" }
                    mirror.mirrorBaqiyatUserDay(dateKey, uid, count, countryCode, safeNickname)
                },
                onFailure = { log.e(it) { "writeUserDay[$dateKey/$uid] failed" } },
            )
        }
    }

    suspend fun fetchUserCount(dateKey: String, uid: String): Result<Int?> {
        log.d { "fetchUserCount[$dateKey/$uid]" }
        return runCatching {
            Firebase.database.reference(playerPath(dateKey, uid))
                .child(COUNT_KEY)
                .valueEvents
                .first()
                .value<Int?>()
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchUserCount[$dateKey/$uid]=$it" } },
                onFailure = { log.e(it) { "fetchUserCount[$dateKey/$uid] failed" } },
            )
        }
    }

    suspend fun fetchDayStats(dateKey: String, uid: String): Result<BaqiyatDayStats> {
        log.d { "fetchDayStats[$dateKey/$uid]" }
        return runCatching {
            val dayRef = Firebase.database.reference(dayPath(dateKey))
            val rankSnapshot = dayRef.child(PLAYERS_PATH).child(uid).child(RANK_KEY)
                .valueEvents
                .first()
            val participantCountSnapshot = dayRef.child(PARTICIPANT_COUNT_KEY)
                .valueEvents
                .first()
            val totalTodaySnapshot = dayRef.child(TOTAL_TODAY_BAQIYAT_KEY)
                .valueEvents
                .first()

            BaqiyatDayStats(
                rank = (rankSnapshot.value as? Number)?.toInt() ?: 0,
                participantCount = (participantCountSnapshot.value as? Number)?.toInt() ?: 0,
                totalTodayBaqiyat = (totalTodaySnapshot.value as? Number)?.toInt() ?: 0,
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchDayStats[$dateKey/$uid] rank=${it.rank} participants=${it.participantCount}" } },
                onFailure = { log.e(it) { "fetchDayStats[$dateKey/$uid] failed" } },
            )
        }
    }

    suspend fun fetchLeaderboard(dateKey: String): Result<List<BaqiyatLeaderboardEntry>> {
        log.d { "fetchLeaderboard[$dateKey]" }
        return runCatching {
            val snap = Firebase.database.reference("$ROOT_PATH/$dateKey/$LEADERBOARD_PATH")
                .valueEvents
                .first()
            if (!snap.exists) return@runCatching emptyList()
            val rawValue = snap.value
            val entries = parseBaqiyatLeaderboardEntries(rawValue)
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
    private fun playersPath(dateKey: String) = "$ROOT_PATH/$dateKey/$PLAYERS_PATH"
    private fun playerPath(dateKey: String, uid: String) = "${playersPath(dateKey)}/$uid"
}

internal fun parseBaqiyatLeaderboardEntries(value: Any?): List<BaqiyatLeaderboardEntry> {
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
        .mapNotNull { (_, rawEntry) -> rawEntry.toBaqiyatLeaderboardEntry() }
}

private fun Any?.toBaqiyatLeaderboardEntry(): BaqiyatLeaderboardEntry? {
    val entry = this as? Map<*, *> ?: return null
    val uid = entry[UID_KEY] as? String ?: return null
    return BaqiyatLeaderboardEntry(
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
