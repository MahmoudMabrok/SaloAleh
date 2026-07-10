package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.ROUND_STREAK_TARGET
import tools.mo3ta.salo.domain.RoundStreakResult

/**
 * Tracks the per-round "perfect week" streak: the number of consecutive days within
 * the current competition round on which the user sent at least one salawat.
 *
 * The streak restarts on a new round and breaks (restarts at 1) whenever a day is
 * missed. Reaching [ROUND_STREAK_TARGET] consecutive active days without a miss earns
 * that round's [Achievement.RoundStreakBadge] — repeatable, one badge per round.
 */
class RoundStreakStore(private val settings: Settings) {

    /**
     * Records salawat activity for [today] in [roundKey]. Idempotent within a day.
     * Returns the up-to-date streak and any badge earned by this call.
     */
    fun recordActivity(roundKey: String, today: LocalDate): RoundStreakResult {
        if (settings.getStringOrNull(KEY_ROUND) != roundKey) {
            // New round — start fresh.
            settings.putString(KEY_ROUND, roundKey)
            settings.remove(KEY_LAST_ACTIVE)
            settings.putInt(KEY_COUNT, 0)
        }

        val lastActive = settings.getStringOrNull(KEY_LAST_ACTIVE)?.let { LocalDate.parse(it) }
        val storedCount = settings.getInt(KEY_COUNT, 0)

        if (lastActive == today) {
            // Already counted today; no change and no re-award.
            return RoundStreakResult(storedCount, null)
        }

        val newStreak = when (lastActive) {
            null -> 1
            today.minusDays(1) -> storedCount + 1
            else -> 1 // A day was missed — streak restarts.
        }

        settings.putString(KEY_LAST_ACTIVE, today.toString())
        settings.putInt(KEY_COUNT, newStreak)

        val newBadge = if (newStreak >= ROUND_STREAK_TARGET && !hasEarnedForRound(roundKey)) {
            val badge = Achievement.RoundStreakBadge(roundKey, today)
            addEarnedBadge(BadgeEntry(roundKey, today))
            badge
        } else {
            null
        }

        return RoundStreakResult(newStreak, newBadge)
    }

    /**
     * The current live streak for [roundKey] as of [today]. Returns 0 when the round
     * differs, no activity has been recorded, or a day has already been missed (the
     * last active day is older than yesterday).
     */
    fun getCurrentStreak(roundKey: String, today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_ROUND) != roundKey) return 0
        val lastActive = settings.getStringOrNull(KEY_LAST_ACTIVE)?.let { LocalDate.parse(it) } ?: return 0
        return if (lastActive == today || lastActive == today.minusDays(1)) {
            settings.getInt(KEY_COUNT, 0)
        } else {
            0
        }
    }

    fun getEarnedBadges(): List<Achievement.RoundStreakBadge> =
        getEarnedBadgesRaw().map { Achievement.RoundStreakBadge(it.roundKey, it.date) }

    fun hasEarnedForRound(roundKey: String): Boolean =
        getEarnedBadgesRaw().any { it.roundKey == roundKey }

    // ── internal helpers ──────────────────────────────────────────────────────────

    @Serializable
    private data class BadgeEntry(val roundKey: String, val date: LocalDate)

    private fun getEarnedBadgesRaw(): List<BadgeEntry> {
        val raw = settings.getStringOrNull(KEY_BADGES) ?: return emptyList()
        return runCatching { json.decodeFromString<List<BadgeEntry>>(raw) }.getOrDefault(emptyList())
    }

    private fun addEarnedBadge(entry: BadgeEntry) {
        val updated = getEarnedBadgesRaw() + entry
        settings.putString(KEY_BADGES, json.encodeToString(updated))
    }

    private fun LocalDate.minusDays(n: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() - n)

    private companion object {
        const val KEY_ROUND = "round_streak_round_key"
        const val KEY_LAST_ACTIVE = "round_streak_last_active"
        const val KEY_COUNT = "round_streak_count"
        const val KEY_BADGES = "round_streak_badges"
        val json = Json { ignoreUnknownKeys = true }
    }
}
