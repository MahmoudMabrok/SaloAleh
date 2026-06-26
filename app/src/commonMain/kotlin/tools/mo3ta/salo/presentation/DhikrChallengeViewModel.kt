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

    fun onScreenEntered() {
        val today = today()
        viewModelScope.launch {
            val uid = sessionStore.getOrCreateUid()

            // Push any previous day's count that wasn't synced yet
            val prev = store.previousEntry(today)
            if (prev != null && firebaseClient.isConfigured()) {
                val (prevDate, prevCount) = prev
                val countryCode = countryCodeProvider.get()
                firebaseClient.writeUserDay(prevDate, uid, prevCount, countryCode)
            }

            val count = store.todayCount(today)
            // Allow tapping immediately with local count — Firebase sync is background-only
            _state.update {
                it.copy(
                    dateKey = today.toString(),
                    todayCount = count,
                    isLoading = false,
                    errorMessage = null,
                )
            }

            if (!firebaseClient.isConfigured()) return@launch

            // Merge remote count in background without blocking the UI
            val remoteCount = firebaseClient.fetchUserCount(today.toString(), uid).getOrNull()
            if (remoteCount != null) {
                val currentCount = store.todayCount(today)
                val merged = maxOf(currentCount, remoteCount)
                if (merged != currentCount) {
                    store.setTodayCount(today, merged)
                    _state.update { it.copy(todayCount = merged) }
                }
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

    fun resetToday() {
        val today = today()
        val updated = store.setTodayCount(today, 0)
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
                val dateKey = today.toString()
                val count = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()

                _state.update { it.copy(isSyncing = true) }
                val result = firebaseClient.writeUserDay(dateKey, uid, count, countryCode)
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

    private fun today(): LocalDate = Clock.System.todayIn(cairoZone)
}
