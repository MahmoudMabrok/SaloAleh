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
import tools.mo3ta.salo.data.albaqara.AlBaqaraChallengeFirebaseClient
import tools.mo3ta.salo.data.albaqara.AlBaqaraChallengeStore
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.data.quran.QuranChallengeFirebaseClient
import tools.mo3ta.salo.data.quran.QuranChallengeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.ALBAQARA_PAGE_COUNT
import tools.mo3ta.salo.domain.AlBaqaraLeaderboardEntry
import tools.mo3ta.salo.domain.ChallengeType

class AlBaqaraChallengeViewModel(
    private val store: AlBaqaraChallengeStore,
    private val firebaseClient: AlBaqaraChallengeFirebaseClient,
    private val sessionStore: MohamedLoversSessionStore,
    private val countryCodeProvider: CountryCodeProvider,
    private val quranStore: QuranChallengeStore,
    private val quranFirebaseClient: QuranChallengeFirebaseClient,
    private val challengeBadgeStore: ChallengeBadgeStore,
) : ViewModel() {

    private val cairoZone = TimeZone.of("Africa/Cairo")
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(AlBaqaraChallengeUiState())
    val state: StateFlow<AlBaqaraChallengeUiState> = _state.asStateFlow()

    fun onLeaderboardOpened() {
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
        publishLifetimeTotal()
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

            // Show local total immediately — tapping is never gated on network.
            // Seed the Quran count too so a credit dialog opens against a real number.
            _state.update {
                it.copy(
                    dateKey = today.toString(),
                    todayCount = store.todayCount(today),
                    quranTodayCount = quranStore.todayCount(today),
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

            // Refresh the Quran baseline so the before→after row is accurate, not stale.
            if (quranFirebaseClient.isConfigured()) {
                val quranRemote = quranFirebaseClient.fetchUserCount(today.toString(), uid).getOrNull()
                if (quranRemote != null) {
                    quranStore.updateRemoteBaseline(today, quranRemote)
                    _state.update { it.copy(quranTodayCount = quranStore.todayCount(today)) }
                }
            }

            refreshStats(today.toString(), uid)
        }
    }

    fun onReadTap() {
        val today = today()
        val updated = store.incrementToday(today)
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                quranTodayCount = quranStore.todayCount(today),
                showQuranCreditDialog = true,
                errorMessage = null,
            )
        }
        recalculateLocalLeaderboard()
    }

    fun onQuranCreditDismissed() {
        _state.update { it.copy(showQuranCreditDialog = false) }
    }

    /**
     * Credit the 48 pages of Al-Baqara to the Quran challenge. Runs under [syncMutex] so a
     * second credit can't corrupt the pending ledger while the first write is in flight.
     *
     * Order matters: fetch the Quran remote baseline *before* writing, because [writeUserDay]
     * is a blind overwrite with no server-side max — writing on a stale local count would
     * destroy a higher remote total. If the baseline fetch fails (or Firebase is unconfigured),
     * the 48 pages still land in the local pending ledger and reconcile on the next Quran-screen
     * visit, but no immediate write is issued.
     */
    fun creditQuranPages() {
        val today = today()
        _state.update { it.copy(isCreditingQuran = true) }
        viewModelScope.launch {
            syncMutex.withLock {
                val uid = sessionStore.getOrCreateUid()

                // 1. Fetch the Quran remote baseline first — the correctness constraint.
                val configured = quranFirebaseClient.isConfigured()
                val baselineFetched = if (configured) {
                    val remote = quranFirebaseClient.fetchUserCount(today.toString(), uid)
                    remote.getOrNull()?.let { quranStore.updateRemoteBaseline(today, it) }
                    remote.isSuccess
                } else {
                    false
                }

                // 2. Add the pages locally (offline-safe; always happens).
                quranStore.addToday(today, ALBAQARA_PAGE_COUNT)

                // 3. 48 clears the Quran daily goal — record the win and keep the streak alive.
                challengeBadgeStore.recordActivity(ChallengeType.QURAN, today)
                challengeBadgeStore.recordWin(ChallengeType.QURAN, today)

                // 4. Only write when the baseline was actually fetched — never on a stale count.
                if (configured && baselineFetched) {
                    val total = quranStore.todayCount(today)
                    val countryCode = countryCodeProvider.get()
                    val streak = challengeBadgeStore.getCurrentStreak(ChallengeType.QURAN, today)
                    val result = quranFirebaseClient.writeUserDay(
                        today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), streak,
                    )
                    if (result.isSuccess) quranStore.onSyncSuccess(today, total)
                }

                // 5. Refresh the count so a subsequent reading's dialog is accurate.
                _state.update {
                    it.copy(
                        quranTodayCount = quranStore.todayCount(today),
                        isCreditingQuran = false,
                        showQuranCreditDialog = false,
                    )
                }
            }
        }
    }

    /** Undo a mistaken tap. Floored at 0; the corrected total syncs on screen exit. */
    fun onUndoTap() {
        val today = today()
        val updated = store.decrementToday(today)
        _state.update {
            it.copy(
                dateKey = today.toString(),
                todayCount = updated,
                errorMessage = null,
            )
        }
        recalculateLocalLeaderboard()
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
                AlBaqaraLeaderboardEntry(
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
                        totalTodayAlBaqara = stats.totalTodayAlBaqara,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(errorMessage = error.message) }
            }
    }

    /**
     * Publish the device's lifetime total for this challenge to the persistent DB user node
     * ({root}/users/{uid}/totalCount). Fire-and-forget and batched on screen enter/leave rather
     * than per-tap, so it never spams the network. A failure never affects the daily-count sync.
     */
    private fun publishLifetimeTotal() {
        viewModelScope.launch {
            if (!firebaseClient.isConfigured()) return@launch
            firebaseClient.writeUserTotal(sessionStore.getOrCreateUid(), store.lifetimeCount())
        }
    }

    private fun today(): LocalDate = Clock.System.todayIn(cairoZone)
}
