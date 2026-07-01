package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.data.baqiyat.BaqiyatPhrase
import tools.mo3ta.salo.domain.BaqiyatLeaderboardEntry

data class BaqiyatUiState(
    val dateKey: String = "",
    val cyclesCompleted: Int = 0,
    val tappedPhrases: Set<BaqiyatPhrase> = emptySet(),
    val rank: Int = 0,
    val participantCount: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showCelebration: Boolean = false,
    val celebrationMilestone: Int = 0,
    val showLeaderboard: Boolean = false,
    val leaderboard: List<BaqiyatLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
)
