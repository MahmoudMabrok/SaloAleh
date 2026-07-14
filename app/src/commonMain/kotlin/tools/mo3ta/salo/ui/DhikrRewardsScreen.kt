package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.dhikr_add
import tools.mo3ta.salo.generated.resources.dhikr_back_cd
import tools.mo3ta.salo.generated.resources.dhikr_daily_goal
import tools.mo3ta.salo.generated.resources.dhikr_freed_today
import tools.mo3ta.salo.generated.resources.dhikr_phrase
import tools.mo3ta.salo.generated.resources.dhikr_progress_count
import tools.mo3ta.salo.generated.resources.dhikr_rank_number
import tools.mo3ta.salo.generated.resources.dhikr_rank_subtitle
import tools.mo3ta.salo.generated.resources.dhikr_rank_unranked
import tools.mo3ta.salo.generated.resources.dhikr_tap_hint
import tools.mo3ta.salo.generated.resources.dhikr_times
import tools.mo3ta.salo.generated.resources.dhikr_today
import tools.mo3ta.salo.presentation.DhikrChallengeViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.dhikr.DhikrColors
import tools.mo3ta.salo.ui.dhikr.DhikrEmancipationZone
import tools.mo3ta.salo.ui.dhikr.DhikrLeaderboardSheet
import tools.mo3ta.salo.ui.dhikr.DhikrLinearProgress
import tools.mo3ta.salo.ui.dhikr.DhikrManualEntryButton
import tools.mo3ta.salo.ui.dhikr.DhikrMilestoneCelebration
import tools.mo3ta.salo.ui.dhikr.DhikrPanel
import tools.mo3ta.salo.ui.dhikr.DhikrProgressRing
import tools.mo3ta.salo.ui.dhikr.DhikrSpacing
import tools.mo3ta.salo.ui.dhikr.DhikrSpoilsReceipt
import tools.mo3ta.salo.ui.dhikr.ManualDhikrSheet
import tools.mo3ta.salo.ui.ghars.ibmPlexArabicFamily


// Concept 1 (عتق الرقاب) — the emancipation-engine hero for the 100-dhikr challenge, which
// rebuilds the counter out of the hadith's gains and ends on the غنائم اليوم spoils receipt.
// Flip to false to fall back to the original progress-ring hero.
private const val DHIKR_GAINS_UI_ENABLED = true

private val DhikrHeroBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF06331F),
        Color(0xFF0E5A39),
        Color(0xFF123D44),
        MohamedLoversPalette.DeepBlue,
    ),
)

@Composable
fun DhikrRewardsScreen(
    onBack: () -> Unit,
    viewModel: DhikrChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.DHIKR_SCREEN_VIEW)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenEntered()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenLeft()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenLeft()
        }
    }

    LaunchedEffect(state.celebrationMilestone) {
        val milestone = state.celebrationMilestone
        if (milestone > 0 && milestone % 100 == 0) {
            startProtectionNotificationService()
        }
    }

    val onHeroTap: () -> Unit = {
        viewModel.onDhikrTap()
        analyticsManager.logAction(
            AppAnalytics.DHIKR_TAP,
            mapOf(AppAnalytics.PARAM_COUNT to (state.todayCount + 1).toString()),
        )
    }
    val onHeroManualEntry: () -> Unit = {
        analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_DHIKR)
        viewModel.showManualDhikrSheet()
    }
    // The spoils sheet can open on demand (the "what you gain" button) as well as on completion.
    var showGainsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DhikrHeroBackground),
    ) {
        if (DHIKR_GAINS_UI_ENABLED) {
            DhikrEmancipationZone(
                count = state.todayCount,
                rank = state.rank,
                participantCount = state.participantCount,
                canCount = !state.isLoading,
                onTap = onHeroTap,
                onBack = onBack,
                onRankClick = { viewModel.onLeaderboardOpened() },
                onManualEntryClick = onHeroManualEntry,
                onViewGains = { showGainsSheet = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DhikrImmersiveZone(
                    count = state.todayCount,
                    target = state.dailyGoal,
                    rank = state.rank,
                    participantCount = state.participantCount,
                    canCount = !state.isLoading,
                    onTap = onHeroTap,
                    onBack = onBack,
                    onRankClick = { viewModel.onLeaderboardOpened() },
                    onManualEntryClick = onHeroManualEntry,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = DhikrColors.Cream,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = DhikrSpacing.ScreenHorizontal)
                            .padding(top = DhikrSpacing.PanelGap, bottom = DhikrSpacing.PanelGap),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DhikrStatsRow(
                            freedCount = state.todayCount / 10,
                            todayCount = state.todayCount,
                            target = state.dailyGoal,
                        )
                    }
                }
            }
        }

        val isHundredMilestone = state.celebrationMilestone > 0 && state.celebrationMilestone % 100 == 0
        if (DHIKR_GAINS_UI_ENABLED) {
            DhikrSpoilsReceipt(
                visible = showGainsSheet || (state.showCelebration && isHundredMilestone),
                onDismiss = {
                    showGainsSheet = false
                    if (state.showCelebration) viewModel.onCelebrationDismissed()
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            DhikrMilestoneCelebration(
                milestone = state.celebrationMilestone,
                visible = state.showCelebration,
                onDismiss = { viewModel.onCelebrationDismissed() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (state.showLeaderboard) {
        DhikrLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = { viewModel.onLeaderboardClosed() },
        )
    }

    ManualDhikrSheet(
        isOpen = state.showManualDhikrSheet,
        showPresetChips = false,
        remaining = state.manualRemainingToday,
        onDismiss = { viewModel.dismissManualDhikrSheet() },
        onSubmit = { count -> viewModel.submitManualDhikr(count) },
        onSubtract = { count -> viewModel.subtractManualDhikr(count) },
    )
}

@Composable
private fun DhikrImmersiveZone(
    count: Int,
    target: Int,
    rank: Int,
    participantCount: Int,
    canCount: Boolean,
    onTap: () -> Unit,
    onBack: () -> Unit,
    onRankClick: () -> Unit,
    onManualEntryClick: () -> Unit,
) {
    val fraction = (count.toFloat() / target.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DhikrHeroBackground)
            .clickable(
                enabled = canCount,
                onClickLabel = stringResource(Res.string.dhikr_add),
                role = Role.Button,
                onClick = onTap,
            ),
    ) {
        LeafCluster(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(86.dp),
            mirror = false,
        )
        LeafCluster(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(86.dp),
            mirror = true,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = DhikrSpacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.dhikr_back_cd),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
                Surface(
                    onClick = onRankClick,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    val chipText = if (rank > 0 && participantCount > 0) {
                        "${stringResource(Res.string.dhikr_rank_number, rank)} · ${stringResource(Res.string.dhikr_rank_subtitle, participantCount)}"
                    } else {
                        stringResource(Res.string.dhikr_rank_unranked)
                    }
                    Text(
                        text = chipText,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            DhikrManualEntryButton(
                onClick = onManualEntryClick,
                onDarkBackground = true,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.dhikr_phrase),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 2.dp, bottom = 12.dp),
            )

            DhikrProgressRing(
                fraction = fraction,
                modifier = Modifier.size(220.dp),
                trackColor = Color.White.copy(alpha = 0.15f),
                fillColor = DhikrColors.LightGreen,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.dhikr_today),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = count.toString(),
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 60.sp,
                    )
                    Text(
                        text = stringResource(Res.string.dhikr_times),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.dhikr_tap_hint),
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DhikrStatsRow(
    freedCount: Int,
    todayCount: Int,
    target: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DhikrSpacing.PanelGap),
    ) {
        DhikrPanel(modifier = Modifier.weight(1f), topOverlap = true) {
            Text(
                text = freedCount.toString(),
                color = DhikrColors.Green,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                fontFamily = ibmPlexArabicFamily(),
                lineHeight = 42.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.dhikr_freed_today),
                color = DhikrColors.Muted,
                fontSize = 12.sp,
                fontFamily = ibmPlexArabicFamily(),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
        DhikrPanel(modifier = Modifier.weight(1f), topOverlap = true) {
            Text(
                text = stringResource(Res.string.dhikr_progress_count, todayCount, target),
                color = DhikrColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ibmPlexArabicFamily(),
            )
            Spacer(Modifier.height(8.dp))
            DhikrLinearProgress(
                fraction = todayCount.toFloat() / target.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.dhikr_daily_goal, target),
                color = DhikrColors.Muted,
                fontSize = 12.sp,
                fontFamily = ibmPlexArabicFamily(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LeafCluster(
    modifier: Modifier = Modifier,
    mirror: Boolean,
) {
    Canvas(modifier = modifier) {
        val stemX = if (mirror) size.width * 0.75f else size.width * 0.25f
        val endX = if (mirror) size.width * 0.38f else size.width * 0.62f
        drawLine(
            color = DhikrColors.LeafStem.copy(alpha = 0.35f),
            start = Offset(stemX, size.height * 0.92f),
            end = Offset(endX, size.height * 0.22f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        repeat(5) { i ->
            val y = size.height * (0.78f - i * 0.12f)
            val x = stemX + (endX - stemX) * (i + 1) / 6f
            val direction = if ((i + if (mirror) 1 else 0) % 2 == 0) -1f else 1f
            drawOval(
                color = DhikrColors.Leaf.copy(alpha = 0.22f),
                topLeft = Offset(x + direction * 4.dp.toPx(), y),
                size = Size(28.dp.toPx(), 12.dp.toPx()),
            )
        }
    }
}
