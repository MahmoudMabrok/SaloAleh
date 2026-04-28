package tools.mo3ta.salo.domain

import kotlinx.coroutines.flow.Flow
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider

class MohamedLoversRepository(
    private val firebaseClient: MohamedLoversFirebaseClient,
    private val networkTimeProvider: NetworkTimeProvider,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
) {
    suspend fun bootstrap(): MohamedLoversBootstrap = MohamedLoversBootstrap(
        firebaseConfigured = firebaseClient.isConfigured(),
        countryCode = countryCodeProvider.get(),
        competitionWindow = networkTimeProvider.getCompetitionWindow(),
        pendingSession = sessionStore.getPendingSession(),
    )

    suspend fun ensureAnonymousUser(): Result<String> = firebaseClient.ensureSignedInAnonymously()

    fun observeTopPlayers(roundKey: String): Flow<Result<List<MohamedLoversPlayer>>> =
        firebaseClient.observeTopPlayers(roundKey)

    fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>> =
        firebaseClient.observeSelfPlayer(roundKey, uid)

    fun registerLocalTap(roundKey: String, delta: Int = 1): MohamedLoversPendingSession =
        sessionStore.incrementPendingClick(roundKey, delta)

    fun getPendingSession(): MohamedLoversPendingSession = sessionStore.getPendingSession()

    suspend fun flushPendingSession(
        countryCode: String,
        fallbackRoundKey: String? = null,
    ): Result<Unit> {
        val pending = sessionStore.getPendingSession()
        val roundKey = pending.roundKey?.takeIf { it.isNotBlank() }
            ?: fallbackRoundKey?.takeIf { it.isNotBlank() }
            ?: return Result.success(Unit)

        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }

        val result = firebaseClient.incrementSession(
            roundKey = roundKey,
            uid = uid,
            delta = pending.clickCount.coerceAtLeast(0),
            countryCode = countryCode,
        )

        result.onSuccess { if (pending.clickCount > 0) sessionStore.clearPendingSession() }
        return result
    }

    fun refreshNetworkTime() = networkTimeProvider.prime()
}
