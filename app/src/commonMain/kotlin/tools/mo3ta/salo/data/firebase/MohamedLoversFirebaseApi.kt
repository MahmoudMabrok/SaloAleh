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
    suspend fun incrementSession(roundKey: String, uid: String, delta: Int, countryCode: String, todayCount: Int): Result<Unit>
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
    suspend fun writeNotificationPrefs(
        uid: String,
        remindersEnabled: Boolean,
        leaderboardEnabled: Boolean,
    ): Result<Unit>
    /**
     * Read everything a restore needs about [uid] in one pass, or null when the account does not
     * exist. Reads only client-readable paths (the round player node, achievements, installDate).
     */
    suspend fun fetchAccountSnapshot(uid: String, roundKey: String): Result<AccountSnapshot?>
    suspend fun fetchUserAchievements(uid: String): Result<Map<String, UserAchievement>>
    /** Reads the current user's cumulative podium medals from `users/{uid}/medals`. */
    suspend fun fetchSelfMedals(uid: String): Result<MohamedLoversMedals>
    suspend fun incrementExternalCount(roundKey: String, uid: String, count: Int): Result<Unit>
    /**
     * Append one audit entry for an external/manual batch at `players/{uid}/externalLog/{timeKey}`.
     * Entries accumulate, so two batches landing in the same minute add up instead of overwriting
     * each other; [count] is negative for a correction.
     *
     * A non-null [dayKey] additionally moves `players/{uid}/externalDaily/{dayKey}` by the same
     * amount — the server-side mirror of the local per-Cairo-day manual allowance ledger. Pass null
     * for pushes that do not consume that allowance (extension syncs), so they are audited only.
     */
    suspend fun appendExternalLog(
        roundKey: String,
        uid: String,
        timeKey: String,
        count: Int,
        dayKey: String?,
    ): Result<Unit>

    /** Reads the manual allowance already consumed on [dayKey]; 0 when the node is absent. */
    suspend fun fetchExternalDailyUsed(roundKey: String, uid: String, dayKey: String): Result<Int>

    /**
     * Records one daily-badge reconciliation at `users/{uid}/badgeAdjustments/{timeKey}`, so a
     * device that repeatedly loses its day count can be spotted. Record-only; see
     * [tools.mo3ta.salo.domain.DailyBadgeAdjustmentLog].
     */
    suspend fun appendBadgeAdjustmentLog(
        uid: String,
        timeKey: String,
        adjustment: DailyBadgeAdjustment,
    ): Result<Unit>
    /**
     * Lower the player's saved competition score by [amount] to correct a mistaken entry.
     * The score is floored at 0 (never negative). Returns the reduction actually applied.
     */
    suspend fun decrementScore(roundKey: String, uid: String, amount: Int): Result<Int>
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
