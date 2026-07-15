package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.albaqara_add
import tools.mo3ta.salo.generated.resources.albaqara_back_cd
import tools.mo3ta.salo.generated.resources.albaqara_phrase
import tools.mo3ta.salo.generated.resources.albaqara_rank_number
import tools.mo3ta.salo.generated.resources.albaqara_rank_subtitle
import tools.mo3ta.salo.generated.resources.albaqara_rank_unranked
import tools.mo3ta.salo.generated.resources.albaqara_tap_hint
import tools.mo3ta.salo.generated.resources.albaqara_times
import tools.mo3ta.salo.generated.resources.albaqara_today
import tools.mo3ta.salo.generated.resources.albaqara_undo
import tools.mo3ta.salo.presentation.AlBaqaraChallengeViewModel
import tools.mo3ta.salo.ui.albaqara.AlBaqaraColors
import tools.mo3ta.salo.ui.albaqara.AlBaqaraHeroBackground
import tools.mo3ta.salo.ui.albaqara.AlBaqaraLeaderboardSheet
import tools.mo3ta.salo.ui.albaqara.AlBaqaraProgressRing
import tools.mo3ta.salo.ui.albaqara.AlBaqaraSpacing

@Composable
fun AlBaqaraChallengeScreen(
    onBack: () -> Unit,
    viewModel: AlBaqaraChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.ALBAQARA_SCREEN_VIEW)
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
            .background(AlBaqaraHeroBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: back + rank chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlBaqaraSpacing.ScreenHorizontal)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.albaqara_back_cd),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
                Surface(
                    onClick = { viewModel.onLeaderboardOpened() },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    val chipText = if (state.rank > 0 && state.participantCount > 0) {
                        "${stringResource(Res.string.albaqara_rank_number, state.rank)} · ${stringResource(Res.string.albaqara_rank_subtitle, state.participantCount)}"
                    } else {
                        stringResource(Res.string.albaqara_rank_unranked)
                    }
                    Text(
                        text = chipText,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.albaqara_phrase),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 2.dp, bottom = 20.dp),
            )

            AlBaqaraProgressRing(
                fraction = if (state.todayCount > 0) 1f else 0f,
                modifier = Modifier.size(220.dp),
                trackColor = Color.White.copy(alpha = 0.15f),
                fillColor = AlBaqaraColors.LightAccent,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.albaqara_today),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = state.todayCount.toString(),
                        color = Color.White,
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 64.sp,
                    )
                    Text(
                        text = stringResource(Res.string.albaqara_times),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Primary action: record one completed reading
            Surface(
                onClick = {
                    viewModel.onReadTap()
                    analyticsManager.logAction(
                        AppAnalytics.ALBAQARA_TAP,
                        mapOf(AppAnalytics.PARAM_COUNT to (state.todayCount + 1).toString()),
                    )
                },
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlBaqaraSpacing.ScreenHorizontal),
                shape = RoundedCornerShape(20.dp),
                color = AlBaqaraColors.LightAccent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.albaqara_add),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (state.todayCount > 0) {
                Surface(
                    onClick = { viewModel.onUndoTap() },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = stringResource(Res.string.albaqara_undo),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = stringResource(Res.string.albaqara_tap_hint),
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showLeaderboard) {
        AlBaqaraLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = { viewModel.onLeaderboardClosed() },
        )
    }
}
