package tools.mo3ta.salo.notification

data class NotificationMessage(
    val title: String,
    val body: String,
    val action: NotificationAction = NotificationAction.OPEN_LEADERBOARD,
)

enum class NotificationAction {
    OPEN_LEADERBOARD,
    OPEN_ACHIEVEMENTS,
    NONE;

    companion object {
        fun from(value: String?): NotificationAction = when (value) {
            "open_leaderboard" -> OPEN_LEADERBOARD
            "open_achievements" -> OPEN_ACHIEVEMENTS
            "none" -> NONE
            else -> OPEN_LEADERBOARD
        }
    }
}
