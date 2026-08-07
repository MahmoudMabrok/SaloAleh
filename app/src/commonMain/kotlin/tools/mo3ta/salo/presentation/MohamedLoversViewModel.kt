package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.time.computeFinalMinutesTick
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.engagement.RoundStreakStore
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.heart.HEART_DECAY_INTERVAL_MS
import tools.mo3ta.salo.data.heart.HEART_LOW_THRESHOLD
import tools.mo3ta.salo.data.heart.HEART_TAP_BONUS
import tools.mo3ta.salo.data.heart.HeartStore
import tools.mo3ta.salo.data.heart.lastHeartResetBoundary
import tools.mo3ta.salo.data.heart.settleHeart
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.billing.PremiumFeature
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.DailyBadge
import tools.mo3ta.salo.domain.DailyBadgeAdjustmentLog
import tools.mo3ta.salo.domain.FirebaseLeaderboard
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow
import tools.mo3ta.salo.domain.MohamedLoversMedals
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_DAILY_PUSH_CAP
import tools.mo3ta.salo.domain.SalawatDailyCap
import tools.mo3ta.salo.domain.SalawatManualCap
import tools.mo3ta.salo.domain.buildMohamedLoversDisplayTag
import tools.mo3ta.salo.ui.getAppVersion
import tools.mo3ta.salo.ui.getAppVersionCode
import tools.mo3ta.salo.ui.setLeaderboardTopicSubscription

class MohamedLoversViewModel(
    private val repository: MohamedLoversRepository,
    private val engagementStore: EngagementStore,
    private val hadithStore: DailyHadithStore,
    private val dailyGoalStore: DailyGoalStore,
    private val roundStreakStore: RoundStreakStore,
    private val settingsStore: NotificationSettingsStore,
    private val sessionStore: MohamedLoversSessionStore,
    private val premiumStore: PremiumStore,
    private val heartStore: HeartStore,
    private val startTimers: Boolean = true,
) : ViewModel() {

    private val _state = MutableStateFlow(MohamedLoversUiState())
    val state: StateFlow<MohamedLoversUiState> = _state.asStateFlow()

    private val flushMutex = Mutex()
    private var selfJob: Job? = null
    private var leaderboardJob: Job? = null
    private var leaderboardModeSwitchJob: Job? = null
    private var remoteLeaderboard: FirebaseLeaderboard = FirebaseLeaderboard(emptyList(), false)
    private var remoteSelfPlayer: MohamedLoversPlayer? = null
    // Server-authoritative podium medals for the current user, fetched once per uid.
    // Medals live on the user node (not the player node) and are only attached to the
    // server-populated top-N leaderboard entries, so the self row needs them separately
    // to render when the user is outside the top-N. Null until the first fetch resolves.
    private var selfMedals: MohamedLoversMedals? = null
    private var selfMedalsFetchedForUid: String? = null
    private var authUid: String? = null
    private var achievementsFetchedFromRtdb = false
    private var currentWindow: MohamedLoversCompetitionWindow = MohamedLoversCompetitionWindow()
    private var inFlightFlush = 0
    private var finalMinutesJob: Job? = null
    private var lastProjectedRank: Int = 0
    private var overtakeCooldownUntil: Long = 0L
    private var rankMovementShown: Boolean = false
    private var sawLeaderboardLive: Boolean = false

    init {
        _state.update {
            it.copy(
                showHadithDialog = hadithStore.showOnStartup,
                isUsingDailyLeaderboard = settingsStore.useDailyLeaderboard,
                showDailyLeaderboardPromo = !settingsStore.dailyLeaderboardPromoShown,
            )
        }
        refresh()
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        if (engagementStore.shouldShowGraceWarning(today)) {
            _state.update { it.copy(showGraceWarning = true) }
        }
        val todayProgress = dailyGoalStore.todayProgress(today)
        _state.update {
            it.copy(
                dailyGoalTarget = dailyGoalStore.todayTarget(today),
                dailyGoalProgress = todayProgress,
                currentDailyBadge = DailyBadge.fromTapCount(todayProgress)?.key,
                manualRemaining = manualRemainingNow(today),
                todayCount = todayProgress,
            )
        }
        settleHeartDecay()
        if (startTimers) {
//            viewModelScope.launch {
//                delay(90_000L)
//                refresh()
////            delay(5*60_000L)
////            refresh()
//            }
            viewModelScope.launch {
                while (isActive) {
                    val ts = sessionStore.getLastSalawatTimestamp()
                    val elapsed = if (ts > 0L) {
                        (Clock.System.now().toEpochMilliseconds() - ts) / 60_000L
                    } else null
                    _state.update { it.copy(lastSalawatElapsedMinutes = elapsed) }
                    delay(60_000L)
                }
            }
            viewModelScope.launch {
                while (isActive) {
                    settleHeartDecay()
                    delay(HEART_DECAY_INTERVAL_MS)
                }
            }
        }
    }

    fun dismissHadithDialog() = _state.update { it.copy(showHadithDialog = false) }

    fun refresh() {
        repository.refreshNetworkTime()
        selfJob?.cancel()
        leaderboardJob?.cancel()
        remoteLeaderboard = FirebaseLeaderboard(emptyList(), false)
        remoteSelfPlayer = null
        authUid = null

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true, isRefreshing = true, error = null,
                    topPlayers = emptyList(), selfEntry = null, selfInTop = false,
                    winnerCode = "", syncedTotal = 0,
                )
            }

            val bootstrap = repository.bootstrap()
            currentWindow = bootstrap.competitionWindow

            // Score masking is scoped to a single round — clear it once a new round has started.
            bootstrap.competitionWindow.roundKey?.let { premiumStore.clearScoreMaskOnNewRound(it) }

            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    countryCode = bootstrap.countryCode,
                    firebaseConfigured = bootstrap.firebaseConfigured,
                    roundKey = bootstrap.competitionWindow.roundKey,
                    roundEndLabel = bootstrap.competitionWindow.roundEnd?.formatDisplay().orEmpty(),
                    roundEndInstant = bootstrap.competitionWindow.roundEnd,
                    networkTimeLabel = bootstrap.competitionWindow.networkNow?.formatDisplay().orEmpty(),
                    status = resolveStatus(bootstrap.firebaseConfigured, bootstrap.competitionWindow),
                    canCount = bootstrap.competitionWindow.networkNow != null,
                    sessionClicks = bootstrap.pendingSession.clickCount,
                    roundStreak = bootstrap.competitionWindow.roundKey?.let { rk ->
                        roundStreakStore.getCurrentStreak(rk, Clock.System.todayIn(TimeZone.of("Africa/Cairo")))
                    } ?: 0,
                    error = null,
                )
            }

            flushPendingSession()
            connectToLeaderboardIfPossible()
            startFinalMinutesTimer()
        }
    }

    fun dismissNewRoundCountdown() {
        _state.update { it.copy(showNewRoundCountdown = false) }
    }

    private fun startFinalMinutesTimer() {
        finalMinutesJob?.cancel()
        val roundEnd = _state.value.roundEndInstant ?: return
        finalMinutesJob = viewModelScope.launch {
            while (isActive) {
                val remaining = (roundEnd - Clock.System.now()).inWholeSeconds
                val tick = computeFinalMinutesTick(remaining)
                if (tick.shouldFlush) flushPendingSession()
                if (tick.showNewRound) {
                    _state.update { it.copy(showNewRoundCountdown = true) }
                    delay(3_000)
                    refresh()
                    break
                }
                delay(tick.nextDelayMillis)
            }
        }
    }

    fun onCountClick() {
        val current = state.value
        val roundKey = current.roundKey ?: return
        if (!current.canCount) return

        val nowMs = Clock.System.now().toEpochMilliseconds()
        sessionStore.saveLastSalawatTimestamp(nowMs)
        val heart = addHeartTap(nowMs)
        val pending = repository.registerLocalTap(roundKey, 1)
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        val wasComplete = dailyGoalStore.isGoalComplete(today)
        dailyGoalStore.recordTap(today, 1)
        val isNowComplete = dailyGoalStore.isGoalComplete(today)
        val streakResult = roundStreakStore.recordActivity(roundKey, today)
        val todayStr = today.toString()
        // The daily-goal tap progress is the single source of truth for today's competition count:
        // it drives the rank strip and daily badge, and is what we publish for the daily leaderboard.
        val rawTaps = dailyGoalStore.todayProgress(today)
        val badge = DailyBadge.fromTapCount(rawTaps)
        val lastMilestone = sessionStore.getLastMilestoneLevel(todayStr)
        var milestoneThreshold: Int? = null
        var milestoneBadgeKey: String? = null
        if (badge != null && badge.threshold > lastMilestone) {
            milestoneThreshold = badge.threshold
            milestoneBadgeKey = badge.key
            // Local celebration guard advances immediately (fires the milestone dialog once).
            sessionStore.saveLastMilestoneLevel(todayStr, badge.threshold)
            // The server badge is NOT written here. Flushing pushes the pending score first and
            // then reconciles the badge (see flushPendingSession -> publishDailyBadgeIfChanged), so
            // the badge never leads the score on the server and a failed publish is retried on the
            // next flush instead of being lost.
            flushPendingSession()
        }
        _state.update {
            it.copy(
                sessionClicks = pending.clickCount,
                todayCount = rawTaps,
                error = null,
                dailyGoalProgress = rawTaps,
                dailyGoalJustCompleted = !wasComplete && isNowComplete,
                milestoneThreshold = milestoneThreshold ?: it.milestoneThreshold,
                milestoneBadgeKey = milestoneBadgeKey ?: it.milestoneBadgeKey,
                currentDailyBadge = badge?.key ?: it.currentDailyBadge,
                lastSalawatElapsedMinutes = 0L,
                heartScore = heart.first,
                showHeartRefillNudge = shouldShowHeartRefillNudge(heart.first, heart.second),
                roundStreak = streakResult.currentStreak,
                roundStreakCelebration = streakResult.newlyEarnedBadge ?: it.roundStreakCelebration,
            )
        }
        publishRoundStreak(roundKey, streakResult.currentStreak, current.roundStreak)
        applyLeaderboard()
    }

    fun dismissRoundStreakCelebration() {
        _state.update { it.copy(roundStreakCelebration = null) }
    }

    /**
     * Publishes the round streak to the player's Firebase record so it renders next to
     * their name on the leaderboard. Fire-and-forget; only writes when the value changed
     * (streak is idempotent within a day) to avoid a network write on every tap.
     */
    private fun publishRoundStreak(roundKey: String, streak: Int, previous: Int) {
        if (streak == previous || !state.value.firebaseConfigured) return
        viewModelScope.launch { repository.writeRoundStreak(roundKey, streak) }
    }

    fun flushPendingSession() {
        viewModelScope.launch {
            flushMutex.withLock {
                val roundKey = state.value.roundKey
                if (!state.value.firebaseConfigured) {
                    _state.update { it.copy(isSavingSession = false) }
                    applyLeaderboard()
                    return@withLock
                }
                if (roundKey.isNullOrBlank()) {
                    _state.update { it.copy(isSavingSession = false) }
                    applyLeaderboard()
                    return@withLock
                }

                inFlightFlush = state.value.sessionClicks
                _state.update { it.copy(isSavingSession = true, error = null) }

                val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
                // Last gate before the network write: whatever is pending is clamped to what is
                // left of the day's cap, and the published day count is clamped to the cap too so
                // the daily leaderboard can never rank a score the player was not allowed to push.
                val allowance = sessionStore.dailyPushRemaining(today)
                val result = repository.flushPendingSession(
                    countryCode = state.value.countryCode,
                    todayCount = dailyGoalStore.todayProgress(today).coerceAtMost(MOHAMED_LOVERS_DAILY_PUSH_CAP),
                    allowance = allowance,
                )
                val flush = result.getOrNull()
                if (flush != null && flush.pushed > 0) sessionStore.recordDailyPush(today, flush.pushed)
                val cappedTodayCount = if (flush != null && flush.discarded > 0) {
                    // "Reset the local number to match the remote one": the discarded salawat never
                    // scored, so today's local count is pulled back to the cap as well, keeping the
                    // self row, the badge and the published daily count on the same number.
                    dailyGoalStore.clampTodayProgress(today, MOHAMED_LOVERS_DAILY_PUSH_CAP)
                } else null
                val latestPending = repository.getPendingSession(roundKey)
                inFlightFlush = 0

                _state.update {
                    it.copy(
                        isSavingSession = false,
                        sessionClicks = latestPending.clickCount,
                        todayCount = cappedTodayCount ?: it.todayCount,
                        dailyGoalProgress = cappedTodayCount ?: it.dailyGoalProgress,
                        dailyCapDiscarded = flush?.discarded?.takeIf { d -> d > 0 } ?: it.dailyCapDiscarded,
                        error = result.exceptionOrNull()?.message
                            ?.takeIf { msg -> msg.isNotBlank() }
                            ?.let(MohamedLoversError::Raw),
                    )
                }
                applyLeaderboard()
                // Reconcile the server daily badge only after the score reached the server, so the
                // badge never leads the score. On a failed flush the score didn't move, so leave
                // the badge alone.
                if (result.isSuccess) publishDailyBadgeIfChanged(roundKey)
            }
        }
    }

    /**
     * Publishes the current local daily badge to the player's Firebase record, but only once the
     * score has been flushed (this is called from [flushPendingSession] after the score write). The
     * published level is persisted only on write success, so a failed publish is transparently
     * retried on the next flush rather than being dropped. Never lowers the badge: the daily tap
     * count only grows within a Cairo day and the guard resets with the date.
     */
    private fun publishDailyBadgeIfChanged(roundKey: String) {
        if (!state.value.firebaseConfigured) return
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        val todayStr = today.toString()
        val badge = DailyBadge.fromTapCount(dailyGoalStore.todayProgress(today)) ?: return
        if (badge.threshold <= sessionStore.getLastPublishedBadgeLevel(todayStr)) return
        viewModelScope.launch {
            repository.writeDailyBadge(roundKey, badge.key)
                .onSuccess { sessionStore.saveLastPublishedBadgeLevel(todayStr, badge.threshold) }
        }
    }

    fun refreshSessionClicks() {
        settleHeartDecay()
        val roundKey = state.value.roundKey ?: return
        val pending = repository.getPendingSession(roundKey)
        _state.update { it.copy(sessionClicks = pending.clickCount) }
        applyLeaderboard()
    }

    /**
     * Called on app resume. The background final-minutes timer can miss a round
     * transition while the process is suspended, so if the previously known round
     * has already ended, do a full [refresh] (new round key, fresh listeners)
     * instead of just re-reading pending taps for the stale round.
     */
    fun onAppResumed() {
        settleHeartDecay()
        val roundEnd = state.value.roundEndInstant
        if (roundEnd != null && Clock.System.now() >= roundEnd) {
            refresh()
        } else {
            refreshSessionClicks()
        }
    }

    fun applyExtensionScore(round: String, count: Int) {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val heart = addHeartTap(nowMs, count)
        repository.registerLocalTap(round, count)
        val pending = repository.getPendingSession(round)
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        val prevStreak = state.value.roundStreak
        val streakResult = roundStreakStore.recordActivity(round, today)
        // Extension salawat count toward today's competition total (and thus the daily leaderboard,
        // which now publishes the daily-goal progress) just like taps and manual entries do.
        dailyGoalStore.recordTap(today, count)
        val todayTotal = dailyGoalStore.todayProgress(today)
        _state.update {
            it.copy(
                sessionClicks = pending.clickCount,
                todayCount = todayTotal,
                dailyGoalProgress = todayTotal,
                heartScore = heart.first,
                showHeartRefillNudge = shouldShowHeartRefillNudge(heart.first, heart.second),
                roundStreak = streakResult.currentStreak,
                roundStreakCelebration = streakResult.newlyEarnedBadge ?: it.roundStreakCelebration,
            )
        }
        publishRoundStreak(round, streakResult.currentStreak, prevStreak)
        applyLeaderboard()
        flushPendingSession()
        // Extension batches are external salawat too, so every one leaves the same audit entry.
        // They do not consume the manual-entry allowance, so the daily ledger is left alone.
        viewModelScope.launch {
            repository.appendExternalLog(round, count, Instant.fromEpochMilliseconds(nowMs))
        }
    }

    fun showManualSalawatSheet() {
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        _state.update {
            it.copy(
                showManualSalawatSheet = true,
                manualRemaining = manualRemainingNow(today),
            )
        }
    }

    fun dismissManualSalawatSheet() {
        _state.update { it.copy(showManualSalawatSheet = false) }
    }

    /** Cairo install day, falling back to today when unset/unparseable. */
    private fun installDate(today: LocalDate): LocalDate =
        runCatching { LocalDate.parse(sessionStore.getOrSetInstallDate(today)) }.getOrDefault(today)

    /**
     * The manual ("record external") allowance for [today] under the gradual new-user ramp: 1,000 on
     * install day climbing to the permanent cap by day 10. Never above the permanent cap. A round
     * streak of [SalawatManualCap.STREAK_CAP_UNLOCK]+ skips the ramp and grants the full cap early.
     */
    private fun manualDailyCap(today: LocalDate): Int =
        SalawatManualCap.dailyCap(today, installDate(today), state.value.roundStreak)

    /**
     * Manual allowance that can actually be scored right now: the manual-entry cap, further limited
     * by what is left of the day's competition push cap. Without the second limit the sheet would
     * offer allowance for salawat that the flush would immediately discard.
     */
    private fun manualRemainingNow(today: LocalDate): Int = minOf(
        sessionStore.manualRemainingToday(today, manualDailyCap(today)),
        sessionStore.dailyPushRemaining(today),
    )

    fun submitManualSalawat(count: Int) {
        val roundKey = state.value.roundKey ?: return
        if (count <= 0) return
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        // Clamp to the day's remaining external-entry allowance so a single manual batch can't
        // flood the competition score. Regular taps are uncapped and never touch this ledger.
        val cap = manualDailyCap(today)
        // Clamp to what the day's push cap still allows *before* touching the manual ledger, so the
        // ledger only ever records salawat that can actually reach the server.
        val pushable = count.coerceAtMost(sessionStore.dailyPushRemaining(today))
        val applied = if (pushable > 0) sessionStore.recordManualEntry(today, pushable, cap) else 0
        if (applied <= 0) {
            _state.update {
                it.copy(
                    showManualSalawatSheet = false,
                    manualRemaining = 0,
                    dailyCapDiscarded = if (pushable <= 0) count else it.dailyCapDiscarded,
                )
            }
            return
        }
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val heart = addHeartTap(nowMs, applied)
        repository.registerLocalTap(roundKey, applied)
        val pending = repository.getPendingSession(roundKey)
        dailyGoalStore.recordTap(today, applied)
        val prevStreak = state.value.roundStreak
        val streakResult = roundStreakStore.recordActivity(roundKey, today)
        val todayTotal = dailyGoalStore.todayProgress(today)
        _state.update {
            it.copy(
                sessionClicks = pending.clickCount,
                todayCount = todayTotal,
                showManualSalawatSheet = false,
                isSubmittingManualSalawat = true,
                dailyGoalProgress = todayTotal,
                lastSalawatElapsedMinutes = 0L,
                heartScore = heart.first,
                showHeartRefillNudge = shouldShowHeartRefillNudge(heart.first, heart.second),
                roundStreak = streakResult.currentStreak,
                roundStreakCelebration = streakResult.newlyEarnedBadge ?: it.roundStreakCelebration,
                manualRemaining = manualRemainingNow(today),
            )
        }
        publishRoundStreak(roundKey, streakResult.currentStreak, prevStreak)
        applyLeaderboard()
        flushPendingSession()
        viewModelScope.launch {
            repository.incrementExternalCount(roundKey, applied)
            _state.update { it.copy(isSubmittingManualSalawat = false) }
            // Audit trail for the push plus the server-side allowance ledger; the applied (capped)
            // amount is what was scored, so it is what both sides record.
            repository.appendExternalLog(
                roundKey = roundKey,
                count = applied,
                at = Instant.fromEpochMilliseconds(nowMs),
                countsTowardDailyCap = true,
            )
        }
        sessionStore.saveLastSalawatTimestamp(nowMs)
    }

    /**
     * Subtract a mistakenly-added batch from the player's competition score, flooring at 0.
     * Reduces the un-flushed local pending first (always safe and instant), then lowers the saved
     * server score for the remainder. The heart index, daily goal and streak are intentionally left
     * untouched — they record activity, not the leaderboard total the user is correcting.
     */
    fun subtractManualSalawat(count: Int) {
        val roundKey = state.value.roundKey ?: return
        if (count <= 0) return

        val serverTotal = remoteSelfPlayer?.totalCount ?: 0
        val pendingClicks = repository.getPendingSession(roundKey).clickCount
        val pendingNet = (pendingClicks - inFlightFlush).coerceAtLeast(0)
        val currentScore = serverTotal + pendingNet
        val applied = count.coerceAtMost(currentScore)
        if (applied <= 0) {
            _state.update { it.copy(showManualSalawatSheet = false) }
            return
        }

        // A correction frees the manual-entry allowance again so a mis-entry can be re-added. The
        // refund is mirrored to the server ledger below, so the startup sync cannot re-apply it.
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        val refunded = sessionStore.refundManualEntry(today, applied)
        // The daily-goal progress (the daily leaderboard/badge source) records activity and is
        // intentionally not walked back by a competition-total correction — same as the heart
        // index and streak below — so today's published daily count is unchanged here.
        val todayTotal = dailyGoalStore.todayProgress(today)

        val pendingReduction = applied.coerceAtMost(pendingClicks)
        if (pendingReduction > 0) repository.decrementPendingClick(roundKey, pendingReduction)
        val serverReduction = applied - pendingReduction

        val pending = repository.getPendingSession(roundKey)
        _state.update {
            it.copy(
                sessionClicks = pending.clickCount,
                todayCount = todayTotal,
                showManualSalawatSheet = false,
                isSubmittingManualSalawat = serverReduction > 0,
                manualRemaining = manualRemainingNow(today),
            )
        }
        applyLeaderboard()

        if (serverReduction > 0) {
            viewModelScope.launch {
                repository.decrementScore(roundKey, serverReduction)
                _state.update { it.copy(isSubmittingManualSalawat = false) }
            }
        }
        // The correction is an external push too — logged as a negative entry so the audit trail
        // stays the net truth, and subtracted from the server allowance ledger so the freed
        // allowance survives a reinstall exactly like the used allowance does.
        if (refunded > 0) {
            viewModelScope.launch {
                repository.appendExternalLog(
                    roundKey = roundKey,
                    count = -refunded,
                    at = Clock.System.now(),
                    countsTowardDailyCap = true,
                )
            }
        }
    }

    private fun settleHeartDecay(nowTs: Long = Clock.System.now().toEpochMilliseconds()) {
        val storedScore = heartStore.getScore()
        val storedAnchor = heartStore.getAnchorTs()
        val settled = settleHeart(
            storedScore = storedScore,
            anchorTs = storedAnchor,
            nowTs = nowTs,
            resetBoundaryTs = lastHeartResetBoundary(nowTs),
        )
        if (settled.score != storedScore || settled.anchorTs != storedAnchor) {
            heartStore.save(settled.score, settled.anchorTs)
        }
        _state.update {
            it.copy(
                heartScore = settled.score,
                showHeartRefillNudge = shouldShowHeartRefillNudge(settled.score, settled.anchorTs),
            )
        }
    }

    private fun addHeartTap(nowTs: Long, count: Int = 1): Pair<Int, Long> {
        val settled = settleHeart(
            storedScore = heartStore.getScore(),
            anchorTs = heartStore.getAnchorTs(),
            nowTs = nowTs,
            resetBoundaryTs = lastHeartResetBoundary(nowTs),
        )
        val heartScore = settled.score + HEART_TAP_BONUS * count
        val heartAnchor = if (settled.anchorTs <= 0L) nowTs else settled.anchorTs
        heartStore.save(heartScore, heartAnchor)
        return heartScore to heartAnchor
    }

    private fun shouldShowHeartRefillNudge(score: Int, anchorTs: Long): Boolean =
        anchorTs > 0L && score <= HEART_LOW_THRESHOLD

    fun resetCurrentRoundScore() {
        val roundKey = state.value.roundKey ?: return
        repository.clearAllPendingRounds()
        _state.update { it.copy(sessionClicks = 0) }
        applyLeaderboard()
        viewModelScope.launch {
            repository.resetPlayerScore(roundKey)
        }
    }

    fun onRoundEndBannerClick() = _state.update { it.copy(showRoundEndResults = true) }

    fun dismissRoundEndResults() {
        _state.update { it.copy(showRoundEndBanner = false, showRoundEndResults = false) }
    }
    fun dismissGraceWarning() {
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        engagementStore.markGraceWarningShown(today)
        _state.update { it.copy(showGraceWarning = false) }
    }
    fun dismissDailyGoalCompleted() = _state.update { it.copy(dailyGoalJustCompleted = false) }
    fun dismissOvertake() = _state.update { it.copy(overtakeRank = null) }
    fun dismissMilestone() = _state.update { it.copy(milestoneThreshold = null, milestoneBadgeKey = null) }
    fun updateNicknameLocal(name: String?) {
        sessionStore.setNickname(name)
        val published = sessionStore.getPublishedName()
        val uid = authUid ?: return
        _state.update { it.copy(selfDisplayTag = buildMohamedLoversDisplayTag(uid, it.countryCode, published)) }
        applyLeaderboard()
    }

    fun commitNickname() {
        viewModelScope.launch { repository.writeNickname(sessionStore.getPublishedName()) }
    }

    fun saveNickname(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        sessionStore.setNickname(trimmed)
        sessionStore.isNicknameEnabled = true
        val published = sessionStore.getPublishedName()
        authUid?.let { uid ->
            _state.update { it.copy(selfDisplayTag = buildMohamedLoversDisplayTag(uid, it.countryCode, published)) }
        }
        viewModelScope.launch { repository.writeNickname(published) }
        applyLeaderboard()
    }

    fun setNicknameEnabled(enabled: Boolean) {
        sessionStore.isNicknameEnabled = enabled
        val published = sessionStore.getPublishedName()
        val uid = authUid ?: return
        _state.update { it.copy(selfDisplayTag = buildMohamedLoversDisplayTag(uid, it.countryCode, published)) }
        viewModelScope.launch { repository.writeNickname(published) }
        applyLeaderboard()
    }

    /** Persists the server-reminder opt-in locally and syncs it to RTDB for the cron scripts. */
    fun setServerRemindersEnabled(enabled: Boolean) {
        settingsStore.serverRemindersEnabled = enabled
        syncNotificationPrefs()
    }

    /** Persists the leaderboard-notification opt-in locally, syncs to RTDB, and subscribes/unsubscribes from the FCM topic. */
    fun setLeaderboardNotifsEnabled(enabled: Boolean) {
        settingsStore.leaderboardNotifsEnabled = enabled
        setLeaderboardTopicSubscription(enabled)
        syncNotificationPrefs()
    }

    private fun syncNotificationPrefs() {
        viewModelScope.launch {
            val uid = repository.ensureAnonymousUser().getOrNull() ?: return@launch
            repository.writeNotificationPrefs(
                uid = uid,
                remindersEnabled = settingsStore.serverRemindersEnabled,
                leaderboardEnabled = settingsStore.leaderboardNotifsEnabled,
            )
        }
    }

    fun dismissRankMovement() = _state.update { it.copy(rankMovementDelta = null) }

    fun dismissDailyLeaderboardPromo() {
        settingsStore.dailyLeaderboardPromoShown = true
        _state.update { it.copy(showDailyLeaderboardPromo = false) }
    }

    fun setLeaderboardMode(daily: Boolean) {
        if (daily == state.value.isUsingDailyLeaderboard) return
        settingsStore.useDailyLeaderboard = daily
        _state.update { it.copy(isUsingDailyLeaderboard = daily, isSwitchingLeaderboardMode = true) }
        leaderboardJob?.cancel()
        // Ranks aren't comparable across modes — without this, landing on a better rank in
        // the other mode reads as an overtake and fires the animation.
        lastProjectedRank = 0
        // Keep the previous list on screen until the other node emits; applyLeaderboard()
        // is suppressed while switching so it can't mix the new mode's self score with
        // the old mode's entries.
        connectToLeaderboardIfPossible()
        leaderboardModeSwitchJob?.cancel()
        leaderboardModeSwitchJob = viewModelScope.launch {
            delay(LEADERBOARD_MODE_SWITCH_TIMEOUT_MS)
            leaderboardModeSwitchJob = null
            remoteLeaderboard = FirebaseLeaderboard(emptyList(), false)
            _state.update { it.copy(isSwitchingLeaderboardMode = false) }
            applyLeaderboard()
        }
    }

    private fun endLeaderboardModeSwitch() {
        leaderboardModeSwitchJob?.cancel()
        leaderboardModeSwitchJob = null
        if (state.value.isSwitchingLeaderboardMode) {
            _state.update { it.copy(isSwitchingLeaderboardMode = false) }
        }
    }

    fun setScoreMasked(masked: Boolean) {
        val roundKey = state.value.roundKey ?: return
        val uid = authUid ?: return
        viewModelScope.launch {
            repository.setScoreMasked(roundKey, uid, masked)
        }
    }

    fun fetchLiveLeaderboard() {
        val roundKey = state.value.roundKey ?: return
        if (!premiumStore.hasFeature(PremiumFeature.LIVE_LEADERBOARD)) return
        if (state.value.isLoadingLiveLeaderboard) return
        _state.update { it.copy(isLoadingLiveLeaderboard = true) }
        viewModelScope.launch {
            repository.fetchLiveLeaderboard(roundKey).onSuccess { leaderboard ->
                remoteLeaderboard = leaderboard
                applyLeaderboard()
            }
            delay(LIVE_COOLDOWN_MS)
            _state.update { it.copy(isLoadingLiveLeaderboard = false) }
        }
    }

    private companion object {
        const val LIVE_COOLDOWN_MS = 30_000L

        // Safety net: if the new mode's node never emits (offline, missing node),
        // stop waiting so the board is never left frozen on the previous mode's list.
        const val LEADERBOARD_MODE_SWITCH_TIMEOUT_MS = 6_000L
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /**
     * Restore today's manual ("record external") allowance from the server ledger on startup. The
     * local ledger is device-side only, so uninstalling and reinstalling used to reset it and hand
     * the user a fresh daily cap; adopting the server's record for the Cairo day closes that hole.
     * Fire-and-forget: a failed read simply leaves the local ledger in charge.
     *
     * Note this reads the ledger on the *current* round's player node, so external entries made
     * earlier on a Friday (before the 19:00 round reset) are not visible to a device that
     * reinstalled after the reset. The local ledger still covers that case for every device that
     * did not reinstall mid-day.
     */
    private suspend fun syncExternalAllowance(roundKey: String) {
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.of("Africa/Cairo")).date
        repository.fetchExternalUsedToday(roundKey, now).onSuccess { remoteUsed ->
            sessionStore.syncManualUsedFromRemote(today, remoteUsed)
            _state.update {
                it.copy(manualRemaining = manualRemainingNow(today))
            }
        }
    }

    private fun connectToLeaderboardIfPossible() {
        val roundKey = state.value.roundKey
        if (!state.value.firebaseConfigured || roundKey.isNullOrBlank()) {
            selfJob?.cancel(); leaderboardJob?.cancel()
            remoteLeaderboard = FirebaseLeaderboard(emptyList(), false); remoteSelfPlayer = null
            endLeaderboardModeSwitch()
            applyLeaderboard()
            return
        }

        viewModelScope.launch {
            repository.fetchRoundTotal(roundKey).onSuccess { total ->
                _state.update { it.copy(roundTotal = total) }
            }
            repository.fetchRoundPlayerCount(roundKey).onSuccess { count ->
                _state.update { it.copy(roundPlayerCount = count) }
            }
            repository.fetchAllTimeTotal().onSuccess { total ->
                _state.update { it.copy(allTimeTotal = total) }
            }
        }

        viewModelScope.launch {
            val uid = repository.ensureAnonymousUser().getOrElse {
                _state.update { it.copy(error = it.error ?: MohamedLoversError.Connection) }
                applyLeaderboard()
                return@launch
            }

            authUid = uid
            val selfPublishedName = sessionStore.getPublishedName()
            _state.update { it.copy(selfDisplayTag = buildMohamedLoversDisplayTag(uid, it.countryCode, selfPublishedName)) }
            val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
            // Also publishes the build being run, so the user node always reflects the
            // currently-installed version (fire-and-forget, like the rest of this startup sync).
            launch { repository.writeUserActivity(uid, today, getAppVersion(), getAppVersionCode()) }
            launch { repository.setSupporter(premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE)) }
            launch { syncExternalAllowance(roundKey) }

            if (!achievementsFetchedFromRtdb) {
                achievementsFetchedFromRtdb = true
                launch {
                    repository.fetchUserAchievements(uid).onSuccess { achievements ->
                        achievements.entries
                            .mapNotNull { (rk, achievement) ->
                                engagementStore.checkAndSaveRankAchievement(
                                    roundKey = rk,
                                    rank = achievement.rank,
                                    today = today,
                                    score = achievement.score,
                                    winnerCode = achievement.winnerCode,
                                )?.takeIf { achievement.rank in 1..10 }
                            }
                            .firstOrNull()
                            ?.let { earned -> _state.update { it.copy(roundEndAchievement = earned) } }
                    }
                }
            }

            if (selfMedalsFetchedForUid != uid) {
                selfMedalsFetchedForUid = uid
                launch {
                    repository.fetchSelfMedals(uid).onSuccess { medals ->
                        selfMedals = medals
                        applyLeaderboard()
                    }
                }
            }

            selfJob?.cancel()
            selfJob = launch {
                repository.observeSelfPlayer(roundKey, uid).collectLatest { result ->
                    result.onSuccess { player ->
                        remoteSelfPlayer = player
                        reconcileFromSelfPlayer(player)
                        applyLeaderboard()
                    }.onFailure { t -> _state.update { it.copy(error = t.toLoversError()) } }
                }
            }

            leaderboardJob?.cancel()
            sawLeaderboardLive = false
            leaderboardJob = launch {
                repository.observeLeaderboard(roundKey, settingsStore.useDailyLeaderboard).collectLatest { result ->
                    result.onSuccess { leaderboard ->
                        remoteLeaderboard = leaderboard
                        endLeaderboardModeSwitch()
                        applyLeaderboard()
                        if (!leaderboard.isFinal) {
                            sawLeaderboardLive = true
                        }
                        if (leaderboard.isFinal && sawLeaderboardLive) {
                            sawLeaderboardLive = false

                            // Build winners top 3
                            val top3 = if (leaderboard.entries.size >= 3) {
                                leaderboard.entries
                                    .sortedByDescending { it.score }
                                    .take(3)
                                    .mapIndexed { i, e ->
                                        MohamedLoversLeaderboardEntry(
                                            rank = i + 1,
                                            displayTag = buildMohamedLoversDisplayTag(e.uid, e.countryCode),
                                            totalCount = e.score,
                                            isCurrentUser = e.uid == uid,
                                            uid = e.uid,
                                        )
                                    }
                            } else emptyList()

                            // Persist achievement + recap stats (always, regardless of viewed)
                            val match = leaderboard.entries.firstOrNull { it.uid == uid }
                            var achievement: tools.mo3ta.salo.domain.Achievement.RankAchievement? = null
                            var recapRank = 0
                            var recapPlayers = state.value.roundPlayerCount
                            var isPersonalBest = false
                            var tapsDelta = 0

                            if (match != null) {
                                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                                achievement = engagementStore.checkAndSaveRankAchievement(
                                    roundKey = roundKey,
                                    rank = match.rank,
                                    today = today,
                                    score = match.score,
                                    winnerCode = remoteSelfPlayer?.winnerCode.orEmpty(),
                                )
                                recapRank = match.rank
                                if (recapRank > 0) {
                                    val syncedTaps = state.value.syncedTotal
                                    val lastTaps = repository.getLastRoundTaps()
                                    val prevBest = repository.getPersonalBestRank()
                                    isPersonalBest = recapRank in 1..10 && recapRank < prevBest
                                    tapsDelta = maxOf(0, syncedTaps - lastTaps)
                                    repository.updatePersonalBestRank(recapRank)
                                    repository.saveLastRoundTaps(syncedTaps)
                                }
                            }

                            _state.update {
                                it.copy(
                                    showRoundEndBanner = true,
                                    winnersTop3 = top3,
                                    recapRank = recapRank,
                                    recapTotalPlayers = recapPlayers,
                                    recapIsPersonalBest = isPersonalBest,
                                    recapTapsDelta = tapsDelta,
                                    roundEndAchievement = achievement,
                                )
                            }
                        }
                    }.onFailure { t ->
                        endLeaderboardModeSwitch()
                        _state.update { it.copy(error = t.toLoversError()) }
                    }
                }
            }
        }
    }

    /**
     * Reconciles the two pieces of server-side truth on every self-player snapshot:
     *
     * 1. **The day's push baseline.** `yesterdayTotalScore` is re-stamped nightly at 23:45 Cairo, so
     *    during a Cairo day `totalCount - yesterdayTotalScore` is what the server has recorded for
     *    today. It is persisted with its fetch time so a user who leaves and comes back later the
     *    same day still knows where the day started, and merged into the local push ledger by taking
     *    the higher of the two — which keeps the cap honest across both a reinstall (local ledger
     *    wiped, server total intact) and a Friday round rollover (server total zeroed mid-day,
     *    local ledger intact).
     * 2. **The published daily badge.** The badge is server-owned evidence of a minimum day count
     *    (and is cleared nightly by the same cron, so it is never stale across days). When it claims
     *    more than the local count knows about, the local count adopts the badge's value and the
     *    user is warned — see [reconcileDailyBadge].
     */
    private fun reconcileFromSelfPlayer(player: MohamedLoversPlayer?) {
        if (player == null) return
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        sessionStore.saveDailyBaseline(
            today = today,
            yesterdayTotalScore = player.yesterdayTotalScore,
            atMs = Clock.System.now().toEpochMilliseconds(),
        )
        sessionStore.syncDailyPushFromRemote(
            today = today,
            serverDayTotal = SalawatDailyCap.serverDayTotal(player.totalCount, player.yesterdayTotalScore),
        )
        reconcileDailyBadge(player, today)
    }

    /**
     * Raises today's local count to the threshold of the server-published daily badge when the badge
     * claims more than the local count does — the badge only ever lands after the score that earned
     * it reached the server, so a lower local count means the device lost the day's progress (a
     * reinstall, cleared storage, or a mid-day switch). A one-shot warning is surfaced with it.
     * No-op when the badge is absent or already covered by the local count.
     *
     * Every adjustment is also recorded under the user node (`badgeAdjustments`) with the time, the
     * short count it found and the badge it adopted — a device producing these repeatedly is worth
     * reviewing. The log write is fire-and-forget and never gates the adjustment itself.
     */
    private fun reconcileDailyBadge(player: MohamedLoversPlayer, today: LocalDate) {
        val badge = DailyBadge.fromKey(player.dailyBadge) ?: return
        val local = dailyGoalStore.todayProgress(today)
        if (badge.threshold <= local) return
        val adjusted = dailyGoalStore.raiseTodayProgress(today, badge.threshold)
        _state.update {
            it.copy(
                todayCount = adjusted,
                dailyGoalProgress = adjusted,
                currentDailyBadge = badge.key,
                badgeAdjustedTo = adjusted,
            )
        }
        if (!state.value.firebaseConfigured) return
        viewModelScope.launch {
            repository.logDailyBadgeAdjustment(
                case = DailyBadgeAdjustmentLog.CASE_BADGE_ABOVE_PROGRESS,
                at = Clock.System.now(),
                progress = local,
                badge = badge,
            )
        }
    }

    /** Clears the "score raised to your saved badge" warning once it has been shown. */
    fun dismissBadgeAdjustment() = _state.update { it.copy(badgeAdjustedTo = null) }

    /** Clears the "daily cap reached" warning once it has been shown. */
    fun dismissDailyCapNotice() = _state.update { it.copy(dailyCapDiscarded = null) }

    private fun applyLeaderboard() {
        // Mid mode-switch the cached entries still belong to the previous mode. Leave the
        // rendered list untouched rather than recomputing it from stale data.
        if (state.value.isSwitchingLeaderboardMode) return
        val uid = authUid
        val isDaily = state.value.isUsingDailyLeaderboard
        val selfRemoteTotal = remoteSelfPlayer?.totalCount ?: 0
        val pendingNet = (state.value.sessionClicks - inFlightFlush).coerceAtLeast(0)
        val selfProjectedTotal = selfRemoteTotal + pendingNet
        // Daily score is now the locally-tracked running day total (which already includes taps not
        // yet flushed to the server); fall back to the server-published todayCount when local is
        // behind (e.g. right after a reinstall).
        // behind (e.g. right after a reinstall).
        val selfProjectedDaily = maxOf(state.value.todayCount, remoteSelfPlayer?.todayCount ?: 0)
        val selfDisplayScore = if (isDaily) selfProjectedDaily else selfProjectedTotal

        val selfPublishedName = sessionStore.getPublishedName()
        // Self medals/badge come from local/self-fetched sources rather than the leaderboard
        // entry: the daily badge is computed locally (and can outrun the periodically-rebuilt
        // server entry), and medals are only attached to the top-N entries server-side. Once
        // self medals have been fetched (non-null), trust them; before that, fall back to the
        // entry's values so a top-ranked current user still shows medals during the fetch.
        val fetchedMedals = selfMedals
        val topEntries = remoteLeaderboard.entries.map { entry ->
            val isCurrentUser = entry.uid == uid
            val score = if (isCurrentUser) selfDisplayScore else entry.score
            val nick = if (isCurrentUser) selfPublishedName else entry.nickname
            MohamedLoversLeaderboardEntry(
                rank = 0,
                displayTag = buildMohamedLoversDisplayTag(entry.uid, entry.countryCode, nick),
                totalCount = score,
                isCurrentUser = isCurrentUser,
                uid = entry.uid,
                rankChange = entry.rankChange,
                scoreMasked = entry.scoreMasked,
                isSupporter = entry.isSupporter,
                dailyBadge = if (isCurrentUser) state.value.currentDailyBadge else entry.dailyBadge,
                roundStreak = if (isCurrentUser) state.value.roundStreak.takeIf { it > 0 } else entry.roundStreak,
                goldMedals = if (isCurrentUser && fetchedMedals != null) fetchedMedals.gold.takeIf { it > 0 } else entry.goldMedals,
                silverMedals = if (isCurrentUser && fetchedMedals != null) fetchedMedals.silver.takeIf { it > 0 } else entry.silverMedals,
                bronzeMedals = if (isCurrentUser && fetchedMedals != null) fetchedMedals.bronze.takeIf { it > 0 } else entry.bronzeMedals,
            )
        }.sortedByDescending { it.totalCount }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

        val selfInTop = uid != null && topEntries.any { it.isCurrentUser }

        val selfEntry = when {
            uid == null || selfDisplayScore <= 0 -> null
            selfInTop -> null
            else -> MohamedLoversLeaderboardEntry(
                rank = remoteSelfPlayer?.rank ?: 0,
                displayTag = buildMohamedLoversDisplayTag(
                    uid,
                    remoteSelfPlayer?.countryCode?.ifBlank { state.value.countryCode }
                        ?: state.value.countryCode,
                    selfPublishedName,
                ),
                totalCount = selfDisplayScore,
                isCurrentUser = true,
                dailyBadge = state.value.currentDailyBadge,
                roundStreak = state.value.roundStreak.takeIf { it > 0 },
                goldMedals = fetchedMedals?.gold?.takeIf { it > 0 },
                silverMedals = fetchedMedals?.silver?.takeIf { it > 0 },
                bronzeMedals = fetchedMedals?.bronze?.takeIf { it > 0 },
                uid = uid,
            )
        }

        val currentRank = when {
            selfInTop -> topEntries.firstOrNull { it.isCurrentUser }?.rank ?: 0
            else -> remoteSelfPlayer?.rank ?: 0
        }

        // Overtake detection
        var overtakeRank: Int? = null
        if (lastProjectedRank > 0 && currentRank in 1 until lastProjectedRank) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now >= overtakeCooldownUntil) {
                overtakeRank = lastProjectedRank
                overtakeCooldownUntil = now + 5_000L
            }
        }
        if (currentRank > 0) lastProjectedRank = currentRank

        val authoritativeRank = remoteSelfPlayer?.rank ?: 0
        var rankDelta: Int? = null
        var oldRank = 0
        var newRank = 0
        if (!rankMovementShown && authoritativeRank > 0) {
            val storedRank = sessionStore.getLastKnownRank()
            if (storedRank > 0 && storedRank != authoritativeRank) {
                rankDelta = storedRank - authoritativeRank
                oldRank = storedRank
                newRank = authoritativeRank
                rankMovementShown = true
            }
        }
        if (authoritativeRank > 0) sessionStore.saveLastKnownRank(authoritativeRank)

        _state.update {
            it.copy(
                syncedTotal = selfRemoteTotal,
                isWinner = remoteSelfPlayer?.isWinner == true,
                winnerCode = remoteSelfPlayer?.winnerCode.orEmpty(),
                topPlayers = topEntries,
                selfEntry = selfEntry,
                selfInTop = selfInTop,
                overtakeRank = overtakeRank ?: it.overtakeRank,
                rankMovementDelta = rankDelta ?: it.rankMovementDelta,
                rankMovementOldRank = if (rankDelta != null) oldRank else it.rankMovementOldRank,
                rankMovementNewRank = if (rankDelta != null) newRank else it.rankMovementNewRank,
            )
        }
    }

    private fun resolveStatus(
        firebaseConfigured: Boolean,
        window: MohamedLoversCompetitionWindow,
    ) = when {
        window.networkNow == null -> MohamedLoversStatus.WaitingNetwork
        !firebaseConfigured -> MohamedLoversStatus.FirebaseOff
        else -> MohamedLoversStatus.Open
    }

    private fun Throwable.toLoversError(): MohamedLoversError =
        message?.takeIf { it.isNotBlank() }?.let(MohamedLoversError::Raw) ?: MohamedLoversError.Connection
}

private fun kotlinx.datetime.Instant.formatDisplay(): String {
    val local = toLocalDateTime(TimeZone.of("Africa/Cairo"))
    val hour = local.hour
    val ampm = if (hour < 12) "AM" else "PM"
    val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
    val m = local.minute.toString().padStart(2, '0')
    val mo = local.monthNumber.toString().padStart(2, '0')
    val d = local.dayOfMonth.toString().padStart(2, '0')
    return "${local.year}/$mo/$d - $h12:$m $ampm"
}
