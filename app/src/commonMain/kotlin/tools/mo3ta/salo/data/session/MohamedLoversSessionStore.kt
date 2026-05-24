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

    fun getPendingSession(roundKey: String): MohamedLoversPendingSession {
        migrateIfNeeded()
        return MohamedLoversPendingSession(
            roundKey = roundKey,
            clickCount = settings.getInt(pendingCountKey(roundKey), 0),
        )
    }

    fun incrementPendingClick(roundKey: String, delta: Int = 1): MohamedLoversPendingSession {
        migrateIfNeeded()
        val key = pendingCountKey(roundKey)
        val updated = settings.getInt(key, 0) + delta.coerceAtLeast(1)
        settings.putInt(key, updated)
        addToPendingIndex(roundKey)
        return MohamedLoversPendingSession(roundKey = roundKey, clickCount = updated)
    }

    fun getAllPendingRounds(): Map<String, Int> {
        migrateIfNeeded()
        return getPendingRoundKeys()
            .associateWith { settings.getInt(pendingCountKey(it), 0) }
            .filter { it.value > 0 }
    }

    fun decrementPendingClick(roundKey: String, delta: Int) {
        val key = pendingCountKey(roundKey)
        val current = settings.getInt(key, 0)
        val remaining = (current - delta).coerceAtLeast(0)
        if (remaining == 0) {
            settings.remove(key)
            val updated = getPendingRoundKeys() - roundKey
            settings.putString(KEY_PENDING_ROUNDS_INDEX, updated.joinToString(","))
        } else {
            settings.putInt(key, remaining)
        }
    }

    fun clearPendingRound(roundKey: String) {
        settings.remove(pendingCountKey(roundKey))
        val updated = getPendingRoundKeys() - roundKey
        settings.putString(KEY_PENDING_ROUNDS_INDEX, updated.joinToString(","))
    }

    fun clearAllPendingRounds() {
        for (roundKey in getPendingRoundKeys()) {
            settings.remove(pendingCountKey(roundKey))
        }
        settings.putString(KEY_PENDING_ROUNDS_INDEX, "")
    }

    private fun migrateIfNeeded() {
        val oldRoundKey = settings.getStringOrNull(KEY_PENDING_ROUND_LEGACY) ?: return
        val oldCount = settings.getInt(KEY_PENDING_COUNT_LEGACY, 0)
        if (oldCount > 0 && oldRoundKey.isNotBlank()) {
            settings.putInt(pendingCountKey(oldRoundKey), oldCount)
            addToPendingIndex(oldRoundKey)
        }
        settings.remove(KEY_PENDING_ROUND_LEGACY)
        settings.remove(KEY_PENDING_COUNT_LEGACY)
    }

    private fun getPendingRoundKeys(): Set<String> =
        settings.getStringOrNull(KEY_PENDING_ROUNDS_INDEX)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    private fun addToPendingIndex(roundKey: String) {
        val rounds = getPendingRoundKeys().toMutableSet()
        rounds.add(roundKey)
        settings.putString(KEY_PENDING_ROUNDS_INDEX, rounds.joinToString(","))
    }

    private fun pendingCountKey(roundKey: String) = "pending_count_$roundKey"

    fun getOrSetInstallDate(today: LocalDate): String {
        val stored = settings.getStringOrNull(KEY_INSTALL_DATE)
        if (stored != null) return stored
        val s = today.toString()
        settings.putString(KEY_INSTALL_DATE, s)
        return s
    }

    fun markRoundEndViewed(roundKey: String) = settings.putString(KEY_ROUND_END_VIEWED, roundKey)
    fun getRoundEndViewedRound(): String? = settings.getStringOrNull(KEY_ROUND_END_VIEWED)

    fun getPersonalBestRank(): Int = settings.getInt(KEY_PERSONAL_BEST_RANK, Int.MAX_VALUE)
    fun updatePersonalBestRank(rank: Int) {
        if (rank < getPersonalBestRank()) settings.putInt(KEY_PERSONAL_BEST_RANK, rank)
    }

    fun getLastRoundTaps(): Int = settings.getInt(KEY_LAST_ROUND_TAPS, 0)
    fun saveLastRoundTaps(taps: Int) = settings.putInt(KEY_LAST_ROUND_TAPS, taps)

    fun getLastAppliedQrTs(): Long = settings.getLong(KEY_LAST_QR_TS, 0L)
    fun saveLastAppliedQrTs(ts: Long) = settings.putLong(KEY_LAST_QR_TS, ts)

    fun getLastMilestoneLevel(today: String): Int {
        if (settings.getStringOrNull(KEY_LAST_MILESTONE_DATE) != today) return 0
        return settings.getInt(KEY_LAST_MILESTONE_LEVEL, 0)
    }

    fun saveLastMilestoneLevel(today: String, threshold: Int) {
        settings.putString(KEY_LAST_MILESTONE_DATE, today)
        settings.putInt(KEY_LAST_MILESTONE_LEVEL, threshold)
    }

    fun getLastKnownRank(): Int = settings.getInt(KEY_LAST_KNOWN_RANK, 0)

    fun saveLastKnownRank(rank: Int) = settings.putInt(KEY_LAST_KNOWN_RANK, rank)

    fun getSavedFcmToken(): String? = settings.getStringOrNull(KEY_FCM_TOKEN)
    fun saveLocalFcmToken(token: String) = settings.putString(KEY_FCM_TOKEN, token)

    fun isFcmTokenSynced(): Boolean = settings.getBoolean(KEY_FCM_TOKEN_SYNCED, false)
    fun setFcmTokenSynced(synced: Boolean) = settings.putBoolean(KEY_FCM_TOKEN_SYNCED, synced)

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun getOrCreateUid(): String {
        val raw = settings.getStringOrNull(KEY_UID)?.takeIf { it.isNotBlank() }
            ?: kotlin.uuid.Uuid.random().toString().also { settings.putString(KEY_UID, it) }
        return sha256hex(raw)
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun getRawUid(): String {
        return settings.getStringOrNull(KEY_UID)?.takeIf { it.isNotBlank() }
            ?: kotlin.uuid.Uuid.random().toString().also { settings.putString(KEY_UID, it) }
    }

    private companion object {
        const val KEY_UID = "user_uid"
        const val KEY_ALIAS = "alias"
        const val KEY_PENDING_ROUND_LEGACY = "pending_round_key"
        const val KEY_PENDING_COUNT_LEGACY = "pending_click_count"
        const val KEY_PENDING_ROUNDS_INDEX = "pending_rounds_index"
        const val ALIAS_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        const val KEY_INSTALL_DATE = "install_date"
        const val KEY_PERSONAL_BEST_RANK = "personal_best_rank"
        const val KEY_LAST_ROUND_TAPS = "last_round_taps"
        const val KEY_FCM_TOKEN = "fcm_token"
        const val KEY_FCM_TOKEN_SYNCED = "fcm_token_synced"
        const val KEY_LAST_QR_TS = "last_applied_qr_ts"
        const val KEY_ROUND_END_VIEWED = "round_end_viewed"
        const val KEY_LAST_MILESTONE_DATE = "last_milestone_date"
        const val KEY_LAST_MILESTONE_LEVEL = "last_milestone_level"
        const val KEY_LAST_KNOWN_RANK = "last_known_rank"
    }
}
