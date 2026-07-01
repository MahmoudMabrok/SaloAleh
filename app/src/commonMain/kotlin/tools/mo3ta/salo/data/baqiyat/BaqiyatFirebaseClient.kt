package tools.mo3ta.salo.data.baqiyat

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.first
import tools.mo3ta.salo.domain.BaqiyatLeaderboardEntry

private const val ROOT_PATH = "baqiyat_saliha"
private const val PLAYERS_PATH = "players"
private const val UID_KEY = "uid"
private const val COUNT_KEY = "count"
private const val COUNTRY_CODE_KEY = "countryCode"
private const val NICKNAME_KEY = "nickname"
private const val UPDATED_AT_KEY = "updatedAt"

class BaqiyatFirebaseClient {

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
            Firebase.database.reference(playerPath(dateKey, uid)).setValue(
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
                onSuccess = { log.d { "writeUserDay[$dateKey/$uid] ok" } },
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

    /** Reads every player under [dateKey] and ranks them client-side by [BaqiyatLeaderboardEntry.count]. */
    suspend fun fetchLeaderboard(dateKey: String): Result<List<BaqiyatLeaderboardEntry>> {
        log.d { "fetchLeaderboard[$dateKey]" }
        return runCatching {
            val snapshot = Firebase.database.reference(playersPath(dateKey)).valueEvents.first()
            snapshot.children
                .mapNotNull { child ->
                    val uid = child.child(UID_KEY).value<String?>() ?: return@mapNotNull null
                    BaqiyatLeaderboardEntry(
                        uid = uid,
                        countryCode = child.child(COUNTRY_CODE_KEY).value<String?>() ?: "",
                        count = child.child(COUNT_KEY).value<Int?>() ?: 0,
                        rank = 0,
                        nickname = child.child(NICKNAME_KEY).value<String?>() ?: "",
                    )
                }
                .sortedByDescending { it.count }
                .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "fetchLeaderboard[$dateKey] ${it.size} entries" } },
                onFailure = { log.e(it) { "fetchLeaderboard[$dateKey] failed" } },
            )
        }
    }

    private fun playersPath(dateKey: String) = "$ROOT_PATH/$dateKey/$PLAYERS_PATH"
    private fun playerPath(dateKey: String, uid: String) = "${playersPath(dateKey)}/$uid"
}
