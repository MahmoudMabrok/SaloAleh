package tools.mo3ta.salo.data.tendays

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import tools.mo3ta.salo.data.firebase.FirestoreMirror
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore

data class TenDaysLeaderboardEntry(
    val uid: String,
    val totalScore: Int,
    val countryCode: String,
)

class TenDaysFirebaseClient(
    private val sessionStore: MohamedLoversSessionStore,
    private val mirror: FirestoreMirror,
) {

    private val log = Logger.withTag("TenDaysFirebase")

    fun observeLeaderboard(periodKey: String): Flow<Result<List<TenDaysLeaderboardEntry>>> =
        Firebase.database.reference("$ROOT/$periodKey/leaderboard")
            .valueEvents
            .map { snapshot ->
                runCatching {
                    snapshot.children.mapNotNull { child ->
                        val uid = child.child("uid").value<String?>() ?: return@mapNotNull null
                        val score = child.child("totalScore").value<Int?>() ?: 0
                        val country = child.child("countryCode").value<String?>() ?: ""
                        TenDaysLeaderboardEntry(uid, score, country)
                    }
                }
            }
            .catch { e ->
                log.e(e) { "observeLeaderboard error" }
                emit(Result.failure(e))
            }

    fun observePlayerCount(periodKey: String): Flow<Result<Int>> =
        Firebase.database.reference("$ROOT/$periodKey/playerCount")
            .valueEvents
            .map { snapshot ->
                runCatching { snapshot.value<Int?>() ?: 0 }
            }
            .catch { e ->
                log.e(e) { "observePlayerCount error" }
                emit(Result.failure(e))
            }

    fun observeSelfRank(periodKey: String, uid: String): Flow<Result<Int>> =
        Firebase.database.reference("$ROOT/$periodKey/players/$uid/rank")
            .valueEvents
            .map { snapshot ->
                runCatching { snapshot.value<Int?>() ?: 0 }
            }
            .catch { e ->
                log.e(e) { "observeSelfRank error" }
                emit(Result.failure(e))
            }

    suspend fun syncScore(
        periodKey: String,
        uid: String,
        totalScore: Int,
        countryCode: String,
    ): Result<Unit> = runCatching {
        val playerRef = Firebase.database.reference("$ROOT/$periodKey/players/$uid")
        playerRef.setValue(
            mapOf(
                "uid" to uid,
                "totalScore" to totalScore,
                "updatedAt" to ServerValue.TIMESTAMP,
                "countryCode" to countryCode,
            )
        )
        log.d { "syncScore[$periodKey/$uid]: $totalScore" }
        mirror.mirrorTenDaysScore(periodKey, uid, totalScore, countryCode)
    }

    private companion object {
        const val ROOT = "ten_days_dhul_hijjah"
    }
}
