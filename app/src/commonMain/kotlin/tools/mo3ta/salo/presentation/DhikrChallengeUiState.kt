package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.domain.DHIKR_CHALLENGE_DAILY_GOAL

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
)
