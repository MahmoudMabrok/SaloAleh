package tools.mo3ta.salo.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.presentation.ZabadChallengeViewModel
import tools.mo3ta.salo.ui.zabad.*

@Composable
fun ZabadScreen(onBack: () -> Unit, viewModel: ZabadChallengeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var phase by remember { mutableFloatStateOf(0f) }
    var washProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        var previous = 0L
        while (true) withInfiniteAnimationFrameNanos { now ->
            if (previous != 0L) {
                val dt = ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
                phase += dt
                if (state.isWashing) washProgress = (washProgress + dt / 1.8f).coerceAtMost(1f)
                else washProgress = 0f
            }
            previous = now
        }
    }
    LaunchedEffect(state.isWashing) {
        if (state.isWashing) { delay(1800); viewModel.onCelebrationDismissed() }
    }
    DisposableEffect(Unit) { onDispose { viewModel.onScreenLeft() } }

    val sea = calculateZabadSea(state.elapsedSinceWashMillis.milliseconds)
    Box(Modifier.fillMaxSize().background(Color(0xFF04121C)).clickable { if (!state.isWashing) viewModel.onZabadTap() }) {
        Canvas(Modifier.fillMaxSize()) { drawZabadSea(sea, phase, washProgress) }
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFFEAF6F4))
        }
        IconButton(onClick = viewModel::onLeaderboardOpened, modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopEnd)) {
            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFE9C46A))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.zabad_phrase), color = Color(0xFFEAF6F4), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text("${state.todayCount % 100}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text(stringResource(Res.string.zabad_progress), color = Color(0xB3EAF6F4), fontSize = 14.sp)
            LinearProgressIndicator(progress = { (state.todayCount % 100) / 100f }, modifier = Modifier.fillMaxWidth(.55f).padding(top = 10.dp), color = Color(0xFF2ED3C4), trackColor = Color.White.copy(alpha=.15f))
        }
        Column(Modifier.fillMaxWidth().padding(28.dp).navigationBarsPadding().align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.zabad_tap_hint), color = Color(0xB3EAF6F4), textAlign = TextAlign.Center, fontSize = 13.sp)
            TextButton(onClick = viewModel::showManualZabadSheet) { Text(stringResource(Res.string.manual_zabad_title), color = Color(0xFF2ED3C4)) }
        }
        if (state.isWashing) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.zabad_verdict_title), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.zabad_verdict_sub), color = Color(0xFFE9C46A), fontSize = 17.sp)
        }
    }
    if (state.showLeaderboard) ZabadLeaderboardSheet(entries = state.leaderboard, currentUid = state.currentUid, isLoading = state.isLeaderboardLoading, participantCount = state.participantCount, onDismiss = viewModel::onLeaderboardClosed)
    ManualZabadSheet(isOpen = state.showManualZabadSheet, onDismiss = viewModel::dismissManualZabadSheet, onSubmit = viewModel::submitManualZabad)
}

private fun DrawScope.drawZabadSea(sea: ZabadSeaState, phase: Float, wash: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF071B2B), Color(0xFF123B49))))
    val line = size.height * (sea.waterLevel + (SEA_FLOOR - sea.waterLevel) * wash)
    val clear = Color(0xFF2ED3C4); val murk = Color(0xFF123B49)
    drawRect(Brush.verticalGradient(listOf(lerp(clear, murk, sea.murk * (1f-wash)), Color(0xFF04121C))), Offset(0f,line), Size(size.width,size.height-line))
    repeat(sea.foamCount) { i ->
        val x0 = ((i * 83.7f + phase * (10 + i % 5)) % (size.width + 80f)) - wash * (size.width + 120f)
        val y = line + 14f + (i % 5) * 18f + sin(phase * 1.4f + i) * 6f
        drawCircle(Color(0xFFEAF6F4).copy(alpha=(.75f * (1f-wash)).coerceAtLeast(0f)), 4f + i % 4, Offset(x0,y))
    }
    val waveX = size.width * (1f-wash)
    if (wash > 0f) drawCircle(Color.White.copy(alpha=(1f-wash)*.65f), size.width*.42f, Offset(waveX,line))
}
