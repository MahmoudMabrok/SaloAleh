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
import tools.mo3ta.salo.data.dhikr.DhikrChallengeFirebaseClient
import tools.mo3ta.salo.data.dhikr.DhikrChallengeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore

class DhikrChallengeViewModel(
    private val store: DhikrChallengeStore,
    private val firebaseClient: DhikrChallengeFirebaseClient,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
) : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(DhikrChallengeUiState())
    val state: StateFlow<DhikrChallengeUiState> = _state.asStateFlow()

    init {
        loadLeaderboard()
    }

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
        val today = today()
        viewModelScope.launch {
            val uid = sessionStore.getOrCreateUid()

            // Flush any previous day's pending that wasn't synced before the day rolled over
            val prev = store.previousEntry(today)
            if (prev != null && firebaseClient.isConfigured()) {
                val (prevDate, prevTotal) = prev
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(prevDate, uid, prevTotal, countryCode, publishedNickname())
                if (result.isSuccess) store.clearPreviousPending()
            }

            // Show local total immediately — tapping is never gated on network
            _state.update {
                it.copy(
                    dateKey = today.toString(),
                    todayCount = store.todayCount(today),
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

            refreshStats(today.toString(), uid)
        }
    }

    fun onDhikrTap() {
        val today = today()
        val updated = store.incrementToday(today)
        val isMilestone = updated > 0 && updated % 100 == 0
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                errorMessage = null,
                showCelebration = isMilestone || it.showCelebration,
                celebrationMilestone = if (isMilestone) updated else it.celebrationMilestone,
            )
        }
    }

    fun onCelebrationDismissed() {
        _state.update { it.copy(showCelebration = false) }
    }

    fun showManualDhikrSheet() {
        _state.update { it.copy(showManualDhikrSheet = true) }
    }

    fun dismissManualDhikrSheet() {
        _state.update { it.copy(showManualDhikrSheet = false) }
    }

    /** Record a batch of dhikr counted outside the app (silently, on fingers, with a tasbih). */
    fun submitManualDhikr(count: Int) {
        if (count <= 0) return
        val today = today()
        val before = store.todayCount(today)
        val updated = store.addToday(today, count)
        val crossedMilestone = updated / 100 > before / 100
        val milestone = updated / 100 * 100
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                showManualDhikrSheet = false,
                isSubmittingManualDhikr = true,
                errorMessage = null,
                showCelebration = (crossedMilestone && milestone > 0) || it.showCelebration,
                celebrationMilestone = if (crossedMilestone && milestone > 0) milestone else it.celebrationMilestone,
            )
        }
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSubmittingManualDhikr = false) }
                    return@withLock
                }
                val total = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                val nickname = publishedNickname()
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, nickname)
                if (result.isSuccess) store.onSyncSuccess(today, total)
                refreshStats(today.toString(), uid)
                _state.update {
                    it.copy(
                        isSubmittingManualDhikr = false,
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
                val nickname = publishedNickname()

                _state.update { it.copy(isSyncing = true) }
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, nickname)
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

    private suspend fun refreshStats(dateKey: String, uid: String) {
        firebaseClient.fetchDayStats(dateKey, uid)
            .onSuccess { stats ->
                _state.update {
                    it.copy(
                        rank = stats.rank,
                        participantCount = stats.participantCount,
                        totalTodayDhikr = stats.totalTodayDhikr,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(errorMessage = error.message) }
            }
    }

    private fun publishedNickname(): String =
        sessionStore.getNickname()?.takeIf { sessionStore.isNicknameEnabled }.orEmpty()

    private fun today(): LocalDate = Clock.System.todayIn(cairoZone)
}
