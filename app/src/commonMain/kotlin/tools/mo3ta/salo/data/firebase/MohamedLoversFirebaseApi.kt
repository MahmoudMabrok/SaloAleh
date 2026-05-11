package tools.mo3ta.salo.data.firebase

import kotlinx.coroutines.flow.Flow
import tools.mo3ta.salo.domain.FirebaseLeaderboard
import tools.mo3ta.salo.domain.MohamedLoversPlayer

interface MohamedLoversFirebaseApi {
    fun isConfigured(): Boolean
    suspend fun ensureSignedInAnonymously(): Result<String>
    fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>>
    suspend fun fetchRoundPlayerCount(roundKey: String): Result<Int>
    suspend fun fetchRoundTotal(roundKey: String): Result<Int>
    suspend fun fetchAllTimeTotal(): Result<Long>
    fun observeLeaderboard(roundKey: String): Flow<Result<FirebaseLeaderboard>>
    suspend fun incrementSession(roundKey: String, uid: String, delta: Int, countryCode: String): Result<Unit>
    suspend fun writeUserActivity(uid: String, installDate: String, lastOpenDate: String): Result<Unit>
    suspend fun writeFcmToken(uid: String, token: String): Result<Unit>
    suspend fun fetchUserAchievements(uid: String): Result<Map<String, Pair<Int, Int?>>>
}
