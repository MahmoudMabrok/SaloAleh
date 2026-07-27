package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.domain.ALF_HASANA_HASANAT_PER_TASBIHA
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.alf_hasana_add
import tools.mo3ta.salo.generated.resources.alf_hasana_back_cd
import tools.mo3ta.salo.generated.resources.alf_hasana_daily_goal
import tools.mo3ta.salo.generated.resources.alf_hasana_hasanat_label
import tools.mo3ta.salo.generated.resources.alf_hasana_manual_entry_button
import tools.mo3ta.salo.generated.resources.alf_hasana_milestone_subtitle
import tools.mo3ta.salo.generated.resources.alf_hasana_milestone_title
import tools.mo3ta.salo.generated.resources.alf_hasana_phrase
import tools.mo3ta.salo.generated.resources.alf_hasana_progress_count
import tools.mo3ta.salo.generated.resources.alf_hasana_rank_number
import tools.mo3ta.salo.generated.resources.alf_hasana_rank_subtitle
import tools.mo3ta.salo.generated.resources.alf_hasana_rank_unranked
import tools.mo3ta.salo.generated.resources.alf_hasana_reward_erased
import tools.mo3ta.salo.generated.resources.alf_hasana_reward_hadith
import tools.mo3ta.salo.generated.resources.alf_hasana_reward_hadith_ref
import tools.mo3ta.salo.generated.resources.alf_hasana_reward_written
import tools.mo3ta.salo.generated.resources.alf_hasana_rewards_close
import tools.mo3ta.salo.generated.resources.alf_hasana_rewards_title
import tools.mo3ta.salo.generated.resources.alf_hasana_tap_hint
import tools.mo3ta.salo.generated.resources.alf_hasana_times
import tools.mo3ta.salo.generated.resources.alf_hasana_today
import tools.mo3ta.salo.generated.resources.alf_hasana_view_rewards
import tools.mo3ta.salo.presentation.AlfHasanaChallengeViewModel
import tools.mo3ta.salo.ui.alfhasana.AlfHasanaColors
import tools.mo3ta.salo.ui.alfhasana.AlfHasanaHeroBackground
import tools.mo3ta.salo.ui.alfhasana.AlfHasanaLeaderboardSheet
import tools.mo3ta.salo.ui.alfhasana.AlfHasanaProgressRing
import tools.mo3ta.salo.ui.alfhasana.AlfHasanaSpacing
import tools.mo3ta.salo.ui.alfhasana.ManualAlfHasanaSheet

@Composable
fun AlfHasanaChallengeScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    manualEntryEnabled: Boolean = true,
    viewModel: AlfHasanaChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.ALF_HASANA_SCREEN_VIEW)
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

    var showRewardsSheet by remember { mutableStateOf(false) }
    // Captured as a plain Int so the counter lambda never reads `state` from inside the hero zone.
    val dailyGoalForCounter = state.dailyGoal

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AlfHasanaHeroBackground),
    ) {
        AlfHasanaHeroZone(
            rank = state.rank,
            participantCount = state.participantCount,
            canCount = !state.isLoading,
            manualEntryVisible = manualEntryEnabled,
            onTap = viewModel::onTasbihTap,
            onBack = onBack,
            onRankClick = viewModel::onLeaderboardOpened,
            onManualEntryClick = {
                analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_ALF_HASANA)
                viewModel.showManualSheet()
            },
            onViewRewards = { showRewardsSheet = true },
            // The counter is a self-collecting island: [AlfHasanaCounter] is its own restartable
            // composable that subscribes to the todayCount flow internally, so a tap recomposes only
            // that widget — the enclosing hero (and the rest of the screen) never re-renders on a click.
            counter = { AlfHasanaCounter(countFlow = viewModel.todayCount, target = dailyGoalForCounter) },
        )

        AlfHasanaMilestoneOverlay(
            milestone = state.celebrationMilestone,
            visible = state.showCelebration,
            onDismiss = viewModel::onCelebrationDismissed,
        )

        AlfHasanaRewardsSheet(
            visible = showRewardsSheet,
            onDismiss = { showRewardsSheet = false },
        )
    }

    if (state.showLeaderboard) {
        AlfHasanaLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = viewModel::onLeaderboardClosed,
        )
    }

    ManualAlfHasanaSheet(
        isOpen = state.showManualSheet,
        remaining = state.manualRemainingToday,
        onDismiss = viewModel::dismissManualSheet,
        onSubmit = viewModel::submitManual,
        onSubtract = viewModel::subtractManual,
    )
}

@Composable
private fun AlfHasanaHeroZone(
    rank: Int,
    participantCount: Int,
    canCount: Boolean,
    manualEntryVisible: Boolean,
    onTap: () -> Unit,
    onBack: () -> Unit,
    onRankClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    onViewRewards: () -> Unit,
    counter: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = canCount,
                onClickLabel = stringResource(Res.string.alf_hasana_add),
                role = Role.Button,
                onClick = onTap,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = AlfHasanaSpacing.ScreenHorizontal),
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
                        contentDescription = stringResource(Res.string.alf_hasana_back_cd),
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
                        "${stringResource(Res.string.alf_hasana_rank_number, rank)} · ${stringResource(Res.string.alf_hasana_rank_subtitle, participantCount)}"
                    } else {
                        stringResource(Res.string.alf_hasana_rank_unranked)
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
                AlfHasanaManualEntryButton(onClick = onManualEntryClick)
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.alf_hasana_phrase),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 44.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 2.dp, bottom = 20.dp),
            )

            counter()

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.alf_hasana_tap_hint),
                color = Color.White.copy(alpha = 0.32f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            ViewRewardsButton(onClick = onViewRewards)

            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * The counter island: number + progress ring + hasanat chip. This is the ONLY composable that reads
 * the running count, so a tap recomposes just this subtree.
 */
@Composable
private fun AlfHasanaCounter(countFlow: StateFlow<Int>, target: Int) {
    val count by countFlow.collectAsStateWithLifecycle()
    val hasanat = count * ALF_HASANA_HASANAT_PER_TASBIHA
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AlfHasanaProgressRing(
            fractionProvider = { if (target > 0) count.toFloat() / target.toFloat() else 0f },
            modifier = Modifier.size(220.dp),
            trackColor = Color.White.copy(alpha = 0.15f),
            fillColor = AlfHasanaColors.LightGold,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.alf_hasana_today),
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
                    text = stringResource(Res.string.alf_hasana_times),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroStatChip(
                value = stringResource(Res.string.alf_hasana_progress_count, count, target),
                label = stringResource(Res.string.alf_hasana_daily_goal, target),
            )
            HeroStatChip(
                value = hasanat.toString(),
                label = stringResource(Res.string.alf_hasana_hasanat_label),
            )
        }
    }
}

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
            Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun AlfHasanaManualEntryButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
    ) {
        Text(
            text = stringResource(Res.string.alf_hasana_manual_entry_button),
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
private fun ViewRewardsButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = AlfHasanaColors.LightGold.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, AlfHasanaColors.LightGold.copy(alpha = 0.55f)),
    ) {
        Text(
            text = stringResource(Res.string.alf_hasana_view_rewards),
            color = AlfHasanaColors.LightGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlfHasanaRewardsSheet(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AlfHasanaColors.Cream,
        contentColor = AlfHasanaColors.Ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.alf_hasana_rewards_title),
                color = AlfHasanaColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.alf_hasana_reward_hadith),
                color = AlfHasanaColors.Ink.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.alf_hasana_reward_hadith_ref),
                color = AlfHasanaColors.Gold,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(20.dp))
            RewardRow(emoji = "🤍", text = stringResource(Res.string.alf_hasana_reward_written))
            Spacer(Modifier.height(10.dp))
            RewardRow(emoji = "🧹", text = stringResource(Res.string.alf_hasana_reward_erased))
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AlfHasanaColors.Gold,
            ) {
                Text(
                    text = stringResource(Res.string.alf_hasana_rewards_close),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun RewardRow(emoji: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlfHasanaColors.Gold.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Text(
            text = text,
            color = AlfHasanaColors.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun AlfHasanaMilestoneOverlay(
    milestone: Int,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible || milestone <= 0) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "🤍", fontSize = 60.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.alf_hasana_milestone_title, milestone),
                color = AlfHasanaColors.LightGold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.alf_hasana_milestone_subtitle),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
            )
        }
    }
}
