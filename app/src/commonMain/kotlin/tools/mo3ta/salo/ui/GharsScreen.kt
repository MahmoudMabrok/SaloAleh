package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material3.Text
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.domain.GHARS_GROVE_SIZE
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.ghars_empty_hint
import tools.mo3ta.salo.generated.resources.ghars_goal_label
import tools.mo3ta.salo.generated.resources.ghars_grove_complete
import tools.mo3ta.salo.generated.resources.ghars_grove_toast
import tools.mo3ta.salo.generated.resources.ghars_groves_label
import tools.mo3ta.salo.generated.resources.ghars_phrase
import tools.mo3ta.salo.generated.resources.ghars_remaining
import tools.mo3ta.salo.generated.resources.ghars_tap_hint
import tools.mo3ta.salo.generated.resources.ghars_title
import tools.mo3ta.salo.generated.resources.ghars_today_label
import tools.mo3ta.salo.generated.resources.ghars_unit
import tools.mo3ta.salo.generated.resources.manual_ghars_title
import tools.mo3ta.salo.generated.resources.zabad_leaderboard_title
import tools.mo3ta.salo.presentation.GharsChallengeViewModel
import tools.mo3ta.salo.ui.ghars.GharsColors
import tools.mo3ta.salo.ui.ghars.GharsLeaderboardSheet
import tools.mo3ta.salo.ui.ghars.ManualGharsSheet
import tools.mo3ta.salo.ui.ghars.PalmGroveCanvas
import tools.mo3ta.salo.ui.ghars.arefRuqaaFamily
import tools.mo3ta.salo.ui.ghars.ibmPlexArabicFamily

@Composable
fun GharsScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    viewModel: GharsChallengeViewModel = koinViewModel(),
) {
    val analyticsManager: AnalyticsManager = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tapInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        analyticsManager.logAction(AppAnalytics.GHARS_SCREEN_VIEW)
        viewModel.onScreenEntered()
    }
    LaunchedEffect(openLeaderboard) {
        if (openLeaderboard) {
            viewModel.onLeaderboardOpened()
            onLeaderboardAutoOpened()
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.onScreenLeft() } }
    LaunchedEffect(state.groveToastNumber) {
        if (state.groveToastNumber > 0) {
            delay(2800)
            viewModel.onGroveToastShown()
        }
    }

    Column(Modifier.fillMaxSize().background(GharsColors.NightIndigo)) {
        // The grove IS the tap target — the whole hero is clickable.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(
                    interactionSource = tapInteraction,
                    indication = null,
                    role = Role.Button,
                ) {
                    analyticsManager.logAction(AppAnalytics.GHARS_TAP)
                    viewModel.onGharsTap()
                },
        ) {
            PalmGroveCanvas(
                count = state.todayCount,
                plantSerial = state.plantSerial,
                modifier = Modifier.fillMaxSize(),
            )

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GharsColors.TextOnSky)
                }
                Text(
                    text = stringResource(Res.string.ghars_title),
                    color = GharsColors.TextOnSky,
                    fontSize = 21.sp,
                    fontFamily = arefRuqaaFamily(),
                )
                IconButton(onClick = viewModel::onLeaderboardOpened) {
                    Icon(Icons.Default.EmojiEvents, null, tint = GharsColors.Gold)
                }
            }

            // Grove-completion toast
            if (state.groveToastNumber > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 88.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFE9A8), Color(0xFFE8C560))))
                        .padding(horizontal = 22.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.ghars_grove_toast, state.groveToastNumber),
                        color = Color(0xFF0E2A20),
                        fontSize = 16.sp,
                        fontFamily = arefRuqaaFamily(),
                    )
                }
            }

            // The tasbeeh — the thing you're here to say — plus the tap hint.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.ghars_phrase),
                    color = GharsColors.TextOnSky,
                    fontSize = 25.sp,
                    fontFamily = arefRuqaaFamily(),
                    textAlign = TextAlign.Center,
                    lineHeight = 40.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (state.todayCount == 0) Res.string.ghars_empty_hint else Res.string.ghars_tap_hint,
                    ),
                    color = if (state.todayCount == 0) GharsColors.Gold else GharsColors.TextOnSky.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    fontFamily = ibmPlexArabicFamily(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        GharsSheet(
            todayCount = state.todayCount,
            dailyGoal = state.dailyGoal,
            completedGroves = state.completedGroves,
            onLeaderboard = viewModel::onLeaderboardOpened,
            onManual = viewModel::showManualGharsSheet,
        )
    }

    if (state.showLeaderboard) {
        GharsLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            isLoading = state.isLeaderboardLoading,
            participantCount = state.participantCount,
            onDismiss = viewModel::onLeaderboardClosed,
        )
    }
    ManualGharsSheet(
        isOpen = state.showManualGharsSheet,
        remaining = state.manualRemainingToday,
        onDismiss = viewModel::dismissManualGharsSheet,
        onSubmit = viewModel::submitManualGhars,
    )
}

@Composable
private fun GharsSheet(
    todayCount: Int,
    dailyGoal: Int,
    completedGroves: Int,
    onLeaderboard: () -> Unit,
    onManual: () -> Unit,
) {
    val inGrove = todayCount % GHARS_GROVE_SIZE
    val remaining = GHARS_GROVE_SIZE - inGrove
    val progress = (todayCount.toFloat() / dailyGoal).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(GharsColors.SandPale)
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.ghars_today_label),
                    color = GharsColors.SheetMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ibmPlexArabicFamily(),
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = todayCount.toString(),
                        color = GharsColors.Accent,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = ibmPlexArabicFamily(),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(Res.string.ghars_unit),
                        color = GharsColors.SheetMuted,
                        fontSize = 14.sp,
                        fontFamily = ibmPlexArabicFamily(),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(GharsColors.SheetStroke),
            )
            Spacer(Modifier.width(18.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(Res.string.ghars_goal_label),
                    color = GharsColors.SheetMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ibmPlexArabicFamily(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dailyGoal.toString(),
                    color = GharsColors.SheetMuted,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = ibmPlexArabicFamily(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (i < completedGroves) GharsColors.Accent else GharsColors.SheetTrack),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                if (completedGroves > 4) {
                    Text(
                        text = "+${completedGroves - 4}",
                        color = GharsColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ibmPlexArabicFamily(),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = stringResource(Res.string.ghars_groves_label),
                    color = GharsColors.SheetMuted,
                    fontSize = 11.sp,
                    fontFamily = ibmPlexArabicFamily(),
                )
            }
            Text(
                text = if (inGrove == 0 && todayCount > 0) {
                    stringResource(Res.string.ghars_grove_complete)
                } else {
                    stringResource(Res.string.ghars_remaining, remaining)
                },
                color = GharsColors.SheetMuted,
                fontSize = 11.sp,
                fontFamily = ibmPlexArabicFamily(),
            )
        }

        Spacer(Modifier.height(9.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(GharsColors.SheetTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(GharsColors.DateAmber, GharsColors.Accent))),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetAction(
                label = stringResource(Res.string.zabad_leaderboard_title),
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = onLeaderboard,
            )
            SheetAction(
                label = stringResource(Res.string.manual_ghars_title),
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onManual,
            )
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (primary) GharsColors.PalmDeep else Color(0xFFE4D4B7))
            .then(if (!primary) Modifier.border(1.dp, GharsColors.SheetStroke, RoundedCornerShape(13.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) Color(0xFFF0E4CB) else Color(0xFF6B5740),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = ibmPlexArabicFamily(),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
