package tools.mo3ta.salo.domain

import kotlinx.datetime.LocalDate

/**
 * Gradual per-Cairo-day cap on manually-recorded ("external") salawat for new users.
 *
 * Instead of hiding the manual-entry button from brand-new users, the button stays visible but the
 * amount that may be added manually each day ramps up: 1,000 on install day, 2,000 the next day, and
 * so on by [RAMP_STEP] each day, reaching [MAX_DAILY_CAP] on day 10. From then on the cap stays flat
 * at [MAX_DAILY_CAP] — the ceiling the ongoing "daily external records" cap builds on.
 *
 * Only manual entries count against this cap; in-app taps are never limited.
 */
object SalawatManualCap {

    /** Days the ramp climbs before flattening (install day counts as day 1). */
    const val RAMP_DAYS = 10

    /** Flat cap once the ramp is over (day 10 onward) — the permanent daily external-entry cap. */
    const val MAX_DAILY_CAP = MOHAMED_LOVERS_MANUAL_DAILY_CAP // 10_000

    /** Extra allowance granted per additional day since install (MAX split evenly across the ramp). */
    const val RAMP_STEP = MAX_DAILY_CAP / RAMP_DAYS // 1_000

    /**
     * @param today the current day in `Africa/Cairo`.
     * @param installDate the day the app was first opened (also in `Africa/Cairo`).
     * @return how many salawat may be recorded manually on [today]: 1,000 on install day, climbing by
     *   [RAMP_STEP] each day up to [MAX_DAILY_CAP] on day 10, then flat.
     */
    fun dailyCap(today: LocalDate, installDate: LocalDate): Int {
        val daysSinceInstall = (today.toEpochDays() - installDate.toEpochDays()).coerceAtLeast(0)
        val step = (daysSinceInstall + 1).coerceIn(1, RAMP_DAYS)
        return step * RAMP_STEP
    }
}
