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
)
