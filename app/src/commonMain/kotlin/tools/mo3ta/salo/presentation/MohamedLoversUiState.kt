package tools.mo3ta.salo.presentation

import kotlinx.datetime.Instant
import tools.mo3ta.salo.domain.Achievement

data class MohamedLoversLeaderboardEntry(
    val rank: Int,
    val displayTag: String,
    val totalCount: Int,
    val isCurrentUser: Boolean,
    val uid: String = "",
    val rankChange: String = "",
    val scoreMasked: Boolean = false,
    val isSupporter: Boolean = false,
    val dailyBadge: String? = null,
){
    val displayedRank = if (rank > 0) "#$rank " else ""
}

enum class MohamedLoversStatus { WaitingNetwork, FirebaseOff, Open }

sealed interface MohamedLoversError {
    data object Connection : MohamedLoversError
    data class Raw(val message: String) : MohamedLoversError
}

data class MohamedLoversUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSavingSession: Boolean = false,
    val countryCode: String = "",
    val selfDisplayTag: String = "",
    val status: MohamedLoversStatus = MohamedLoversStatus.WaitingNetwork,
    val firebaseConfigured: Boolean = true,
    val roundKey: String? = null,
    val roundEndLabel: String = "",
    val roundEndInstant: Instant? = null,
    val networkTimeLabel: String = "",
    val canCount: Boolean = false,
    val syncedTotal: Int = 0,
    val sessionClicks: Int = 0,
    val isWinner: Boolean = false,
    val winnerCode: String = "",
    val selfEntry: MohamedLoversLeaderboardEntry? = null,
    val selfInTop: Boolean = false,
    val topPlayers: List<MohamedLoversLeaderboardEntry> = emptyList(),
    val isUsingDailyLeaderboard: Boolean = false,
    val showDailyLeaderboardPromo: Boolean = false,
    val isLoadingLiveLeaderboard: Boolean = false,
    val hasLiveLeaderboardAccess: Boolean = false,
    val error: MohamedLoversError? = null,
    val roundTotal: Int = 0,
    val roundPlayerCount: Int = 0,
    val allTimeTotal: Long = 0L,
    val showHadithDialog: Boolean = false,

    // Round end results (banner + full-screen, shown once per completed round)
    val showRoundEndBanner: Boolean = false,
    val showRoundEndResults: Boolean = false,
    val winnersTop3: List<MohamedLoversLeaderboardEntry> = emptyList(),
    val recapRank: Int = 0,
    val recapTotalPlayers: Int = 0,
    val recapIsPersonalBest: Boolean = false,
    val recapTapsDelta: Int = 0,
    val roundEndAchievement: Achievement.RankAchievement? = null,

    // Streak grace dialog
    val showGraceWarning: Boolean = false,

    // Manual salawat import
    val showManualSalawatSheet: Boolean = false,
    val isSubmittingManualSalawat: Boolean = false,

    // Daily goal
    val dailyGoalTarget: Int = 0,
    val dailyGoalProgress: Int = 0,
    val dailyGoalJustCompleted: Boolean = false,

    // New round transition
    val showNewRoundCountdown: Boolean = false,

    // Motivation: overtake alerts
    val overtakeRank: Int? = null,

    // Motivation: daily milestone celebration
    val milestoneThreshold: Int? = null,
    val milestoneBadgeKey: String? = null,
    val currentDailyBadge: String? = null,

    // Motivation: rank movement summary
    val rankMovementDelta: Int? = null,
    val rankMovementOldRank: Int = 0,
    val rankMovementNewRank: Int = 0,

    // Idle salawat tracking
    val lastSalawatElapsedMinutes: Long? = null,
)
