package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import tools.mo3ta.salo.domain.KALIMAT_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.KalimatLeaderboardEntry

/**
 * Structural state for the "الكلمات الأربع" challenge screen. The fast-changing tap count is intentionally
 * NOT part of this state — it lives in its own [KalimatChallengeViewModel.todayCount] flow so a tap
 * recomposes only the counter, not the whole screen.
 */
data class KalimatChallengeUiState(
    val dateKey: String = "",
    val dailyGoal: Int = KALIMAT_CHALLENGE_DAILY_GOAL,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayKalimat: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showCelebration: Boolean = false,
    val celebrationMilestone: Int = 0,
    val showLeaderboard: Boolean = false,
    val showManualSheet: Boolean = false,
    val isSubmittingManual: Boolean = false,
    val manualRemainingToday: Int = CHALLENGE_MANUAL_DAILY_CAP,
    val leaderboard: List<KalimatLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
    val currentStreak: Int = 0,
)
