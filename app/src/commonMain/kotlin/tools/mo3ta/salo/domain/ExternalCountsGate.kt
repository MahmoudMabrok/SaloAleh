package tools.mo3ta.salo.domain

import kotlinx.datetime.LocalDate

/**
 * Gates the "record external counts" entry point — the manual-entry button that lets a user add
 * salawat/dhikr they performed OUTSIDE the app. It stays hidden for brand-new users so the first
 * days are spent tapping in-app, then is revealed permanently once the user has spent
 * [NEW_USER_GRACE_DAYS] days since install.
 *
 * Because install date is fixed and days-since-install only grows, the "forever" part is automatic:
 * once the threshold is crossed it can never go back to hidden. No extra persisted flag is needed.
 *
 * This has nothing to do with the leaderboard, which stays visible to every user.
 */
object ExternalCountsGate {

    /** Days a new user must spend before the manual external-counts entry button appears. */
    const val NEW_USER_GRACE_DAYS = 3

    /**
     * @param today the current day in `Africa/Cairo`.
     * @param installDate the day the app was first opened (also in `Africa/Cairo`).
     * @return true once the user has spent at least [NEW_USER_GRACE_DAYS] days since install.
     */
    fun canShowExternalCountsEntry(today: LocalDate, installDate: LocalDate): Boolean =
        (today.toEpochDays() - installDate.toEpochDays()) >= NEW_USER_GRACE_DAYS
}
