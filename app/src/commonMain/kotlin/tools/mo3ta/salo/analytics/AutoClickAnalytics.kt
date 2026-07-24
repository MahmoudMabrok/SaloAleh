package tools.mo3ta.salo.analytics

/**
 * Fired when the auto-click guard first blocks injected taps and warns the user — once per
 * install, at the same moment the dialog appears, so the event count is a count of warned users
 * rather than of blocked taps.
 *
 * Carries only the last 6 characters of the device uid, matching the suffix already shown in
 * place of a nickname on the leaderboard, so a flagged user can be correlated with their entry
 * without shipping the full identifier.
 */
fun AnalyticsManager.logAutoClickBlocked(uidSuffix: String) {
    logAction(
        "auto_click_blocked",
        mapOf("uid_suffix" to uidSuffix),
    )
}
