package tools.mo3ta.salo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.EngagementData
import tools.mo3ta.salo.domain.ExternalCountsGate
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.ui.AchievementCelebrationDialog
import tools.mo3ta.salo.ui.AchievementsScreen
import tools.mo3ta.salo.ui.AlKahfReminderDialog
import tools.mo3ta.salo.ui.AutoClickDetectedDialog
import tools.mo3ta.salo.ui.baqiyat.BaqiyatScreen
import tools.mo3ta.salo.ui.ChallengesScreen
import tools.mo3ta.salo.ui.DhikrRewardsScreen
import tools.mo3ta.salo.ui.IstighfarRewardsScreen
import tools.mo3ta.salo.ui.AlBaqaraChallengeScreen
import tools.mo3ta.salo.ui.ZabadScreen
import tools.mo3ta.salo.ui.GharsScreen
import tools.mo3ta.salo.ui.HadithListScreen
import tools.mo3ta.salo.ui.MohamedLoversScreen
import tools.mo3ta.salo.ui.FcmPermissionReminderDialog
import tools.mo3ta.salo.ui.NotificationRationaleDialog
import tools.mo3ta.salo.ui.OnboardingScreen
import tools.mo3ta.salo.ui.PlatformBackHandler
import tools.mo3ta.salo.ui.ReviewDialog
import tools.mo3ta.salo.ui.VersionUpdateDialog
import tools.mo3ta.salo.ui.getAppVersion
import tools.mo3ta.salo.ui.openStorePage
import tools.mo3ta.salo.ui.settings.ExtensionQrScreen
import tools.mo3ta.salo.data.billing.BillingManager
import tools.mo3ta.salo.data.language.LanguageStore
import tools.mo3ta.salo.data.billing.PremiumFeature
import tools.mo3ta.salo.data.billing.ProductRegistry
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.data.billing.SupportTier
import tools.mo3ta.salo.ui.settings.PaywallScreen
import tools.mo3ta.salo.ui.settings.ReferralScreen
import tools.mo3ta.salo.ui.settings.PremiumPromoDialog
import tools.mo3ta.salo.ui.settings.PurchaseSuccessDialog
import tools.mo3ta.salo.ui.settings.SettingsScreen
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.ui.NicknamePromptDialog
import tools.mo3ta.salo.ui.ReferralAnnouncementDialog
import tools.mo3ta.salo.ui.SalawatVariantAnnouncementDialog
import tools.mo3ta.salo.ui.StreakBadgeAnnouncementDialog
import tools.mo3ta.salo.ui.takbeer.TakbeerAnnouncementDialog
import tools.mo3ta.salo.ui.takbeer.TakbeerSessionScreen
import tools.mo3ta.salo.ui.support.MilestoneSupportDialog
import tools.mo3ta.salo.data.MilestoneTracker
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.referral.ReferralStore
import tools.mo3ta.salo.data.reminder.AlKahfReminderStore
import tools.mo3ta.salo.data.update.UpdateChecker
import tools.mo3ta.salo.data.update.UpdatePrompt
import tools.mo3ta.salo.domain.isNewerVersion
import tools.mo3ta.salo.analytics.BillingAnalytics
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.notification.NotificationAction
import tools.mo3ta.salo.notification.NotificationMessage
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.bottom_nav_achievements
import tools.mo3ta.salo.generated.resources.bottom_nav_articles
import tools.mo3ta.salo.generated.resources.bottom_nav_challenges
import tools.mo3ta.salo.generated.resources.bottom_nav_mohamed_lovers
import tools.mo3ta.salo.generated.resources.bottom_nav_settings
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.NotificationMessageDialog
import tools.mo3ta.salo.ui.QuranChallengeScreen
import tools.mo3ta.salo.ui.tendays.TenDaysScreen

// Temporarily suppress app announcements; the review dialog remains enabled after onboarding.
private const val APP_ANNOUNCEMENTS_ENABLED = false

// The per-round streak badge announcement ships independently of the global suppression
// above so the new feature is surfaced once. Flip to false to hide it.
private const val STREAK_BADGE_ANNOUNCEMENT_ENABLED = true

// The remote-config-driven "update available" prompt is independent of the global
// announcement suppression above: it must reach every user when a newer release ships.
// Flip to false to disable the startup version check entirely.
private const val UPDATE_PROMPT_ENABLED = true

// The Friday "read Surah Al-Kahf" reminder is a devotional nudge shown once per Friday
// (Cairo timezone), independent of the globally-suppressed app announcements above.
// Flip to false to disable it.
private const val ALKAHF_REMINDER_ENABLED = true

@Composable
fun App(
    engagementData: EngagementData? = null,
    onNotificationPermissionRequest: (() -> Unit)? = null,
    newVersionAvailable: String? = null,
    notificationMessage: NotificationMessage? = null,
    referralCode: String? = null,
    // Set when a challenge bubble is dragged onto "open app": jump straight to that
    // challenge's screen (no dialog, no leaderboard). Cleared via [onOpenChallengeHandled].
    openChallenge: NotificationAction? = null,
    onOpenChallengeHandled: () -> Unit = {},
    // Set when the auto-click guard blocks injected taps for the first time on this install.
    autoClickDetected: Boolean = false,
    onAutoClickWarningDismissed: () -> Unit = {},
) {
    val languageStore = koinInject<LanguageStore>()
    val storedLang = languageStore.language
    val layoutDirection = when (storedLang) {
        "en", "zh" -> LayoutDirection.Ltr
        else -> LayoutDirection.Rtl
    }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
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
        var selectedTab by remember { mutableStateOf(SaloTab.MohamedLovers) }
        var pendingNotificationMessage by remember(notificationMessage) {
            mutableStateOf(notificationMessage)
        }
        val settings = koinInject<Settings>()
        val analyticsManager = koinInject<AnalyticsManager>()
        val sessionStoreApp = koinInject<MohamedLoversSessionStore>()
        var openLeaderboardSheet by remember { mutableStateOf(false) }
        var showOnboarding by remember { mutableStateOf(!sessionStoreApp.hasExistingUserState()) }
        var showTakbeerSession by remember { mutableStateOf(false) }
        var showTenDays by remember { mutableStateOf(false) }
        var showDhikrRewards by remember { mutableStateOf(false) }
        var showBaqiyatChallenge by remember { mutableStateOf(false) }
        var showIstighfarChallenge by remember { mutableStateOf(false) }
        var showZabadChallenge by remember { mutableStateOf(false) }
        var showGharsChallenge by remember { mutableStateOf(false) }
        var showQuranChallenge by remember { mutableStateOf(false) }
        var showAlBaqaraChallenge by remember { mutableStateOf(false) }
        // Set when a challenge push is tapped: the just-opened challenge screen auto-opens
        // its leaderboard sheet once, then clears the flag.
        var openChallengeLeaderboard by remember { mutableStateOf(false) }
        var showExtensionQr by remember { mutableStateOf(false) }
        var showReferral by remember { mutableStateOf(false) }
        var showPaywall by remember { mutableStateOf(false) }
        var nicknamePromptRequested by remember { mutableStateOf(false) }
        var nicknamePromptDismissedThisSession by remember { mutableStateOf(false) }

        PlatformBackHandler(
            enabled = showPaywall ||
                showReferral ||
                showDhikrRewards ||
                showBaqiyatChallenge ||
                showIstighfarChallenge ||
                showZabadChallenge ||
                showGharsChallenge ||
                showQuranChallenge ||
                showAlBaqaraChallenge ||
                showTakbeerSession ||
                showTenDays ||
                showExtensionQr ||
                showOnboarding ||
                selectedTab != SaloTab.MohamedLovers,
        ) {
            when {
                showPaywall -> showPaywall = false
                showReferral -> showReferral = false
                showDhikrRewards -> showDhikrRewards = false
                showBaqiyatChallenge -> showBaqiyatChallenge = false
                showIstighfarChallenge -> showIstighfarChallenge = false
                showZabadChallenge -> showZabadChallenge = false
                showGharsChallenge -> showGharsChallenge = false
                showQuranChallenge -> showQuranChallenge = false
                showAlBaqaraChallenge -> showAlBaqaraChallenge = false
                showTakbeerSession -> showTakbeerSession = false
                showTenDays -> showTenDays = false
                showExtensionQr -> showExtensionQr = false
                showOnboarding -> showOnboarding = false
                selectedTab != SaloTab.MohamedLovers -> selectedTab = SaloTab.MohamedLovers
            }
        }

        var takbeerAnnouncementDone by remember {
            mutableStateOf(settings.getBoolean("takbeer_announcement_shown", false))
        }
        val referralStore = koinInject<ReferralStore>()
        val firebaseApi = koinInject<MohamedLoversFirebaseApi>()
        LaunchedEffect(Unit) { analyticsManager.setUserId(sessionStoreApp.getOrCreateUid()) }
        // A challenge bubble dragged onto "open app" jumps directly to its screen.
        LaunchedEffect(openChallenge) {
            when (openChallenge ?: return@LaunchedEffect) {
                NotificationAction.OPEN_DHIKR_CHALLENGE -> showDhikrRewards = true
                NotificationAction.OPEN_ISTIGHFAR_CHALLENGE -> showIstighfarChallenge = true
                NotificationAction.OPEN_ZABAD_CHALLENGE -> showZabadChallenge = true
                NotificationAction.OPEN_GHARS_CHALLENGE -> showGharsChallenge = true
                else -> Unit
            }
            onOpenChallengeHandled()
        }
        LaunchedEffect(referralCode) {
            val code = referralCode
                ?: referralStore.getPendingReferralCode()
                ?: run { referralStore.markFirstLaunchDone(); return@LaunchedEffect }
            if (referralStore.isReferralApplied() || referralStore.isFirstLaunchDone()) {
                referralStore.clearPendingReferralCode()
                referralStore.markFirstLaunchDone()
                return@LaunchedEffect
            }
            val myUid = sessionStoreApp.getOrCreateUid()
            val referrerUid = firebaseApi.lookupReferralCode(code).getOrNull()
            if (referrerUid != null && referrerUid != myUid) {
                firebaseApi.applyReferral(referrerUid, myUid).onSuccess {
                    referralStore.saveReferredBy(referrerUid)
                    referralStore.markReferralApplied()
                    referralStore.clearPendingReferralCode()
                }
            } else {
                referralStore.clearPendingReferralCode()
            }
            referralStore.markFirstLaunchDone()
        }
        var salawatVariantAnnouncementDone by remember {
            mutableStateOf(settings.getBoolean("salawat_variant_announcement_shown", false))
        }
        val mohamedLoversViewModel: MohamedLoversViewModel = koinViewModel()
        val mlState by mohamedLoversViewModel.state.collectAsStateWithLifecycle()
        val nicknamePromptBlocked = showPaywall ||
            showReferral ||
            showDhikrRewards ||
            showBaqiyatChallenge ||
            showIstighfarChallenge ||
            showZabadChallenge ||
            showGharsChallenge ||
            showQuranChallenge ||
            showAlBaqaraChallenge ||
            showTakbeerSession ||
            showTenDays ||
            showExtensionQr ||
            showOnboarding
        val canShowAppAnnouncements = APP_ANNOUNCEMENTS_ENABLED && !showOnboarding
        val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
        val installDate = remember {
            runCatching { LocalDate.parse(sessionStoreApp.getOrSetInstallDate(today)) }.getOrDefault(today)
        }
        val manualEntryEnabled = ExternalCountsGate.canShowExternalCountsEntry(today, installDate)
        val shouldShowNicknamePrompt = !nicknamePromptBlocked &&
            sessionStoreApp.getNickname() == null &&
            (nicknamePromptRequested || (selectedTab == SaloTab.MohamedLovers && !nicknamePromptDismissedThisSession))

        when {
            showPaywall -> PaywallScreen(onBack = { showPaywall = false })
            showReferral -> ReferralScreen(onBack = { showReferral = false })
            showExtensionQr -> ExtensionQrScreen(onBack = { showExtensionQr = false })
            showOnboarding -> OnboardingScreen(
                onDone = {
                    showOnboarding = false
                    nicknamePromptRequested = true
                    nicknamePromptDismissedThisSession = false
                },
            )
            showTakbeerSession -> TakbeerSessionScreen(onBack = { showTakbeerSession = false })
            showTenDays -> TenDaysScreen(
                onBack = { showTenDays = false },
                onOpenTakbeerSession = { showTakbeerSession = true },
            )
            showDhikrRewards -> DhikrRewardsScreen(
                onBack = { showDhikrRewards = false },
                openLeaderboard = openChallengeLeaderboard,
                onLeaderboardAutoOpened = { openChallengeLeaderboard = false },
                manualEntryEnabled = manualEntryEnabled,
            )
            showBaqiyatChallenge -> BaqiyatScreen(
                onBack = { showBaqiyatChallenge = false },
                openLeaderboard = openChallengeLeaderboard,
                onLeaderboardAutoOpened = { openChallengeLeaderboard = false },
                manualEntryEnabled = manualEntryEnabled,
            )
            showIstighfarChallenge -> IstighfarRewardsScreen(
                onBack = { showIstighfarChallenge = false },
                openLeaderboard = openChallengeLeaderboard,
                onLeaderboardAutoOpened = { openChallengeLeaderboard = false },
                manualEntryEnabled = manualEntryEnabled,
            )
            showZabadChallenge -> ZabadScreen(
                onBack = { showZabadChallenge = false },
                openLeaderboard = openChallengeLeaderboard,
                onLeaderboardAutoOpened = { openChallengeLeaderboard = false },
                manualEntryEnabled = manualEntryEnabled,
            )
            showGharsChallenge -> GharsScreen(
                onBack = { showGharsChallenge = false },
                openLeaderboard = openChallengeLeaderboard,
                onLeaderboardAutoOpened = { openChallengeLeaderboard = false },
                manualEntryEnabled = manualEntryEnabled,
            )
            showQuranChallenge -> QuranChallengeScreen(
                onBack = { showQuranChallenge = false },
                openLeaderboard = openChallengeLeaderboard,
                onLeaderboardAutoOpened = { openChallengeLeaderboard = false },
                manualEntryEnabled = manualEntryEnabled,
            )
            showAlBaqaraChallenge -> AlBaqaraChallengeScreen(
                onBack = { showAlBaqaraChallenge = false },
            )
            else -> SaloTabScaffold(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                content = { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        when (selectedTab) {
                            SaloTab.MohamedLovers -> MohamedLoversScreen(
                                onOpenPaywall = { showPaywall = true },
                                viewModel = mohamedLoversViewModel,
                                openInfoSheet = openLeaderboardSheet,
                                onInfoSheetOpened = { openLeaderboardSheet = false },
                                announcementsEnabled = APP_ANNOUNCEMENTS_ENABLED,
                            )
                            SaloTab.Challenges -> ChallengesScreen(
                                onOpenDhikrChallenge = { showDhikrRewards = true },
                                onOpenTenDays = { showTenDays = true },
                                onOpenTakbeerSession = { showTakbeerSession = true },
                                onOpenBaqiyatChallenge = { showBaqiyatChallenge = true },
                                onOpenIstighfarChallenge = { showIstighfarChallenge = true },
                                onOpenZabadChallenge = { showZabadChallenge = true },
                                onOpenGharsChallenge = { showGharsChallenge = true },
                                onOpenQuranChallenge = { showQuranChallenge = true },
                                onOpenAlBaqaraChallenge = { showAlBaqaraChallenge = true },
                            )
                            SaloTab.Achievements -> AchievementsScreen(
                                onBack = { selectedTab = SaloTab.MohamedLovers },
                                showBackButton = false,
                            )
                            SaloTab.Articles -> HadithListScreen(
                                onBack = { selectedTab = SaloTab.MohamedLovers },
                                showBackButton = false,
                            )
                            SaloTab.Settings -> SettingsScreen(
                                onBack = { selectedTab = SaloTab.MohamedLovers },
                                showBackButton = false,
                                onOpenOnboarding = {
                                    showOnboarding = true
                                    nicknamePromptDismissedThisSession = false
                                },
                                onOpenExtensionQr = { showExtensionQr = true },
                                onOpenPaywall = { showPaywall = true },
                                onOpenReferral = { showReferral = true },
                            )
                        }
                    }
                },
            )
        }

        if (canShowAppAnnouncements && showRationale) {
            NotificationRationaleDialog(
                onAllow = {
                    showRationale = false
                    selectedTab = SaloTab.Settings
                },
                onDismiss = { showRationale = false },
            )
        }

        var showFcmReminder by remember {
            mutableStateOf(engagementData?.shouldReshowFcmAlert == true)
        }
        if (canShowAppAnnouncements && showFcmReminder) {
            FcmPermissionReminderDialog(
                onAllow = {
                    showFcmReminder = false
                    onNotificationPermissionRequest?.invoke()
                },
                onDismiss = { showFcmReminder = false },
            )
        }

        if (canShowAppAnnouncements) pendingBadge?.let { badge ->
            AchievementCelebrationDialog(
                achievement = badge,
                onDismiss = { pendingBadge = null },
            )
        }

        if (shouldShowNicknamePrompt) {
            NicknamePromptDialog(
                initialName = sessionStoreApp.getNickname().orEmpty(),
                onSave = { name ->
                    mohamedLoversViewModel.saveNickname(name)
                    nicknamePromptRequested = false
                    nicknamePromptDismissedThisSession = false
                    sessionStoreApp.isNicknameAnnouncementShown = true
                    analyticsManager.logAction(AppAnalytics.NICKNAME_ANNOUNCEMENT_OPENED)
                },
                onDismiss = {
                    nicknamePromptRequested = false
                    nicknamePromptDismissedThisSession = true
                    analyticsManager.logAction(AppAnalytics.NICKNAME_ANNOUNCEMENT_DISMISSED)
                },
            )
        }

        if (canShowAppAnnouncements && !shouldShowNicknamePrompt && !takbeerAnnouncementDone) {
            TakbeerAnnouncementDialog(
                onOpen = {
                    settings.putBoolean("takbeer_announcement_shown", true)
                    takbeerAnnouncementDone = true
                    showTakbeerSession = true
                    analyticsManager.logAction(AppAnalytics.TAKBEER_ANNOUNCEMENT_OPENED)
                },
                onDismiss = {
                    settings.putBoolean("takbeer_announcement_shown", true)
                    takbeerAnnouncementDone = true
                    analyticsManager.logAction(AppAnalytics.TAKBEER_ANNOUNCEMENT_DISMISSED)
                },
            )
        }

        if (
            !shouldShowNicknamePrompt &&
            canShowAppAnnouncements &&
            takbeerAnnouncementDone &&
            !salawatVariantAnnouncementDone
        ) {
            SalawatVariantAnnouncementDialog(
                onOpenSettings = {
                    settings.putBoolean("salawat_variant_announcement_shown", true)
                    salawatVariantAnnouncementDone = true
                    selectedTab = SaloTab.Settings
                    analyticsManager.logAction(AppAnalytics.SALAWAT_VARIANT_ANNOUNCEMENT_OPENED)
                },
                onDismiss = {
                    settings.putBoolean("salawat_variant_announcement_shown", true)
                    salawatVariantAnnouncementDone = true
                    analyticsManager.logAction(AppAnalytics.SALAWAT_VARIANT_ANNOUNCEMENT_DISMISSED)
                },
            )
        }

        var referralAnnouncementDone by remember {
            mutableStateOf(settings.getBoolean("referral_announcement_shown", false))
        }
        if (
            !shouldShowNicknamePrompt &&
            canShowAppAnnouncements &&
            salawatVariantAnnouncementDone &&
            !referralAnnouncementDone &&
            installDate < today
        ) {
            ReferralAnnouncementDialog(
                onOpen = {
                    settings.putBoolean("referral_announcement_shown", true)
                    referralAnnouncementDone = true
                    showReferral = true
                    analyticsManager.logAction(AppAnalytics.REFERRAL_ANNOUNCEMENT_OPENED)
                },
                onDismiss = {
                    settings.putBoolean("referral_announcement_shown", true)
                    referralAnnouncementDone = true
                    analyticsManager.logAction(AppAnalytics.REFERRAL_ANNOUNCEMENT_DISMISSED)
                },
            )
        }

        var streakBadgeAnnouncementDone by remember {
            mutableStateOf(settings.getBoolean("streak_badge_announcement_shown", false))
        }
        if (
            STREAK_BADGE_ANNOUNCEMENT_ENABLED &&
            !showOnboarding &&
            !shouldShowNicknamePrompt &&
            !streakBadgeAnnouncementDone
        ) {
            StreakBadgeAnnouncementDialog(
                onOpenAchievements = {
                    settings.putBoolean("streak_badge_announcement_shown", true)
                    streakBadgeAnnouncementDone = true
                    selectedTab = SaloTab.Achievements
                    analyticsManager.logAction(AppAnalytics.STREAK_BADGE_ANNOUNCEMENT_OPENED)
                },
                onDismiss = {
                    settings.putBoolean("streak_badge_announcement_shown", true)
                    streakBadgeAnnouncementDone = true
                    analyticsManager.logAction(AppAnalytics.STREAK_BADGE_ANNOUNCEMENT_DISMISSED)
                },
            )
        }

        val billingManager = koinInject<BillingManager>()
        var showPremiumPromo by remember {
            val shown = settings.getBoolean("premium_promo_shown", false)
            mutableStateOf(!shown && billingManager.isEnabled)
        }
        if (canShowAppAnnouncements && showPremiumPromo) {
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

        var celebratedTier by remember { mutableStateOf<SupportTier?>(null) }
        val repository = koinInject<MohamedLoversRepository>()
        LaunchedEffect(billingManager) {
            billingManager.purchaseEvents.collect { productId ->
                val tier = ProductRegistry.tiers.firstOrNull { it.productId == productId } ?: return@collect
                repository.recordPurchase(productId)
                if (PremiumFeature.SUPPORTER_BADGE in tier.features) {
                    repository.setSupporter(true)
                }
                showPaywall = false
                celebratedTier = tier
            }
        }
        LaunchedEffect(billingManager) {
            billingManager.subscriptionDeactivated.collect {
                repository.setSupporter(false)
            }
        }
        LaunchedEffect(billingManager) {
            billingManager.supporterRestored.collect { isSupporter ->
                repository.setSupporter(isSupporter)
            }
        }
        celebratedTier?.let { tier ->
            PurchaseSuccessDialog(
                tier = tier,
                onDismiss = { celebratedTier = null },
            )
        }

        val milestoneTracker = koinInject<MilestoneTracker>()
        val premiumStore = koinInject<PremiumStore>()
        var milestonePending by remember { mutableStateOf<Int?>(null) }
        val currentScore = mlState.syncedTotal + mlState.sessionClicks
        LaunchedEffect(currentScore, canShowAppAnnouncements) {
            if (premiumStore.highestTier != null) return@LaunchedEffect
            val hit = milestoneTracker.checkMilestone(currentScore) ?: return@LaunchedEffect
            milestonePending = hit
        }
        milestonePending?.let { milestone ->
            MilestoneSupportDialog(
                milestone = milestone,
                onSupport = {
                    milestoneTracker.markShown(milestone)
                    milestonePending = null
                    showPaywall = true
                    analyticsManager.logAction(
                        BillingAnalytics.MILESTONE_SUPPORT_CTA_TAPPED,
                        mapOf(BillingAnalytics.PARAM_MILESTONE_VALUE to milestone.toString()),
                    )
                },
                onDismiss = {
                    milestoneTracker.markShown(milestone)
                    milestonePending = null
                    analyticsManager.logAction(
                        BillingAnalytics.MILESTONE_SUPPORT_DISMISSED,
                        mapOf(BillingAnalytics.PARAM_MILESTONE_VALUE to milestone.toString()),
                    )
                },
            )
            LaunchedEffect(milestone) {
                analyticsManager.logAction(
                    BillingAnalytics.MILESTONE_SUPPORT_SHOWN,
                    mapOf(BillingAnalytics.PARAM_MILESTONE_VALUE to milestone.toString()),
                )
            }
        }

        val engagementStore = koinInject<EngagementStore>()
        var showReview by remember {
            mutableStateOf(engagementStore.shouldShowReviewDialog(today, installDate))
        }
        if (!showOnboarding && showReview) {
            ReviewDialog(
                onGoToStore = {
                    analyticsManager.logAction(AppAnalytics.REVIEW_DIALOG_GO_TO_STORE)
                    engagementStore.markReviewCompleted()
                    showReview = false
                    openStorePage()
                },
                onDismiss = {
                    analyticsManager.logAction(AppAnalytics.REVIEW_DIALOG_DISMISSED)
                    engagementStore.markReviewDialogShown(today)
                    showReview = false
                },
            )
            LaunchedEffect(Unit) {
                analyticsManager.logAction(AppAnalytics.REVIEW_DIALOG_SHOWN)
            }
        }

        pendingNotificationMessage?.let { msg ->
            NotificationMessageDialog(
                message = msg,
                onDismiss = {
                    pendingNotificationMessage = null
                    when (msg.action) {
                        NotificationAction.OPEN_LEADERBOARD -> {
                            selectedTab = SaloTab.MohamedLovers
                            openLeaderboardSheet = true
                        }
                        NotificationAction.OPEN_ACHIEVEMENTS -> selectedTab = SaloTab.Achievements
                        NotificationAction.OPEN_DHIKR_CHALLENGE -> {
                            showDhikrRewards = true
                            openChallengeLeaderboard = true
                        }
                        NotificationAction.OPEN_BAQIYAT_CHALLENGE -> {
                            showBaqiyatChallenge = true
                            openChallengeLeaderboard = true
                        }
                        NotificationAction.OPEN_ISTIGHFAR_CHALLENGE -> {
                            showIstighfarChallenge = true
                            openChallengeLeaderboard = true
                        }
                        NotificationAction.OPEN_QURAN_CHALLENGE -> {
                            showQuranChallenge = true
                            openChallengeLeaderboard = true
                        }
                        NotificationAction.OPEN_ZABAD_CHALLENGE -> {
                            showZabadChallenge = true
                            openChallengeLeaderboard = true
                        }
                        NotificationAction.OPEN_GHARS_CHALLENGE -> {
                            showGharsChallenge = true
                            openChallengeLeaderboard = true
                        }
                        NotificationAction.NONE -> Unit
                    }
                },
            )
        }

        // Friday reminder to read Surah Al-Kahf: shown at most once per day, and only on
        // Fridays. Unlike the round/heart logic this uses the device's *local* day (not
        // Cairo) so "Friday" matches the user's own calendar. Independent of the
        // suppressed app announcements above so the devotional nudge always reaches
        // users, but never over onboarding or the nickname prompt.
        val alKahfReminderStore = koinInject<AlKahfReminderStore>()
        val localToday = Clock.System.todayIn(TimeZone.currentSystemDefault())
        var showAlKahfReminder by remember {
            mutableStateOf(ALKAHF_REMINDER_ENABLED && alKahfReminderStore.shouldShow(localToday))
        }
        if (
            showAlKahfReminder &&
            !showOnboarding &&
            !shouldShowNicknamePrompt
        ) {
            AlKahfReminderDialog(
                onDismiss = {
                    alKahfReminderStore.markShown(localToday)
                    showAlKahfReminder = false
                },
            )
        }

        // Shown the first time the app is driven by an auto-clicker. Not suppressed during
        // onboarding — injected taps are being dropped right now, so the user needs to know
        // why nothing is registering.
        if (autoClickDetected) {
            AutoClickDetectedDialog(onDismiss = onAutoClickWarningDismissed)
        }

        // Update prompt, fed by two sources into one dialog. Tapping a "new version"
        // FCM notification shows it immediately for the pushed version — an explicit
        // tap outranks an earlier "Later" for that release. On a plain launch the
        // remote-config check (app_config/latestVersion) decides instead, honoring the
        // per-version dismissal. Either way the dialog only appears when the version is
        // strictly newer than this build, and it is independent of the suppressed app
        // announcements above so it always reaches users on old versions.
        val updateChecker = koinInject<UpdateChecker>()
        var pendingAppUpdate by remember { mutableStateOf<UpdatePrompt?>(null) }
        LaunchedEffect(newVersionAvailable) {
            if (!UPDATE_PROMPT_ENABLED) return@LaunchedEffect
            val current = getAppVersion()
            val decision = updateChecker.check(current)
            // A forced update (build below the minimum supported version) always wins,
            // even over an explicit "new version" notification tap.
            pendingAppUpdate = if (decision?.forced == true) {
                decision
            } else {
                newVersionAvailable
                    ?.takeIf { it.isNotBlank() && isNewerVersion(it, current) }
                    ?.let { UpdatePrompt(it, forced = false) }
                    ?: decision
            }
        }
        // A forced update blocks the whole app, including onboarding; the soft prompt is
        // still suppressed while onboarding is on screen.
        if (UPDATE_PROMPT_ENABLED) pendingAppUpdate?.let { prompt ->
            if (prompt.forced || !showOnboarding) {
                VersionUpdateDialog(
                    version = prompt.version,
                    forced = prompt.forced,
                    // "Update now" only opens the store; it is not a dismissal. For a soft
                    // prompt the user who doesn't finish updating is reminded next launch;
                    // for a forced one the dialog stays up so the app remains blocked.
                    onUpdate = {
                        if (!prompt.forced) pendingAppUpdate = null
                        openStorePage()
                    },
                    // "Later" (soft only) suppresses this exact version forever.
                    onDismiss = {
                        updateChecker.markDismissed(prompt.version)
                        pendingAppUpdate = null
                    },
                )
            }
        }
    }
}

private enum class SaloTab {
    MohamedLovers,
    Challenges,
    Achievements,
    Articles,
    Settings,
}

private data class SaloTabItem(
    val tab: SaloTab,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun SaloTabScaffold(
    selectedTab: SaloTab,
    onTabSelected: (SaloTab) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MohamedLoversPalette.DeepBlue,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            SaloBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        },
        content = content,
    )
}

@Composable
private fun SaloBottomNavigationBar(
    selectedTab: SaloTab,
    onTabSelected: (SaloTab) -> Unit,
) {
    val items = listOf(
        SaloTabItem(
            tab = SaloTab.MohamedLovers,
            label = stringResource(Res.string.bottom_nav_mohamed_lovers),
            icon = Icons.Default.Favorite,
        ),
        SaloTabItem(
            tab = SaloTab.Challenges,
            label = stringResource(Res.string.bottom_nav_challenges),
            icon = Icons.Default.Spa,
        ),
        SaloTabItem(
            tab = SaloTab.Achievements,
            label = stringResource(Res.string.bottom_nav_achievements),
            icon = Icons.Default.EmojiEvents,
        ),
        SaloTabItem(
            tab = SaloTab.Articles,
            label = stringResource(Res.string.bottom_nav_articles),
            icon = Icons.Default.AutoStories,
        ),
        SaloTabItem(
            tab = SaloTab.Settings,
            label = stringResource(Res.string.bottom_nav_settings),
            icon = Icons.Default.Settings,
        ),
    )

    NavigationBar(
        containerColor = Color(0xFF0A1528),
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selectedTab == item.tab,
                onClick = { onTabSelected(item.tab) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(text = item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MohamedLoversPalette.GoldHighlight,
                    selectedTextColor = MohamedLoversPalette.GoldHighlight,
                    indicatorColor = MohamedLoversPalette.GoldBase.copy(alpha = 0.16f),
                    unselectedIconColor = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                    unselectedTextColor = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                ),
            )
        }
    }
}
