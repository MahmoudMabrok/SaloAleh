package tools.mo3ta.salo.domain

/** Current daily streak at which a challenge unlocks the higher external-entry allowance. */
const val MILESTONE_MANUAL_STREAK_DAYS = 30

/** External-entry daily cap for a user with a milestone-eligible streak. */
const val MILESTONE_MANUAL_DAILY_CAP = 10_000

/**
 * External ("manual") entry allowance for the count challenges — the sheet where a count
 * reached on fingers or a tasbih is typed in.
 *
 * The allowance is per challenge, per user, per Cairo day. A current streak of at least
 * [MILESTONE_MANUAL_STREAK_DAYS] in that challenge raises its cap to
 * [MILESTONE_MANUAL_DAILY_CAP]; every other challenge keeps its own base cap. Eligibility
 * follows the *current* streak, so a streak that later resets drops the cap back to base.
 * In-app taps never consume this allowance.
 */
object ChallengeManualCap {

    fun baseCapFor(challenge: ChallengeType): Int = when (challenge) {
        ChallengeType.BAQIYAT -> BAQIYAT_MANUAL_DAILY_CAP
        else -> CHALLENGE_MANUAL_DAILY_CAP
    }

    /** Cap for [challenge] given the user's current streak in that same challenge. */
    fun dailyCap(challenge: ChallengeType, currentStreak: Int): Int =
        dailyCap(baseCapFor(challenge), currentStreak)

    /** Cap from an explicit base — usable by stores that already know their own base. */
    fun dailyCap(baseCap: Int, currentStreak: Int): Int =
        if (currentStreak >= MILESTONE_MANUAL_STREAK_DAYS) maxOf(baseCap, MILESTONE_MANUAL_DAILY_CAP) else baseCap
}
