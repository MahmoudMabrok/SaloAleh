package tools.mo3ta.salo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.data.billing.PremiumFeature
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.salawat.SalawatVariantStore
import tools.mo3ta.salo.data.salawat.SalawatVariants
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.grace_warning
import tools.mo3ta.salo.generated.resources.grace_warning_cta
import tools.mo3ta.salo.generated.resources.grace_warning_title
import tools.mo3ta.salo.generated.resources.heart_index_label
import tools.mo3ta.salo.generated.resources.heart_index_tooltip
import tools.mo3ta.salo.generated.resources.heart_index_dialog_body
import tools.mo3ta.salo.generated.resources.heart_index_dialog_dismiss
import tools.mo3ta.salo.generated.resources.heart_index_dialog_title
import tools.mo3ta.salo.generated.resources.heart_refill_nudge
import tools.mo3ta.salo.generated.resources.mohamed_lovers_blocked_firebase_off
import tools.mo3ta.salo.generated.resources.mohamed_lovers_blocked_waiting_network
import tools.mo3ta.salo.generated.resources.mohamed_lovers_code_copied
import tools.mo3ta.salo.generated.resources.mohamed_lovers_connection_error
import tools.mo3ta.salo.generated.resources.mohamed_lovers_reward_text
import tools.mo3ta.salo.generated.resources.main_screen_rank_chip_tooltip
import tools.mo3ta.salo.generated.resources.main_screen_bubble_tooltip
import tools.mo3ta.salo.generated.resources.main_screen_manual_salawat_button
import tools.mo3ta.salo.generated.resources.new_round_title
import tools.mo3ta.salo.generated.resources.new_round_subtitle
import tools.mo3ta.salo.generated.resources.new_round_cta
import tools.mo3ta.salo.generated.resources.premium_promo_others_achievements_note
import tools.mo3ta.salo.generated.resources.mohamed_lovers_countdown_day
import tools.mo3ta.salo.generated.resources.mohamed_lovers_countdown_hour
import tools.mo3ta.salo.generated.resources.mohamed_lovers_countdown_minute
import tools.mo3ta.salo.generated.resources.mohamed_lovers_countdown_second
import tools.mo3ta.salo.generated.resources.idle_banner_prefix
import tools.mo3ta.salo.generated.resources.idle_minutes_one
import tools.mo3ta.salo.generated.resources.idle_minutes_two
import tools.mo3ta.salo.generated.resources.idle_minutes_plural
import tools.mo3ta.salo.generated.resources.idle_hours_one
import tools.mo3ta.salo.generated.resources.idle_hours_two
import tools.mo3ta.salo.generated.resources.idle_hours_plural
import tools.mo3ta.salo.generated.resources.idle_days_one
import tools.mo3ta.salo.generated.resources.idle_days_two
import tools.mo3ta.salo.generated.resources.idle_days_plural
import tools.mo3ta.salo.presentation.MohamedLoversError
import tools.mo3ta.salo.presentation.MohamedLoversStatus
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import tools.mo3ta.salo.ui.components.DailyHadithDialog
import tools.mo3ta.salo.ui.components.MohamedLoversArchShrine
import tools.mo3ta.salo.ui.components.ManualSalawatSheet
import tools.mo3ta.salo.ui.components.MohamedLoversFonts
import tools.mo3ta.salo.ui.components.MohamedLoversCounter
import tools.mo3ta.salo.ui.components.MohamedLoversHadithBanner
import tools.mo3ta.salo.ui.components.MohamedLoversInfoSheet
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.components.MohamedLoversPrayerOverlay
import tools.mo3ta.salo.ui.components.MohamedLoversSkyBackground
import tools.mo3ta.salo.ui.RoundEndResultsScreen
import tools.mo3ta.salo.ui.components.RoundEndBanner
import tools.mo3ta.salo.ui.components.OvertakeOverlay
import tools.mo3ta.salo.ui.components.MilestoneCelebration
import tools.mo3ta.salo.domain.DailyBadge
import tools.mo3ta.salo.ui.components.DailyBadgeInfoDialog
import tools.mo3ta.salo.ui.components.DailyBadgeTiersSheet
import tools.mo3ta.salo.ui.components.DailyRankStrip
import tools.mo3ta.salo.ui.components.RankMovementBanner
import tools.mo3ta.salo.ui.AchievementCelebrationDialog
import tools.mo3ta.salo.ui.components.RoundRecapSheet
import tools.mo3ta.salo.ui.components.UserAchievementsSheet
import tools.mo3ta.salo.ui.settings.PremiumPromoDialog

@Composable
fun MohamedLoversScreen(
    onOpenPaywall: () -> Unit = {},
    openInfoSheet: Boolean = false,
    onInfoSheetOpened: () -> Unit = {},
    announcementsEnabled: Boolean = true,
    viewModel: MohamedLoversViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyticsManager: AnalyticsManager = koinInject()
    val settingsStore: NotificationSettingsStore = koinInject()
    val salawatVariantStore: SalawatVariantStore = koinInject()
    val premiumStore: PremiumStore = koinInject()
    var showRankChip by remember { mutableStateOf(settingsStore.showRankChip) }

    val codeCopiedLabel = stringResource(Res.string.mohamed_lovers_code_copied)
    val connectionErrorLabel = stringResource(Res.string.mohamed_lovers_connection_error)
    val prayerText = stringResource(SalawatVariants.textResIds[salawatVariantStore.variantIndex])
    val rewardText = stringResource(Res.string.mohamed_lovers_reward_text)
    val waitingNetworkLabel = stringResource(Res.string.mohamed_lovers_blocked_waiting_network)
    val firebaseOffLabel = stringResource(Res.string.mohamed_lovers_blocked_firebase_off)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushPendingSession()
            if (event == Lifecycle.Event.ON_RESUME) {
                showRankChip = settingsStore.showRankChip
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPendingSession()
        }
    }

    LaunchedEffect(state.error) {
        val message = when (val err = state.error) {
            MohamedLoversError.Connection -> connectionErrorLabel
            is MohamedLoversError.Raw -> err.message
            null -> null
        }
        if (!message.isNullOrBlank()) {
            showPlatformToast(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit){
        analyticsManager.logView("Mohamed_lovers")
    }

    var archCenter by remember { mutableStateOf<Offset?>(null) }
    var isLit by remember { mutableStateOf(false) }
    var infoSheetOpen by remember { mutableStateOf(false) }
    LaunchedEffect(openInfoSheet) {
        if (openInfoSheet) {
            infoSheetOpen = true
            onInfoSheetOpened()
        }
    }
    var badgeTiersSheetOpen by remember { mutableStateOf(false) }
    var badgeDialogKey by remember { mutableStateOf<String?>(null) }
    var showRankTooltip by remember { mutableStateOf(false) }
    var showBubbleTooltip by remember { mutableStateOf(false) }
    var selectedUserAchievements by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showOthersAchievementsPromo by remember { mutableStateOf(false) }
    var showHeartTooltip by remember { mutableStateOf(announcementsEnabled) }
    var showHeartInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLit) {
        if (isLit) { delay(1600); isLit = false }
    }

    LaunchedEffect(announcementsEnabled) {
        showHeartTooltip = announcementsEnabled
        if (announcementsEnabled) {
            delay(7000)
            showHeartTooltip = false
        }
    }

    val currentUserEntry = state.topPlayers.firstOrNull { it.isCurrentUser } ?: state.selfEntry
    val rankChipVisible = showRankChip && (currentUserEntry?.rank ?: 0) > 0
    LaunchedEffect(rankChipVisible, announcementsEnabled) {
        if ( rankChipVisible && !settingsStore.rankChipTooltipShown) {
            delay(1200)
            showRankTooltip = true
            settingsStore.rankChipTooltipShown = true
            delay(4000)
            showRankTooltip = false
        }
    }

    LaunchedEffect(state.canCount, announcementsEnabled) {
        if (announcementsEnabled && state.canCount && !settingsStore.bubbleTooltipShown) {
            delay(2500)
            showBubbleTooltip = true
            settingsStore.bubbleTooltipShown = true
            delay(5000)
            showBubbleTooltip = false
        }
    }

    val blockedMessage = when (state.status) {
        MohamedLoversStatus.WaitingNetwork -> waitingNetworkLabel
        MohamedLoversStatus.FirebaseOff -> firebaseOffLabel
        MohamedLoversStatus.Open -> ""
    }
    val tapsEnabled = state.status == MohamedLoversStatus.Open && state.canCount && !state.isLoading

    Box(modifier = Modifier.fillMaxSize()) {
        MohamedLoversSkyBackground()
        MohamedLoversPrayerOverlay(
            archCenter = archCenter,
            enabled = tapsEnabled,
            prayerText = prayerText,
            rewardText = rewardText,
            blockedMessage = blockedMessage,
            onBlessing = { isLit = true },
            onTap = {
                if (tapsEnabled) viewModel.onCountClick()
                analyticsManager.logAction(AppAnalytics.MOHAMED_LOVERS_SKY_TAP)
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MohamedLoversHadithBanner()
                if (state.showRoundEndBanner && !state.showRoundEndResults) {
                    Spacer(Modifier.height(8.dp))
                    RoundEndBanner(onClick = { viewModel.onRoundEndBannerClick() })
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        AnimatedVisibility(
                            visible = showHeartTooltip,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        ) {
                            HeartIndexTooltip(
                                modifier = Modifier.widthIn(max = 190.dp),
                            )
                        }
                        if (showHeartTooltip) Spacer(Modifier.height(6.dp)) else Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            HeartIndexIndicator(
                                score = state.heartScore,
                                onClick = {
                                    showHeartTooltip = false
                                    showHeartInfoDialog = true
                                },
                            )

                            if (rankChipVisible) {
                                Column(horizontalAlignment = Alignment.End) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                        DailyRankStrip(
                                            rank = currentUserEntry!!.rank,
                                            totalPlayers = state.roundPlayerCount,
                                            todayTaps = state.dailyGoalProgress,
                                            currentBadgeKey = state.currentDailyBadge,
                                            onStripClick = {
                                                analyticsManager.logAction(AppAnalytics.OPEN_INFO_SHEET, mapOf(AppAnalytics.PARAM_SOURCE to "rank_strip"))
                                                infoSheetOpen = true
                                            },
                                            onBadgeClick = { badgeTiersSheetOpen = true },
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = showRankTooltip,
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.15f),
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .clickable { showRankTooltip = false },
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.main_screen_rank_chip_tooltip),
                                                color = MohamedLoversPalette.GoldGlow,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        FloatingBubbleButton(roundKey = state.roundKey)
                        AnimatedVisibility(
                            visible = showBubbleTooltip,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { showBubbleTooltip = false },
                            ) {
                                Text(
                                    text = stringResource(Res.string.main_screen_bubble_tooltip),
                                    color = MohamedLoversPalette.GoldGlow,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }

            MohamedLoversArchShrine(
                isLit = isLit,
                onArchCenterPositioned = { archCenter = it },
                modifier = Modifier.align(Alignment.Center),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MohamedLoversCounter(
                    total = state.syncedTotal + state.sessionClicks,
                    pending = state.sessionClicks,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Surface(
                    onClick = {
                        analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_SALAWAT)
                        viewModel.showManualSalawatSheet()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MohamedLoversPalette.GoldBase.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = stringResource(Res.string.main_screen_manual_salawat_button),
                        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = MohamedLoversFonts.body,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                val elapsedMinutes = state.lastSalawatElapsedMinutes
                if (elapsedMinutes != null && elapsedMinutes >= 1) {
                    Spacer(Modifier.height(8.dp))
                    IdleBanner(
                        elapsedMinutes = elapsedMinutes,
                        onClick = {
                            if (tapsEnabled) viewModel.onCountClick()
                        },
                    )
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MohamedLoversPalette.GoldHighlight,
                )
            }
        }
        ManualSalawatSheet(
            isOpen = state.showManualSalawatSheet,
            onDismiss = { viewModel.dismissManualSalawatSheet() },
            onSubmit = { count -> viewModel.submitManualSalawat(count) },
        )
        MohamedLoversInfoSheet(
            isOpen = infoSheetOpen,
            state = state,
            onDismiss = { infoSheetOpen = false },
            onCopyWinnerCode = { code ->
                copyToClipboard(code)
                showPlatformToast(codeCopiedLabel)
            },
            onToggleLeaderboardType = { daily -> viewModel.setLeaderboardMode(daily) },
            isPremium = premiumStore.hasFeature(PremiumFeature.FRIDAY_SCORES),
            hasLiveAccess = premiumStore.hasFeature(PremiumFeature.LIVE_LEADERBOARD),
            onFetchLiveLeaderboard = { viewModel.fetchLiveLeaderboard() },
            onOpenPaywall = {
                infoSheetOpen = false
                onOpenPaywall()
            },
            onBadgeClick = { key -> badgeDialogKey = key },
            onUserClick = { uid, tag ->
                analyticsManager.logAction(AppAnalytics.LEADERBOARD_USER_CLICK)
                val isSelf = uid.isNotBlank() && uid == currentUserEntry?.uid
                if (isSelf || premiumStore.hasFeature(PremiumFeature.OTHERS_ACHIEVEMENTS)) {
                    selectedUserAchievements = uid to tag
                } else {
                    showOthersAchievementsPromo = true
                }
            },
        )
        badgeDialogKey?.let { key ->
            DailyBadge.fromKey(key)?.let { badge ->
                DailyBadgeInfoDialog(
                    badge = badge,
                    onDismiss = { badgeDialogKey = null },
                )
            }
        }
        if (badgeTiersSheetOpen) {
            DailyBadgeTiersSheet(
                todayTaps = state.dailyGoalProgress,
                currentBadgeKey = state.currentDailyBadge,
                onDismiss = { badgeTiersSheetOpen = false },
            )
        }
        selectedUserAchievements?.let { (uid, tag) ->
            UserAchievementsSheet(
                uid = uid,
                displayTag = tag,
                onDismiss = { selectedUserAchievements = null },
            )
        }
        if (showOthersAchievementsPromo) {
            PremiumPromoDialog(
                onOpen = {
                    showOthersAchievementsPromo = false
                    onOpenPaywall()
                },
                onDismiss = { showOthersAchievementsPromo = false },
                featureNote = stringResource(Res.string.premium_promo_others_achievements_note),
            )
        }
        if (state.showRoundEndResults) {
            RoundEndResultsScreen(
                winnersTop3 = state.winnersTop3,
                recapRank = state.recapRank,
                recapTotalPlayers = state.recapTotalPlayers,
                isPersonalBest = state.recapIsPersonalBest,
                tapsDelta = state.recapTapsDelta,
                achievement = state.roundEndAchievement,
                onDismiss = { viewModel.dismissRoundEndResults() },
            )
        }
        if (showHeartInfoDialog) {
            HeartIndexInfoDialog(onDismiss = { showHeartInfoDialog = false })
        }
        if (state.showHadithDialog) {
            DailyHadithDialog(
                onDismiss = { viewModel.dismissHadithDialog() },
            )
        }
        if (state.showGraceWarning) {
            GraceWarningDialog(onDismiss = { viewModel.dismissGraceWarning() })
        }
//        if (announcementsEnabled) {
//            BubbleFeaturePromo(roundKey = state.roundKey)
//        }
//        if (announcementsEnabled && state.showDailyLeaderboardPromo) {
//            DailyLeaderboardPromoDialog(onDismiss = { viewModel.dismissDailyLeaderboardPromo() })
//        }
//        if (announcementsEnabled && state.showNewRoundCountdown) {
//            NewRoundCountdownOverlay(
//                roundEndInstant = state.roundEndInstant,
//                onDismiss = { viewModel.dismissNewRoundCountdown() },
//            )
//        }

        // Overtake alert
        OvertakeOverlay(
                overtakeRank = state.overtakeRank,
                onDismiss = viewModel::dismissOvertake,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            )

        // Daily milestone celebration
        MilestoneCelebration(
                threshold = state.milestoneThreshold,
                badgeKey = state.milestoneBadgeKey,
                onDismiss = viewModel::dismissMilestone,
            )

        // Rank movement summary
            RankMovementBanner(
                delta = state.rankMovementDelta,
                oldRank = state.rankMovementOldRank,
                newRank = state.rankMovementNewRank,
                onDismiss = viewModel::dismissRankMovement,
                modifier = Modifier.align(Alignment.Center),
            )
    }
}

@Composable
private fun GraceWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MohamedLoversPalette.DeepBlue,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(Res.string.grace_warning_title),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.grace_warning),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.grace_warning_cta),
                    color = MohamedLoversPalette.GoldHighlight,
                )
            }
        },
    )
}

@Composable
private fun NewRoundCountdownOverlay(
    roundEndInstant: kotlinx.datetime.Instant?,
    onDismiss: () -> Unit,
) {
    var remainingSeconds by remember { mutableStateOf(0L) }

    if (roundEndInstant != null) {
        LaunchedEffect(roundEndInstant) {
            while (true) {
                val diff = (roundEndInstant - kotlinx.datetime.Clock.System.now()).inWholeSeconds
                remainingSeconds = if (diff > 0) diff else 0
                delay(1000)
            }
        }
    }

    val days = (remainingSeconds / 86400).toInt()
    val hours = ((remainingSeconds % 86400) / 3600).toInt()
    val minutes = ((remainingSeconds % 3600) / 60).toInt()
    val seconds = (remainingSeconds % 60).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MohamedLoversPalette.DeepBlue,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(Res.string.new_round_title),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.new_round_subtitle),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CountdownCell(days, stringResource(Res.string.mohamed_lovers_countdown_day))
                        CountdownCell(hours, stringResource(Res.string.mohamed_lovers_countdown_hour))
                        CountdownCell(minutes, stringResource(Res.string.mohamed_lovers_countdown_minute))
                        CountdownCell(seconds, stringResource(Res.string.mohamed_lovers_countdown_second))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.new_round_cta),
                    color = MohamedLoversPalette.GoldHighlight,
                    fontSize = 16.sp,
                )
            }
        },
    )
}

@Composable
private fun CountdownCell(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MohamedLoversPalette.GoldBase.copy(alpha = 0.12f),
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun HeartIndexIndicator(score: Int, onClick: () -> Unit) {
    val fillFraction = (score.coerceAtLeast(0).toFloat() / HEART_VISUAL_FULL_SCORE).coerceIn(0f, 1f)
    val activeTint = Color(0xFFE85D75)
    val emptyTint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.35f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MohamedLoversPalette.SkyTop.copy(alpha = 0.58f),
        border = androidx.compose.foundation.BorderStroke(1.dp, activeTint.copy(alpha = 0.32f)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            HeartFillIcon(
                fillFraction = fillFraction,
                emptyTint = emptyTint,
                activeTint = activeTint,
            )
            Text(
                text = score.toString(),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.88f),
                fontSize = 16.sp,
                fontFamily = MohamedLoversFonts.body,
            )
        }
    }
}

@Composable
private fun HeartFillIcon(
    fillFraction: Float,
    emptyTint: Color,
    activeTint: Color,
) {
    val label = stringResource(Res.string.heart_index_label)
    Box(modifier = Modifier.size(26.dp)) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = label,
            tint = emptyTint,
            modifier = Modifier.matchParentSize(),
        )
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = activeTint,
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    clipRect(right = size.width * fillFraction) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}

@Composable
private fun HeartIndexTooltip(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MohamedLoversPalette.SkyTop.copy(alpha = 0.74f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE85D75).copy(alpha = 0.30f)),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.heart_index_tooltip),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontFamily = MohamedLoversFonts.body,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HeartIndexInfoDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MohamedLoversPalette.DeepBlue,
        titleContentColor = MohamedLoversPalette.GoldHighlight,
        textContentColor = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
        title = {
            Text(
                text = stringResource(Res.string.heart_index_dialog_title),
                fontFamily = MohamedLoversFonts.body,
                fontSize = 18.sp,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.heart_index_dialog_body),
                fontFamily = MohamedLoversFonts.body,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.heart_index_dialog_dismiss),
                    color = MohamedLoversPalette.GoldHighlight,
                    fontFamily = MohamedLoversFonts.body,
                )
            }
        },
    )
}

@Composable
private fun HeartRefillBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE85D75).copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE85D75).copy(alpha = 0.35f)),
    ) {
        Text(
            text = stringResource(Res.string.heart_refill_nudge),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontFamily = MohamedLoversFonts.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private const val HEART_VISUAL_FULL_SCORE = 1000f

@Composable
private fun formatIdleDuration(totalMinutes: Long): String {
    val prefix = stringResource(Res.string.idle_banner_prefix)
    val unit = when {
        totalMinutes < 60 -> {
            val m = totalMinutes.toInt()
            when (m) {
                1 -> stringResource(Res.string.idle_minutes_one)
                2 -> stringResource(Res.string.idle_minutes_two)
                else -> stringResource(Res.string.idle_minutes_plural, m)
            }
        }
        totalMinutes < 1440 -> {
            val h = (totalMinutes / 60).toInt()
            when (h) {
                1 -> stringResource(Res.string.idle_hours_one)
                2 -> stringResource(Res.string.idle_hours_two)
                else -> stringResource(Res.string.idle_hours_plural, h)
            }
        }
        else -> {
            val d = (totalMinutes / 1440).toInt()
            when (d) {
                1 -> stringResource(Res.string.idle_days_one)
                2 -> stringResource(Res.string.idle_days_two)
                else -> stringResource(Res.string.idle_days_plural, d)
            }
        }
    }
    return "$prefix $unit"
}

@Composable
private fun IdleBanner(elapsedMinutes: Long, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MohamedLoversPalette.GoldBase.copy(alpha = 0.25f)),
    ) {
        Text(
            text = formatIdleDuration(elapsedMinutes),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontFamily = MohamedLoversFonts.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
