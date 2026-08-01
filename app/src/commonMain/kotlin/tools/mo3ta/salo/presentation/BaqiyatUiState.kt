package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.data.baqiyat.BaqiyatPhrase
import tools.mo3ta.salo.domain.BAQIYAT_MANUAL_DAILY_CAP
import tools.mo3ta.salo.domain.BaqiyatLeaderboardEntry

data class BaqiyatUiState(
    val dateKey: String = "",
    val cyclesCompleted: Int = 0,
    /** Position in the cycle: which phrase the single tap button is currently showing. */
    val currentPhraseIndex: Int = 0,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayBaqiyat: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showCelebration: Boolean = false,
    val celebrationMilestone: Int = 0,
    val showLeaderboard: Boolean = false,
    val showManualBaqiyatSheet: Boolean = false,
    val isSubmittingManualBaqiyat: Boolean = false,
    val manualRemainingToday: Int = BAQIYAT_MANUAL_DAILY_CAP,
    val leaderboard: List<BaqiyatLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
    val currentStreak: Int = 0,
) {
    /** The phrase shown on the tap button right now. */
    val currentPhrase: BaqiyatPhrase
        get() = BaqiyatPhrase.entries[currentPhraseIndex.coerceIn(0, BaqiyatPhrase.entries.lastIndex)]
}
