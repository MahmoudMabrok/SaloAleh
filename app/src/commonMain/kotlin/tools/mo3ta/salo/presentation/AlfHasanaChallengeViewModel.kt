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
import tools.mo3ta.salo.data.alfhasana.AlfHasanaChallengeFirebaseClient
import tools.mo3ta.salo.data.alfhasana.AlfHasanaChallengeStore
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.ALF_HASANA_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.AlfHasanaLeaderboardEntry
import tools.mo3ta.salo.domain.ChallengeType

/**
 * ViewModel for the "ألف حسنة" tasbih challenge.
 *
 * Recomposition strategy: the running tap count is exposed as a dedicated [todayCount] flow, separate
 * from the structural [state]. A tap only emits on [todayCount] (plus the occasional milestone/streak
 * update on [state]), so only the counter widget recomposes — the rest of the screen stays put.
 */
class AlfHasanaChallengeViewModel(
    private val store: AlfHasanaChallengeStore,
    private val firebaseClient: AlfHasanaChallengeFirebaseClient,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
    private val challengeBadgeStore: ChallengeBadgeStore,
) : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(AlfHasanaChallengeUiState())
    val state: StateFlow<AlfHasanaChallengeUiState> = _state.asStateFlow()

    /** The running tap total for today — the only thing that changes on a tap. */
    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    fun onLeaderboardOpened() {
        recalculateLocalLeaderboard()
        _state.update { it.copy(showLeaderboard = true) }
    }

    fun onLeaderboardClosed() {
        _state.update { it.copy(showLeaderboard = false) }
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

    fun onScreenEntered() {
        loadLeaderboard()
        val today = today()
        viewModelScope.launch {
            val uid = sessionStore.getOrCreateUid()

            // Flush any previous day's pending that wasn't synced before the day rolled over.
            val prev = store.previousEntry(today)
            if (prev != null && firebaseClient.isConfigured()) {
                val (prevDate, prevTotal) = prev
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(prevDate, uid, prevTotal, countryCode, sessionStore.getPublishedName())
                if (result.isSuccess) store.clearPreviousPending()
            }

            // Show local total immediately — tapping is never gated on network.
            _todayCount.value = store.todayCount(today)
            _state.update {
                it.copy(
                    dateKey = today.toString(),
                    manualRemainingToday = store.manualRemainingToday(today),
                    currentStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.ALF_HASANA, today),
                    isLoading = false,
                    errorMessage = null,
                )
            }

            if (!firebaseClient.isConfigured()) return@launch

            // Background: fetch remote baseline and advance local if remote is higher.
            val remoteCount = firebaseClient.fetchUserCount(today.toString(), uid).getOrNull()
            if (remoteCount != null) {
                store.updateRemoteBaseline(today, remoteCount)
                _todayCount.value = store.todayCount(today)
            }
            maybeRecordWin(today, store.todayCount(today))

            refreshStats(today.toString(), uid)
        }
    }

    fun onTasbihTap() {
        val today = today()
        val before = _todayCount.value
        val updated = store.incrementToday(today)
        _todayCount.value = updated
        maybeRecordWin(today, updated)
        // Reward only on reaching the daily goal — no sub-goal milestones.
        if (before < ALF_HASANA_CHALLENGE_DAILY_GOAL && updated >= ALF_HASANA_CHALLENGE_DAILY_GOAL) {
            _state.update {
                it.copy(showCelebration = true, celebrationMilestone = ALF_HASANA_CHALLENGE_DAILY_GOAL, errorMessage = null)
            }
        }
    }

    fun onCelebrationDismissed() {
        _state.update { it.copy(showCelebration = false) }
    }

    fun showManualSheet() {
        _state.update { it.copy(showManualSheet = true) }
    }

    fun dismissManualSheet() {
        _state.update { it.copy(showManualSheet = false) }
    }

    /** Record a batch of tasbih counted outside the app (silently, on fingers, with a tasbih). */
    fun submitManual(count: Int) {
        if (count <= 0) return
        val today = today()
        val before = store.todayCount(today)
        val updated = store.addToday(today, count)
        _todayCount.value = updated
        maybeRecordWin(today, updated)
        // Reward only on reaching the daily goal — no sub-goal milestones.
        val crossedGoal = before < ALF_HASANA_CHALLENGE_DAILY_GOAL && updated >= ALF_HASANA_CHALLENGE_DAILY_GOAL
        _state.update {
            it.copy(
                dateKey = today.toString(),
                manualRemainingToday = store.manualRemainingToday(today),
                showManualSheet = false,
                isSubmittingManual = true,
                errorMessage = null,
                showCelebration = crossedGoal || it.showCelebration,
                celebrationMilestone = if (crossedGoal) ALF_HASANA_CHALLENGE_DAILY_GOAL else it.celebrationMilestone,
            )
        }
        syncCorrectedTotal(today)
    }

    /** Subtract a mistakenly-entered batch from today's count. Floored at 0; syncs the corrected total. */
    fun subtractManual(count: Int) {
        if (count <= 0) return
        val today = today()
        val updated = store.subtractToday(today, count)
        _todayCount.value = updated
        _state.update {
            it.copy(
                dateKey = today.toString(),
                manualRemainingToday = store.manualRemainingToday(today),
                showManualSheet = false,
                isSubmittingManual = true,
                errorMessage = null,
            )
        }
        syncCorrectedTotal(today)
    }

    private fun syncCorrectedTotal(today: LocalDate) {
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSubmittingManual = false) }
                    return@withLock
                }
                val total = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(
                    today.toString(), uid, total, countryCode, sessionStore.getPublishedName(),
                    challengeBadgeStore.getCurrentStreak(ChallengeType.ALF_HASANA, today),
                )
                if (result.isSuccess) store.onSyncSuccess(today, total)
                refreshStats(today.toString(), uid)
                _state.update {
                    it.copy(
                        isSubmittingManual = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun onScreenLeft() {
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) return@withLock
                val today = today()
                val total = store.todayCount(today)
                if (total == 0) return@withLock
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                _state.update { it.copy(isSyncing = true) }
                val result = firebaseClient.writeUserDay(
                    today.toString(), uid, total, countryCode, sessionStore.getPublishedName(),
                    challengeBadgeStore.getCurrentStreak(ChallengeType.ALF_HASANA, today),
                )
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

    /**
     * Recompute the current user's local placement. Deliberately NOT called on every tap — only on
     * leaderboard open and after a sync — so tapping stays free of leaderboard work.
     */
    private fun recalculateLocalLeaderboard() {
        val current = _state.value
        val uid = current.currentUid
        if (uid.isEmpty()) return

        val localCount = _todayCount.value
        val localStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.ALF_HASANA, today())
        val entries = current.leaderboard.toMutableList()

        val existingIndex = entries.indexOfFirst { it.uid == uid }
        if (existingIndex >= 0) {
            entries[existingIndex] = entries[existingIndex].copy(count = localCount, streak = localStreak)
        } else if (localCount > 0) {
            entries.add(
                AlfHasanaLeaderboardEntry(
                    uid = uid, countryCode = "", count = localCount,
                    rank = 0, rankChange = "new", streak = localStreak,
                ),
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
                        totalTodayAlfHasana = stats.totalTodayAlfHasana,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(errorMessage = error.message) }
            }
    }

    /**
     * Any activity (a single tasbiha) keeps the daily streak alive; reaching the daily goal
     * additionally wins the day, bumping the challenge badge count by 1 (once per day). Only emits
     * on [state] when the streak value actually changes, so repeat taps stay cheap.
     */
    private fun maybeRecordWin(today: LocalDate, total: Int) {
        if (total > 0) {
            challengeBadgeStore.recordActivity(ChallengeType.ALF_HASANA, today)
        }
        if (total >= ALF_HASANA_CHALLENGE_DAILY_GOAL) {
            challengeBadgeStore.recordWin(ChallengeType.ALF_HASANA, today)
        }
        val streak = challengeBadgeStore.getCurrentStreak(ChallengeType.ALF_HASANA, today)
        if (streak != _state.value.currentStreak) {
            _state.update { it.copy(currentStreak = streak) }
        }
    }

    private fun today(): LocalDate = Clock.System.todayIn(cairoZone)
}
