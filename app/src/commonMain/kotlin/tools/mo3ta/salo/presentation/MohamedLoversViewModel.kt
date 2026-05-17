package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.domain.FirebaseLeaderboard
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_FRIDAY_MULTIPLIER
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.domain.buildMohamedLoversDisplayTag

class MohamedLoversViewModel(
    private val repository: MohamedLoversRepository,
    private val engagementStore: EngagementStore,
    private val hadithStore: DailyHadithStore,
    private val dailyGoalStore: DailyGoalStore,
    private val settingsStore: NotificationSettingsStore,
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
        _state.update {
            it.copy(
                dailyGoalTarget = dailyGoalStore.todayTarget(today),
                dailyGoalProgress = dailyGoalStore.todayProgress(today),
            )
        }
        viewModelScope.launch {
            delay(90_000L)
            refresh()
            delay(5*60_000L)
            refresh()
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

            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    countryCode = bootstrap.countryCode,
                    firebaseConfigured = bootstrap.firebaseConfigured,
                    isFridayBonus = bootstrap.competitionWindow.isFridayBonus,
                    roundKey = bootstrap.competitionWindow.roundKey,
                    roundEndLabel = bootstrap.competitionWindow.roundEnd?.formatDisplay().orEmpty(),
                    networkTimeLabel = bootstrap.competitionWindow.networkNow?.formatDisplay().orEmpty(),
                    status = resolveStatus(bootstrap.firebaseConfigured, bootstrap.competitionWindow),
                    canCount = bootstrap.competitionWindow.networkNow != null,
                    sessionClicks = bootstrap.pendingSession.clickCount,
                    error = null,
                )
            }

            flushPendingSession()
            connectToLeaderboardIfPossible()
        }
    }

    fun onCountClick() {
        val current = state.value
        val roundKey = current.roundKey ?: return
        if (!current.canCount) return

        val delta = if (current.isFridayBonus) MOHAMED_LOVERS_FRIDAY_MULTIPLIER else 1
        val pending = repository.registerLocalTap(roundKey, delta)
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        val wasComplete = dailyGoalStore.isGoalComplete(today)
        dailyGoalStore.recordTap(today, delta)
        val isNowComplete = dailyGoalStore.isGoalComplete(today)
        _state.update {
            it.copy(
                sessionClicks = pending.clickCount,
                error = null,
                dailyGoalProgress = dailyGoalStore.todayProgress(today),
                dailyGoalJustCompleted = !wasComplete && isNowComplete,
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

    fun dismissRoundRecap() = _state.update { it.copy(showRoundRecap = false) }
    fun dismissGraceWarning() {
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        engagementStore.markGraceWarningShown(today)
        _state.update { it.copy(showGraceWarning = false) }
    }
    fun dismissDailyGoalCompleted() = _state.update { it.copy(dailyGoalJustCompleted = false) }
    fun dismissNewlyEarnedAchievement() = _state.update { it.copy(newlyEarnedRankAchievement = null) }
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
            _state.update { it.copy(selfDisplayTag = buildMohamedLoversDisplayTag(uid, it.countryCode)) }
            val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
            launch { repository.writeUserActivity(uid, today) }

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
                            ?.let { earned -> _state.update { it.copy(newlyEarnedRankAchievement = earned) } }
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
            leaderboardJob = launch {
                repository.observeLeaderboard(roundKey, settingsStore.useDailyLeaderboard).collectLatest { result ->
                    result.onSuccess { leaderboard ->
                        remoteLeaderboard = leaderboard
                        applyLeaderboard()
                        if (leaderboard.isFinal) {
                            val match = leaderboard.entries.firstOrNull { it.uid == uid }
                            if (match != null) {
                                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                                val achievement = engagementStore.checkAndSaveRankAchievement(
                                    roundKey = roundKey,
                                    rank = match.rank,
                                    today = today,
                                    score = match.score,
                                    winnerCode = remoteSelfPlayer?.winnerCode.orEmpty(),
                                )
                                if (achievement != null) {
                                    _state.update { it.copy(newlyEarnedRankAchievement = achievement) }
                                }
                                // Round recap — shown once per completed round
                                val recapRound = repository.getRecapShownRound()
                                if (recapRound != roundKey) {
                                    val rank = match.rank
                                    val syncedTaps = state.value.syncedTotal
                                    val lastTaps = repository.getLastRoundTaps()
                                    val prevBest = repository.getPersonalBestRank()
                                    val isPersonalBest = rank in 1..10 && rank < prevBest
                                    if (rank > 0) {
                                        repository.updatePersonalBestRank(rank)
                                        repository.saveLastRoundTaps(syncedTaps)
                                        repository.markRecapShown(roundKey)
                                        _state.update {
                                            it.copy(
                                                showRoundRecap = true,
                                                recapRank = rank,
                                                recapTotalPlayers = it.roundPlayerCount,
                                                recapIsPersonalBest = isPersonalBest,
                                                recapTapsDelta = maxOf(0, syncedTaps - lastTaps),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure { t -> _state.update { it.copy(error = t.toLoversError()) } }
                }
            }
        }
    }

    private fun applyLeaderboard() {
        val uid = authUid
        val selfRemoteTotal = remoteSelfPlayer?.totalCount ?: 0
        val pendingNet = (state.value.sessionClicks - inFlightFlush).coerceAtLeast(0)
        val selfProjectedTotal = selfRemoteTotal + pendingNet

        val topEntries = remoteLeaderboard.entries.map { entry ->
            val isCurrentUser = entry.uid == uid
            MohamedLoversLeaderboardEntry(
                rank = 0,
                displayTag = buildMohamedLoversDisplayTag(entry.uid, entry.countryCode),
                totalCount = if (isCurrentUser) selfProjectedTotal else entry.score,
                isCurrentUser = isCurrentUser,
                rankChange = entry.rankChange,
            )
        }.sortedByDescending { it.totalCount }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

        val selfInTop = uid != null && topEntries.any { it.isCurrentUser }

        val selfEntry = when {
            uid == null || selfProjectedTotal <= 0 -> null
            selfInTop -> null
            else -> MohamedLoversLeaderboardEntry(
                rank = remoteSelfPlayer?.rank ?: 0,
                displayTag = buildMohamedLoversDisplayTag(
                    uid,
                    remoteSelfPlayer?.countryCode?.ifBlank { state.value.countryCode }
                        ?: state.value.countryCode,
                ),
                totalCount = selfProjectedTotal,
                isCurrentUser = true,
            )
        }

        _state.update {
            it.copy(
                syncedTotal = selfRemoteTotal,
                isWinner = remoteSelfPlayer?.isWinner == true,
                winnerCode = remoteSelfPlayer?.winnerCode.orEmpty(),
                topPlayers = topEntries,
                selfEntry = selfEntry,
                selfInTop = selfInTop,
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
