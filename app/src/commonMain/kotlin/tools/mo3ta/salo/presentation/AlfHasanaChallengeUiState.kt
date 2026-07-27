package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.ALF_HASANA_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.AlfHasanaLeaderboardEntry
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP

/**
 * Structural state for the "ألف حسنة" challenge screen. The fast-changing tap count is intentionally
 * NOT part of this state — it lives in its own [AlfHasanaChallengeViewModel.todayCount] flow so a tap
 * recomposes only the counter, not the whole screen.
 */
data class AlfHasanaChallengeUiState(
    val dateKey: String = "",
    val dailyGoal: Int = ALF_HASANA_CHALLENGE_DAILY_GOAL,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayAlfHasana: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showCelebration: Boolean = false,
    val celebrationMilestone: Int = 0,
    val showLeaderboard: Boolean = false,
    val showManualSheet: Boolean = false,
    val isSubmittingManual: Boolean = false,
    val manualRemainingToday: Int = CHALLENGE_MANUAL_DAILY_CAP,
    val leaderboard: List<AlfHasanaLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
    val currentStreak: Int = 0,
)
