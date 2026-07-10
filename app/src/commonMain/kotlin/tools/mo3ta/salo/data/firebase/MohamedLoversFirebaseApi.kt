package tools.mo3ta.salo.data.firebase

import kotlinx.coroutines.flow.Flow
import tools.mo3ta.salo.domain.FirebaseLeaderboard
import tools.mo3ta.salo.domain.HeroesBoard
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.UserAchievement

data class ReferralStats(val referralCount: Int, val salawatTotal: Long)

interface MohamedLoversFirebaseApi {
    fun isConfigured(): Boolean
    suspend fun ensureSignedInAnonymously(): Result<String>
    fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>>
    suspend fun fetchRoundPlayerCount(roundKey: String): Result<Int>
    suspend fun fetchRoundTotal(roundKey: String): Result<Int>
    suspend fun fetchAllTimeTotal(): Result<Long>
    suspend fun fetchHeroes(): Result<HeroesBoard?>
    fun observeLeaderboard(roundKey: String, daily: Boolean = false): Flow<Result<FirebaseLeaderboard>>
    suspend fun fetchLiveLeaderboard(roundKey: String): Result<FirebaseLeaderboard>
    suspend fun incrementSession(roundKey: String, uid: String, delta: Int, countryCode: String): Result<Unit>
    suspend fun writeUserActivity(uid: String, installDate: String, lastOpenDate: String): Result<Unit>
    suspend fun writeFcmToken(uid: String, token: String): Result<Unit>
    suspend fun writeNotificationPrefs(
        uid: String,
        remindersEnabled: Boolean,
        leaderboardEnabled: Boolean,
    ): Result<Unit>
    suspend fun fetchUserAchievements(uid: String): Result<Map<String, UserAchievement>>
    suspend fun incrementExternalCount(roundKey: String, uid: String, count: Int): Result<Unit>
    suspend fun resetPlayerScore(roundKey: String, uid: String): Result<Unit>
    suspend fun setScoreMasked(roundKey: String, uid: String, masked: Boolean): Result<Unit>
    suspend fun setSupporter(roundKey: String, uid: String, supporter: Boolean): Result<Unit>
    suspend fun writeDailyBadge(roundKey: String, uid: String, badgeKey: String?): Result<Unit>
    suspend fun writeRoundStreak(roundKey: String, uid: String, streak: Int): Result<Unit>
    suspend fun writeSupporterStatus(uid: String, supporter: Boolean): Result<Unit>
    suspend fun writePurchaseMetadata(
        uid: String,
        productId: String,
        productType: String,
        purchaseDate: String,
    ): Result<Unit>
    suspend fun writeNickname(roundKey: String, uid: String, nickname: String): Result<Unit>
    suspend fun writeReferralCode(uid: String, code: String): Result<Unit>
    suspend fun lookupReferralCode(code: String): Result<String?>
    suspend fun applyReferral(referrerUid: String, referredUid: String): Result<Unit>
    suspend fun fetchReferralStats(code: String): Result<ReferralStats>
}
