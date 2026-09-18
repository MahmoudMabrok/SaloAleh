package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.data.time.daysUntilFriday
import tools.mo3ta.salo.data.time.weekKey
import tools.mo3ta.salo.data.time.weekStartSaturday
import tools.mo3ta.salo.domain.ChallengeType

/** 0 means the user has not set a personal weekly goal. */
const val WEEKLY_GOAL_UNSET = 0
const val WEEKLY_GOAL_MAX = 1_000_000

val WEEKLY_GOAL_PRESETS: List<Int> = listOf(100, 300, 500, 1_000, 2_000)

data class WeeklyGoalProgress(
    val challengeId: String,
    val goal: Int,
    val count: Int,
    val weekStart: LocalDate,
    val daysLeft: Int,
) {
    val hasGoal: Boolean get() = goal > 0
    val completed: Boolean get() = hasGoal && count >= goal
    val fraction: Float
        get() = if (goal <= 0) 0f else (count.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
}

/**
 * Personal Saturday→Friday weekly goal per challenge. Local only — not a leaderboard.
 *
 * Challenge stores keep only *today's* count and wipe it at Cairo midnight, so this store
 * folds each day's running total into a week accumulator:
 *  - new week (Saturday) resets counts and keeps the goal
 *  - same Cairo day replaces today's contribution (taps don't double-count)
 *  - a later day in the same week adds today's count on top of days already folded in
 */
class WeeklyGoalStore(private val settings: Settings) {

    fun goal(challengeId: String): Int =
        settings.getInt(goalKey(challengeId), WEEKLY_GOAL_UNSET).coerceIn(WEEKLY_GOAL_UNSET, WEEKLY_GOAL_MAX)

    fun goal(type: ChallengeType): Int = goal(type.id)

    fun setGoal(challengeId: String, goal: Int) {
        val clamped = goal.coerceIn(WEEKLY_GOAL_UNSET, WEEKLY_GOAL_MAX)
        if (clamped == WEEKLY_GOAL_UNSET) {
            settings.remove(goalKey(challengeId))
        } else {
            settings.putInt(goalKey(challengeId), clamped)
        }
    }

    fun setGoal(type: ChallengeType, goal: Int) = setGoal(type.id, goal)

    fun weekCount(challengeId: String, today: LocalDate): Int {
        rolloverIfNeeded(today)
        return settings.getInt(countKey(challengeId), 0).coerceAtLeast(0)
    }

    fun weekCount(type: ChallengeType, today: LocalDate): Int = weekCount(type.id, today)

    /**
     * Fold [todayCount] into this week's total for [challengeId]. Returns the week total.
     * Safe to call on every tap and on screen/hub enter.
     */
    fun syncFromTodayCount(challengeId: String, today: LocalDate, todayCount: Int): Int {
        rolloverIfNeeded(today)
        val safeToday = todayCount.coerceAtLeast(0)
        val lastDate = settings.getStringOrNull(lastDateKey(challengeId))
        val current = settings.getInt(countKey(challengeId), 0).coerceAtLeast(0)
        val updated = when {
            lastDate == today.toString() -> {
                val lastToday = settings.getInt(lastTodayKey(challengeId), 0).coerceAtLeast(0)
                (current - lastToday + safeToday).coerceAtLeast(0)
            }
            lastDate != null && weekKey(runCatching { LocalDate.parse(lastDate) }.getOrNull() ?: today) == weekKey(today) -> {
                current + safeToday
            }
            else -> safeToday
        }
        settings.putInt(countKey(challengeId), updated)
        settings.putString(lastDateKey(challengeId), today.toString())
        settings.putInt(lastTodayKey(challengeId), safeToday)
        return updated
    }

    fun syncFromTodayCount(type: ChallengeType, today: LocalDate, todayCount: Int): Int =
        syncFromTodayCount(type.id, today, todayCount)

    fun snapshot(challengeId: String, today: LocalDate): WeeklyGoalProgress {
        rolloverIfNeeded(today)
        return WeeklyGoalProgress(
            challengeId = challengeId,
            goal = goal(challengeId),
            count = settings.getInt(countKey(challengeId), 0).coerceAtLeast(0),
            weekStart = weekStartSaturday(today),
            daysLeft = daysUntilFriday(today),
        )
    }

    fun snapshot(type: ChallengeType, today: LocalDate): WeeklyGoalProgress =
        snapshot(type.id, today)

    fun snapshotAll(today: LocalDate): Map<ChallengeType, WeeklyGoalProgress> =
        ChallengeType.entries.associateWith { snapshot(it, today) }

    private fun rolloverIfNeeded(today: LocalDate) {
        val currentWeek = weekKey(today)
        val storedWeek = settings.getStringOrNull(KEY_WEEK)
        if (storedWeek == currentWeek) return
        settings.putString(KEY_WEEK, currentWeek)
        ChallengeType.entries.forEach { type ->
            settings.putInt(countKey(type.id), 0)
            settings.remove(lastDateKey(type.id))
            settings.remove(lastTodayKey(type.id))
        }
    }

    private companion object {
        const val KEY_WEEK = "weekly_goal_week"
        fun goalKey(id: String) = "weekly_goal_$id"
        fun countKey(id: String) = "weekly_goal_count_$id"
        fun lastDateKey(id: String) = "weekly_goal_last_date_$id"
        fun lastTodayKey(id: String) = "weekly_goal_last_today_$id"
    }
}

/** Lets challenge stores fold today's total without taking WeeklyGoalStore in their constructor. */
internal fun Settings.syncWeeklyGoal(challengeId: String, today: LocalDate, todayCount: Int): Int {
    WeeklyGoalStore(this).syncFromTodayCount(challengeId, today, todayCount)
    return todayCount
}
