package tools.mo3ta.salo.data.session

import com.russhwolf.settings.Settings
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

    private companion object {
        const val KEY_ALIAS = "alias"
        const val KEY_PENDING_ROUND = "pending_round_key"
        const val KEY_PENDING_COUNT = "pending_click_count"
        const val ALIAS_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
