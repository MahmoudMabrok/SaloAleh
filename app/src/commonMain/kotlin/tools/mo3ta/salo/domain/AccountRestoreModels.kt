package tools.mo3ta.salo.domain

/**
 * What the server still knows about an account, read in one pass before a restore commits.
 *
 * Limited to the client-readable paths: the round's player node, `users/{uid}/achievements` and
 * `users/{uid}/installDate`. The rest of the user node is `.read: false` and stays server-only,
 * so notification preferences are not recovered — the user re-sets them in Settings.
 */
data class AccountSnapshot(
    /** Proof the account exists: written on every app start, so any real account has one. */
    val installDate: String = "",
    val achievements: Map<String, UserAchievement> = emptyMap(),
    /** Current round competition score — informational, the leaderboard is its own source. */
    val totalCount: Int = 0,
    /** Today's published competition count, which the daily leaderboard ranks on. */
    val todayCount: Int = 0,
    val roundStreak: Int = 0,
    /** The name published on the leaderboard; equals the uid suffix when no nickname was set. */
    val nickname: String = "",
)

/** What a restore recovered, for the confirmation shown to the user. */
data class AccountRestoreSummary(
    val totalCount: Int = 0,
    val achievements: Int = 0,
    val roundStreak: Int = 0,
    val challengeTotals: Int = 0,
)

sealed interface AccountRestoreResult {
    data class Restored(val summary: AccountRestoreSummary) : AccountRestoreResult

    /** The text is not a backup code at all. */
    data object InvalidCode : AccountRestoreResult

    /** The user pasted their public (hashed) id instead of the backup code. */
    data object PublicIdGiven : AccountRestoreResult

    /** The code belongs to the account already active on this device — nothing to do. */
    data object SameAccount : AccountRestoreResult

    /** Well-formed code, but the server has no such account. Local data is left untouched. */
    data object NotFound : AccountRestoreResult

    /** The lookup failed (offline, permissions). Local data is left untouched. */
    data class Failed(val error: Throwable? = null) : AccountRestoreResult
}
