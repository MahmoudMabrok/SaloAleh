package tools.mo3ta.salo.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.billing.ProductRegistry
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider

class MohamedLoversRepository(
    private val firebaseClient: MohamedLoversFirebaseApi,
    private val networkTimeProvider: NetworkTimeProvider,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
    private val premiumStore: PremiumStore,
) {
    suspend fun bootstrap(): MohamedLoversBootstrap {
        val window = networkTimeProvider.getCompetitionWindow()
        return MohamedLoversBootstrap(
            firebaseConfigured = firebaseClient.isConfigured(),
            countryCode = countryCodeProvider.get(),
            competitionWindow = window,
            pendingSession = sessionStore.getPendingSession(window.roundKey ?: ""),
        )
    }

    suspend fun ensureAnonymousUser(): Result<String> = firebaseClient.ensureSignedInAnonymously()

    fun observeLeaderboard(roundKey: String, daily: Boolean = false): Flow<Result<FirebaseLeaderboard>> =
        firebaseClient.observeLeaderboard(roundKey, daily)

    suspend fun fetchLiveLeaderboard(roundKey: String): Result<FirebaseLeaderboard> =
        firebaseClient.fetchLiveLeaderboard(roundKey)

    suspend fun fetchRoundTotal(roundKey: String): Result<Int> =
        firebaseClient.fetchRoundTotal(roundKey)

    suspend fun fetchRoundPlayerCount(roundKey: String): Result<Int> =
        firebaseClient.fetchRoundPlayerCount(roundKey)

    suspend fun fetchAllTimeTotal(): Result<Long> =
        firebaseClient.fetchAllTimeTotal()

    fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>> =
        firebaseClient.observeSelfPlayer(roundKey, uid)

    fun registerLocalTap(roundKey: String, delta: Int = 1): MohamedLoversPendingSession =
        sessionStore.incrementPendingClick(roundKey, delta)

    fun getPendingSession(roundKey: String): MohamedLoversPendingSession =
        sessionStore.getPendingSession(roundKey)

    fun clearAllPendingRounds() = sessionStore.clearAllPendingRounds()

    suspend fun resetPlayerScore(roundKey: String): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        sessionStore.clearPendingRound(roundKey)
        return firebaseClient.resetPlayerScore(roundKey, uid)
    }

    suspend fun setScoreMasked(roundKey: String, uid: String, masked: Boolean): Result<Unit> =
        firebaseClient.setScoreMasked(roundKey, uid, masked)

    suspend fun recordPurchase(productId: String): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        val productType = ProductRegistry.typeFor(productId).name
        val purchaseDate = Clock.System.todayIn(TimeZone.of("Africa/Cairo")).toString()
        return firebaseClient.writePurchaseMetadata(
            uid = uid,
            productId = productId,
            productType = productType,
            purchaseDate = purchaseDate,
        )
    }

    suspend fun setSupporter(supporter: Boolean): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        firebaseClient.writeSupporterStatus(uid, supporter)
        val roundKey = networkTimeProvider.getCompetitionWindow().roundKey
        if (roundKey != null) {
            firebaseClient.setSupporter(roundKey, uid, supporter)
        }
        return Result.success(Unit)
    }

    suspend fun writeDailyBadge(roundKey: String, badgeKey: String?): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.writeDailyBadge(roundKey, uid, badgeKey)
    }

    suspend fun setScoreMasked(masked: Boolean): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        val roundKey = networkTimeProvider.getCompetitionWindow().roundKey
            ?: return Result.failure(IllegalStateException("No active round"))
        // Bind the local mask flag to this round so it is cleared once the next round starts.
        premiumStore.scoreMaskedRoundKey = if (masked) roundKey else null
        return firebaseClient.setScoreMasked(roundKey, uid, masked)
    }

    suspend fun incrementExternalCount(roundKey: String, count: Int): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.incrementExternalCount(roundKey, uid, count)
    }

    suspend fun flushPendingSession(countryCode: String): Result<Unit> {
        val allPending = sessionStore.getAllPendingRounds()
        if (allPending.isEmpty()) return Result.success(Unit)

        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }

        var lastError: Throwable? = null
        for ((roundKey, count) in allPending) {
            val result = firebaseClient.incrementSession(
                roundKey = roundKey,
                uid = uid,
                delta = count,
                countryCode = countryCode,
            )
            result.onSuccess { sessionStore.decrementPendingClick(roundKey, count) }
                .onFailure { lastError = it }
        }
        return if (lastError != null) Result.failure(lastError!!) else Result.success(Unit)
    }

    fun refreshNetworkTime() = networkTimeProvider.prime()

    fun getPersonalBestRank(): Int = sessionStore.getPersonalBestRank()
    fun updatePersonalBestRank(rank: Int) = sessionStore.updatePersonalBestRank(rank)
    fun getLastRoundTaps(): Int = sessionStore.getLastRoundTaps()
    fun saveLastRoundTaps(taps: Int) = sessionStore.saveLastRoundTaps(taps)

    fun getOrSetInstallDate(today: kotlinx.datetime.LocalDate): String = sessionStore.getOrSetInstallDate(today)

    suspend fun fetchUserAchievements(uid: String): Result<Map<String, UserAchievement>> =
        firebaseClient.fetchUserAchievements(uid)

    suspend fun writeNickname(nickname: String): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        val roundKey = networkTimeProvider.getCompetitionWindow().roundKey
            ?: return Result.failure(IllegalStateException("No active round"))
        return firebaseClient.writeNickname(roundKey, uid, nickname)
    }

    suspend fun writeUserActivity(uid: String, today: kotlinx.datetime.LocalDate): Result<Unit> {
        val installDate = sessionStore.getOrSetInstallDate(today)
        val lastOpenDate = today.toString()
        return firebaseClient.writeUserActivity(uid, installDate, lastOpenDate)
    }

    /** Syncs the user's server-notification opt-in flags to RTDB so the cron scripts honour them. */
    suspend fun writeNotificationPrefs(
        uid: String,
        remindersEnabled: Boolean,
        leaderboardEnabled: Boolean,
    ): Result<Unit> = firebaseClient.writeNotificationPrefs(uid, remindersEnabled, leaderboardEnabled)
}
