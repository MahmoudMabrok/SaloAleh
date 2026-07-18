package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import tools.mo3ta.salo.domain.DHIKR_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.DhikrLeaderboardEntry

data class DhikrChallengeUiState(
    val dateKey: String = "",
    val todayCount: Int = 0,
    val dailyGoal: Int = DHIKR_CHALLENGE_DAILY_GOAL,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayDhikr: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showCelebration: Boolean = false,
    val celebrationMilestone: Int = 0,
    val showLeaderboard: Boolean = false,
    val showManualDhikrSheet: Boolean = false,
    val isSubmittingManualDhikr: Boolean = false,
    val manualRemainingToday: Int = CHALLENGE_MANUAL_DAILY_CAP,
    val leaderboard: List<DhikrLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
    val currentStreak: Int = 0,
)
