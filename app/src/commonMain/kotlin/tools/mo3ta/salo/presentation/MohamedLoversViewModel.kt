package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.hadith.DailyHadithStore
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
) : ViewModel() {

    private val _state = MutableStateFlow(MohamedLoversUiState())
    val state: StateFlow<MohamedLoversUiState> = _state.asStateFlow()

    private val flushMutex = Mutex()
    private var selfJob: Job? = null
    private var leaderboardJob: Job? = null
    private var remoteLeaderboard: FirebaseLeaderboard = FirebaseLeaderboard(emptyList(), false)
    private var remoteSelfPlayer: MohamedLoversPlayer? = null
    private var authUid: String? = null
    private var currentWindow: MohamedLoversCompetitionWindow = MohamedLoversCompetitionWindow()

    init {
        _state.update { it.copy(showHadithDialog = hadithStore.showOnStartup) }
        refresh()
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
        _state.update { it.copy(sessionClicks = pending.clickCount, error = null) }
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

                _state.update { it.copy(isSavingSession = true, error = null) }

                val result = repository.flushPendingSession(
                    countryCode = state.value.countryCode,
                    fallbackRoundKey = roundKey,
                )
                val latestPending = repository.getPendingSession()

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

            selfJob?.cancel()
            selfJob = launch {
                repository.observeSelfPlayer(roundKey, uid).collectLatest { result ->
                    result.onSuccess { player -> remoteSelfPlayer = player; applyLeaderboard() }
                        .onFailure { t -> _state.update { it.copy(error = t.toLoversError()) } }
                }
            }

            leaderboardJob?.cancel()
            leaderboardJob = launch {
                repository.observeLeaderboard(roundKey).collectLatest { result ->
                    result.onSuccess { leaderboard ->
                        remoteLeaderboard = leaderboard
                        applyLeaderboard()
                        if (leaderboard.isFinal) {
                            val match = leaderboard.entries.firstOrNull { it.uid == uid }
                            if (match != null) {
                                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                                val achievement = engagementStore.checkAndSaveRankAchievement(roundKey, match.rank, today)
                                if (achievement != null) {
                                    _state.update { it.copy(newlyEarnedRankAchievement = achievement) }
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
        val selfProjectedTotal = selfRemoteTotal + state.value.sessionClicks

        val topEntries = remoteLeaderboard.entries.map { entry ->
            val isCurrentUser = entry.uid == uid
            MohamedLoversLeaderboardEntry(
                rank = entry.rank,
                displayTag = buildMohamedLoversDisplayTag(entry.uid, entry.countryCode),
                totalCount = if (isCurrentUser) selfProjectedTotal else entry.score,
                isCurrentUser = isCurrentUser,
            )
        }

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
