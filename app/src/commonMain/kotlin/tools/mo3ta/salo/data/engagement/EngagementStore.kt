package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.domain.EngagementData
import tools.mo3ta.salo.domain.UserAchievement

class EngagementStore(private val settings: Settings) {

    fun recordOpen(today: LocalDate): EngagementData {
        val openCount = settings.getInt(KEY_OPEN_COUNT, 0) + 1
        settings.putInt(KEY_OPEN_COUNT, openCount)

        val lastDateStr = settings.getStringOrNull(KEY_LAST_OPEN_DATE)
        val lastDate = lastDateStr?.let { LocalDate.parse(it) }

        var graceConsumedNow = false
        val streak = when {
            lastDate == null -> 1
            lastDate == today -> settings.getInt(KEY_STREAK, 1)
            lastDate == today.minusDays(1) -> settings.getInt(KEY_STREAK, 1) + 1
            lastDate == today.minusDays(2) -> {
                if (isGraceAvailable(today)) {
                    consumeGrace(today)
                    graceConsumedNow = true
                    settings.getInt(KEY_STREAK, 1) + 1
                } else {
                    1
                }
            }
            else -> 1
        }

        if (lastDate != today) {
            settings.putString(KEY_LAST_OPEN_DATE, today.toString())
            settings.putInt(KEY_STREAK, streak)
        }

        val badge7Already = settings.getBoolean(KEY_BADGE_7, false)
        val badge30Already = settings.getBoolean(KEY_BADGE_30, false)
        val newBadge = when {
            streak >= 30 && !badge30Already -> {
                settings.putBoolean(KEY_BADGE_30, true)
                settings.putString(KEY_BADGE_30_DATE, today.toString())
                BadgeType.STREAK_30
            }
            streak >= 7 && !badge7Already -> {
                settings.putBoolean(KEY_BADGE_7, true)
                settings.putString(KEY_BADGE_7_DATE, today.toString())
                BadgeType.STREAK_7
            }
            else -> null
        }

        val notifAskedAt = settings.getInt(KEY_NOTIF_ASKED_AT_OPEN, -1)
        val shouldAskNotif = notifAskedAt == -1
        if (shouldAskNotif) settings.putInt(KEY_NOTIF_ASKED_AT_OPEN, openCount)

        return EngagementData(
            openCount = openCount,
            currentStreak = streak,
            newlyEarnedBadge = newBadge,
            shouldRequestNotifPermission = shouldAskNotif,
            graceConsumedNow = graceConsumedNow,
        )
    }

    fun wasGraceConsumedToday(today: LocalDate): Boolean {
        val date = settings.getStringOrNull(KEY_GRACE_DATE) ?: return false
        return LocalDate.parse(date) == today
    }

    fun shouldShowGraceWarning(today: LocalDate): Boolean {
        val graceDate = settings.getStringOrNull(KEY_GRACE_DATE) ?: return false
        if (runCatching { LocalDate.parse(graceDate) }.getOrNull() != today) return false
        return settings.getStringOrNull(KEY_GRACE_WARNING_SHOWN_DATE) != graceDate
    }

    fun markGraceWarningShown(today: LocalDate) {
        if (wasGraceConsumedToday(today)) {
            settings.putString(KEY_GRACE_WARNING_SHOWN_DATE, today.toString())
        }
    }

    private fun isGraceAvailable(today: LocalDate): Boolean {
        val used = settings.getBoolean(KEY_GRACE_USED, false)
        if (!used) return true
        val lastGrace = settings.getStringOrNull(KEY_GRACE_DATE) ?: return true
        return today.toEpochDays() - LocalDate.parse(lastGrace).toEpochDays() >= 7
    }

    private fun consumeGrace(today: LocalDate) {
        settings.putBoolean(KEY_GRACE_USED, true)
        settings.putString(KEY_GRACE_DATE, today.toString())
    }

    fun getCurrentStreak(): Int = settings.getInt(KEY_STREAK, 1)

    /** Records the date when FCM permission was first denied. No-op if already recorded. */
    fun saveFcmPermDenied(today: LocalDate) {
        if (settings.getStringOrNull(KEY_FCM_PERM_DENIED_DATE) == null) {
            settings.putString(KEY_FCM_PERM_DENIED_DATE, today.toString())
        }
    }

    /** Resets the FCM denial date to today (called after showing the re-ask dialog). */
    fun resetFcmPermDenied(today: LocalDate) {
        settings.putString(KEY_FCM_PERM_DENIED_DATE, today.toString())
    }

    /** Clears FCM denial tracking (called when permission is granted). */
    fun clearFcmPermDenied() {
        settings.remove(KEY_FCM_PERM_DENIED_DATE)
    }

    /** Days since FCM permission was denied, or -1 if never denied. */
    fun fcmPermDeniedDaysAgo(today: LocalDate): Int {
        val dateStr = settings.getStringOrNull(KEY_FCM_PERM_DENIED_DATE) ?: return -1
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return -1
        return (today.toEpochDays() - date.toEpochDays()).toInt()
    }

    fun shouldShowReviewDialog(today: LocalDate, installDate: LocalDate): Boolean {
        if (settings.getBoolean(KEY_REVIEW_COMPLETED, false)) return false
        val dismissCount = settings.getInt(KEY_REVIEW_DISMISS_COUNT, 0)
        if (dismissCount >= 3) return false
        val daysSinceInstall = (today.toEpochDays() - installDate.toEpochDays()).toInt()
        if (daysSinceInstall < 2) return false
        val lastShown = settings.getStringOrNull(KEY_REVIEW_LAST_SHOWN)
        if (lastShown == null) return true
        val lastDate = runCatching { LocalDate.parse(lastShown) }.getOrNull() ?: return true
        val intervalDays = if (dismissCount >= 2) 7 else 2
        return (today.toEpochDays() - lastDate.toEpochDays()) >= intervalDays
    }

    fun markReviewDialogShown(today: LocalDate) {
        settings.putString(KEY_REVIEW_LAST_SHOWN, today.toString())
        val count = settings.getInt(KEY_REVIEW_DISMISS_COUNT, 0)
        settings.putInt(KEY_REVIEW_DISMISS_COUNT, count + 1)
    }

    fun markReviewCompleted() {
        settings.putBoolean(KEY_REVIEW_COMPLETED, true)
    }

    fun missedDays(today: LocalDate): Int {
        val lastDateStr = settings.getStringOrNull(KEY_LAST_OPEN_DATE) ?: return 0
        val lastDate = LocalDate.parse(lastDateStr)
        val days = today.toEpochDays() - lastDate.toEpochDays()
        return if (days > 0) days.toInt() else 0
    }

    fun checkAndSaveRankAchievement(
        roundKey: String,
        rank: Int,
        today: LocalDate,
        score: Int? = null,
        winnerCode: String = "",
    ): Achievement.RankAchievement? {
        val existing = getRankAchievementsRaw()
        val existingIndex = existing.indexOfFirst { it.roundKey == roundKey }
        if (existingIndex != -1) {
            val current = existing[existingIndex]
            val updated = current.copy(
                score = score ?: current.score,
                winnerCode = winnerCode.ifBlank { current.winnerCode },
            )
            if (updated != current) {
                settings.putString(
                    KEY_RANK_ACHIEVEMENTS,
                    encodeRankAchievements(existing.toMutableList().apply { set(existingIndex, updated) }),
                )
            }
            return null
        }
        val updated = existing + RankEntry(roundKey, rank, today, score, winnerCode)
        settings.putString(KEY_RANK_ACHIEVEMENTS, encodeRankAchievements(updated))
        return Achievement.RankAchievement(
            roundKey = roundKey,
            rank = rank,
            earnedDate = today,
            score = score,
            winnerCode = winnerCode,
        )
    }

    /**
     * Replace the local rank history with the copy the server holds at
     * `mohamed_lovers/users/{uid}/achievements` (account restore).
     *
     * The server entry has no earned-date, so the round key — the round's Friday date — is used
     * as the date, falling back to [fallbackDate] for any key that is not a date.
     */
    fun restoreRankAchievements(achievements: Map<String, UserAchievement>, fallbackDate: LocalDate) {
        val entries = achievements.entries
            .sortedBy { it.key }
            .map { (roundKey, achievement) ->
                RankEntry(
                    roundKey = roundKey,
                    rank = achievement.rank,
                    date = runCatching { LocalDate.parse(roundKey) }.getOrDefault(fallbackDate),
                    score = achievement.score,
                    winnerCode = achievement.winnerCode,
                )
            }
        settings.putString(KEY_RANK_ACHIEVEMENTS, encodeRankAchievements(entries))
    }

    fun getAllAchievements(): List<Achievement> {
        val streakBadges = buildList {
            settings.getStringOrNull(KEY_BADGE_7_DATE)?.let {
                add(Achievement.StreakBadge(BadgeType.STREAK_7, LocalDate.parse(it)))
            }
            settings.getStringOrNull(KEY_BADGE_30_DATE)?.let {
                add(Achievement.StreakBadge(BadgeType.STREAK_30, LocalDate.parse(it)))
            }
        }
        val rankAchievements = getRankAchievementsRaw().map {
            Achievement.RankAchievement(
                roundKey = it.roundKey,
                rank = it.rank,
                earnedDate = it.date,
                score = it.score,
                winnerCode = it.winnerCode,
            )
        }
        return (streakBadges + rankAchievements).sortedByDescending {
            when (it) {
                is Achievement.StreakBadge -> it.earnedDate
                is Achievement.RankAchievement -> it.earnedDate
                is Achievement.RoundStreakBadge -> it.earnedDate
            }
        }
    }

    @Serializable
    private data class RankEntry(
        val roundKey: String,
        val rank: Int,
        val date: LocalDate,
        val score: Int? = null,
        val winnerCode: String = "",
    )

    private fun getRankAchievementsRaw(): List<RankEntry> {
        val raw = settings.getStringOrNull(KEY_RANK_ACHIEVEMENTS) ?: return emptyList()
        if (raw.startsWith("[")) {
            return runCatching { json.decodeFromString<List<RankEntry>>(raw) }.getOrDefault(emptyList())
        }
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size < 3) return@mapNotNull null
            val rank = parts[1].toIntOrNull() ?: return@mapNotNull null
            val score = parts.getOrNull(3)?.toIntOrNull()
            runCatching { RankEntry(parts[0], rank, LocalDate.parse(parts[2]), score) }.getOrNull()
        }
    }

    private fun encodeRankAchievements(list: List<RankEntry>): String =
        json.encodeToString(list)

    private fun LocalDate.minusDays(n: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() - n)

    private companion object {
        const val KEY_OPEN_COUNT = "eng_open_count"
        const val KEY_LAST_OPEN_DATE = "eng_last_open_date"
        const val KEY_STREAK = "eng_streak"
        const val KEY_BADGE_7 = "eng_badge_7"
        const val KEY_BADGE_30 = "eng_badge_30"
        const val KEY_BADGE_7_DATE = "eng_badge_7_date"
        const val KEY_BADGE_30_DATE = "eng_badge_30_date"
        const val KEY_NOTIF_ASKED_AT_OPEN = "eng_notif_asked_at"
        const val KEY_RANK_ACHIEVEMENTS = "eng_rank_achievements"
        const val KEY_GRACE_USED = "eng_grace_used"
        const val KEY_GRACE_DATE = "eng_grace_date"
        const val KEY_GRACE_WARNING_SHOWN_DATE = "eng_grace_warning_shown_date"
        const val KEY_FCM_PERM_DENIED_DATE = "eng_fcm_perm_denied_date"
        const val KEY_REVIEW_COMPLETED = "eng_review_completed"
        const val KEY_REVIEW_LAST_SHOWN = "eng_review_last_shown"
        const val KEY_REVIEW_DISMISS_COUNT = "eng_review_dismiss_count"
        val json = Json { ignoreUnknownKeys = true }
    }
}
