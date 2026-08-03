package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.baqiyat.BaqiyatFirebaseClient
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
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(
        BaqiyatUiState(
            currentUid = sessionStore.getOrCreateUid(),
            playerName = sessionStore.getPublishedName(),
        )
    )
    val state: StateFlow<BaqiyatUiState> = _state.asStateFlow()

    /**
     * The screen is split into three flows so a tap only touches what a tap changes.
     *
     * A completed cycle moves the counter *and* re-ranks the local leaderboard, so a screen that
     * collected the whole state would recompose top to bottom on every tap — the thing the
     * challenge-screen rule in CLAUDE.md forbids. Instead [shell] blanks out exactly those two
     * fields (with `distinctUntilChanged` on top, so it simply does not emit on a tap), while
     * [cycles] feeds the counter leaf and [leaderboardEntries] feeds the sheet.
     */
    val shell: StateFlow<BaqiyatUiState> = _state
        .map { it.withoutTapVaryingFields() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.withoutTapVaryingFields())

    val cycles: StateFlow<Int> = _state
        .map { it.cyclesCompleted }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.cyclesCompleted)

    val leaderboardEntries: StateFlow<List<BaqiyatLeaderboardEntry>> = _state
        .map { it.leaderboard }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.leaderboard)

    private val _cycleSerial = MutableStateFlow(0)

    /**
     * Bumped once per cycle the user actually completes here (tap or manual entry) — never by a
     * remote-baseline sync on screen enter. The hive canvas launches one swarm of sparks per bump,
     * so opening the screen with 40 cycles already synced does not fire 40 launches.
     */
    val cycleSerial: StateFlow<Int> = _cycleSerial.asStateFlow()

    private fun BaqiyatUiState.withoutTapVaryingFields(): BaqiyatUiState =
        copy(cyclesCompleted = 0, leaderboard = emptyList())

    fun onScreenEntered() {
        publishLifetimeTotal()
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
                    manualRemainingToday = store.manualRemainingToday(today),
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

    /**
     * One tap = one completed cycle of all the phrases: +1 to the counter, like every other
     * count challenge. The phrases are shown on screen to recite; the app does not step through
     * them.
     */
    fun onCycleTap() {
        val today = today()
        val updated = store.incrementToday(today)
        maybeRecordWin(today, updated)
        _cycleSerial.update { it + 1 }
        _state.update {
            it.copy(
                cyclesCompleted = updated,
                showCelebration = true,
                celebrationMilestone = updated,
            )
        }
        recalculateLocalLeaderboard()
    }

    fun onCelebrationDismissed() {
        _state.update { it.copy(showCelebration = false) }
    }

    fun showManualBaqiyatSheet() {
        _state.update { it.copy(showManualBaqiyatSheet = true) }
    }

    fun dismissManualBaqiyatSheet() {
        _state.update { it.copy(showManualBaqiyatSheet = false) }
    }

    /** Record a batch of Baqiyat cycles counted outside the app (on fingers, with a tasbih). */
    fun submitManualBaqiyat(count: Int) {
        if (count <= 0) return
        val today = today()
        val updated = store.addToday(today, count)
        maybeRecordWin(today, updated)
        // One launch for the whole batch, not one per cycle recorded outside the app.
        _cycleSerial.update { it + 1 }
        _state.update {
            it.copy(
                dateKey = today.toString(),
                cyclesCompleted = updated,
                manualRemainingToday = store.manualRemainingToday(today),
                showManualBaqiyatSheet = false,
                isSubmittingManualBaqiyat = true,
                errorMessage = null,
            )
        }
        recalculateLocalLeaderboard()
        syncManualEntry(today)
    }

    /** Subtract a mistakenly-entered batch from today's count. Floored at 0; syncs the corrected total. */
    fun subtractManualBaqiyat(count: Int) {
        if (count <= 0) return
        val today = today()
        val updated = store.subtractToday(today, count)
        _state.update {
            it.copy(
                dateKey = today.toString(),
                cyclesCompleted = updated,
                manualRemainingToday = store.manualRemainingToday(today),
                showManualBaqiyatSheet = false,
                isSubmittingManualBaqiyat = true,
                errorMessage = null,
            )
        }
        recalculateLocalLeaderboard()
        syncManualEntry(today)
    }

    /** Push the corrected local total to Firebase after a manual add/subtract. */
    private fun syncManualEntry(today: LocalDate) {
        viewModelScope.launch {
            syncMutex.withLock {
                if (!firebaseClient.isConfigured()) {
                    _state.update { it.copy(isSubmittingManualBaqiyat = false) }
                    return@withLock
                }
                val total = store.todayCount(today)
                val uid = sessionStore.getOrCreateUid()
                val countryCode = countryCodeProvider.get()
                val result = firebaseClient.writeUserDay(today.toString(), uid, total, countryCode, sessionStore.getPublishedName(), challengeBadgeStore.getCurrentStreak(ChallengeType.BAQIYAT, today))
                if (result.isSuccess) store.onSyncSuccess(today, total)
                refreshLeaderboard(today.toString(), uid)
                _state.update {
                    it.copy(
                        isSubmittingManualBaqiyat = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun onLeaderboardOpened() {
        _state.update { it.copy(showLeaderboard = true) }
    }

    fun onLeaderboardClosed() {
        _state.update { it.copy(showLeaderboard = false) }
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
