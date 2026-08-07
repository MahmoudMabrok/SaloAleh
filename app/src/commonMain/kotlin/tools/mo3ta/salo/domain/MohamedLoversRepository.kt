package tools.mo3ta.salo.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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

    suspend fun fetchHeroes(): Result<HeroesBoard?> =
        firebaseClient.fetchHeroes()

    fun observeSelfPlayer(roundKey: String, uid: String): Flow<Result<MohamedLoversPlayer?>> =
        firebaseClient.observeSelfPlayer(roundKey, uid)

    fun registerLocalTap(roundKey: String, delta: Int = 1): MohamedLoversPendingSession =
        sessionStore.incrementPendingClick(roundKey, delta)

    fun getPendingSession(roundKey: String): MohamedLoversPendingSession =
        sessionStore.getPendingSession(roundKey)

    fun clearAllPendingRounds() = sessionStore.clearAllPendingRounds()

    fun decrementPendingClick(roundKey: String, delta: Int) =
        sessionStore.decrementPendingClick(roundKey, delta)

    /**
     * Lower the player's saved competition score by [amount] to correct a mistaken entry,
     * flooring at 0. Returns the reduction actually applied on the server.
     */
    suspend fun decrementScore(roundKey: String, amount: Int): Result<Int> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.decrementScore(roundKey, uid, amount)
    }

    suspend fun resetPlayerScore(roundKey: String): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        sessionStore.clearPendingRound(roundKey)
        return firebaseClient.resetPlayerScore(roundKey, uid)
    }

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

    suspend fun writeRoundStreak(roundKey: String, streak: Int): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.writeRoundStreak(roundKey, uid, streak)
    }

    suspend fun incrementExternalCount(roundKey: String, count: Int): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.incrementExternalCount(roundKey, uid, count)
    }

    /**
     * Record one external/manual salawat push under the player's `externalLog`, keyed by the Cairo
     * minute it was entered — every push, so the log is the complete trail of what a user claimed.
     * [count] is negative for a correction.
     *
     * [countsTowardDailyCap] also moves the server-side `externalDaily/{Cairo day}` ledger, the
     * mirror of the local manual-entry allowance. Manual entries (and their corrections) consume
     * that allowance; extension syncs do not, so they pass `false` and are audited only.
     */
    suspend fun appendExternalLog(
        roundKey: String,
        count: Int,
        at: Instant,
        countsTowardDailyCap: Boolean = false,
    ): Result<Unit> {
        if (!ExternalSalawatLog.shouldLog(count)) return Result.success(Unit)
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.appendExternalLog(
            roundKey = roundKey,
            uid = uid,
            timeKey = ExternalSalawatLog.entryKey(at),
            count = count,
            dayKey = if (countsTowardDailyCap) ExternalSalawatLog.dayKey(at) else null,
        )
    }

    /**
     * The manual-entry allowance already consumed on the Cairo day containing [at], as recorded on
     * the server. Read at startup so a reinstall — which wipes the local ledger — cannot hand the
     * user a fresh daily cap.
     */
    /**
     * Records one daily-badge score reconciliation under the user node, so devices that keep losing
     * their day count can be reviewed. [progress] is today's local count *before* the raise and
     * [badge] the server-published badge that triggered it. Record-only and fire-and-forget — a
     * failure here never affects the adjustment the user already saw.
     */
    suspend fun logDailyBadgeAdjustment(
        case: String,
        at: Instant,
        progress: Int,
        badge: DailyBadge,
    ): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.appendBadgeAdjustmentLog(
            uid = uid,
            timeKey = DailyBadgeAdjustmentLog.entryKey(at),
            adjustment = DailyBadgeAdjustment(
                case = case,
                atMs = at.toEpochMilliseconds(),
                progress = progress,
                badgeKey = badge.key,
                badgeValue = badge.threshold,
            ),
        )
    }

    suspend fun fetchExternalUsedToday(roundKey: String, at: Instant): Result<Int> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.fetchExternalDailyUsed(roundKey, uid, ExternalSalawatLog.dayKey(at))
    }

    /**
     * Pushes every pending round's score to Firebase, clamped to [allowance] — what is left of the
     * day's [MOHAMED_LOVERS_DAILY_PUSH_CAP]. Pending score above the allowance is **discarded, not
     * deferred**: the pending counter is cleared in full whether or not all of it was pushed, so the
     * locally-displayed score falls back in line with the remote one instead of retrying forever.
     *
     * [todayCount] is the absolute Cairo-day count published for the daily leaderboard; the caller
     * clamps it to the same cap before passing it in.
     */
    suspend fun flushPendingSession(
        countryCode: String,
        todayCount: Int = 0,
        allowance: Int = MOHAMED_LOVERS_DAILY_PUSH_CAP,
    ): Result<MohamedLoversFlushResult> {
        val allPending = sessionStore.getAllPendingRounds()
        if (allPending.isEmpty()) return Result.success(MohamedLoversFlushResult())

        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }

        var remaining = allowance.coerceAtLeast(0)
        var pushed = 0
        var discarded = 0
        var lastError: Throwable? = null
        for ((roundKey, count) in allPending) {
            val applied = SalawatDailyCap.allowedPush(requested = count, pushedToday = 0, cap = remaining)
            if (applied <= 0) {
                // The day's allowance is spent: this pending score can never be scored, so drop it
                // rather than leave it queued to inflate tomorrow's first flush.
                sessionStore.decrementPendingClick(roundKey, count)
                discarded += count
                continue
            }
            val result = firebaseClient.incrementSession(
                roundKey = roundKey,
                uid = uid,
                delta = applied,
                countryCode = countryCode,
                todayCount = todayCount,
            )
            result.onSuccess {
                sessionStore.decrementPendingClick(roundKey, count)
                remaining -= applied
                pushed += applied
                discarded += count - applied
            }.onFailure { lastError = it }
        }
        return if (lastError != null) Result.failure(lastError!!)
        else Result.success(MohamedLoversFlushResult(pushed = pushed, discarded = discarded))
    }

    fun refreshNetworkTime() = networkTimeProvider.prime()

    fun getPersonalBestRank(): Int = sessionStore.getPersonalBestRank()
    fun updatePersonalBestRank(rank: Int) = sessionStore.updatePersonalBestRank(rank)
    fun getLastRoundTaps(): Int = sessionStore.getLastRoundTaps()
    fun saveLastRoundTaps(taps: Int) = sessionStore.saveLastRoundTaps(taps)

    fun getOrSetInstallDate(today: kotlinx.datetime.LocalDate): String = sessionStore.getOrSetInstallDate(today)

    suspend fun fetchUserAchievements(uid: String): Result<Map<String, UserAchievement>> =
        firebaseClient.fetchUserAchievements(uid)

    /** Current user's cumulative podium medals from the server-authoritative user node. */
    suspend fun fetchSelfMedals(uid: String): Result<MohamedLoversMedals> =
        firebaseClient.fetchSelfMedals(uid)

    suspend fun writeNickname(nickname: String): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        val roundKey = networkTimeProvider.getCompetitionWindow().roundKey
            ?: return Result.failure(IllegalStateException("No active round"))
        return firebaseClient.writeNickname(roundKey, uid, nickname)
    }

    /**
     * Stamps the user node on app start: install/last-open dates plus the build being run
     * ([appVersion] versionName, [appVersionCode] version code), so the running version is
     * always current server-side.
     */
    suspend fun writeUserActivity(
        uid: String,
        today: kotlinx.datetime.LocalDate,
        appVersion: String,
        appVersionCode: Int,
    ): Result<Unit> {
        val installDate = sessionStore.getOrSetInstallDate(today)
        val lastOpenDate = today.toString()
        return firebaseClient.writeUserActivity(uid, installDate, lastOpenDate, appVersion, appVersionCode)
    }

    /** Syncs the user's server-notification opt-in flags to RTDB so the cron scripts honour them. */
    suspend fun writeNotificationPrefs(
        uid: String,
        remindersEnabled: Boolean,
        leaderboardEnabled: Boolean,
    ): Result<Unit> = firebaseClient.writeNotificationPrefs(uid, remindersEnabled, leaderboardEnabled)
}
