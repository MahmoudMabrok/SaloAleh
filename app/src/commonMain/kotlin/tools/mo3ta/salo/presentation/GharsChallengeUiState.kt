package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.GHARS_CHALLENGE_DAILY_GOAL
import tools.mo3ta.salo.domain.GharsLeaderboardEntry

data class GharsChallengeUiState(
    val dateKey: String = "",
    val todayCount: Int = 0,
    val dailyGoal: Int = GHARS_CHALLENGE_DAILY_GOAL,
    val completedGroves: Int = 0,
    val rank: Int = 0,
    val participantCount: Int = 0,
    val totalTodayGhars: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val showLeaderboard: Boolean = false,
    val showManualGharsSheet: Boolean = false,
    val isSubmittingManualGhars: Boolean = false,
    val leaderboard: List<GharsLeaderboardEntry> = emptyList(),
    val isLeaderboardLoading: Boolean = false,
    val currentUid: String = "",
    // Transient toast shown when a grove completes: "اكتمل بستانك رقم N".
    val groveToastNumber: Int = 0,
    // Monotonic counter bumped only on a real user plant (tap / manual entry) — never on
    // screen-entry load or remote-baseline sync. The canvas sprouts a palm only when this
    // changes, so re-opening the screen shows the settled grove instead of re-growing a palm.
    val plantSerial: Int = 0,
)
