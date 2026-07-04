package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.ISTIGHFAR_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.IstighfarLeaderboardEntry

data class IstighfarChallengeUiState(
    val dateKey: String = "",
    val todayCount: Int = 0,
    val dailyGoal: Int = ISTIGHFAR_CHALLENGE_DAILY_GOAL,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayIstighfar: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showCelebration: Boolean = false,
    val celebrationMilestone: Int = 0,
    val showLeaderboard: Boolean = false,
    val showManualIstighfarSheet: Boolean = false,
    val isSubmittingManualIstighfar: Boolean = false,
    val leaderboard: List<IstighfarLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
)
