package tools.mo3ta.salo.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.firebase.ReferralStats

open class FakeMohamedLoversFirebaseApi : MohamedLoversFirebaseApi {

    var incrementResult: Result<Unit> = Result.success(Unit)
    var signInResult: Result<String> = Result.success("fake-uid")
    val incrementCalls = mutableListOf<IncrementCall>()
    var incrementGate: CompletableDeferred<Unit>? = null
    var selfPlayerFlow: MutableSharedFlow<Result<MohamedLoversPlayer?>>? = null

    data class IncrementCall(val roundKey: String, val uid: String, val delta: Int, val countryCode: String, val todayCount: Int = 0)

    override fun isConfigured(): Boolean = true

    override suspend fun ensureSignedInAnonymously(): Result<String> = signInResult

    var appConfigResult: Result<AppUpdateConfig?> = Result.success(null)
    override suspend fun fetchAppConfig(): Result<AppUpdateConfig?> = appConfigResult

    override fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>> =
        selfPlayerFlow ?: flowOf(Result.success(null))

    override suspend fun fetchRoundPlayerCount(roundKey: String): Result<Int> = Result.success(0)

    override suspend fun fetchRoundTotal(roundKey: String): Result<Int> = Result.success(0)

    override suspend fun fetchAllTimeTotal(): Result<Long> = Result.success(0L)

    var heroesResult: Result<HeroesBoard?> = Result.success(null)
    override suspend fun fetchHeroes(): Result<HeroesBoard?> = heroesResult

    override fun observeLeaderboard(roundKey: String, daily: Boolean): Flow<Result<FirebaseLeaderboard>> =
        flowOf(Result.success(FirebaseLeaderboard(emptyList(), false)))

    override suspend fun incrementSession(roundKey: String, uid: String, delta: Int, countryCode: String, todayCount: Int): Result<Unit> {
        incrementGate?.await()
        incrementCalls.add(IncrementCall(roundKey, uid, delta, countryCode, todayCount))
        return incrementResult
    }

    override suspend fun writeUserActivity(uid: String, installDate: String, lastOpenDate: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun writeFcmToken(uid: String, token: String): Result<Unit> =
        Result.success(Unit)

    data class NotificationPrefsCall(
        val uid: String,
        val remindersEnabled: Boolean,
        val leaderboardEnabled: Boolean,
    )

    val notificationPrefsCalls = mutableListOf<NotificationPrefsCall>()

    override suspend fun writeNotificationPrefs(
        uid: String,
        remindersEnabled: Boolean,
        leaderboardEnabled: Boolean,
    ): Result<Unit> {
        notificationPrefsCalls.add(NotificationPrefsCall(uid, remindersEnabled, leaderboardEnabled))
        return Result.success(Unit)
    }

    override suspend fun fetchUserAchievements(uid: String): Result<Map<String, UserAchievement>> =
        Result.success(emptyMap())

    var selfMedalsResult: Result<MohamedLoversMedals> = Result.success(MohamedLoversMedals())
    override suspend fun fetchSelfMedals(uid: String): Result<MohamedLoversMedals> = selfMedalsResult

    override suspend fun resetPlayerScore(roundKey: String, uid: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun setScoreMasked(roundKey: String, uid: String, masked: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun incrementExternalCount(roundKey: String, uid: String, count: Int): Result<Unit> =
        Result.success(Unit)

    data class DecrementScoreCall(val roundKey: String, val uid: String, val amount: Int)

    val decrementScoreCalls = mutableListOf<DecrementScoreCall>()

    override suspend fun decrementScore(roundKey: String, uid: String, amount: Int): Result<Int> {
        decrementScoreCalls.add(DecrementScoreCall(roundKey, uid, amount))
        return Result.success(amount.coerceAtLeast(0))
    }

    override suspend fun setSupporter(roundKey: String, uid: String, supporter: Boolean): Result<Unit> =
        Result.success(Unit)

    data class WriteDailyBadgeCall(val roundKey: String, val uid: String, val badgeKey: String?)

    val writeDailyBadgeCalls = mutableListOf<WriteDailyBadgeCall>()
    var writeDailyBadgeResult: Result<Unit> = Result.success(Unit)

    override suspend fun writeDailyBadge(roundKey: String, uid: String, badgeKey: String?): Result<Unit> {
        writeDailyBadgeCalls.add(WriteDailyBadgeCall(roundKey, uid, badgeKey))
        return writeDailyBadgeResult
    }

    override suspend fun writeRoundStreak(roundKey: String, uid: String, streak: Int): Result<Unit> =
        Result.success(Unit)

    override suspend fun fetchLiveLeaderboard(roundKey: String): Result<FirebaseLeaderboard> =
        Result.success(FirebaseLeaderboard(emptyList(), false))

    override suspend fun writeSupporterStatus(uid: String, supporter: Boolean): Result<Unit> =
        Result.success(Unit)

    data class PurchaseCall(
        val uid: String,
        val productId: String,
        val productType: String,
        val purchaseDate: String,
    )

    val purchaseCalls = mutableListOf<PurchaseCall>()

    override suspend fun writePurchaseMetadata(
        uid: String,
        productId: String,
        productType: String,
        purchaseDate: String,
    ): Result<Unit> {
        purchaseCalls.add(PurchaseCall(uid, productId, productType, purchaseDate))
        return Result.success(Unit)
    }

    override suspend fun writeNickname(roundKey: String, uid: String, nickname: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun writeReferralCode(uid: String, code: String): Result<Unit> = Result.success(Unit)

    override suspend fun lookupReferralCode(code: String): Result<String?> = Result.success(null)

    override suspend fun applyReferral(referrerUid: String, referredUid: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun fetchReferralStats(code: String): Result<ReferralStats> =
        Result.success(ReferralStats(referralCount = 0, salawatTotal = 0L))
}
