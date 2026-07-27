package tools.mo3ta.salo.data.session

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.data.crypto.sha256hex
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_MANUAL_DAILY_CAP
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

    fun hasExistingUserState(): Boolean =
        listOf(
            KEY_UID,
            KEY_ALIAS,
            KEY_PENDING_ROUNDS_INDEX,
            KEY_PENDING_ROUND_LEGACY,
            KEY_NICKNAME,
            KEY_FCM_TOKEN,
        ).any { settings.hasKey(it) }

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

    /**
     * The highest daily-badge threshold that has been *successfully published to the server* today.
     * Kept separate from [getLastMilestoneLevel] (which gates the one-time local celebration): a
     * failed badge publish must not advance this guard, so the write is retried on the next flush.
     */
    fun getLastPublishedBadgeLevel(today: String): Int {
        if (settings.getStringOrNull(KEY_LAST_PUBLISHED_BADGE_DATE) != today) return 0
        return settings.getInt(KEY_LAST_PUBLISHED_BADGE_LEVEL, 0)
    }

    fun saveLastPublishedBadgeLevel(today: String, threshold: Int) {
        settings.putString(KEY_LAST_PUBLISHED_BADGE_DATE, today)
        settings.putInt(KEY_LAST_PUBLISHED_BADGE_LEVEL, threshold)
    }

    fun getLastKnownRank(): Int = settings.getInt(KEY_LAST_KNOWN_RANK, 0)

    fun saveLastKnownRank(rank: Int) = settings.putInt(KEY_LAST_KNOWN_RANK, rank)

    fun getLastSalawatTimestamp(): Long = settings.getLong(KEY_LAST_SALAWAT_TS, 0L)
    fun saveLastSalawatTimestamp(ts: Long) = settings.putLong(KEY_LAST_SALAWAT_TS, ts)

    /**
     * Total competition salawat credited on this Cairo day (taps + manual + extension), tracked
     * locally so it can be published to the player node and drive the daily leaderboard directly
     * (instead of the server diffing against yesterday's snapshot). Unlike the pending-session
     * counter this is NOT cleared on flush — it is the running day total, reset only when the Cairo
     * day changes.
     */
    fun getTodayCount(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_TODAY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_TODAY_COUNT, 0)
    }

    /**
     * One-time initialization of today's running total from a pre-existing same-day [baseline]
     * (the daily-goal tap progress that predates the today-count ledger). Only seeds when the
     * ledger has not yet been initialized for [today]; a device that updated to the today-count
     * build mid-day thus starts the daily leaderboard from its real daily count instead of 0,
     * rather than under-reporting until the Cairo day rolls over. On a genuine new day the caller's
     * baseline is itself 0 (the daily-goal progress is for the previous day), so this correctly
     * resets to 0. Never lowers an already-tracked count. Returns the resulting today count.
     */
    fun seedTodayCountIfUnset(today: LocalDate, baseline: Int): Int {
        val date = today.toString()
        if (settings.getStringOrNull(KEY_TODAY_DATE) == date) return settings.getInt(KEY_TODAY_COUNT, 0)
        val seed = baseline.coerceAtLeast(0)
        settings.putString(KEY_TODAY_DATE, date)
        settings.putInt(KEY_TODAY_COUNT, seed)
        return seed
    }

    /** Adds [delta] to today's running total, rolling over to 0 when the Cairo day changed. Returns the new total. */
    fun addTodayCount(today: LocalDate, delta: Int): Int {
        ensureTodayCountToday(today)
        if (delta <= 0) return settings.getInt(KEY_TODAY_COUNT, 0)
        val updated = settings.getInt(KEY_TODAY_COUNT, 0) + delta
        settings.putInt(KEY_TODAY_COUNT, updated)
        return updated
    }

    /** Subtracts [delta] from today's running total after a correction, floored at 0. Returns the new total. */
    fun subtractTodayCount(today: LocalDate, delta: Int): Int {
        ensureTodayCountToday(today)
        if (delta <= 0) return settings.getInt(KEY_TODAY_COUNT, 0)
        val updated = (settings.getInt(KEY_TODAY_COUNT, 0) - delta).coerceAtLeast(0)
        settings.putInt(KEY_TODAY_COUNT, updated)
        return updated
    }

    private fun ensureTodayCountToday(today: LocalDate) {
        val date = today.toString()
        if (settings.getStringOrNull(KEY_TODAY_DATE) == date) return
        settings.putString(KEY_TODAY_DATE, date)
        settings.putInt(KEY_TODAY_COUNT, 0)
    }

    /**
     * How much more may still be credited to the competition today via manual ("record external")
     * entry — [dailyCap] minus what has already been used this Cairo day. Regular taps are uncapped
     * and do not count against this ledger.
     *
     * [dailyCap] defaults to the permanent [MOHAMED_LOVERS_MANUAL_DAILY_CAP]; callers pass a lower
     * value to apply the gradual new-user ramp (see `SalawatManualCap`).
     */
    fun manualRemainingToday(today: LocalDate, dailyCap: Int = MOHAMED_LOVERS_MANUAL_DAILY_CAP): Int {
        if (settings.getStringOrNull(KEY_MANUAL_DATE) != today.toString()) return dailyCap
        return (dailyCap - settings.getInt(KEY_MANUAL_USED, 0)).coerceAtLeast(0)
    }

    /**
     * Record a manual external entry for [today], clamped so cumulative manual entry never exceeds
     * [dailyCap] for the Cairo day. Returns the amount actually applied (0 once the day's allowance
     * is exhausted). [dailyCap] defaults to the permanent [MOHAMED_LOVERS_MANUAL_DAILY_CAP]; a lower
     * value applies the gradual new-user ramp.
     */
    fun recordManualEntry(today: LocalDate, count: Int, dailyCap: Int = MOHAMED_LOVERS_MANUAL_DAILY_CAP): Int {
        ensureManualToday(today)
        if (count <= 0) return 0
        val used = settings.getInt(KEY_MANUAL_USED, 0)
        val applied = count.coerceAtMost((dailyCap - used).coerceAtLeast(0))
        if (applied > 0) settings.putInt(KEY_MANUAL_USED, used + applied)
        return applied
    }

    /**
     * Refund [count] to today's manual-entry allowance after a correction (subtract), so a
     * corrected over-entry frees the daily allowance to be used again. Floored at 0.
     */
    fun refundManualEntry(today: LocalDate, count: Int) {
        if (count <= 0) return
        ensureManualToday(today)
        val used = settings.getInt(KEY_MANUAL_USED, 0)
        settings.putInt(KEY_MANUAL_USED, (used - count).coerceAtLeast(0))
    }

    private fun ensureManualToday(today: LocalDate) {
        val date = today.toString()
        if (settings.getStringOrNull(KEY_MANUAL_DATE) == date) return
        settings.putString(KEY_MANUAL_DATE, date)
        settings.putInt(KEY_MANUAL_USED, 0)
    }

    fun getNickname(): String? = settings.getStringOrNull(KEY_NICKNAME)?.takeIf { it.isNotBlank() }

    fun getPublishedName(): String =
        getNickname()?.takeIf { isNicknameEnabled } ?: getOrCreateUid().takeLast(6)
    fun setNickname(name: String?) {
        if (name.isNullOrBlank()) settings.remove(KEY_NICKNAME)
        else settings.putString(KEY_NICKNAME, name.trim().take(MAX_NICKNAME_LENGTH))
    }

    var isNicknameEnabled: Boolean
        get() = settings.getBoolean(KEY_NICKNAME_ENABLED, false)
        set(v) = settings.putBoolean(KEY_NICKNAME_ENABLED, v)

    var isNicknameAnnouncementShown: Boolean
        get() = settings.getBoolean(KEY_NICKNAME_ANNOUNCEMENT_SHOWN, false)
        set(v) = settings.putBoolean(KEY_NICKNAME_ANNOUNCEMENT_SHOWN, v)

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
        const val KEY_LAST_MILESTONE_DATE = "last_milestone_date"
        const val KEY_LAST_MILESTONE_LEVEL = "last_milestone_level"
        const val KEY_LAST_PUBLISHED_BADGE_DATE = "last_published_badge_date"
        const val KEY_LAST_PUBLISHED_BADGE_LEVEL = "last_published_badge_level"
        const val KEY_LAST_KNOWN_RANK = "last_known_rank"
        const val KEY_LAST_SALAWAT_TS = "last_salawat_ts"
        // Per-Cairo-day running total of competition salawat, published to the player node.
        const val KEY_TODAY_DATE = "ml_today_date"
        const val KEY_TODAY_COUNT = "ml_today_count"
        // Per-Cairo-day ledger for manual ("record external") entry into the competition.
        const val KEY_MANUAL_DATE = "ml_manual_date"
        const val KEY_MANUAL_USED = "ml_manual_used"
        const val KEY_NICKNAME = "user_nickname"
        const val KEY_NICKNAME_ENABLED = "nickname_enabled"
        const val KEY_NICKNAME_ANNOUNCEMENT_SHOWN = "nickname_announcement_shown"
        const val MAX_NICKNAME_LENGTH = 20
    }
}
