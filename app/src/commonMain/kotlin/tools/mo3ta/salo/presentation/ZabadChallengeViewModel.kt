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
import tools.mo3ta.salo.domain.ZabadLeaderboardEntry
import tools.mo3ta.salo.data.zabad.ZabadChallengeFirebaseClient
import tools.mo3ta.salo.data.zabad.ZabadChallengeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.ZABAD_CHALLENGE_DAILY_GOAL

class ZabadChallengeViewModel(
    private val store: ZabadChallengeStore,
    private val firebaseClient: ZabadChallengeFirebaseClient,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
    private val challengeBadgeStore: ChallengeBadgeStore,
) : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(ZabadChallengeUiState())
    val state: StateFlow<ZabadChallengeUiState> = _state.asStateFlow()

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
        loadLeaderboard()
        val today = today()
        sampleSea(today)
        viewModelScope.launch {
            val uid = sessionStore.getOrCreateUid()

            // Flush any previous day's pending that wasn't synced before the day rolled over
            val prev = store.previousEntry(today)
            if (prev != null && firebaseClient.isConfigured()) {
                val (prevDate, prevTotal) = prev
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(prevDate, uid, prevTotal, countryCode, sessionStore.getPublishedName())
                if (result.isSuccess) store.clearPreviousPending()
            }

            // Show local total immediately — tapping is never gated on network
            _state.update {
                it.copy(
                    dateKey = today.toString(),
                    todayCount = store.todayCount(today),
                    manualRemainingToday = store.manualRemainingToday(today),
                    isLoading = false,
                    errorMessage = null,
                )
            }

            if (!firebaseClient.isConfigured()) return@launch

            // Background: fetch remote baseline and advance local if remote is higher
            // (e.g. user counted on another device or the previous session synced more)
            val remoteCount = firebaseClient.fetchUserCount(today.toString(), uid).getOrNull()
            if (remoteCount != null) {
                store.updateRemoteBaseline(today, remoteCount)
                _state.update { it.copy(todayCount = store.todayCount(today)) }
            }
            maybeRecordWin(today, store.todayCount(today))

            refreshStats(today.toString(), uid)
        }
    }

    fun onZabadTap() {
        val today = today()
        val updated = store.incrementToday(today)
        maybeRecordWin(today, updated)
        val isMilestone = updated > 0 && updated % ZABAD_CHALLENGE_DAILY_GOAL == 0
        if (isMilestone) store.recordWash(today, Clock.System.now().toEpochMilliseconds())
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                errorMessage = null,
                showCelebration = isMilestone || it.showCelebration,
                celebrationMilestone = if (isMilestone) updated else it.celebrationMilestone,
                elapsedSinceWashMillis = if (isMilestone) 0L else it.elapsedSinceWashMillis,
                roundsToday = store.roundsToday(today),
                isWashing = isMilestone,
            )
        }
        recalculateLocalLeaderboard()
    }

    fun onCelebrationDismissed() {
        _state.update { it.copy(showCelebration = false, isWashing = false) }
    }

    fun showManualZabadSheet() {
        _state.update { it.copy(showManualZabadSheet = true) }
    }

    fun dismissManualZabadSheet() {
        _state.update { it.copy(showManualZabadSheet = false) }
    }

    /** Record a batch of zabad counted outside the app (silently, on fingers, with a tasbih). */
    fun submitManualZabad(count: Int) {
        if (count <= 0) return
        val today = today()
        val before = store.todayCount(today)
        val updated = store.addToday(today, count)
        maybeRecordWin(today, updated)
        val crossedMilestone = updated / ZABAD_CHALLENGE_DAILY_GOAL > before / ZABAD_CHALLENGE_DAILY_GOAL
        val milestone = updated / ZABAD_CHALLENGE_DAILY_GOAL * ZABAD_CHALLENGE_DAILY_GOAL
        if (crossedMilestone) store.recordWash(
            today,
            Clock.System.now().toEpochMilliseconds(),
            updated / ZABAD_CHALLENGE_DAILY_GOAL - before / ZABAD_CHALLENGE_DAILY_GOAL,
        )
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                manualRemainingToday = store.manualRemainingToday(today),
                showManualZabadSheet = false,
                isSubmittingManualZabad = true,
                errorMessage = null,
                showCelebration = (crossedMilestone && milestone > 0) || it.showCelebration,
                celebrationMilestone = if (crossedMilestone && milestone > 0) milestone else it.celebrationMilestone,
                elapsedSinceWashMillis = if (crossedMilestone) 0L else it.elapsedSinceWashMillis,
                roundsToday = store.roundsToday(today),
                isWashing = crossedMilestone,
            )
        }
        recalculateLocalLeaderboard()
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSubmittingManualZabad = false) }
                    return@withLock
                }
                val total = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName())
                if (result.isSuccess) store.onSyncSuccess(today, total)
                refreshStats(today.toString(), uid)
                _state.update {
                    it.copy(
                        isSubmittingManualZabad = false,
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
                rank = 0,
                errorMessage = null,
            )
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
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName())
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
        val entries = current.leaderboard.toMutableList()

        val existingIndex = entries.indexOfFirst { it.uid == uid }
        if (existingIndex >= 0) {
            entries[existingIndex] = entries[existingIndex].copy(count = localCount)
        } else if (localCount > 0) {
            entries.add(
                ZabadLeaderboardEntry(
                    uid = uid, countryCode = "", count = localCount,
                    rank = 0, rankChange = "new",
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
                        totalTodayZabad = stats.totalTodayZabad,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(errorMessage = error.message) }
            }
    }

    /**
     * Read the silence behind the sea, once. Called on screen entry; a wash re-samples it by zeroing
     * [ZabadChallengeUiState.elapsedSinceWashMillis] directly. Nothing else moves the water, so the
     * level, foam and murk the user opens into are the ones they tap on.
     */
    private fun sampleSea(today: LocalDate) {
        val lastWash = store.lastWashTimestamp()
        _state.update {
            it.copy(
                elapsedSinceWashMillis =
                    if (lastWash == 0L) 0L
                    else (Clock.System.now().toEpochMilliseconds() - lastWash).coerceAtLeast(0L),
                roundsToday = store.roundsToday(today),
            )
        }
    }

    /** Reaching the daily goal wins the day: the challenge badge count goes up by 1 (once per day). */
    private fun maybeRecordWin(today: LocalDate, total: Int) {
        if (total >= ZABAD_CHALLENGE_DAILY_GOAL) {
            challengeBadgeStore.recordWin(ChallengeType.ZABAD, today)
        }
    }

    private fun today(): LocalDate = Clock.System.todayIn(cairoZone)
}
