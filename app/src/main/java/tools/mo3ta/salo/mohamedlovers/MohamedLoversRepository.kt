package com.elsharif.dailyseventy.domain.mohamedlovers

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MohamedLoversRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseClient: MohamedLoversFirebaseClient,
    private val networkTimeProvider: MohamedLoversNetworkTimeProvider,
    private val sessionStore: MohamedLoversSessionStore,
) {
    suspend fun bootstrap(): MohamedLoversBootstrap {
        return MohamedLoversBootstrap(
            firebaseConfigured = firebaseClient.isConfigured(),
            countryCode = resolveCountryCode(),
            competitionWindow = networkTimeProvider.getCompetitionWindow(),
            pendingSession = sessionStore.getPendingSession(),
        )
    }

    suspend fun ensureAnonymousUser(): Result<String> = firebaseClient.ensureSignedInAnonymously()

    fun observeTopPlayers(roundKey: String): Flow<Result<List<MohamedLoversPlayer>>> {
        return firebaseClient.observeTopPlayers(roundKey)
    }

    fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>> {
        return firebaseClient.observeSelfPlayer(roundKey, uid)
    }

    fun registerLocalTap(roundKey: String, delta: Int = 1): MohamedLoversPendingSession {
        return sessionStore.incrementPendingClick(roundKey, delta)
    }

    fun getPendingSession(): MohamedLoversPendingSession = sessionStore.getPendingSession()

    suspend fun flushPendingSession(
        countryCode: String,
        fallbackRoundKey: String? = null,
    ): Result<Unit> {
        val pending = sessionStore.getPendingSession()
        val roundKey = pending.roundKey?.takeIf { it.isNotBlank() }
            ?: fallbackRoundKey?.takeIf { it.isNotBlank() }

        if (roundKey == null) {
            Log.w(
                "TestTest",
                "MohamedLoversRepo: flush skipped, no roundKey (pending=${pending.roundKey}, fallback=$fallbackRoundKey). NTP not synced.",
            )
            return Result.success(Unit)
        }

        val uid = ensureAnonymousUser().getOrElse {
            Log.e("TestTest", "MohamedLoversRepo: anon auth failed during flush", it)
            return Result.failure(it)
        }

        val delta = pending.clickCount.coerceAtLeast(0)
        val result = firebaseClient.incrementSession(
            roundKey = roundKey,
            uid = uid,
            delta = delta,
            countryCode = countryCode,
        )

        result
            .onFailure { error ->
                Log.e(
                    "TestTest",
                    "MohamedLoversRepo: flush incrementSession failed roundKey=$roundKey uid=$uid delta=$delta",
                    error,
                )
            }
            .onSuccess {
                if (pending.clickCount > 0) {
                    sessionStore.clearPendingSession()
                }
            }

        return result
    }

    fun refreshNetworkTime() {
        networkTimeProvider.prime()
    }

    private fun resolveCountryCode(): String {
        val telephonyIso = runCatching {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val localeIso = context.resources.configuration.locales[0].country
            .takeIf { it.isNotBlank() }

        val resolved = (telephonyIso ?: localeIso)?.uppercase(Locale.ROOT)
        return resolved?.takeIf { it.length >= 2 } ?: MOHAMED_LOVERS_UNKNOWN_COUNTRY_CODE
    }
}
