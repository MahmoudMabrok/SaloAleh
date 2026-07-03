package tools.mo3ta.salo.data.billing

/** Static kill switches, toggled directly in code. No remote config. */
object FeatureFlags {
    /** Master switch for the Score Masking premium feature. False disables it everywhere,
     *  regardless of purchase state: the Settings toggle is hidden and masked leaderboard
     *  entries always render the real score. */
    const val SCORE_MASKING_ENABLED = false
}
