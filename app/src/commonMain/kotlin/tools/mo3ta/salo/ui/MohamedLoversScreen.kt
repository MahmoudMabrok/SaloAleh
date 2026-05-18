package tools.mo3ta.salo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.grace_warning
import tools.mo3ta.salo.generated.resources.grace_warning_cta
import tools.mo3ta.salo.generated.resources.grace_warning_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_blocked_firebase_off
import tools.mo3ta.salo.generated.resources.mohamed_lovers_blocked_waiting_network
import tools.mo3ta.salo.generated.resources.mohamed_lovers_code_copied
import tools.mo3ta.salo.generated.resources.mohamed_lovers_connection_error
import tools.mo3ta.salo.generated.resources.mohamed_lovers_info_cd
import tools.mo3ta.salo.generated.resources.mohamed_lovers_prayer_text
import tools.mo3ta.salo.generated.resources.mohamed_lovers_reward_text
import tools.mo3ta.salo.presentation.MohamedLoversError
import tools.mo3ta.salo.presentation.MohamedLoversStatus
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import tools.mo3ta.salo.ui.components.DailyHadithDialog
import tools.mo3ta.salo.ui.components.MohamedLoversArchShrine
import tools.mo3ta.salo.ui.components.MohamedLoversCounter
import tools.mo3ta.salo.ui.components.MohamedLoversHadithBanner
import tools.mo3ta.salo.ui.components.MohamedLoversInfoSheet
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.components.MohamedLoversPrayerOverlay
import tools.mo3ta.salo.ui.components.MohamedLoversSkyBackground
import tools.mo3ta.salo.ui.AchievementCelebrationDialog
import tools.mo3ta.salo.ui.components.RoundRecapSheet

@Composable
fun MohamedLoversScreen(
    onOpenAchievements: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenHadithList: () -> Unit = {},
    onOpenTenDays: () -> Unit = {},
    viewModel: MohamedLoversViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyticsManager: AnalyticsManager = koinInject()
    val settingsStore: NotificationSettingsStore = koinInject()
    var showRankChip by remember { mutableStateOf(settingsStore.showRankChip) }

    val codeCopiedLabel = stringResource(Res.string.mohamed_lovers_code_copied)
    val connectionErrorLabel = stringResource(Res.string.mohamed_lovers_connection_error)
    val prayerText = stringResource(Res.string.mohamed_lovers_prayer_text)
    val rewardText = stringResource(Res.string.mohamed_lovers_reward_text)
    val waitingNetworkLabel = stringResource(Res.string.mohamed_lovers_blocked_waiting_network)
    val firebaseOffLabel = stringResource(Res.string.mohamed_lovers_blocked_firebase_off)
    val infoCd = stringResource(Res.string.mohamed_lovers_info_cd)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushPendingSession()
            if (event == Lifecycle.Event.ON_RESUME) {
                showRankChip = settingsStore.showRankChip
                viewModel.refreshSessionClicks()
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
    var showRankTooltip by remember { mutableStateOf(false) }
    var showBubbleTooltip by remember { mutableStateOf(false) }

    LaunchedEffect(isLit) {
        if (isLit) { delay(1600); isLit = false }
    }

    val currentUserEntry = state.topPlayers.firstOrNull { it.isCurrentUser } ?: state.selfEntry
    val rankChipVisible = showRankChip && (currentUserEntry?.rank ?: 0) > 0
    LaunchedEffect(rankChipVisible) {
        if (rankChipVisible && !settingsStore.rankChipTooltipShown) {
            delay(1200)
            showRankTooltip = true
            settingsStore.rankChipTooltipShown = true
            delay(4000)
            showRankTooltip = false
        }
    }

    LaunchedEffect(state.canCount) {
        if (state.canCount && !settingsStore.bubbleTooltipShown) {
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
                analyticsManager.logAction(
                    "mohamed_lovers_sky_tap",
                    emptyMap(),
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            Column (Modifier.align(Alignment.TopCenter).padding(top = 96.dp)){
                MohamedLoversHadithBanner()
                // Rank chip + first-time tooltip
                if (rankChipVisible) {
                    val rank = currentUserEntry!!.rank
                    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "🏅" }
                    Spacer(Modifier.height(6.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.13f),
                            modifier = Modifier.clickable {
                                analyticsManager.logAction("open_info_sheet", mapOf("source" to "rank_chip"))
                                infoSheetOpen = true
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = "$medal ", fontSize = 18.sp)
                                Text(
                                    text = "ترتيبك ",
                                    color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                )
                                Text(
                                    text = "$rank",
                                    color = MohamedLoversPalette.GoldHighlight,
                                    fontSize = 18.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                                if (state.roundPlayerCount > 0) {
                                    Text(
                                        text = " / ${state.roundPlayerCount}",
                                        color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.45f),
                                        fontSize = 14.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    )
                                }
                            }
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
                                    text = "يمكنك التحكم في هذا من الإعدادات",
                                    color = MohamedLoversPalette.GoldGlow,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                text = "صلّ على النبي ﷺ وأنت تتصفح",
                                color = MohamedLoversPalette.GoldGlow,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            MohamedLoversArchShrine(
                isLit = isLit,
                onArchCenterPositioned = { archCenter = it },
                modifier = Modifier.align(Alignment.Center),
            )
            MohamedLoversCounter(
                total = state.syncedTotal + state.sessionClicks,
                pending = state.sessionClicks,
                isFridayBonus = state.isFridayBonus,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 40.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(84.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    }
            )

            Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 36.dp)) {
                IconButton(onClick = {
                    analyticsManager.logAction("open_info_sheet", mapOf("source" to "icon"))
                    infoSheetOpen = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = infoCd,
                        tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 36.dp)) {
                Row {
                    IconButton(onClick = onOpenAchievements) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "الإنجازات",
                            tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                        )
                    }
                    IconButton(onClick = onOpenHadithList) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = "الأحاديث",
                            tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                        )
                    }
                    IconButton(onClick = onOpenTenDays) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "عشر ذي الحجة",
                            tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                        )
                    }
                    IconButton(onClick = {
                        analyticsManager.logAction("open_settings")
                        onOpenSettings()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MohamedLoversPalette.GoldHighlight,
                )
            }
        }
        MohamedLoversInfoSheet(
            isOpen = infoSheetOpen,
            state = state,
            onDismiss = { infoSheetOpen = false },
            onCopyWinnerCode = { code ->
                copyToClipboard(code)
                showPlatformToast(codeCopiedLabel)
            },
        )
        if (state.showRoundRecap) {
            RoundRecapSheet(
                rank = state.recapRank,
                totalPlayers = state.recapTotalPlayers,
                isPersonalBest = state.recapIsPersonalBest,
                tapsDelta = state.recapTapsDelta,
                onDismiss = { viewModel.dismissRoundRecap() },
            )
        }
        if (state.showHadithDialog) {
            DailyHadithDialog(
                onDismiss = { viewModel.dismissHadithDialog() },
            )
        }
        if (state.showGraceWarning) {
            GraceWarningDialog(onDismiss = { viewModel.dismissGraceWarning() })
        }
        state.newlyEarnedRankAchievement?.let { achievement ->
            AchievementCelebrationDialog(
                achievement = achievement,
                onDismiss = { viewModel.dismissNewlyEarnedAchievement() },
            )
        }
        BubbleFeaturePromo(roundKey = state.roundKey)
        if (state.showDailyLeaderboardPromo) {
            DailyLeaderboardPromoDialog(onDismiss = { viewModel.dismissDailyLeaderboardPromo() })
        }
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
