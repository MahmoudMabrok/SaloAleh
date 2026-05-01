package tools.mo3ta.salo.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_TOP_LIMIT
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
import tools.mo3ta.salo.domain.MohamedLoversPlayer

class MohamedLoversFirebaseClient(private val sessionStore: MohamedLoversSessionStore) {

    fun isConfigured(): Boolean = runCatching { Firebase.database }.isSuccess

    suspend fun ensureSignedInAnonymously(): Result<String> =
        runCatching { sessionStore.getOrCreateUid() }

    fun observeTopPlayers(
        roundKey: String,
        limit: Int = MOHAMED_LOVERS_TOP_LIMIT,
    ): Flow<Result<List<MohamedLoversPlayer>>> =
        Firebase.database.reference(playersPath(roundKey))
            .orderByChild(TOTAL_COUNT_KEY)
            .limitToLast(limit)
            .valueEvents
            .map { snapshot -> runCatching { snapshot.children.mapNotNull { it.toPlayer() } } }

    fun observeSelfPlayer(
        roundKey: String,
        uid: String,
    ): Flow<Result<MohamedLoversPlayer?>> =
        Firebase.database.reference(playersPath(roundKey)).child(uid)
            .valueEvents
            .map { snapshot -> runCatching { snapshot.takeIf { it.exists }?.toPlayer() } }

    suspend fun incrementSession(
        roundKey: String,
        uid: String,
        delta: Int,
        countryCode: String,
    ): Result<Unit> = runCatching {
        val safeCode = countryCode.takeIf { it.length >= 2 } ?: MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
        Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
            mapOf(
                UID_KEY to uid,
                COUNTRY_CODE_KEY to safeCode,
                TOTAL_COUNT_KEY to ServerValue.increment(delta.toDouble()),
                UPDATED_AT_KEY to ServerValue.TIMESTAMP,
            )
        )
    }

    private fun playersPath(roundKey: String) = "$ROOT_PATH/$roundKey/$PLAYERS_PATH"

    private fun dev.gitlive.firebase.database.DataSnapshot.toPlayer(): MohamedLoversPlayer? {
        val map = value as? Map<*, *> ?: return null
        val uid = map[UID_KEY] as? String ?: key ?: return null
        return MohamedLoversPlayer(
            uid = uid,
            totalCount = (map[TOTAL_COUNT_KEY] as? Number)?.toInt() ?: 0,
            isWinner = map[IS_WINNER_KEY] as? Boolean ?: false,
            winnerCode = map[WINNER_CODE_KEY] as? String ?: "",
            countryCode = map[COUNTRY_CODE_KEY] as? String ?: "",
            updatedAt = (map[UPDATED_AT_KEY] as? Number)?.toLong() ?: 0L,
        )
    }

    private companion object {
        const val ROOT_PATH = "mohamed_lovers"
        const val PLAYERS_PATH = "players"
        const val UID_KEY = "uid"
        const val TOTAL_COUNT_KEY = "totalCount"
        const val IS_WINNER_KEY = "isWinner"
        const val WINNER_CODE_KEY = "winnerCode"
        const val COUNTRY_CODE_KEY = "countryCode"
        const val UPDATED_AT_KEY = "updatedAt"
    }
}
