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
import tools.mo3ta.salo.data.ghars.GharsChallengeFirebaseClient
import tools.mo3ta.salo.data.ghars.GharsChallengeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.domain.GHARS_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.GHARS_GROVE_SIZE
import tools.mo3ta.salo.domain.GharsLeaderboardEntry

class GharsChallengeViewModel(
    private val store: GharsChallengeStore,
    private val firebaseClient: GharsChallengeFirebaseClient,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
    private val challengeBadgeStore: ChallengeBadgeStore,
) : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(GharsChallengeUiState())
    val state: StateFlow<GharsChallengeUiState> = _state.asStateFlow()

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
            val local = store.todayCount(today)
            _state.update {
                it.copy(
                    dateKey = today.toString(),
                    todayCount = local,
                    completedGroves = local / GHARS_GROVE_SIZE,
                    lifetimePalms = store.lifetimeCount(),
                    manualRemainingToday = store.manualRemainingToday(today),
                    isLoading = false,
                    errorMessage = null,
                )
            }

            if (!firebaseClient.isConfigured()) return@launch

            // Background: fetch remote baseline and advance local if remote is higher
            val remoteCount = firebaseClient.fetchUserCount(today.toString(), uid).getOrNull()
            if (remoteCount != null) {
                store.updateRemoteBaseline(today, remoteCount)
                val updated = store.todayCount(today)
                _state.update { it.copy(todayCount = updated, completedGroves = updated / GHARS_GROVE_SIZE) }
            }
            maybeRecordWin(today, store.todayCount(today))

            refreshStats(today.toString(), uid)
        }
    }

    fun onGharsTap() {
        val today = today()
        val updated = store.incrementToday(today)
        maybeRecordWin(today, updated)
        val completedAGrove = updated % GHARS_GROVE_SIZE == 0 && updated > 0
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                completedGroves = updated / GHARS_GROVE_SIZE,
                lifetimePalms = store.lifetimeCount(),
                errorMessage = null,
                groveToastNumber = if (completedAGrove) updated / GHARS_GROVE_SIZE else it.groveToastNumber,
                plantSerial = it.plantSerial + 1,
            )
        }
        recalculateLocalLeaderboard()
    }

    fun onGroveToastShown() {
        _state.update { it.copy(groveToastNumber = 0) }
    }

    fun showManualGharsSheet() {
        _state.update { it.copy(showManualGharsSheet = true) }
    }

    fun dismissManualGharsSheet() {
        _state.update { it.copy(showManualGharsSheet = false) }
    }

    /** Record a batch of tasbeeh counted outside the app (silently, on fingers, with a tasbih). */
    fun submitManualGhars(count: Int) {
        if (count <= 0) return
        val today = today()
        val before = store.todayCount(today)
        val updated = store.addToday(today, count)
        maybeRecordWin(today, updated)
        val crossedGrove = updated / GHARS_GROVE_SIZE > before / GHARS_GROVE_SIZE
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                completedGroves = updated / GHARS_GROVE_SIZE,
                lifetimePalms = store.lifetimeCount(),
                manualRemainingToday = store.manualRemainingToday(today),
                showManualGharsSheet = false,
                isSubmittingManualGhars = true,
                errorMessage = null,
                groveToastNumber = if (crossedGrove) updated / GHARS_GROVE_SIZE else it.groveToastNumber,
                plantSerial = it.plantSerial + 1,
            )
        }
        recalculateLocalLeaderboard()
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSubmittingManualGhars = false) }
                    return@withLock
                }
                val total = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), challengeBadgeStore.getCurrentStreak(ChallengeType.GHARS, today))
                if (result.isSuccess) store.onSyncSuccess(today, total)
                refreshStats(today.toString(), uid)
                _state.update {
                    it.copy(
                        isSubmittingManualGhars = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    /** Subtract a mistakenly-entered batch from today's count. Floored at 0; syncs the corrected total. */
    fun subtractManualGhars(count: Int) {
        if (count <= 0) return
        val today = today()
        val updated = store.subtractToday(today, count)
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                completedGroves = updated / GHARS_GROVE_SIZE,
                manualRemainingToday = store.manualRemainingToday(today),
                showManualGharsSheet = false,
                isSubmittingManualGhars = true,
                errorMessage = null,
            )
        }
        recalculateLocalLeaderboard()
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSubmittingManualGhars = false) }
                    return@withLock
                }
                val total = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), challengeBadgeStore.getCurrentStreak(ChallengeType.GHARS, today))
                if (result.isSuccess) store.onSyncSuccess(today, total)
                refreshStats(today.toString(), uid)
                _state.update {
                    it.copy(
                        isSubmittingManualGhars = false,
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
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), challengeBadgeStore.getCurrentStreak(ChallengeType.GHARS, today))
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
        val localStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.GHARS, today())
        val entries = current.leaderboard.toMutableList()

        val existingIndex = entries.indexOfFirst { it.uid == uid }
        if (existingIndex >= 0) {
            entries[existingIndex] = entries[existingIndex].copy(count = localCount, streak = localStreak)
        } else if (localCount > 0) {
            entries.add(
                GharsLeaderboardEntry(
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
                        totalTodayGhars = stats.totalTodayGhars,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(errorMessage = error.message) }
            }
    }

    /** Reaching the daily goal wins the day: the challenge badge count goes up by 1 (once per day). */
    private fun maybeRecordWin(today: LocalDate, total: Int) {
        if (total >= GHARS_CHALLENGE_DAILY_GOAL) {
            challengeBadgeStore.recordWin(ChallengeType.GHARS, today)
        }
    }

    private fun today(): LocalDate = Clock.System.todayIn(cairoZone)
}
