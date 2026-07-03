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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.time.computeFinalMinutesTick
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
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
import tools.mo3ta.salo.domain.FirebaseLeaderboard
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.domain.buildMohamedLoversDisplayTag
import tools.mo3ta.salo.ui.setLeaderboardTopicSubscription

class MohamedLoversViewModel(
    private val repository: MohamedLoversRepository,
    private val engagementStore: EngagementStore,
    private val hadithStore: DailyHadithStore,
    private val dailyGoalStore: DailyGoalStore,
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
    private var remoteLeaderboard: FirebaseLeaderboard = FirebaseLeaderboard(emptyList(), false)
    private var remoteSelfPlayer: MohamedLoversPlayer? = null
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
            )
        }
        settleHeartDecay()
        if (startTimers) {
            viewModelScope.launch {
                delay(90_000L)
                refresh()
//            delay(5*60_000L)
//            refresh()
            }
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
        val todayStr = today.toString()
        val rawTaps = dailyGoalStore.todayProgress(today)
        val badge = DailyBadge.fromTapCount(rawTaps)
        val lastMilestone = sessionStore.getLastMilestoneLevel(todayStr)
        var milestoneThreshold: Int? = null
        var milestoneBadgeKey: String? = null
        if (badge != null && badge.threshold > lastMilestone) {
            milestoneThreshold = badge.threshold
            milestoneBadgeKey = badge.key
            sessionStore.saveLastMilestoneLevel(todayStr, badge.threshold)
            viewModelScope.launch { repository.writeDailyBadge(roundKey, badge.key) }
        }
        _state.update {
            it.copy(
                sessionClicks = pending.clickCount,
                error = null,
                dailyGoalProgress = rawTaps,
                dailyGoalJustCompleted = !wasComplete && isNowComplete,
                milestoneThreshold = milestoneThreshold ?: it.milestoneThreshold,
                milestoneBadgeKey = milestoneBadgeKey ?: it.milestoneBadgeKey,
                currentDailyBadge = badge?.key ?: it.currentDailyBadge,
                lastSalawatElapsedMinutes = 0L,
                heartScore = heart.first,
                showHeartRefillNudge = shouldShowHeartRefillNudge(heart.first, heart.second),
            )
        }
        applyLeaderboard()
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

                val result = repository.flushPendingSession(
                    countryCode = state.value.countryCode,
                )
                val latestPending = repository.getPendingSession(roundKey)
                inFlightFlush = 0

                _state.update {
                    it.copy(
                        isSavingSession = false,
                        sessionClicks = latestPending.clickCount,
                        error = result.exceptionOrNull()?.message
                            ?.takeIf { msg -> msg.isNotBlank() }
                            ?.let(MohamedLoversError::Raw),
                    )
                }
                applyLeaderboard()
            }
        }
    }

    fun refreshSessionClicks() {
        settleHeartDecay()
        val roundKey = state.value.roundKey ?: return
        val pending = repository.getPendingSession(roundKey)
        _state.update { it.copy(sessionClicks = pending.clickCount) }
        applyLeaderboard()
    }

    fun applyExtensionScore(round: String, count: Int) {
        repository.registerLocalTap(round, count)
        val pending = repository.getPendingSession(round)
        _state.update { it.copy(sessionClicks = pending.clickCount) }
        applyLeaderboard()
        flushPendingSession()
    }

    fun showManualSalawatSheet() {
        _state.update { it.copy(showManualSalawatSheet = true) }
    }

    fun dismissManualSalawatSheet() {
        _state.update { it.copy(showManualSalawatSheet = false) }
    }

    fun submitManualSalawat(count: Int) {
        val roundKey = state.value.roundKey ?: return
        if (count <= 0) return
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val heart = addHeartTap(nowMs)
        repository.registerLocalTap(roundKey, count)
        val pending = repository.getPendingSession(roundKey)
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        dailyGoalStore.recordTap(today, count)
        _state.update {
            it.copy(
                sessionClicks = pending.clickCount,
                showManualSalawatSheet = false,
                isSubmittingManualSalawat = true,
                dailyGoalProgress = dailyGoalStore.todayProgress(today),
                lastSalawatElapsedMinutes = 0L,
                heartScore = heart.first,
                showHeartRefillNudge = shouldShowHeartRefillNudge(heart.first, heart.second),
            )
        }
        applyLeaderboard()
        flushPendingSession()
        viewModelScope.launch {
            repository.incrementExternalCount(roundKey, count)
            _state.update { it.copy(isSubmittingManualSalawat = false) }
        }
        sessionStore.saveLastSalawatTimestamp(nowMs)
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

    private fun addHeartTap(nowTs: Long): Pair<Int, Long> {
        val settled = settleHeart(
            storedScore = heartStore.getScore(),
            anchorTs = heartStore.getAnchorTs(),
            nowTs = nowTs,
            resetBoundaryTs = lastHeartResetBoundary(nowTs),
        )
        val heartScore = settled.score + HEART_TAP_BONUS
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
        settingsStore.useDailyLeaderboard = daily
        _state.update { it.copy(isUsingDailyLeaderboard = daily) }
        leaderboardJob?.cancel()
        remoteLeaderboard = FirebaseLeaderboard(emptyList(), false)
        applyLeaderboard()
        connectToLeaderboardIfPossible()
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
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun connectToLeaderboardIfPossible() {
        val roundKey = state.value.roundKey
        if (!state.value.firebaseConfigured || roundKey.isNullOrBlank()) {
            selfJob?.cancel(); leaderboardJob?.cancel()
            remoteLeaderboard = FirebaseLeaderboard(emptyList(), false); remoteSelfPlayer = null
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
            launch { repository.writeUserActivity(uid, today) }
            launch { repository.setSupporter(premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE)) }

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

            selfJob?.cancel()
            selfJob = launch {
                repository.observeSelfPlayer(roundKey, uid).collectLatest { result ->
                    result.onSuccess { player -> remoteSelfPlayer = player; applyLeaderboard() }
                        .onFailure { t -> _state.update { it.copy(error = t.toLoversError()) } }
                }
            }

            leaderboardJob?.cancel()
            sawLeaderboardLive = false
            leaderboardJob = launch {
                repository.observeLeaderboard(roundKey, settingsStore.useDailyLeaderboard).collectLatest { result ->
                    result.onSuccess { leaderboard ->
                        remoteLeaderboard = leaderboard
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
                    }.onFailure { t -> _state.update { it.copy(error = t.toLoversError()) } }
                }
            }
        }
    }

    private fun applyLeaderboard() {
        val uid = authUid
        val isDaily = state.value.isUsingDailyLeaderboard
        val selfRemoteTotal = remoteSelfPlayer?.totalCount ?: 0
        val pendingNet = (state.value.sessionClicks - inFlightFlush).coerceAtLeast(0)
        val selfProjectedTotal = selfRemoteTotal + pendingNet
        val selfProjectedDaily = (selfRemoteTotal - (remoteSelfPlayer?.yesterdayTotalScore ?: 0)).coerceAtLeast(0) + pendingNet
        val selfDisplayScore = if (isDaily) selfProjectedDaily else selfProjectedTotal

        val selfPublishedName = sessionStore.getPublishedName()
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
                dailyBadge = entry.dailyBadge,
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
