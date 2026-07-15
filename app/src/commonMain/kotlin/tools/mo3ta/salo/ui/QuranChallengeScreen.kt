package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import tools.mo3ta.salo.generated.resources.quran_back_cd
import tools.mo3ta.salo.generated.resources.quran_daily_goal
import tools.mo3ta.salo.generated.resources.quran_manual_entry_button
import tools.mo3ta.salo.generated.resources.quran_page_add
import tools.mo3ta.salo.generated.resources.quran_pages_label
import tools.mo3ta.salo.generated.resources.quran_pages_read
import tools.mo3ta.salo.generated.resources.quran_phrase
import tools.mo3ta.salo.generated.resources.quran_progress_count
import tools.mo3ta.salo.generated.resources.quran_rank_number
import tools.mo3ta.salo.generated.resources.quran_rank_subtitle
import tools.mo3ta.salo.generated.resources.quran_rank_unranked
import tools.mo3ta.salo.generated.resources.quran_today
import tools.mo3ta.salo.generated.resources.quran_tap_hint
import tools.mo3ta.salo.generated.resources.quran_view_rewards
import tools.mo3ta.salo.presentation.QuranChallengeViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.quran.QuranColors
import tools.mo3ta.salo.ui.quran.QuranLeaderboardSheet
import tools.mo3ta.salo.ui.quran.QuranMilestoneCelebration
import tools.mo3ta.salo.ui.quran.QuranProgressRing
import tools.mo3ta.salo.ui.quran.QuranRewardsSheet
import tools.mo3ta.salo.ui.quran.QuranSpacing
import tools.mo3ta.salo.ui.quran.ManualQuranSheet


private val QuranHeroBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F2318),
        Color(0xFF1A4035),
        Color(0xFF2E4A3F),
        MohamedLoversPalette.DeepBlue,
    ),
)

@Composable
fun QuranChallengeScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    viewModel: QuranChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.QURAN_SCREEN_VIEW)
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
            .background(QuranHeroBackground),
    ) {
        QuranImmersiveZone(
            count = state.todayCount,
            target = state.dailyGoal,
            rank = state.rank,
            participantCount = state.participantCount,
            canCount = !state.isLoading,
            onPageTap = {
                viewModel.onQuranPageTap()
                analyticsManager.logAction(
                    AppAnalytics.QURAN_TAP,
                    mapOf(AppAnalytics.PARAM_COUNT to (state.todayCount + 1).toString()),
                )
            },
            onBack = onBack,
            onRankClick = { viewModel.onLeaderboardOpened() },
            onManualEntryClick = {
                analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_QURAN)
                viewModel.showManualQuranSheet()
            },
            onViewRewards = { showRewardsSheet = true },
            modifier = Modifier.fillMaxSize(),
        )

        QuranMilestoneCelebration(
            milestone = state.celebrationMilestone,
            visible = state.showCelebration,
            onDismiss = { viewModel.onCelebrationDismissed() },
            modifier = Modifier.fillMaxSize(),
        )

        QuranRewardsSheet(
            visible = showRewardsSheet,
            onDismiss = { showRewardsSheet = false },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (state.showLeaderboard) {
        QuranLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = { viewModel.onLeaderboardClosed() },
        )
    }

    ManualQuranSheet(
        isOpen = state.showManualQuranSheet,
        remaining = state.manualRemainingToday,
        onDismiss = { viewModel.dismissManualQuranSheet() },
        onSubmit = { count -> viewModel.submitManualQuran(count) },
    )
}

@Composable
private fun QuranManualEntryButton(
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
            text = stringResource(Res.string.quran_manual_entry_button),
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
private fun QuranPageButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = QuranColors.Teal.copy(alpha = if (enabled) 0.85f else 0.3f),
        border = BorderStroke(1.dp, QuranColors.LightTeal.copy(alpha = if (enabled) 0.6f else 0.2f)),
    ) {
        Text(
            text = stringResource(Res.string.quran_page_add),
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun QuranImmersiveZone(
    count: Int,
    target: Int,
    rank: Int,
    participantCount: Int,
    canCount: Boolean,
    onPageTap: () -> Unit,
    onBack: () -> Unit,
    onRankClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    onViewRewards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = (count.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val goalsCompleted = if (target > 0) count / target else 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(QuranHeroBackground),
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
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = QuranSpacing.ScreenHorizontal),
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
                        contentDescription = stringResource(Res.string.quran_back_cd),
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
                        "${stringResource(Res.string.quran_rank_number, rank)} · ${stringResource(Res.string.quran_rank_subtitle, participantCount)}"
                    } else {
                        stringResource(Res.string.quran_rank_unranked)
                    }
                    Text(
                        text = chipText,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            QuranManualEntryButton(onClick = onManualEntryClick)

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.quran_phrase),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 2.dp, bottom = 16.dp),
            )

            QuranProgressRing(
                fraction = fraction,
                modifier = Modifier.size(220.dp),
                trackColor = Color.White.copy(alpha = 0.15f),
                fillColor = QuranColors.LightTeal,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.quran_today),
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
                        text = stringResource(Res.string.quran_pages_label),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            QuranStatChips(
                goalsCompleted = goalsCompleted,
                todayCount = count,
                target = target,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.quran_tap_hint),
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            QuranPageButton(
                onClick = onPageTap,
                enabled = canCount,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            ViewRewardsButton(onClick = onViewRewards)

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun QuranStatChips(
    goalsCompleted: Int,
    todayCount: Int,
    target: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroStatChip(
            value = stringResource(Res.string.quran_progress_count, todayCount, target),
            label = stringResource(Res.string.quran_daily_goal, target),
        )
        HeroStatChip(
            value = goalsCompleted.toString(),
            label = stringResource(Res.string.quran_pages_read),
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

/** "Virtues" — opens the Quran virtues sheet on demand. */
@Composable
private fun ViewRewardsButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = QuranColors.LightTeal.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, QuranColors.LightTeal.copy(alpha = 0.55f)),
    ) {
        Text(
            text = stringResource(Res.string.quran_view_rewards),
            color = QuranColors.LightTeal,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp),
        )
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
            color = QuranColors.StarStem.copy(alpha = 0.35f),
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
                color = QuranColors.Star.copy(alpha = 0.22f),
                topLeft = Offset(x + direction * 4.dp.toPx(), y),
                size = Size(28.dp.toPx(), 12.dp.toPx()),
            )
        }
    }
}
