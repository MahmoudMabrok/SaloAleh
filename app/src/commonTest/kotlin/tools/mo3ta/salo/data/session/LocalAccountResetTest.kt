package tools.mo3ta.salo.data.session

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalAccountResetTest {

    @Test
    fun accountScopedKeys_areCleared() {
        val settings = MapSettings()
        settings.putString("user_uid", "old-uid")
        settings.putInt("heart_score", 480)
        settings.putInt("round_streak_count", 9)
        settings.putInt("daily_goal_progress", 1200)
        settings.putString("eng_rank_achievements", "[]")
        settings.putInt("ghars_challenge_lifetime", 700)
        settings.putString("user_nickname", "someone")

        LocalAccountReset(settings).wipeAccountData()

        assertNull(settings.getStringOrNull("user_uid"))
        assertNull(settings.getIntOrNull("heart_score"))
        assertNull(settings.getIntOrNull("round_streak_count"))
        assertNull(settings.getIntOrNull("daily_goal_progress"))
        assertNull(settings.getStringOrNull("eng_rank_achievements"))
        assertNull(settings.getIntOrNull("ghars_challenge_lifetime"))
        assertNull(settings.getStringOrNull("user_nickname"))
    }

    @Test
    fun devicePreferences_survive() {
        val settings = MapSettings()
        settings.putString("app_language", "en")
        settings.putInt("salawat_variant_index", 3)
        settings.putBoolean("notif_daily_enabled", false)
        settings.putBoolean("show_rank_chip", false)
        settings.putBoolean("top_bar_tooltips_shown", true)
        settings.putString("user_uid", "old-uid")

        LocalAccountReset(settings).wipeAccountData()

        assertEquals("en", settings.getStringOrNull("app_language"))
        assertEquals(3, settings.getIntOrNull("salawat_variant_index"))
        assertFalse(settings.getBoolean("notif_daily_enabled", true))
        assertFalse(settings.getBoolean("show_rank_chip", true))
        assertTrue(settings.getBoolean("top_bar_tooltips_shown", false))
        assertNull(settings.getStringOrNull("user_uid"))
    }

    @Test
    fun purchases_survive_becauseTheyFollowTheStoreAccount() {
        val settings = MapSettings()
        settings.putBoolean("purchased_support_premium", true)
        settings.putBoolean("sub_active_generous_month", true)

        LocalAccountReset(settings).wipeAccountData()

        assertTrue(settings.getBoolean("purchased_support_premium", false))
        assertTrue(settings.getBoolean("sub_active_generous_month", false))
    }

    @Test
    fun notificationAskMarker_survives_soTogglesAreNotSilentlyTurnedOff() {
        val settings = MapSettings()
        // MainActivity reads the absence of this key as a first-ever open and calls
        // NotificationSettingsStore.initializeToOff(), which would undo the preserved toggles.
        settings.putInt("eng_notif_asked_at", 1)
        settings.putBoolean("notif_daily_enabled", true)

        LocalAccountReset(settings).wipeAccountData()

        assertEquals(1, settings.getIntOrNull("eng_notif_asked_at"))
        assertTrue(settings.getBoolean("notif_daily_enabled", false))
    }

    @Test
    fun fcmToken_isDropped_soTheRestoredUidRegistersAFreshOne() {
        val settings = MapSettings()
        settings.putString("fcm_token", "token-of-previous-account")
        settings.putBoolean("fcm_token_synced", true)

        LocalAccountReset(settings).wipeAccountData()

        assertNull(settings.getStringOrNull("fcm_token"))
        assertFalse(settings.getBoolean("fcm_token_synced", false))
    }
}
