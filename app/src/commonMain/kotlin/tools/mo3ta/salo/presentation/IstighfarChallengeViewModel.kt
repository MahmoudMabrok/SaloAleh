package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.domain.IstighfarLeaderboardEntry
import tools.mo3ta.salo.data.istighfar.IstighfarChallengeFirebaseClient
import tools.mo3ta.salo.data.istighfar.IstighfarChallengeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.ISTIGHFAR_CHALLENGE_DAILY_GOAL

class IstighfarChallengeViewModel(
    private val store: IstighfarChallengeStore,
    private val firebaseClient: IstighfarChallengeFirebaseClient,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
    private val challengeBadgeStore: ChallengeBadgeStore,
) : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(IstighfarChallengeUiState())
    val state: StateFlow<IstighfarChallengeUiState> = _state.asStateFlow()

    fun onLeaderboardOpened() {
        _state.update { it.copy(showLeaderboard = true) }
    }

    private fun loadLeaderboard() {
        viewModelScope.launch {
            val uid = sessionStore.getOrCreateUid()
            _state.update { it.copy(currentUid = uid) }
            if (!firebaseClient.isConfigured()) return@launch

            val dateKey = today().toString()
            _state.update { it.copy(isLeaderboardLoading = true) }
            firebaseClient.fetchLeaderboard(dateKey)
                .onSuccess { entries ->
                    _state.update { it.copy(leaderboard = entries, isLeaderboardLoading = false, currentUid = uid) }
                }
                .onFailure {
                    _state.update { it.copy(isLeaderboardLoading = false) }
                }
        }
    }

    fun onLeaderboardClosed() {
        _state.update { it.copy(showLeaderboard = false) }
    }

    fun onScreenEntered() {
        publishLifetimeTotal()
        loadLeaderboard()
        val today = today()
        viewModelScope.launch {
            val uid = sessionStore.getOrCreateUid()

            val prev = store.previousEntry(today)
            if (prev != null && firebaseClient.isConfigured()) {
                val (prevDate, prevTotal) = prev
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(prevDate, uid, prevTotal, countryCode, sessionStore.getPublishedName())
                if (result.isSuccess) store.clearPreviousPending()
            }

            _state.update {
                it.copy(
                    dateKey = today.toString(),
                    todayCount = store.todayCount(today),
                    lifetimeCount = store.lifetimeCount(),
                    manualRemainingToday = store.manualRemainingToday(today),
                    currentStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.ISTIGHFAR, today),
                    isLoading = false,
                    errorMessage = null,
                )
            }

            if (!firebaseClient.isConfigured()) return@launch

            val remoteCount = firebaseClient.fetchUserCount(today.toString(), uid).getOrNull()
            if (remoteCount != null) {
                store.updateRemoteBaseline(today, remoteCount)
                _state.update { it.copy(todayCount = store.todayCount(today),
                    lifetimeCount = store.lifetimeCount()) }
            }
            maybeRecordWin(today, store.todayCount(today))

            refreshStats(today.toString(), uid)
        }
    }

    fun onIstighfarTap() {
        val today = today()
        val updated = store.incrementToday(today)
        maybeRecordWin(today, updated)
        val isMilestone = updated == ISTIGHFAR_CHALLENGE_DAILY_GOAL
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                lifetimeCount = store.lifetimeCount(),
                errorMessage = null,
                showCelebration = isMilestone || it.showCelebration,
                celebrationMilestone = if (isMilestone) updated else it.celebrationMilestone,
            )
        }
        recalculateLocalLeaderboard()
    }

    fun onCelebrationDismissed() {
        _state.update { it.copy(showCelebration = false) }
    }

    fun showManualIstighfarSheet() {
        _state.update { it.copy(showManualIstighfarSheet = true) }
    }

    fun dismissManualIstighfarSheet() {
        _state.update { it.copy(showManualIstighfarSheet = false) }
    }

    fun submitManualIstighfar(count: Int) {
        if (count <= 0) return
        val today = today()
        val before = store.todayCount(today)
        val updated = store.addToday(today, count)
        maybeRecordWin(today, updated)
        val crossedGoal = before < ISTIGHFAR_CHALLENGE_DAILY_GOAL && updated >= ISTIGHFAR_CHALLENGE_DAILY_GOAL
        val milestone = ISTIGHFAR_CHALLENGE_DAILY_GOAL
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                lifetimeCount = store.lifetimeCount(),
                manualRemainingToday = store.manualRemainingToday(today),
                showManualIstighfarSheet = false,
                isSubmittingManualIstighfar = true,
                errorMessage = null,
                showCelebration = crossedGoal || it.showCelebration,
                celebrationMilestone = if (crossedGoal) milestone else it.celebrationMilestone,
            )
        }
        recalculateLocalLeaderboard()
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSubmittingManualIstighfar = false) }
                    return@withLock
                }
                val total = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), challengeBadgeStore.getCurrentStreak(ChallengeType.ISTIGHFAR, today))
                if (result.isSuccess) store.onSyncSuccess(today, total)
                refreshStats(today.toString(), uid)
                _state.update {
                    it.copy(
                        isSubmittingManualIstighfar = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun subtractManualIstighfar(count: Int) {
        if (count <= 0) return
        val today = today()
        val updated = store.subtractToday(today, count)
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                lifetimeCount = store.lifetimeCount(),
                manualRemainingToday = store.manualRemainingToday(today),
                showManualIstighfarSheet = false,
                isSubmittingManualIstighfar = true,
                errorMessage = null,
            )
        }
        recalculateLocalLeaderboard()
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSubmittingManualIstighfar = false) }
                    return@withLock
                }
                val total = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), challengeBadgeStore.getCurrentStreak(ChallengeType.ISTIGHFAR, today))
                if (result.isSuccess) store.onSyncSuccess(today, total)
                refreshStats(today.toString(), uid)
                _state.update {
                    it.copy(
                        isSubmittingManualIstighfar = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun resetToday() {
        val today = today()
        val updated = store.resetToday(today)
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                lifetimeCount = store.lifetimeCount(),
                rank = 0,
                errorMessage = null,
            )
        }
    }

    fun onScreenLeft() {
        publishLifetimeTotal()
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) return@withLock
                val today = today()
                val total = store.todayCount(today)
                if (total == 0) return@withLock
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                _state.update { it.copy(isSyncing = true) }
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), challengeBadgeStore.getCurrentStreak(ChallengeType.ISTIGHFAR, today))
                if (result.isSuccess) store.onSyncSuccess(today, total)
                _state.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    private fun recalculateLocalLeaderboard() {
        val current = _state.value
        val uid = current.currentUid
        if (uid.isEmpty()) return

        val localCount = current.todayCount
        val localStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.ISTIGHFAR, today())
        val entries = current.leaderboard.toMutableList()

        val existingIndex = entries.indexOfFirst { it.uid == uid }
        if (existingIndex >= 0) {
            entries[existingIndex] = entries[existingIndex].copy(count = localCount, streak = localStreak)
        } else if (localCount > 0) {
            entries.add(
                IstighfarLeaderboardEntry(
                    uid = uid, countryCode = "", count = localCount,
                    rank = 0, rankChange = "new", streak = localStreak,
                )
            )
        }

        val ranked = entries.sortedByDescending { it.count }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
        val newRank = ranked.firstOrNull { it.uid == uid }?.rank ?: current.rank
        _state.update { it.copy(leaderboard = ranked, rank = newRank) }
    }

    private suspend fun refreshStats(dateKey: String, uid: String) {
        firebaseClient.fetchDayStats(dateKey, uid)
            .onSuccess { stats ->
                _state.update {
                    it.copy(
                        rank = stats.rank,
                        participantCount = stats.participantCount,
                        totalTodayIstighfar = stats.totalTodayIstighfar,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(errorMessage = error.message) }
            }
    }

    private fun maybeRecordWin(today: LocalDate, total: Int) {
        if (total > 0) {
            challengeBadgeStore.recordActivity(ChallengeType.ISTIGHFAR, today)
        }
        if (total >= ISTIGHFAR_CHALLENGE_DAILY_GOAL) {
            challengeBadgeStore.recordWin(ChallengeType.ISTIGHFAR, today)
        }
        _state.update { it.copy(currentStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.ISTIGHFAR, today)) }
    }

    private fun publishLifetimeTotal() {
        viewModelScope.launch {
            if (!firebaseClient.isConfigured()) return@launch
            firebaseClient.writeUserTotal(sessionStore.getOrCreateUid(), store.lifetimeCount())
        }
    }

    private fun today(): LocalDate = Clock.System.todayIn(cairoZone)
}
