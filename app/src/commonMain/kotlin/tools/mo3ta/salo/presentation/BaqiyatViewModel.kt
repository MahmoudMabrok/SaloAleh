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
import tools.mo3ta.salo.data.baqiyat.BaqiyatFirebaseClient
import tools.mo3ta.salo.data.baqiyat.BaqiyatPhrase
import tools.mo3ta.salo.data.baqiyat.BaqiyatStore
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.BAQIYAT_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.ChallengeType

class BaqiyatViewModel(
    private val store: BaqiyatStore,
    private val firebaseClient: BaqiyatFirebaseClient,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
    private val challengeBadgeStore: ChallengeBadgeStore,
) : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")
    private val activePhrases = BaqiyatPhrase.entries.take(4)
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(BaqiyatUiState(currentUid = sessionStore.getOrCreateUid()))
    val state: StateFlow<BaqiyatUiState> = _state.asStateFlow()

    fun onScreenEntered() {
        val today = today()
        viewModelScope.launch {
            val uid = sessionStore.getOrCreateUid()

            // Flush any previous day's pending cycles that weren't synced before the day rolled over
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
                    cyclesCompleted = store.todayCount(today),
                    phraseOrder = activePhrases,
                    tappedPhrases = emptySet(),
                    isLoading = false,
                    errorMessage = null,
                )
            }

            if (!firebaseClient.isConfigured()) return@launch

            // Background: fetch remote baseline and advance local if remote is higher
            val remoteCount = firebaseClient.fetchUserCount(today.toString(), uid).getOrNull()
            if (remoteCount != null) {
                store.updateRemoteBaseline(today, remoteCount)
                _state.update { it.copy(cyclesCompleted = store.todayCount(today)) }
            }
            maybeRecordWin(today, store.todayCount(today))

            refreshLeaderboard(today.toString(), uid)
        }
    }

    /** Hides [phrase]; once all phrases are hidden, a full cycle completes: +1 to the counter, all shown again. */
    fun onPhraseTap(phrase: BaqiyatPhrase) {
        val current = _state.value
        if (phrase in current.tappedPhrases) return

        val tapped = current.tappedPhrases + phrase
        if (tapped.size < BaqiyatPhrase.entries.size) {
            _state.update { it.copy(tappedPhrases = tapped) }
            return
        }

        val today = today()
        val updated = store.incrementToday(today)
        maybeRecordWin(today, updated)
        _state.update {
            it.copy(
                tappedPhrases = emptySet(),
                phraseOrder = shuffledActivePhrases(current.phraseOrder),
                cyclesCompleted = updated,
                showCelebration = true,
                celebrationMilestone = updated,
            )
        }
    }

    fun onCelebrationDismissed() {
        _state.update { it.copy(showCelebration = false) }
    }

    fun onLeaderboardOpened() {
        _state.update { it.copy(showLeaderboard = true) }
    }

    fun onLeaderboardClosed() {
        _state.update { it.copy(showLeaderboard = false) }
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
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), challengeBadgeStore.getCurrentStreak(ChallengeType.BAQIYAT, today))
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

    private suspend fun refreshLeaderboard(dateKey: String, uid: String) {
        _state.update { it.copy(isLeaderboardLoading = true) }
        val statsResult = firebaseClient.fetchDayStats(dateKey, uid)
        val leaderboardResult = firebaseClient.fetchLeaderboard(dateKey)
        val stats = statsResult.getOrNull()
        val entries = leaderboardResult.getOrNull().orEmpty()

        _state.update {
            it.copy(
                leaderboard = entries,
                rank = stats?.rank ?: entries.firstOrNull { entry -> entry.uid == uid }?.rank ?: 0,
                participantCount = stats?.participantCount ?: entries.size,
                totalTodayBaqiyat = stats?.totalTodayBaqiyat ?: 0,
                isLeaderboardLoading = false,
                errorMessage = statsResult.exceptionOrNull()?.message ?: leaderboardResult.exceptionOrNull()?.message,
            )
        }
    }

    /** Reaching the daily cycles goal wins the day: the challenge badge count goes up by 1 (once per day). */
    private fun maybeRecordWin(today: LocalDate, cycles: Int) {
        if (cycles >= BAQIYAT_CHALLENGE_DAILY_GOAL) {
            challengeBadgeStore.recordWin(ChallengeType.BAQIYAT, today)
        }
    }

    private fun today(): LocalDate = Clock.System.todayIn(cairoZone)

    private fun shuffledActivePhrases(currentOrder: List<BaqiyatPhrase>): List<BaqiyatPhrase> {
        val shuffled = activePhrases.shuffled()
        return if (activePhrases.size > 1 && shuffled == currentOrder) {
            activePhrases.drop(1) + activePhrases.first()
        } else {
            shuffled
        }
    }
}
