package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.domain.EngagementData

class EngagementStore(private val settings: Settings) {

    fun recordOpen(today: LocalDate): EngagementData {
        val openCount = settings.getInt(KEY_OPEN_COUNT, 0) + 1
        settings.putInt(KEY_OPEN_COUNT, openCount)

        val lastDateStr = settings.getStringOrNull(KEY_LAST_OPEN_DATE)
        val lastDate = lastDateStr?.let { LocalDate.parse(it) }

        val streak = when {
            lastDate == null -> 1
            lastDate == today -> settings.getInt(KEY_STREAK, 1)
            lastDate == today.minusDays(1) -> settings.getInt(KEY_STREAK, 1) + 1
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
        val shouldAskNotif = openCount == 3 && notifAskedAt == -1
        if (shouldAskNotif) settings.putInt(KEY_NOTIF_ASKED_AT_OPEN, openCount)

        return EngagementData(
            openCount = openCount,
            currentStreak = streak,
            newlyEarnedBadge = newBadge,
            shouldRequestNotifPermission = shouldAskNotif,
        )
    }

    fun missedDays(today: LocalDate): Int {
        val lastDateStr = settings.getStringOrNull(KEY_LAST_OPEN_DATE) ?: return 0
        val lastDate = LocalDate.parse(lastDateStr)
        val days = today.toEpochDays() - lastDate.toEpochDays()
        return if (days > 0) days.toInt() else 0
    }

    fun checkAndSaveRankAchievement(roundKey: String, rank: Int, today: LocalDate): Achievement.RankAchievement? {
        val existing = getRankAchievementsRaw()
        if (existing.any { it.first == roundKey }) return null
        val updated = existing + Triple(roundKey, rank, today)
        settings.putString(KEY_RANK_ACHIEVEMENTS, encodeRankAchievements(updated))
        return Achievement.RankAchievement(roundKey = roundKey, rank = rank, earnedDate = today)
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
        val rankAchievements = getRankAchievementsRaw().map { (rk, rank, date) ->
            Achievement.RankAchievement(roundKey = rk, rank = rank, earnedDate = date)
        }
        return (streakBadges + rankAchievements).sortedByDescending {
            when (it) {
                is Achievement.StreakBadge -> it.earnedDate.toString()
                is Achievement.RankAchievement -> it.earnedDate.toString()
            }
        }
    }

    private fun getRankAchievementsRaw(): List<Triple<String, Int, LocalDate>> {
        val raw = settings.getStringOrNull(KEY_RANK_ACHIEVEMENTS) ?: return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) return@mapNotNull null
            val rank = parts[1].toIntOrNull() ?: return@mapNotNull null
            runCatching { Triple(parts[0], rank, LocalDate.parse(parts[2])) }.getOrNull()
        }
    }

    private fun encodeRankAchievements(list: List<Triple<String, Int, LocalDate>>): String =
        list.joinToString(";") { "${it.first}:${it.second}:${it.third}" }

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
    }
}
