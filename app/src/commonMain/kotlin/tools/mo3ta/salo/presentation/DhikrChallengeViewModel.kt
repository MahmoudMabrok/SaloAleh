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
        val count = store.todayCount(today)
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = count,
                isLoading = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val uid = sessionStore.getOrCreateUid()
            val dateKey = today.toString()

            if (firebaseClient.isConfigured()) {
                val mergedCount = firebaseClient.fetchUserCount(dateKey, uid)
                    .getOrNull()
                    ?.let { remoteCount -> maxOf(count, remoteCount) }
                    ?: count

                if (mergedCount != count) store.setTodayCount(today, mergedCount)
                _state.update {
                    it.copy(
                        todayCount = mergedCount,
                        isLoading = false,
                    )
                }
                // Fetch rank and participant count directly, not gated on write success
                refreshStats(dateKey, uid)
            } else {
                _state.update { it.copy(isLoading = false) }
            }

            syncCurrentDay(refreshStats = false)
        }
    }

    fun onDhikrTap() {
        val today = today()
        val updated = store.incrementToday(today)
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                isLoading = false,
                errorMessage = null,
            )
        }
        syncCurrentDay(refreshStats = true)
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
        syncCurrentDay(refreshStats = true)
    }

    private fun syncCurrentDay(refreshStats: Boolean) {
        viewModelScope.launch {
            syncMutex.withLock {
                val today = today()
                val dateKey = today.toString()
                val count = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()

                _state.update {
                    it.copy(
                        dateKey = dateKey,
                        todayCount = count,
                        isSyncing = true,
                        errorMessage = null,
                    )
                }

                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSyncing = false) }
                    return@withLock
                }

                val countryCode = countryCodeProvider.get()
                val writeResult = firebaseClient.writeUserDay(
                    dateKey = dateKey,
                    uid = uid,
                    count = count,
                    countryCode = countryCode,
                )

                val writeError = writeResult.exceptionOrNull()
                if (writeError != null) {
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            errorMessage = writeError.message,
                        )
                    }
                    return@withLock
                }

                if (refreshStats) refreshStats(dateKey, uid)
                _state.update { it.copy(isSyncing = false) }
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
