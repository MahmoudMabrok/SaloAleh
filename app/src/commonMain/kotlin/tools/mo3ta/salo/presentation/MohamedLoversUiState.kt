package tools.mo3ta.salo.presentation

import kotlinx.datetime.Instant
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.HeroesBoard
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_MANUAL_DAILY_CAP

data class MohamedLoversLeaderboardEntry(
    val rank: Int,
    val displayTag: String,
    val totalCount: Int,
    val isCurrentUser: Boolean,
    val uid: String = "",
    val rankChange: String = "",
    val isSupporter: Boolean = false,
    val dailyBadge: String? = null,
    val roundStreak: Int? = null,
    val goldMedals: Int? = null,
    val silverMedals: Int? = null,
    val bronzeMedals: Int? = null,
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
    // Running total of competition salawat credited this Cairo day (drives the daily leaderboard).
    val todayCount: Int = 0,
    val isWinner: Boolean = false,
    val winnerCode: String = "",
    val selfEntry: MohamedLoversLeaderboardEntry? = null,
    val selfInTop: Boolean = false,
    val topPlayers: List<MohamedLoversLeaderboardEntry> = emptyList(),
    val isUsingDailyLeaderboard: Boolean = true,
    val isSwitchingLeaderboardMode: Boolean = false,
    val showDailyLeaderboardPromo: Boolean = false,
    val isLoadingLiveLeaderboard: Boolean = false,
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
    // Remaining manual ("record external") allowance for today (cap resets each Cairo day).
    val manualRemaining: Int = MOHAMED_LOVERS_MANUAL_DAILY_CAP,

    // Daily goal
    val dailyGoalTarget: Int = 0,
    val dailyGoalProgress: Int = 0,
    val dailyGoalJustCompleted: Boolean = false,

    // Per-round daily salawat streak ("perfect week" badge)
    val roundStreak: Int = 0,
    val roundStreakCelebration: Achievement.RoundStreakBadge? = null,

    // New round transition
    val showNewRoundCountdown: Boolean = false,

    // Motivation: overtake alerts
    val overtakeRank: Int? = null,

    // Motivation: daily milestone celebration
    val milestoneThreshold: Int? = null,
    val milestoneBadgeKey: String? = null,
    val currentDailyBadge: String? = null,

    // Daily competition push cap: salawat dropped by the last flush because the day's cap was
    // already spent. One-shot — the screen shows it as a toast and clears it.
    val dailyCapDiscarded: Int? = null,
    // Today's count was raised to the server-published daily badge value (local progress was
    // behind the badge the server already recorded). One-shot, shown as a toast.
    val badgeAdjustedTo: Int? = null,

    // Motivation: rank movement summary
    val rankMovementDelta: Int? = null,
    val rankMovementOldRank: Int = 0,
    val rankMovementNewRank: Int = 0,

    // Idle salawat tracking
    val lastSalawatElapsedMinutes: Long? = null,

    // Heart index
    val heartScore: Int = 0,
    val showHeartRefillNudge: Boolean = false,

    // Heroes / champions of the day
    val heroesBoard: HeroesBoard? = null,
    val showHeroesSheet: Boolean = false,
    val heroesLoading: Boolean = false,
)
