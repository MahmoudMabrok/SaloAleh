package tools.mo3ta.salo.data.session

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.data.crypto.sha256hex
import tools.mo3ta.salo.domain.MohamedLoversPendingSession

class MohamedLoversSessionStore(private val settings: Settings) {

    fun getOrCreateAlias(): String {
        settings.getStringOrNull(KEY_ALIAS)?.takeIf { it.isNotBlank() }?.let { return it }
        val suffix = (1..4).map { ALIAS_CHARS[kotlin.random.Random.nextInt(ALIAS_CHARS.length)] }.joinToString("")
        val alias = "محب محمد $suffix"
        settings.putString(KEY_ALIAS, alias)
        return alias
    }

    fun getPendingSession(): MohamedLoversPendingSession = MohamedLoversPendingSession(
        roundKey = settings.getStringOrNull(KEY_PENDING_ROUND),
        clickCount = settings.getInt(KEY_PENDING_COUNT, 0),
    )

    fun incrementPendingClick(roundKey: String, delta: Int = 1): MohamedLoversPendingSession {
        val currentRoundKey = settings.getStringOrNull(KEY_PENDING_ROUND)
        val currentCount = if (currentRoundKey == roundKey) settings.getInt(KEY_PENDING_COUNT, 0) else 0
        val updated = MohamedLoversPendingSession(
            roundKey = roundKey,
            clickCount = currentCount + delta.coerceAtLeast(1),
        )
        settings.putString(KEY_PENDING_ROUND, roundKey)
        settings.putInt(KEY_PENDING_COUNT, updated.clickCount)
        return updated
    }

    fun clearPendingSession() {
        settings.remove(KEY_PENDING_ROUND)
        settings.remove(KEY_PENDING_COUNT)
    }

    fun getOrSetInstallDate(today: LocalDate): String {
        val stored = settings.getStringOrNull(KEY_INSTALL_DATE)
        if (stored != null) return stored
        val s = today.toString()
        settings.putString(KEY_INSTALL_DATE, s)
        return s
    }

    fun markRecapShown(roundKey: String) = settings.putString(KEY_RECAP_SHOWN_ROUND, roundKey)
    fun getRecapShownRound(): String? = settings.getStringOrNull(KEY_RECAP_SHOWN_ROUND)

    fun getPersonalBestRank(): Int = settings.getInt(KEY_PERSONAL_BEST_RANK, Int.MAX_VALUE)
    fun updatePersonalBestRank(rank: Int) {
        if (rank < getPersonalBestRank()) settings.putInt(KEY_PERSONAL_BEST_RANK, rank)
    }

    fun getLastRoundTaps(): Int = settings.getInt(KEY_LAST_ROUND_TAPS, 0)
    fun saveLastRoundTaps(taps: Int) = settings.putInt(KEY_LAST_ROUND_TAPS, taps)

    fun getSavedFcmToken(): String? = settings.getStringOrNull(KEY_FCM_TOKEN)
    fun saveLocalFcmToken(token: String) = settings.putString(KEY_FCM_TOKEN, token)

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun getOrCreateUid(): String {
        val raw = settings.getStringOrNull(KEY_UID)?.takeIf { it.isNotBlank() }
            ?: kotlin.uuid.Uuid.random().toString().also { settings.putString(KEY_UID, it) }
        return sha256hex(raw)
    }

    private companion object {
        const val KEY_UID = "user_uid"
        const val KEY_ALIAS = "alias"
        const val KEY_PENDING_ROUND = "pending_round_key"
        const val KEY_PENDING_COUNT = "pending_click_count"
        const val ALIAS_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        const val KEY_INSTALL_DATE = "install_date"
        const val KEY_RECAP_SHOWN_ROUND = "recap_shown_round"
        const val KEY_PERSONAL_BEST_RANK = "personal_best_rank"
        const val KEY_LAST_ROUND_TAPS = "last_round_taps"
        const val KEY_FCM_TOKEN = "fcm_token"
    }
}
