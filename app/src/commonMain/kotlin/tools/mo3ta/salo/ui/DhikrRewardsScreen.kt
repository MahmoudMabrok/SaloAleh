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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.dhikr_add
import tools.mo3ta.salo.generated.resources.dhikr_back_cd
import tools.mo3ta.salo.generated.resources.dhikr_daily_goal
import tools.mo3ta.salo.generated.resources.dhikr_freed_today
import tools.mo3ta.salo.generated.resources.dhikr_phrase
import tools.mo3ta.salo.generated.resources.dhikr_progress_count
import tools.mo3ta.salo.generated.resources.dhikr_rank_number
import tools.mo3ta.salo.generated.resources.dhikr_rank_subtitle
import tools.mo3ta.salo.generated.resources.dhikr_reward_book
import tools.mo3ta.salo.generated.resources.dhikr_reward_cage
import tools.mo3ta.salo.generated.resources.dhikr_reward_crown
import tools.mo3ta.salo.generated.resources.dhikr_reward_crown_label
import tools.mo3ta.salo.generated.resources.dhikr_reward_feather
import tools.mo3ta.salo.generated.resources.dhikr_reward_shield
import tools.mo3ta.salo.generated.resources.dhikr_rewards_title
import tools.mo3ta.salo.generated.resources.dhikr_tap_hint
import tools.mo3ta.salo.generated.resources.dhikr_times
import tools.mo3ta.salo.generated.resources.dhikr_today
import tools.mo3ta.salo.presentation.DhikrChallengeViewModel
import tools.mo3ta.salo.ui.dhikr.DhikrBrushes
import tools.mo3ta.salo.ui.dhikr.DhikrColors
import tools.mo3ta.salo.ui.dhikr.DhikrLinearProgress
import tools.mo3ta.salo.ui.dhikr.DhikrPanel
import tools.mo3ta.salo.ui.dhikr.DhikrProgressRing
import tools.mo3ta.salo.ui.dhikr.DhikrSpacing

@Composable
fun DhikrRewardsScreen(
    onBack: () -> Unit,
    viewModel: DhikrChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DhikrColors.Ink),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DhikrImmersiveZone(
                count = state.todayCount,
                target = state.dailyGoal,
                rank = state.rank,
                participantCount = state.participantCount,
                canCount = !state.isLoading,
                onTap = { viewModel.onDhikrTap() },
                onBack = onBack,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = DhikrColors.Cream,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DhikrSpacing.ScreenHorizontal)
                        .padding(top = DhikrSpacing.PanelGap, bottom = DhikrSpacing.PanelGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DhikrRewardsGridCard()
                    Spacer(Modifier.height(DhikrSpacing.PanelGap))
                    DhikrStatsRow(
                        freedCount = state.todayCount / 100,
                        todayCount = state.todayCount,
                        target = state.dailyGoal,
                    )
                }
            }
        }
    }
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
) {
    val shownRank = rank.takeIf { it > 0 } ?: 18
    val shownParticipants = participantCount.takeIf { it > 0 } ?: 2458
    val fraction = (count.toFloat() / target.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DhikrBrushes.Header)
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
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Text(
                        text = "${stringResource(Res.string.dhikr_rank_number, shownRank)} · ${stringResource(Res.string.dhikr_rank_subtitle, shownParticipants)}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            Text(
                text = stringResource(Res.string.dhikr_phrase),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp, bottom = 20.dp),
            )

            DhikrProgressRing(
                fraction = fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                trackColor = Color.White.copy(alpha = 0.15f),
                fillColor = DhikrColors.LightGreen,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.dhikr_today),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = count.toString(),
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 76.sp,
                    )
                    Text(
                        text = stringResource(Res.string.dhikr_times),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.dhikr_tap_hint),
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DhikrRewardsGridCard() {
    val rewards = listOf(
        DhikrReward("🕊", stringResource(Res.string.dhikr_reward_cage)),
        DhikrReward("📖", stringResource(Res.string.dhikr_reward_book)),
        DhikrReward("🛡", stringResource(Res.string.dhikr_reward_shield)),
        DhikrReward("🌿", stringResource(Res.string.dhikr_reward_feather)),
        DhikrReward("👑", stringResource(Res.string.dhikr_reward_crown), isHero = true),
    )

    DhikrPanel(modifier = Modifier.fillMaxWidth(), topOverlap = true) {
        Text(
            text = stringResource(Res.string.dhikr_rewards_title),
            color = DhikrColors.Ink,
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
                        color = DhikrColors.Stroke,
                    )
                }
            }
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
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.dhikr_freed_today),
                color = DhikrColors.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
        DhikrPanel(modifier = Modifier.weight(1f), topOverlap = true) {
            Text(
                text = stringResource(Res.string.dhikr_progress_count, todayCount, target),
                color = DhikrColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
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
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class DhikrReward(
    val icon: String,
    val text: String,
    val isHero: Boolean = false,
)

@Composable
private fun RewardColumn(
    reward: DhikrReward,
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
            color = DhikrColors.Ink,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HeroRewardColumn(
    reward: DhikrReward,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(DhikrColors.Green.copy(alpha = 0.09f))
            .border(1.dp, DhikrColors.LightGreen.copy(alpha = 0.35f), shape)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.dhikr_reward_crown_label),
            color = DhikrColors.Green,
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
            color = DhikrColors.Ink,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
