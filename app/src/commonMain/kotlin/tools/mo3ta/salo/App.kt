package tools.mo3ta.salo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.EngagementData
import tools.mo3ta.salo.ui.AchievementCelebrationDialog
import tools.mo3ta.salo.ui.AchievementsScreen
import tools.mo3ta.salo.ui.HadithListScreen
import tools.mo3ta.salo.ui.MohamedLoversScreen
import tools.mo3ta.salo.ui.FcmPermissionReminderDialog
import tools.mo3ta.salo.ui.NotificationRationaleDialog
import tools.mo3ta.salo.ui.OnboardingScreen
import tools.mo3ta.salo.ui.PlatformBackHandler
import tools.mo3ta.salo.ui.settings.SettingsScreen
import tools.mo3ta.salo.ui.tendays.TenDaysScreen

@Composable
fun App(
    engagementData: EngagementData? = null,
    onNotificationPermissionRequest: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var showRationale by remember {
            mutableStateOf(engagementData?.shouldRequestNotifPermission == true)
        }
        var pendingBadge by remember {
            mutableStateOf(
                engagementData?.newlyEarnedBadge?.let {
                    Achievement.StreakBadge(it, Clock.System.todayIn(TimeZone.currentSystemDefault()))
                }
            )
        }
        var showAchievements by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showHadithList by remember { mutableStateOf(false) }
        var showOnboarding by remember { mutableStateOf(false) }
        var showTenDays by remember { mutableStateOf(false) }

        PlatformBackHandler(enabled = showTenDays || showHadithList || showAchievements || showSettings || showOnboarding) {
            when {
                showTenDays -> showTenDays = false
                showHadithList -> showHadithList = false
                showAchievements -> showAchievements = false
                showOnboarding -> showOnboarding = false
                showSettings -> showSettings = false
            }
        }

        when {
            showOnboarding -> OnboardingScreen(onDone = { showOnboarding = false })
            showSettings -> SettingsScreen(
                onBack = { showSettings = false },
                onOpenOnboarding = { showOnboarding = true },
            )
            showAchievements -> AchievementsScreen(onBack = { showAchievements = false })
            showHadithList -> HadithListScreen(onBack = { showHadithList = false })
            showTenDays -> TenDaysScreen(onBack = { showTenDays = false })
            else -> MohamedLoversScreen(
                onOpenAchievements = { showAchievements = true },
                onOpenSettings = { showSettings = true },
                onOpenHadithList = { showHadithList = true },
                onOpenTenDays = { showTenDays = true },
            )
        }

        if (showRationale) {
            NotificationRationaleDialog(
                onAllow = {
                    showRationale = false
                    showSettings = true
                },
                onDismiss = { showRationale = false },
            )
        }

        var showFcmReminder by remember {
            mutableStateOf(engagementData?.shouldReshowFcmAlert == true)
        }
        if (showFcmReminder) {
            FcmPermissionReminderDialog(
                onAllow = {
                    showFcmReminder = false
                    onNotificationPermissionRequest?.invoke()
                },
                onDismiss = { showFcmReminder = false },
            )
        }

        pendingBadge?.let { badge ->
            AchievementCelebrationDialog(
                achievement = badge,
                onDismiss = { pendingBadge = null },
            )
        }
    }
}
