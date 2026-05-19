package tools.mo3ta.salo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.EngagementData
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.ui.AchievementCelebrationDialog
import tools.mo3ta.salo.ui.AchievementsScreen
import tools.mo3ta.salo.ui.HadithListScreen
import tools.mo3ta.salo.ui.MohamedLoversScreen
import tools.mo3ta.salo.ui.FcmPermissionReminderDialog
import tools.mo3ta.salo.ui.NotificationRationaleDialog
import tools.mo3ta.salo.ui.OnboardingScreen
import tools.mo3ta.salo.ui.PlatformBackHandler
import tools.mo3ta.salo.ui.ReviewDialog
import tools.mo3ta.salo.ui.openStorePage
import tools.mo3ta.salo.ui.settings.ExtensionQrScreen
import tools.mo3ta.salo.data.billing.BillingManager
import tools.mo3ta.salo.ui.settings.PaywallScreen
import tools.mo3ta.salo.ui.settings.PremiumPromoDialog
import tools.mo3ta.salo.ui.settings.SettingsScreen
import tools.mo3ta.salo.ui.tendays.TenDaysPromoDialog
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
        var showExtensionQr by remember { mutableStateOf(false) }
        var showPaywall by remember { mutableStateOf(false) }

        PlatformBackHandler(enabled = showPaywall || showTenDays || showExtensionQr || showHadithList || showAchievements || showSettings || showOnboarding) {
            when {
                showPaywall -> showPaywall = false
                showTenDays -> showTenDays = false
                showExtensionQr -> showExtensionQr = false
                showHadithList -> showHadithList = false
                showAchievements -> showAchievements = false
                showOnboarding -> showOnboarding = false
                showSettings -> showSettings = false
            }
        }

        when {
            showPaywall -> PaywallScreen(onBack = { showPaywall = false })
            showExtensionQr -> ExtensionQrScreen(onBack = { showExtensionQr = false })
            showOnboarding -> OnboardingScreen(onDone = { showOnboarding = false })
            showSettings -> SettingsScreen(
                onBack = { showSettings = false },
                onOpenOnboarding = { showOnboarding = true },
                onOpenExtensionQr = { showExtensionQr = true },
                onOpenPaywall = { showPaywall = true },
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

        val settings = koinInject<Settings>()
        var showTenDaysPromo by remember {
            val shown = settings.getBoolean("ten_days_promo_shown", false)
            mutableStateOf(!shown)
        }
        if (showTenDaysPromo) {
            TenDaysPromoDialog(
                onOpen = {
                    settings.putBoolean("ten_days_promo_shown", true)
                    showTenDaysPromo = false
                    showTenDays = true
                },
                onDismiss = {
                    settings.putBoolean("ten_days_promo_shown", true)
                    showTenDaysPromo = false
                },
            )
        }

        val billingManager = koinInject<BillingManager>()
        var showPremiumPromo by remember {
            val shown = settings.getBoolean("premium_promo_shown", false)
            mutableStateOf(!shown && billingManager.isEnabled)
        }
        if (showPremiumPromo) {
            PremiumPromoDialog(
                onOpen = {
                    settings.putBoolean("premium_promo_shown", true)
                    showPremiumPromo = false
                    showPaywall = true
                },
                onDismiss = {
                    settings.putBoolean("premium_promo_shown", true)
                    showPremiumPromo = false
                },
            )
        }

        val engagementStore = koinInject<EngagementStore>()
        val sessionStore = koinInject<MohamedLoversSessionStore>()
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        val installDate = remember {
            runCatching { LocalDate.parse(sessionStore.getOrSetInstallDate(today)) }.getOrDefault(today)
        }
        var showReview by remember {
            mutableStateOf(engagementStore.shouldShowReviewDialog(today, installDate))
        }
        if (showReview) {
            ReviewDialog(
                onGoToStore = {
                    engagementStore.markReviewCompleted()
                    showReview = false
                    openStorePage()
                },
                onDismiss = {
                    engagementStore.markReviewDialogShown(today)
                    showReview = false
                },
            )
        }
    }
}
