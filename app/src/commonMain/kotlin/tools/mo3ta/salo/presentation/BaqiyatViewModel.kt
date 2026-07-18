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
import tools.mo3ta.salo.domain.BaqiyatLeaderboardEntry
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
                    currentStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.BAQIYAT, today),
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

    /** Marks [phrase] green; once all phrases are marked, a full cycle completes: +1 to the counter, all reset. */
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
                cyclesCompleted = updated,
                showCelebration = true,
                celebrationMilestone = updated,
            )
        }
        recalculateLocalLeaderboard()
    }

    /** Reshuffles the card order on user request, preserving any already-marked phrases. */
    fun onShuffle() {
        _state.update { it.copy(phraseOrder = shuffledActivePhrases(it.phraseOrder)) }
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

    /**
     * Patch the current user's row into the in-memory leaderboard from local state so tapping
     * reflects on the board immediately, without waiting for a remote fetch. Mirrors the other
     * challenges: update the existing row (or insert a synthetic "new" one) with the local count
     * and streak, then re-sort by count and re-assign ranks.
     */
    private fun recalculateLocalLeaderboard() {
        val current = _state.value
        val uid = current.currentUid
        if (uid.isEmpty()) return

        val localCount = current.cyclesCompleted
        val localStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.BAQIYAT, today())
        val entries = current.leaderboard.toMutableList()

        val existingIndex = entries.indexOfFirst { it.uid == uid }
        if (existingIndex >= 0) {
            entries[existingIndex] = entries[existingIndex].copy(count = localCount, streak = localStreak)
        } else if (localCount > 0) {
            entries.add(
                BaqiyatLeaderboardEntry(
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

    /**
     * Any activity (a single completed cycle) keeps the daily streak alive; reaching the daily
     * cycles goal additionally wins the day, bumping the challenge badge count by 1 (once per day).
     */
    private fun maybeRecordWin(today: LocalDate, cycles: Int) {
        if (cycles > 0) {
            challengeBadgeStore.recordActivity(ChallengeType.BAQIYAT, today)
        }
        if (cycles >= BAQIYAT_CHALLENGE_DAILY_GOAL) {
            challengeBadgeStore.recordWin(ChallengeType.BAQIYAT, today)
        }
        _state.update { it.copy(currentStreak = challengeBadgeStore.getCurrentStreak(ChallengeType.BAQIYAT, today)) }
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
