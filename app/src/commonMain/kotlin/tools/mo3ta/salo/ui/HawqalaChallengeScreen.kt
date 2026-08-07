package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
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
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.hawqala_add
import tools.mo3ta.salo.generated.resources.hawqala_back_cd
import tools.mo3ta.salo.generated.resources.hawqala_daily_goal
import tools.mo3ta.salo.generated.resources.hawqala_manual_entry_button
import tools.mo3ta.salo.generated.resources.hawqala_milestone_subtitle
import tools.mo3ta.salo.generated.resources.hawqala_milestone_title
import tools.mo3ta.salo.generated.resources.hawqala_phrase
import tools.mo3ta.salo.generated.resources.hawqala_progress_count
import tools.mo3ta.salo.generated.resources.hawqala_rank_number
import tools.mo3ta.salo.generated.resources.hawqala_rank_subtitle
import tools.mo3ta.salo.generated.resources.hawqala_rank_unranked
import tools.mo3ta.salo.generated.resources.hawqala_reward_burden
import tools.mo3ta.salo.generated.resources.hawqala_reward_hadith
import tools.mo3ta.salo.generated.resources.hawqala_reward_hadith_ref
import tools.mo3ta.salo.generated.resources.hawqala_reward_treasure
import tools.mo3ta.salo.generated.resources.hawqala_rewards_close
import tools.mo3ta.salo.generated.resources.hawqala_rewards_title
import tools.mo3ta.salo.generated.resources.hawqala_tap_hint
import tools.mo3ta.salo.generated.resources.hawqala_times
import tools.mo3ta.salo.generated.resources.hawqala_today
import tools.mo3ta.salo.generated.resources.hawqala_view_rewards
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.presentation.HawqalaChallengeViewModel
import tools.mo3ta.salo.ui.hawqala.HawqalaColors
import tools.mo3ta.salo.ui.hawqala.HawqalaHeroBackground
import tools.mo3ta.salo.ui.hawqala.HawqalaLeaderboardSheet
import tools.mo3ta.salo.ui.hawqala.HawqalaProgressRing
import tools.mo3ta.salo.ui.hawqala.HawqalaSpacing
import tools.mo3ta.salo.ui.hawqala.ManualHawqalaSheet

@Composable
fun HawqalaChallengeScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    manualEntryEnabled: Boolean = true,
    viewModel: HawqalaChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.HAWQALA_SCREEN_VIEW)
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
            .background(HawqalaHeroBackground),
    ) {
        HawqalaHeroZone(
            rank = state.rank,
            participantCount = state.participantCount,
            canCount = !state.isLoading,
            manualEntryVisible = manualEntryEnabled,
            onTap = viewModel::onHawqalaTap,
            onBack = onBack,
            onRankClick = viewModel::onLeaderboardOpened,
            onManualEntryClick = {
                analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_HAWQALA)
                viewModel.showManualSheet()
            },
            onViewRewards = { showRewardsSheet = true },
            // The counter is a self-collecting island: [HawqalaCounter] is its own restartable
            // composable that subscribes to the todayCount flow internally, so a tap recomposes only
            // that widget — the enclosing hero (and the rest of the screen) never re-renders on a click.
            counter = { HawqalaCounter(countFlow = viewModel.todayCount, target = dailyGoalForCounter) },
        )

        HawqalaMilestoneOverlay(
            milestone = state.celebrationMilestone,
            visible = state.showCelebration,
            onDismiss = viewModel::onCelebrationDismissed,
        )

        HawqalaRewardsSheet(
            visible = showRewardsSheet,
            onDismiss = { showRewardsSheet = false },
        )
    }

    if (state.showLeaderboard) {
        HawqalaLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = viewModel::onLeaderboardClosed,
        )
    }

    ManualHawqalaSheet(
        isOpen = state.showManualSheet,
        remaining = state.manualRemainingToday,
        onDismiss = viewModel::dismissManualSheet,
        onSubmit = viewModel::submitManual,
        onSubtract = viewModel::subtractManual,
    )
}

@Composable
private fun HawqalaHeroZone(
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
            // No ripple: a tap on this full-screen surface only updates the counter — never a
            // full-screen ripple across the whole hero (see CLAUDE.md challenge-counter rule).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = canCount,
                onClickLabel = stringResource(Res.string.hawqala_add),
                role = Role.Button,
                onClick = onTap,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = HawqalaSpacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.hawqala_back_cd),
                            tint = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    // Floating-bubble toggle (Android); no-op on iOS.
                    ChallengeBubbleButton(ChallengeType.HAWQALA.id)
                }
                Surface(
                    onClick = onRankClick,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    val chipText = if (rank > 0 && participantCount > 0) {
                        "${stringResource(Res.string.hawqala_rank_number, rank)} · ${stringResource(Res.string.hawqala_rank_subtitle, participantCount)}"
                    } else {
                        stringResource(Res.string.hawqala_rank_unranked)
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
                HawqalaManualEntryButton(onClick = onManualEntryClick)
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.hawqala_phrase),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 44.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 2.dp, bottom = 12.dp),
            )

            // The hadith transcript lives on the screen itself; the reward breakdown stays behind
            // the "what you gain" button — matching the other challenge screens.
            Text(
                text = stringResource(Res.string.hawqala_reward_hadith),
                color = HawqalaColors.LightAccent.copy(alpha = 0.82f),
                fontSize = 15.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(Res.string.hawqala_reward_hadith_ref),
                color = HawqalaColors.LightAccent.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            counter()

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.hawqala_tap_hint),
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
 * The counter island: number + progress ring + progress chip. This is the ONLY composable that reads
 * the running count, so a tap recomposes just this subtree.
 */
@Composable
private fun HawqalaCounter(countFlow: StateFlow<Int>, target: Int) {
    val count by countFlow.collectAsStateWithLifecycle()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HawqalaProgressRing(
            fractionProvider = { if (target > 0) count.toFloat() / target.toFloat() else 0f },
            modifier = Modifier.size(220.dp),
            trackColor = Color.White.copy(alpha = 0.15f),
            fillColor = HawqalaColors.LightAccent,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.hawqala_today),
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
                    text = stringResource(Res.string.hawqala_times),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        HawqalaStatChip(
            value = stringResource(Res.string.hawqala_progress_count, count, target),
            label = stringResource(Res.string.hawqala_daily_goal, target),
        )
    }
}

@Composable
private fun HawqalaStatChip(value: String, label: String) {
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
private fun HawqalaManualEntryButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
    ) {
        Text(
            text = stringResource(Res.string.hawqala_manual_entry_button),
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
        color = HawqalaColors.LightAccent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, HawqalaColors.LightAccent.copy(alpha = 0.55f)),
    ) {
        Text(
            text = stringResource(Res.string.hawqala_view_rewards),
            color = HawqalaColors.LightAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HawqalaRewardsSheet(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HawqalaColors.Cream,
        contentColor = HawqalaColors.Ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.hawqala_rewards_title),
                color = HawqalaColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // The hadith transcript is shown on the challenge screen; this sheet holds the
            // reward breakdown only.
            RewardRow(emoji = "💎", text = stringResource(Res.string.hawqala_reward_treasure))
            Spacer(Modifier.height(10.dp))
            RewardRow(emoji = "🕊️", text = stringResource(Res.string.hawqala_reward_burden))
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = HawqalaColors.Accent,
            ) {
                Text(
                    text = stringResource(Res.string.hawqala_rewards_close),
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
            .background(HawqalaColors.Accent.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Text(
            text = text,
            color = HawqalaColors.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun HawqalaMilestoneOverlay(
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
            Text(text = "💎", fontSize = 60.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.hawqala_milestone_title, milestone),
                color = HawqalaColors.LightAccent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.hawqala_milestone_subtitle),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
            )
        }
    }
}
