package tools.mo3ta.salo.analytics

object BillingAnalytics {
    const val PAYWALL_VIEWED = "paywall_viewed"
    const val PURCHASE_STARTED = "purchase_started"
    const val PURCHASE_COMPLETED = "purchase_completed"
    const val PURCHASE_FAILED = "purchase_failed"
    const val PURCHASE_RESTORED = "purchase_restored"
    const val SUBSCRIPTION_STARTED = "subscription_started"

    const val PARAM_PRODUCT_ID = "product_id"
    const val PARAM_ERROR = "error"
    const val PARAM_PERIOD = "period"

    const val MILESTONE_SUPPORT_SHOWN = "milestone_support_shown"
    const val MILESTONE_SUPPORT_CTA_TAPPED = "milestone_support_cta_tapped"
    const val MILESTONE_SUPPORT_DISMISSED = "milestone_support_dismissed"
    const val LEADERBOARD_SUPPORT_HINT_TAPPED = "leaderboard_support_hint_tapped"
    const val SETTINGS_DOT_VISIBLE = "settings_dot_visible"

    const val PARAM_MILESTONE_VALUE = "milestone_value"
    const val PARAM_HINT_TYPE = "hint_type"
}
