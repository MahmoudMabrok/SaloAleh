package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import tools.mo3ta.salo.domain.HAWQALA_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.HawqalaLeaderboardEntry

/**
 * Structural state for the "كنوز الجنة" challenge screen. The fast-changing tap count is
 * intentionally NOT part of this state — it lives in its own [HawqalaChallengeViewModel.todayCount]
 * flow so a tap recomposes only the counter, not the whole screen.
 */
data class HawqalaChallengeUiState(
    val dateKey: String = "",
    val dailyGoal: Int = HAWQALA_CHALLENGE_DAILY_GOAL,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayHawqala: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showCelebration: Boolean = false,
    val celebrationMilestone: Int = 0,
    val showLeaderboard: Boolean = false,
    val showManualSheet: Boolean = false,
    val isSubmittingManual: Boolean = false,
    val manualRemainingToday: Int = CHALLENGE_MANUAL_DAILY_CAP,
    val leaderboard: List<HawqalaLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
    val currentStreak: Int = 0,
)
