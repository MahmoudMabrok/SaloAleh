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
import tools.mo3ta.salo.ui.NotificationRationaleDialog
import tools.mo3ta.salo.ui.settings.SettingsScreen

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

        when {
            showSettings -> SettingsScreen(onBack = { showSettings = false })
            showAchievements -> AchievementsScreen(onBack = { showAchievements = false })
            showHadithList -> HadithListScreen(onBack = { showHadithList = false })
            else -> MohamedLoversScreen(
                onOpenAchievements = { showAchievements = true },
                onOpenSettings = { showSettings = true },
                onOpenHadithList = { showHadithList = true },
            )
        }

        if (showRationale) {
            NotificationRationaleDialog(
                onAllow = {
                    showRationale = false
                    onNotificationPermissionRequest?.invoke()
                },
                onDismiss = { showRationale = false },
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
