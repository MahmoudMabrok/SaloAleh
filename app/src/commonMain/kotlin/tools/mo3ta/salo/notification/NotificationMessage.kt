package tools.mo3ta.salo.notification

data class NotificationMessage(
    val title: String,
    val body: String,
    val action: NotificationAction = NotificationAction.OPEN_LEADERBOARD,
)

enum class NotificationAction {
    OPEN_LEADERBOARD,
    OPEN_ACHIEVEMENTS,
    // Challenge actions open the relevant challenge screen and then surface its
    // own leaderboard sheet (used by the per-challenge daily "champion" pushes).
    OPEN_DHIKR_CHALLENGE,
    OPEN_BAQIYAT_CHALLENGE,
    OPEN_ISTIGHFAR_CHALLENGE,
    OPEN_QURAN_CHALLENGE,
    OPEN_ZABAD_CHALLENGE,
    OPEN_GHARS_CHALLENGE,
    OPEN_ALF_HASANA_CHALLENGE,
    OPEN_KALIMAT_CHALLENGE,
    NONE;

    companion object {
        fun from(value: String?): NotificationAction = when (value) {
            "open_leaderboard" -> OPEN_LEADERBOARD
            "open_achievements" -> OPEN_ACHIEVEMENTS
            "open_dhikr_challenge" -> OPEN_DHIKR_CHALLENGE
            "open_baqiyat_challenge" -> OPEN_BAQIYAT_CHALLENGE
            "open_istighfar_challenge" -> OPEN_ISTIGHFAR_CHALLENGE
            "open_quran_challenge" -> OPEN_QURAN_CHALLENGE
            "open_zabad_challenge" -> OPEN_ZABAD_CHALLENGE
            "open_ghars_challenge" -> OPEN_GHARS_CHALLENGE
            "open_alf_hasana_challenge" -> OPEN_ALF_HASANA_CHALLENGE
            "open_kalimat_challenge" -> OPEN_KALIMAT_CHALLENGE
            "none" -> NONE
            else -> OPEN_LEADERBOARD
        }
    }
}
