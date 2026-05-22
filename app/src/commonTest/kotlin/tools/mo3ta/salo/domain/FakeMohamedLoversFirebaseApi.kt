package tools.mo3ta.salo.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi

open class FakeMohamedLoversFirebaseApi : MohamedLoversFirebaseApi {

    var incrementResult: Result<Unit> = Result.success(Unit)
    var signInResult: Result<String> = Result.success("fake-uid")
    val incrementCalls = mutableListOf<IncrementCall>()
    var incrementGate: CompletableDeferred<Unit>? = null
    var selfPlayerFlow: MutableSharedFlow<Result<MohamedLoversPlayer?>>? = null

    data class IncrementCall(val roundKey: String, val uid: String, val delta: Int, val countryCode: String)

    override fun isConfigured(): Boolean = true

    override suspend fun ensureSignedInAnonymously(): Result<String> = signInResult

    override fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>> =
        selfPlayerFlow ?: flowOf(Result.success(null))

    override suspend fun fetchRoundPlayerCount(roundKey: String): Result<Int> = Result.success(0)

    override suspend fun fetchRoundTotal(roundKey: String): Result<Int> = Result.success(0)

    override suspend fun fetchAllTimeTotal(): Result<Long> = Result.success(0L)

    override fun observeLeaderboard(roundKey: String, daily: Boolean): Flow<Result<FirebaseLeaderboard>> =
        flowOf(Result.success(FirebaseLeaderboard(emptyList(), false)))

    override suspend fun incrementSession(roundKey: String, uid: String, delta: Int, countryCode: String): Result<Unit> {
        incrementGate?.await()
        incrementCalls.add(IncrementCall(roundKey, uid, delta, countryCode))
        return incrementResult
    }

    override suspend fun writeUserActivity(uid: String, installDate: String, lastOpenDate: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun writeFcmToken(uid: String, token: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun fetchUserAchievements(uid: String): Result<Map<String, UserAchievement>> =
        Result.success(emptyMap())

    override suspend fun resetPlayerScore(roundKey: String, uid: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun setScoreMasked(roundKey: String, uid: String, masked: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun incrementExternalCount(roundKey: String, uid: String, count: Int): Result<Unit> =
        Result.success(Unit)

    override suspend fun setSupporter(roundKey: String, uid: String, supporter: Boolean): Result<Unit> =
        Result.success(Unit)
}
