package tools.mo3ta.salo.domain

import kotlinx.coroutines.flow.Flow
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider

class MohamedLoversRepository(
    private val firebaseClient: MohamedLoversFirebaseApi,
    private val networkTimeProvider: NetworkTimeProvider,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
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

    // Recap
    fun markRecapShown(roundKey: String) = sessionStore.markRecapShown(roundKey)
    fun getRecapShownRound(): String? = sessionStore.getRecapShownRound()
    fun getPersonalBestRank(): Int = sessionStore.getPersonalBestRank()
    fun updatePersonalBestRank(rank: Int) = sessionStore.updatePersonalBestRank(rank)
    fun getLastRoundTaps(): Int = sessionStore.getLastRoundTaps()
    fun saveLastRoundTaps(taps: Int) = sessionStore.saveLastRoundTaps(taps)

    fun getOrSetInstallDate(today: kotlinx.datetime.LocalDate): String = sessionStore.getOrSetInstallDate(today)

    suspend fun fetchUserAchievements(uid: String): Result<Map<String, UserAchievement>> =
        firebaseClient.fetchUserAchievements(uid)

    suspend fun writeUserActivity(uid: String, today: kotlinx.datetime.LocalDate): Result<Unit> {
        val installDate = sessionStore.getOrSetInstallDate(today)
        val lastOpenDate = today.toString()
        return firebaseClient.writeUserActivity(uid, installDate, lastOpenDate)
    }
}
