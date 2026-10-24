package tools.mo3ta.salo.data.firebase

import kotlinx.coroutines.flow.Flow
import tools.mo3ta.salo.domain.AccountSnapshot
import tools.mo3ta.salo.domain.AppUpdateConfig
import tools.mo3ta.salo.domain.DailyBadgeAdjustment
import tools.mo3ta.salo.domain.FirebaseLeaderboard
import tools.mo3ta.salo.domain.HeroesBoard
import tools.mo3ta.salo.domain.MohamedLoversMedals
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.UserAchievement

data class ReferralStats(val referralCount: Int, val salawatTotal: Long)

interface MohamedLoversFirebaseApi {
    fun isConfigured(): Boolean
    suspend fun ensureSignedInAnonymously(): Result<String>
    /** Reads the remote app-update config; null when it is absent. */
    suspend fun fetchAppConfig(): Result<AppUpdateConfig?>
    fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>>
    suspend fun fetchRoundPlayerCount(roundKey: String): Result<Int>
    suspend fun fetchRoundTotal(roundKey: String): Result<Int>
    suspend fun fetchAllTimeTotal(): Result<Long>
    suspend fun fetchHeroes(): Result<HeroesBoard?>
    fun observeLeaderboard(roundKey: String, daily: Boolean = false): Flow<Result<FirebaseLeaderboard>>
    suspend fun fetchLiveLeaderboard(roundKey: String): Result<FirebaseLeaderboard>
    suspend fun incrementSession(roundKey: String, uid: String, delta: Int, countryCode: String, todayCount: Int, flushToken: String): Result<Unit>
    /**
     * Stamps the user node with the open dates and the build the user is running.
     * [appVersion] is the versionName (skipped when blank) and [appVersionCode] the integer
     * version code (skipped when non-positive, i.e. unavailable on this platform).
     */
    suspend fun writeUserActivity(
        uid: String,
        installDate: String,
        lastOpenDate: String,
        appVersion: String,
        appVersionCode: Int,
    ): Result<Unit>
    suspend fun writeFcmToken(uid: String, token: String): Result<Unit>
