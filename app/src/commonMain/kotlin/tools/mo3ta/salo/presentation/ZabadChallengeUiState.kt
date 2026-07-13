package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import tools.mo3ta.salo.domain.ZABAD_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.ZabadLeaderboardEntry

data class ZabadChallengeUiState(
    val dateKey: String = "",
    val todayCount: Int = 0,
    val dailyGoal: Int = ZABAD_CHALLENGE_DAILY_GOAL,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayZabad: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showCelebration: Boolean = false,
    val celebrationMilestone: Int = 0,
    val showLeaderboard: Boolean = false,
    val showManualZabadSheet: Boolean = false,
    val isSubmittingManualZabad: Boolean = false,
    val manualRemainingToday: Int = CHALLENGE_MANUAL_DAILY_CAP,
    val leaderboard: List<ZabadLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
    val elapsedSinceWashMillis: Long = 0L,
    val roundsToday: Int = 0,
    val isWashing: Boolean = false,
)
