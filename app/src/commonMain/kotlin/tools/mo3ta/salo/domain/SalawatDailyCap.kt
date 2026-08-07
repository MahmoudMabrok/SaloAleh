package tools.mo3ta.salo.domain

/**
 * Hard ceiling on how many salawat the client may push into the Mohamed Lovers competition in a
 * single Cairo day, across every source (taps, manual entry, extension sync) and every round.
 *
 * This is a different ceiling from [MOHAMED_LOVERS_MANUAL_DAILY_CAP], which limits only the
 * manual ("record external") sheet. This one is the last gate before the network write: whatever
 * survives the manual cap still has to fit under it.
 */
const val MOHAMED_LOVERS_DAILY_PUSH_CAP = 25_000

/**
 * Pure math for the daily push cap. All of it works off a single number — how much has already
 * been pushed for the current Cairo day — which the caller reconciles from two sources:
 *
 * - the **local ledger** (`MohamedLoversSessionStore.dailyPushUsed`), which counts what this device
 *   pushed today and survives the app being closed and reopened later the same day;
 * - the **server-derived day total** ([serverDayTotal]), `totalCount - yesterdayTotalScore`, where
 *   `yesterdayTotalScore` is the baseline the nightly cron stamps at 23:45 Cairo. It survives a
 *   reinstall (which wipes the local ledger) but resets at a Friday 19:00 round rollover.
 *
 * Taking the **higher** of the two covers both holes: the local ledger carries the day across a
 * round rollover, the server total carries it across a reinstall.
 */
object SalawatDailyCap {

    const val DAILY_CAP = MOHAMED_LOVERS_DAILY_PUSH_CAP

    /**
     * Today's competition count as the server sees it: the round total minus the baseline the
     * daily cron stamped at the last 23:45 Cairo close. A player with no baseline yet (joined
     * today, or a freshly rolled-over round node) reads their whole round total, which for them
     * *is* today's count.
     */
    fun serverDayTotal(totalCount: Int, yesterdayTotalScore: Int): Int =
        (totalCount - yesterdayTotalScore).coerceAtLeast(0)

    /** How much may still be pushed today after [pushedToday] has already gone out. */
    fun remaining(pushedToday: Int, cap: Int = DAILY_CAP): Int =
        (cap - pushedToday.coerceAtLeast(0)).coerceAtLeast(0)

    /**
     * The portion of [requested] that fits under the day's remaining allowance. The rest is not
     * deferred to tomorrow — it is discarded, and the local score is reset to match the server.
     */
    fun allowedPush(requested: Int, pushedToday: Int, cap: Int = DAILY_CAP): Int =
        requested.coerceAtLeast(0).coerceAtMost(remaining(pushedToday, cap))
}
