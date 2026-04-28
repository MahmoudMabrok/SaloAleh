package tools.mo3ta.salo.presentation

data class MohamedLoversLeaderboardEntry(
    val rank: Int,
    val displayTag: String,
    val totalCount: Int,
    val isCurrentUser: Boolean,
)

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
    val isFridayBonus: Boolean = false,
    val roundKey: String? = null,
    val roundEndLabel: String = "",
    val networkTimeLabel: String = "",
    val canCount: Boolean = false,
    val syncedTotal: Int = 0,
    val sessionClicks: Int = 0,
    val isWinner: Boolean = false,
    val winnerCode: String = "",
    val selfEntry: MohamedLoversLeaderboardEntry? = null,
    val selfInTop: Boolean = false,
    val topPlayers: List<MohamedLoversLeaderboardEntry> = emptyList(),
    val error: MohamedLoversError? = null,
)
