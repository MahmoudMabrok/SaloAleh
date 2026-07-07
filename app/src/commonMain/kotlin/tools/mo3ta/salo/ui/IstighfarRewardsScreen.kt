package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import tools.mo3ta.salo.generated.resources.istighfar_reward_ease
import tools.mo3ta.salo.generated.resources.istighfar_reward_provision
import tools.mo3ta.salo.generated.resources.istighfar_reward_record
import tools.mo3ta.salo.generated.resources.istighfar_reward_relief
import tools.mo3ta.salo.generated.resources.istighfar_reward_sunnah
import tools.mo3ta.salo.generated.resources.istighfar_reward_sunnah_label
import tools.mo3ta.salo.generated.resources.istighfar_rewards_title
import tools.mo3ta.salo.generated.resources.istighfar_tap_hint
import tools.mo3ta.salo.generated.resources.istighfar_times
import tools.mo3ta.salo.generated.resources.istighfar_today
import tools.mo3ta.salo.presentation.IstighfarChallengeViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.istighfar.IstighfarColors
import tools.mo3ta.salo.ui.istighfar.IstighfarLeaderboardSheet
import tools.mo3ta.salo.ui.istighfar.IstighfarLinearProgress
import tools.mo3ta.salo.ui.istighfar.IstighfarMilestoneCelebration
import tools.mo3ta.salo.ui.istighfar.IstighfarPanel
import tools.mo3ta.salo.ui.istighfar.IstighfarProgressRing
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
    viewModel: IstighfarChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.ISTIGHFAR_SCREEN_VIEW)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IstighfarHeroBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
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
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = IstighfarColors.Cream,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IstighfarSpacing.ScreenHorizontal)
                        .padding(top = IstighfarSpacing.PanelGap, bottom = IstighfarSpacing.PanelGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IstighfarRewardsGridCard()
                    Spacer(Modifier.height(IstighfarSpacing.PanelGap))
                    IstighfarStatsRow(
                        cyclesCompleted = state.todayCount / state.dailyGoal,
                        todayCount = state.todayCount,
                        target = state.dailyGoal,
                    )
                }
            }
        }

        IstighfarMilestoneCelebration(
            milestone = state.celebrationMilestone,
            visible = state.showCelebration,
            onDismiss = { viewModel.onCelebrationDismissed() },
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
        onDismiss = { viewModel.dismissManualIstighfarSheet() },
        onSubmit = { count -> viewModel.submitManualIstighfar(count) },
    )
}

@Composable
private fun IstighfarManualEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onDarkBackground: Boolean = false,
) {
    val containerColor = if (onDarkBackground) {
        Color.White.copy(alpha = 0.14f)
    } else {
        IstighfarColors.Amber.copy(alpha = 0.08f)
    }
    val borderColor = if (onDarkBackground) {
        Color.White.copy(alpha = 0.24f)
    } else {
        IstighfarColors.LightAmber.copy(alpha = 0.4f)
    }
    val textColor = if (onDarkBackground) {
        Color.White.copy(alpha = 0.92f)
    } else {
        IstighfarColors.Amber
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = stringResource(Res.string.istighfar_manual_entry_button),
            color = textColor,
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
) {
    val fraction = (count.toFloat() / target.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(IstighfarHeroBackground)
            .clickable(
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
                .fillMaxWidth()
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

            IstighfarManualEntryButton(
                onClick = onManualEntryClick,
                onDarkBackground = true,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.istighfar_phrase),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 2.dp, bottom = 12.dp),
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

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.istighfar_tap_hint),
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IstighfarRewardsGridCard() {
    val rewards = listOf(
        IstighfarReward("🚪", stringResource(Res.string.istighfar_reward_relief)),
        IstighfarReward("🌤", stringResource(Res.string.istighfar_reward_ease)),
        IstighfarReward("🎁", stringResource(Res.string.istighfar_reward_provision)),
        IstighfarReward("📖", stringResource(Res.string.istighfar_reward_record)),
        IstighfarReward("👑", stringResource(Res.string.istighfar_reward_sunnah), isHero = true),
    )

    IstighfarPanel(modifier = Modifier.fillMaxWidth(), topOverlap = true) {
        Text(
            text = stringResource(Res.string.istighfar_rewards_title),
            color = IstighfarColors.Ink,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            rewards.forEachIndexed { index, reward ->
                if (reward.isHero) {
                    HeroRewardColumn(reward = reward, modifier = Modifier.weight(1f))
                } else {
                    RewardColumn(reward = reward, modifier = Modifier.weight(1f))
                }
                if (index != rewards.lastIndex) {
                    VerticalDivider(
                        modifier = Modifier.height(96.dp),
                        color = IstighfarColors.Stroke,
                    )
                }
            }
        }
    }
}

@Composable
private fun IstighfarStatsRow(
    cyclesCompleted: Int,
    todayCount: Int,
    target: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(IstighfarSpacing.PanelGap),
    ) {
        IstighfarPanel(modifier = Modifier.weight(1f), topOverlap = true) {
            Text(
                text = cyclesCompleted.toString(),
                color = IstighfarColors.Amber,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.istighfar_cycles_label),
                color = IstighfarColors.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
        IstighfarPanel(modifier = Modifier.weight(1f), topOverlap = true) {
            Text(
                text = stringResource(Res.string.istighfar_progress_count, todayCount, target),
                color = IstighfarColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            IstighfarLinearProgress(
                fraction = todayCount.toFloat() / target.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.istighfar_daily_goal, target),
                color = IstighfarColors.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class IstighfarReward(
    val icon: String,
    val text: String,
    val isHero: Boolean = false,
)

@Composable
private fun RewardColumn(
    reward: IstighfarReward,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = reward.icon,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = reward.text,
            color = IstighfarColors.Ink,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HeroRewardColumn(
    reward: IstighfarReward,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(IstighfarColors.Amber.copy(alpha = 0.09f))
            .border(1.dp, IstighfarColors.LightAmber.copy(alpha = 0.35f), shape)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.istighfar_reward_sunnah_label),
            color = IstighfarColors.Amber,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = reward.icon,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = reward.text,
            color = IstighfarColors.Ink,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
