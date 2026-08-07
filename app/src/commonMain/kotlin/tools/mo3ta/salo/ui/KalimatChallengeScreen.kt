package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.kalimat_add
import tools.mo3ta.salo.generated.resources.kalimat_back_cd
import tools.mo3ta.salo.generated.resources.kalimat_daily_goal
import tools.mo3ta.salo.generated.resources.kalimat_manual_entry_button
import tools.mo3ta.salo.generated.resources.kalimat_milestone_subtitle
import tools.mo3ta.salo.generated.resources.kalimat_hadith_dhikr
import tools.mo3ta.salo.generated.resources.kalimat_milestone_title
import tools.mo3ta.salo.generated.resources.kalimat_progress_count
import tools.mo3ta.salo.generated.resources.kalimat_rank_number
import tools.mo3ta.salo.generated.resources.kalimat_rank_subtitle
import tools.mo3ta.salo.generated.resources.kalimat_rank_unranked
import tools.mo3ta.salo.generated.resources.kalimat_reward_hadith
import tools.mo3ta.salo.generated.resources.kalimat_reward_hadith_ref
import tools.mo3ta.salo.generated.resources.kalimat_reward_outweigh
import tools.mo3ta.salo.generated.resources.kalimat_reward_pleasing
import tools.mo3ta.salo.generated.resources.kalimat_rewards_close
import tools.mo3ta.salo.generated.resources.kalimat_rewards_title
import tools.mo3ta.salo.generated.resources.kalimat_scale_day
import tools.mo3ta.salo.generated.resources.kalimat_scale_outweigh
import tools.mo3ta.salo.generated.resources.kalimat_tap_hint
import tools.mo3ta.salo.generated.resources.kalimat_times
import tools.mo3ta.salo.generated.resources.kalimat_today
import tools.mo3ta.salo.generated.resources.kalimat_view_rewards
import tools.mo3ta.salo.generated.resources.kalimat_word_creation
import tools.mo3ta.salo.generated.resources.kalimat_word_ink
import tools.mo3ta.salo.generated.resources.kalimat_word_pleasure
import tools.mo3ta.salo.generated.resources.kalimat_word_throne
import tools.mo3ta.salo.presentation.KalimatChallengeViewModel
import tools.mo3ta.salo.ui.kalimat.KalimatColors
import tools.mo3ta.salo.ui.kalimat.KalimatHeroBackground
import tools.mo3ta.salo.ui.kalimat.KalimatLeaderboardSheet
import tools.mo3ta.salo.ui.kalimat.KalimatProgressRing
import tools.mo3ta.salo.ui.kalimat.KalimatScaleCanvas
import tools.mo3ta.salo.ui.kalimat.KalimatSpacing
import tools.mo3ta.salo.ui.kalimat.ManualKalimatSheet

@Composable
fun KalimatChallengeScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    manualEntryEnabled: Boolean = true,
    viewModel: KalimatChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.KALIMAT_SCREEN_VIEW)
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
    val scaleWords = kalimatWords()
    val scaleDayLabel = stringResource(Res.string.kalimat_scale_day)
    val scaleOutweighLabel = stringResource(Res.string.kalimat_scale_outweigh)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KalimatHeroBackground),
    ) {
        KalimatHeroZone(
            rank = state.rank,
            participantCount = state.participantCount,
            canCount = !state.isLoading,
            manualEntryVisible = manualEntryEnabled,
            onTap = viewModel::onTasbihTap,
            onBack = onBack,
            onRankClick = viewModel::onLeaderboardOpened,
            onManualEntryClick = {
                analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_KALIMAT)
                viewModel.showManualSheet()
            },
            onViewRewards = { showRewardsSheet = true },
            // The counter is a self-collecting island: [KalimatCounter] is its own restartable
            // composable that subscribes to the todayCount flow internally, so a tap recomposes only
            // that widget — the enclosing hero (and the rest of the screen) never re-renders on a tap.
            counter = { KalimatCounter(countFlow = viewModel.todayCount, target = dailyGoalForCounter) },
            // Same contract for the scale: it collects its own signals inside the canvas, so a
            // tasbiha invalidates the draw phase only.
            scale = {
                KalimatScaleCanvas(
                    words = scaleWords,
                    dayLabel = scaleDayLabel,
                    outweighLabel = scaleOutweighLabel,
                    tasbihSignal = viewModel.tasbihSerial,
                    countSignal = viewModel.todayCount,
                    goal = dailyGoalForCounter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            },
        )

        KalimatMilestoneOverlay(
            milestone = state.celebrationMilestone,
            visible = state.showCelebration,
            onDismiss = viewModel::onCelebrationDismissed,
        )

        KalimatRewardsSheet(
            visible = showRewardsSheet,
            onDismiss = { showRewardsSheet = false },
        )
    }

    if (state.showLeaderboard) {
        KalimatLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = viewModel::onLeaderboardClosed,
        )
    }

    ManualKalimatSheet(
        isOpen = state.showManualSheet,
        remaining = state.manualRemainingToday,
        onDismiss = viewModel::dismissManualSheet,
        onSubmit = viewModel::submitManual,
        onSubtract = viewModel::subtractManual,
    )
}

@Composable
private fun KalimatHeroZone(
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
    scale: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Whole-screen tap: a tap anywhere records one dhikr. No ripple — the click only updates
            // the counter, never a full-screen ripple across the hero (see CLAUDE.md counter rule).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = canCount,
                onClickLabel = stringResource(Res.string.kalimat_add),
                role = Role.Button,
                onClick = onTap,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = KalimatSpacing.ScreenHorizontal),
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
                            contentDescription = stringResource(Res.string.kalimat_back_cd),
                            tint = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    // Floating-bubble toggle (Android); no-op on iOS.
                    ChallengeBubbleButton(ChallengeType.KALIMAT.id)
                }
                Surface(
                    onClick = onRankClick,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    val chipText = if (rank > 0 && participantCount > 0) {
                        "${stringResource(Res.string.kalimat_rank_number, rank)} · ${stringResource(Res.string.kalimat_rank_subtitle, participantCount)}"
                    } else {
                        stringResource(Res.string.kalimat_rank_unranked)
                    }
                    Text(
                        text = chipText,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            // The narration comes first and comes whole: it sits directly under the header, is
            // measured before anything that can flex, and carries no maxLines — so the transcript is
            // never the thing that gives way when the screen is short. Only the scale below it
            // shrinks. The full four-word dhikr the user actually recites is highlighted in place
            // inside the transcript, rather than repeated as a standalone (and incomplete) heading
            // above it. The reward breakdown stays behind the "what you gain" button — matching the
            // other challenge screens.
            Text(
                text = kalimatHadithHighlighted(),
                color = KalimatColors.LightGold.copy(alpha = 0.82f),
                fontSize = 16.sp,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(Res.string.kalimat_reward_hadith_ref),
                color = KalimatColors.LightGold.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            if (manualEntryVisible) {
                Spacer(Modifier.height(14.dp))
                KalimatManualEntryButton(onClick = onManualEntryClick)
            }

            // The hadith, played out: the day's pan fills on its own, every tasbiha sends the four
            // words into the other one, and a completed round tips the beam and empties it again.
            // Takes whatever height is left between the transcript and the counter.
            scale()

            counter()

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.kalimat_tap_hint),
                color = Color.White.copy(alpha = 0.32f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            ViewRewardsButton(onClick = onViewRewards)

            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * The counter island: a circular ring showing the running count. This is the ONLY composable that
 * reads the running count, so a tap recomposes just this subtree — never the whole hero (see
 * CLAUDE.md challenge-counter rule). Tapping is handled by the full-screen surface in the hero zone.
 */
@Composable
private fun KalimatCounter(
    countFlow: StateFlow<Int>,
    target: Int,
) {
    val count by countFlow.collectAsStateWithLifecycle()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KalimatProgressRing(
            // Within-cycle progress: the ring returns to 0 the moment the goal is reached and starts
            // filling again for the next cycle, so counting past the goal restarts the ring each
            // milestone (the goal celebration still fires to mark the completed cycle). Mirrors the
            // istighfar/dhikr rings (#155).
            fractionProvider = { if (target > 0) (count % target).toFloat() / target.toFloat() else 0f },
            // Trimmed from 220.dp when the scale moved in above it — the ring gave up the space.
            modifier = Modifier.size(176.dp),
            trackColor = Color.White.copy(alpha = 0.15f),
            fillColor = KalimatColors.LightGold,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.kalimat_today),
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
                    text = stringResource(Res.string.kalimat_times),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        HeroStatChip(
            value = stringResource(Res.string.kalimat_progress_count, count, target),
            label = stringResource(Res.string.kalimat_daily_goal, target),
        )
    }
}

/**
 * The four words of the hadith — the qualifiers that are weighed — in the order they are said. The
 * opening tasbiha (سُبْحَانَ اللهِ وَبِحَمْدِهِ) is the dhikr itself, not one of the four, so it is not
 * among them.
 */
@Composable
private fun kalimatWords(): List<String> = listOf(
    stringResource(Res.string.kalimat_word_creation),
    stringResource(Res.string.kalimat_word_pleasure),
    stringResource(Res.string.kalimat_word_throne),
    stringResource(Res.string.kalimat_word_ink),
)

/**
 * The hadith transcript with the recited four-word dhikr ([kalimat_hadith_dhikr]) emphasised in
 * place inside it. Falls back to the plain hadith if the substring isn't present (e.g. a locale that
 * phrases it differently).
 */
@Composable
private fun kalimatHadithHighlighted(): AnnotatedString {
    val hadith = stringResource(Res.string.kalimat_reward_hadith)
    val dhikr = stringResource(Res.string.kalimat_hadith_dhikr)
    return buildAnnotatedString {
        val idx = hadith.indexOf(dhikr)
        if (idx < 0 || dhikr.isEmpty()) {
            append(hadith)
        } else {
            append(hadith.substring(0, idx))
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                append(dhikr)
            }
            append(hadith.substring(idx + dhikr.length))
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
private fun KalimatManualEntryButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
    ) {
        Text(
            text = stringResource(Res.string.kalimat_manual_entry_button),
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
        color = KalimatColors.LightGold.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, KalimatColors.LightGold.copy(alpha = 0.55f)),
    ) {
        Text(
            text = stringResource(Res.string.kalimat_view_rewards),
            color = KalimatColors.LightGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KalimatRewardsSheet(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KalimatColors.Cream,
        contentColor = KalimatColors.Ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.kalimat_rewards_title),
                color = KalimatColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // The hadith transcript is shown on the challenge screen; this sheet holds the
            // reward breakdown only.
            RewardRow(emoji = "⚖️", text = stringResource(Res.string.kalimat_reward_outweigh))
            Spacer(Modifier.height(10.dp))
            RewardRow(emoji = "🤍", text = stringResource(Res.string.kalimat_reward_pleasing))
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = KalimatColors.Gold,
            ) {
                Text(
                    text = stringResource(Res.string.kalimat_rewards_close),
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
            .background(KalimatColors.Gold.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Text(
            text = text,
            color = KalimatColors.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun KalimatMilestoneOverlay(
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
            Text(text = "⚖️", fontSize = 60.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.kalimat_milestone_title, milestone),
                color = KalimatColors.LightGold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.kalimat_milestone_subtitle),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
            )
        }
    }
}
