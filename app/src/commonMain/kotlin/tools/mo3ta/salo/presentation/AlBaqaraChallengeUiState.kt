package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.ALBAQARA_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.AlBaqaraLeaderboardEntry

data class AlBaqaraChallengeUiState(
    val dateKey: String = "",
    val todayCount: Int = 0,
    val dailyGoal: Int = ALBAQARA_CHALLENGE_DAILY_GOAL,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayAlBaqara: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showLeaderboard: Boolean = false,
    val leaderboard: List<AlBaqaraLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
    // Quran-credit prompt: offered after each reading so the 48 pages of Al-Baqara
    // can be added to the Quran challenge.
    val showQuranCreditDialog: Boolean = false,
    val quranTodayCount: Int = 0,
    val isCreditingQuran: Boolean = false,
)
