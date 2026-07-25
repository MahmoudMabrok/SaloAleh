package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import tools.mo3ta.salo.generated.resources.istighfar_add
import tools.mo3ta.salo.generated.resources.istighfar_back_cd
import tools.mo3ta.salo.generated.resources.istighfar_cycles_label
import tools.mo3ta.salo.generated.resources.istighfar_daily_goal
import tools.mo3ta.salo.generated.resources.istighfar_manual_entry_button
import tools.mo3ta.salo.generated.resources.istighfar_phrase
import tools.mo3ta.salo.generated.resources.istighfar_progress_count
import tools.mo3ta.salo.generated.resources.istighfar_rank_number
import tools.mo3ta.salo.generated.resources.istighfar_rank_subtitle
import tools.mo3ta.salo.generated.resources.istighfar_rank_unranked
import tools.mo3ta.salo.generated.resources.istighfar_tap_hint
import tools.mo3ta.salo.generated.resources.istighfar_times
import tools.mo3ta.salo.generated.resources.istighfar_today
import tools.mo3ta.salo.generated.resources.istighfar_view_rewards
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.presentation.IstighfarChallengeViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.istighfar.IstighfarColors
import tools.mo3ta.salo.ui.istighfar.IstighfarLeaderboardSheet
import tools.mo3ta.salo.ui.istighfar.IstighfarMilestoneCelebration
import tools.mo3ta.salo.ui.istighfar.IstighfarProgressRing
import tools.mo3ta.salo.ui.istighfar.IstighfarRewardsSheet
import tools.mo3ta.salo.ui.istighfar.IstighfarSpacing
import tools.mo3ta.salo.ui.istighfar.ManualIstighfarSheet


private val IstighfarHeroBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF33200F),
        Color(0xFF5A3A1B),
        Color(0xFF3D2E44),
        MohamedLoversPalette.DeepBlue,
    ),
)

@Composable
fun IstighfarRewardsScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    manualEntryEnabled: Boolean = true,
    viewModel: IstighfarChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.ISTIGHFAR_SCREEN_VIEW)
    }

    LaunchedEffect(openLeaderboard) {
        if (openLeaderboard) {
            viewModel.onLeaderboardOpened()
            onLeaderboardAutoOpened()
        }
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

    // The virtues live behind a button now — the sheet opens on demand instead of filling the screen.
    var showRewardsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IstighfarHeroBackground),
    ) {
        IstighfarImmersiveZone(
            count = state.todayCount,
            target = state.dailyGoal,
            rank = state.rank,
            participantCount = state.participantCount,
            canCount = !state.isLoading,
            onTap = {
                viewModel.onIstighfarTap()
                analyticsManager.logAction(
                    AppAnalytics.ISTIGHFAR_TAP,
                    mapOf(AppAnalytics.PARAM_COUNT to (state.todayCount + 1).toString()),
                )
            },
            onBack = onBack,
            onRankClick = { viewModel.onLeaderboardOpened() },
            onManualEntryClick = {
                analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_ISTIGHFAR)
                viewModel.showManualIstighfarSheet()
            },
            manualEntryVisible = manualEntryEnabled,
            onViewRewards = { showRewardsSheet = true },
            modifier = Modifier.fillMaxSize(),
        )

        IstighfarMilestoneCelebration(
            milestone = state.celebrationMilestone,
            visible = state.showCelebration,
            onDismiss = { viewModel.onCelebrationDismissed() },
            modifier = Modifier.fillMaxSize(),
        )

        IstighfarRewardsSheet(
            visible = showRewardsSheet,
            onDismiss = { showRewardsSheet = false },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (state.showLeaderboard) {
        IstighfarLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = { viewModel.onLeaderboardClosed() },
        )
    }

    ManualIstighfarSheet(
        isOpen = state.showManualIstighfarSheet,
        remaining = state.manualRemainingToday,
        onDismiss = { viewModel.dismissManualIstighfarSheet() },
        onSubmit = { count -> viewModel.submitManualIstighfar(count) },
        onSubtract = { count -> viewModel.subtractManualIstighfar(count) },
    )
}

@Composable
private fun IstighfarManualEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
    ) {
        Text(
            text = stringResource(Res.string.istighfar_manual_entry_button),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
        )
    }
}

@Composable
private fun IstighfarImmersiveZone(
    count: Int,
    target: Int,
    rank: Int,
    participantCount: Int,
    canCount: Boolean,
    onTap: () -> Unit,
    onBack: () -> Unit,
    onRankClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    manualEntryVisible: Boolean = true,
    onViewRewards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = (count.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val cyclesCompleted = if (target > 0) count / target else 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IstighfarHeroBackground)
            // No ripple: a tap on this full-screen surface only updates the counter and its
            // related parts — never a full-screen ripple effect across the whole hero.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = canCount,
                onClickLabel = stringResource(Res.string.istighfar_add),
                role = Role.Button,
                onClick = onTap,
            ),
    ) {
        StarCluster(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(86.dp),
            mirror = false,
        )
        StarCluster(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(86.dp),
            mirror = true,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = IstighfarSpacing.ScreenHorizontal),
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
                        contentDescription = stringResource(Res.string.istighfar_back_cd),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
                ChallengeBubbleButton(ChallengeType.ISTIGHFAR.id)
                Surface(
                    onClick = onRankClick,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    val chipText = if (rank > 0 && participantCount > 0) {
                        "${stringResource(Res.string.istighfar_rank_number, rank)} · ${stringResource(Res.string.istighfar_rank_subtitle, participantCount)}"
                    } else {
                        stringResource(Res.string.istighfar_rank_unranked)
                    }
                    Text(
                        text = chipText,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            if (manualEntryVisible) {
                IstighfarManualEntryButton(onClick = onManualEntryClick)
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.istighfar_phrase),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 2.dp, bottom = 16.dp),
            )

            IstighfarProgressRing(
                fraction = fraction,
                modifier = Modifier.size(220.dp),
                trackColor = Color.White.copy(alpha = 0.15f),
                fillColor = IstighfarColors.LightAmber,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.istighfar_today),
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
                        text = stringResource(Res.string.istighfar_times),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            IstighfarStatChips(
                cyclesCompleted = cyclesCompleted,
                todayCount = count,
                target = target,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.istighfar_tap_hint),
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            ViewRewardsButton(onClick = onViewRewards)

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun IstighfarStatChips(
    cyclesCompleted: Int,
    todayCount: Int,
    target: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroStatChip(
            value = stringResource(Res.string.istighfar_progress_count, todayCount, target),
            label = stringResource(Res.string.istighfar_daily_goal, target),
        )
        HeroStatChip(
            value = cyclesCompleted.toString(),
            label = stringResource(Res.string.istighfar_cycles_label),
        )
    }
}

/** Compact translucent stat pill on the dark hero — replaces the old cream stats panel. */
@Composable
private fun HeroStatChip(value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** "What do you gain" — opens the virtues sheet on demand. */
@Composable
private fun ViewRewardsButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = IstighfarColors.LightAmber.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, IstighfarColors.LightAmber.copy(alpha = 0.55f)),
    ) {
        Text(
            text = stringResource(Res.string.istighfar_view_rewards),
            color = IstighfarColors.LightAmber,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun StarCluster(
    modifier: Modifier = Modifier,
    mirror: Boolean,
) {
    Canvas(modifier = modifier) {
        val stemX = if (mirror) size.width * 0.75f else size.width * 0.25f
        val endX = if (mirror) size.width * 0.38f else size.width * 0.62f
        drawLine(
            color = IstighfarColors.StarStem.copy(alpha = 0.35f),
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
                color = IstighfarColors.Star.copy(alpha = 0.22f),
                topLeft = Offset(x + direction * 4.dp.toPx(), y),
                size = Size(28.dp.toPx(), 12.dp.toPx()),
            )
        }
    }
}
