package tools.mo3ta.salo.data.session

import com.russhwolf.settings.Settings
import tools.mo3ta.salo.data.billing.ProductRegistry

/**
 * Wipes the identity-scoped half of local storage when an account is restored onto this device.
 *
 * Everything the app persists lives in one key-value store, and most of it belongs to the
 * *account* (uid, salawat counters, streaks, heart index, badges, achievements, ledgers) rather
 * than to the device. Adopting someone else's backup code while that state survives would let
 * the throwaway account's counters be published under the restored uid — most damagingly the
 * absolute writes (`todayCount`, each challenge's lifetime `totalCount`), which would overwrite
 * real server values with this device's zeroes.
 *
 * So a restore clears the store wholesale and re-applies only the keys below, which describe the
 * *device* rather than the account: display preferences, notification toggles, and purchases
 * (Play entitlements follow the Google account, not the backup code, and are re-queried at
 * startup anyway).
 *
 * The FCM token is deliberately **not** preserved: the restored uid must register a fresh token,
 * which `MainActivity.ensureFcmTokenSynced()` does on the next start once the local copy is gone.
 */
class LocalAccountReset(private val settings: Settings) {

    fun wipeAccountData() {
        val booleans = DEVICE_BOOLEAN_KEYS.plus(purchaseKeys())
            .mapNotNull { key -> settings.getBooleanOrNull(key)?.let { key to it } }
        val strings = DEVICE_STRING_KEYS
            .mapNotNull { key -> settings.getStringOrNull(key)?.let { key to it } }
        val ints = DEVICE_INT_KEYS
            .mapNotNull { key -> settings.getIntOrNull(key)?.let { key to it } }

        settings.clear()

        booleans.forEach { (key, value) -> settings.putBoolean(key, value) }
        strings.forEach { (key, value) -> settings.putString(key, value) }
        ints.forEach { (key, value) -> settings.putInt(key, value) }
    }

    private fun purchaseKeys(): List<String> =
        ProductRegistry.oneTimeProductIds.map { "purchased_$it" } +
            ProductRegistry.subscriptionProductIds.map { "sub_active_$it" }

    private companion object {
        val DEVICE_BOOLEAN_KEYS = listOf(
            // Notification and display preferences.
            "notif_daily_enabled",
            "notif_friday_enabled",
            "notif_evening_enabled",
            "notif_server_reminders_enabled",
            "notif_leaderboard_enabled",
            "show_rank_chip",
            "use_daily_leaderboard",
            "hadith_show_startup",
            "tenDays_autoPlayTakbeer",
            // One-shot "already seen" flags — kept so a restore does not replay every promo,
            // tooltip and announcement the user has already dismissed on this device.
            "rank_chip_tooltip_shown",
            "bubble_tooltip_shown",
            "daily_lb_promo_shown",
            "top_bar_tooltips_shown",
            "takbeer_announcement_shown",
            "salawat_variant_announcement_shown",
            "nickname_announcement_shown",
            "auto_click_warning_shown",
            "eng_review_completed",
            "first_launch_done",
            "install_referrer_checked",
        )

        val DEVICE_STRING_KEYS = listOf(
            "app_language",
            "update_prompt_dismissed_version",
            "eng_review_last_shown",
        )

        val DEVICE_INT_KEYS = listOf(
            // Marks that this device has already been through the notification-permission ask.
            // It must survive: MainActivity treats its absence as a first-ever open and calls
            // NotificationSettingsStore.initializeToOff(), which would silently switch off the
            // reminder toggles this reset just went out of its way to preserve.
            "eng_notif_asked_at",
            "eng_review_dismiss_count",
            "salawat_variant_index",
            "hadith_current_index",
            "tenDays_takbeerIntervalMinutes",
            "tenDays_takbeerRepeatCount",
        )
    }
}
