package tools.mo3ta.salo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import tools.mo3ta.salo.domain.ChallengeType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.presentation.ZabadChallengeViewModel
import tools.mo3ta.salo.ui.ghars.arefRuqaaFamily
import tools.mo3ta.salo.ui.ghars.ibmPlexArabicFamily
import tools.mo3ta.salo.ui.zabad.*

@Composable
fun ZabadScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    viewModel: ZabadChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()

    LaunchedEffect(openLeaderboard) {
        if (openLeaderboard) {
            viewModel.onLeaderboardOpened()
            onLeaderboardAutoOpened()
        }
    }

    var phase by remember { mutableFloatStateOf(0f) }
    var washProgress by remember { mutableFloatStateOf(0f) }
    val ripples = remember { mutableStateListOf<Ripple>() }
    val surges = remember { mutableStateListOf<Surge>() }

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.ZABAD_SCREEN_VIEW)
        var previous = 0L
        while (true) withInfiniteAnimationFrameNanos { now ->
            if (previous != 0L) {
                val dt = ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
                phase += dt
                washProgress = if (state.isWashing) (washProgress + dt / 1.8f).coerceAtMost(1f) else 0f
                if (ripples.isNotEmpty()) {
                    ripples.forEach { it.age += dt * 2.4f }
                    ripples.removeAll { it.age >= 1f }
                }
                if (surges.isNotEmpty()) {
                    surges.forEach { it.age += dt * 1.5f }
                    surges.removeAll { it.age >= 1f }
                }
            }
            previous = now
        }
    }
    LaunchedEffect(state.isWashing) {
        if (state.isWashing) { delay(1800); viewModel.onCelebrationDismissed() }
    }
    DisposableEffect(Unit) { onDispose { viewModel.onScreenLeft() } }

    val sea = calculateZabadSea(state.elapsedSinceWashMillis.milliseconds)
    // the verdict needs the screen to itself — the counter would otherwise print through it
    val counterAlpha by animateFloatAsState(if (state.isWashing) 0f else 1f, label = "counterAlpha")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF04121C))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = viewModel.state.value
                    if (!s.isWashing) {
                        viewModel.onZabadTap()
                        analyticsManager.logAction(
                            AppAnalytics.ZABAD_TAP,
                            mapOf(AppAnalytics.PARAM_COUNT to (s.todayCount + 1).toString()),
                        )
                        if (size.width > 0) {
                            val xFrac = offset.x / size.width.toFloat()
                            ripples.add(Ripple(xFrac))
                            surges.add(Surge(xFrac))
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawZabadSea(sea, phase, washProgress, ripples, surges) }
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFFEAF6F4))
        }
        Row(
            modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChallengeBubbleButton(ChallengeType.ZABAD.id)
            IconButton(onClick = viewModel::onLeaderboardOpened) {
                Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFE9C46A))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).align(Alignment.Center).alpha(counterAlpha), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.zabad_phrase), color = Color(0xFFEAF6F4), fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = arefRuqaaFamily(), textAlign = TextAlign.Center, lineHeight = 42.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.zabad_hadith),
                color = Color(0xFFE9C46A).copy(alpha = .82f),
                fontSize = 15.sp,
                fontFamily = arefRuqaaFamily(),
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(Res.string.zabad_hadith_ref),
                color = Color(0xFFE9C46A).copy(alpha = .5f),
                fontSize = 11.sp,
                fontFamily = ibmPlexArabicFamily(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text("${state.todayCount % 100}", color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Light, fontFamily = ibmPlexArabicFamily())
            Text(stringResource(Res.string.zabad_progress), color = Color(0xB3EAF6F4), fontSize = 14.sp, fontFamily = ibmPlexArabicFamily())
            LinearProgressIndicator(progress = { (state.todayCount % 100) / 100f }, modifier = Modifier.fillMaxWidth(.55f).padding(top = 10.dp), color = Color(0xFF2ED3C4), trackColor = Color.White.copy(alpha = .15f))
            if (!state.isWashing && state.elapsedSinceWashMillis > 0L) {
                val totalMinutes = state.elapsedSinceWashMillis / 60_000L
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(Res.string.zabad_accumulated, (totalMinutes / 60).toString(), (totalMinutes % 60).toString()),
                    color = Color(0xFFEAF6F4).copy(alpha = (.42f + sea.murk * .45f).coerceAtMost(.9f)),
                    fontSize = 12.sp,
                    fontFamily = ibmPlexArabicFamily(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF04121C).copy(alpha = .4f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(28.dp).navigationBarsPadding().align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.zabad_tap_hint), color = Color(0xB3EAF6F4), textAlign = TextAlign.Center, fontSize = 13.sp, fontFamily = ibmPlexArabicFamily())
            TextButton(onClick = {
                analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_ZABAD)
                viewModel.showManualZabadSheet()
            }) { Text(stringResource(Res.string.manual_zabad_title), color = Color(0xFF2ED3C4), fontFamily = ibmPlexArabicFamily()) }
        }
        if (state.isWashing) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.zabad_verdict_title), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = arefRuqaaFamily())
            Text(stringResource(Res.string.zabad_verdict_sub), color = Color(0xFFE9C46A), fontSize = 17.sp, fontFamily = arefRuqaaFamily())
        }
    }
    if (state.showLeaderboard) ZabadLeaderboardSheet(entries = state.leaderboard, currentUid = state.currentUid, isLoading = state.isLeaderboardLoading, participantCount = state.participantCount, onDismiss = viewModel::onLeaderboardClosed)
    ManualZabadSheet(isOpen = state.showManualZabadSheet, remaining = state.manualRemainingToday, onDismiss = viewModel::dismissManualZabadSheet, onSubmit = viewModel::submitManualZabad, onSubtract = viewModel::subtractManualZabad)
}

/** A tap-triggered surface ring; [xFrac] is the horizontal tap position 0..1, [age] runs 0→1. */
private class Ripple(val xFrac: Float) {
    var age: Float = 0f
}

/** A tap-triggered swell in the waterline itself, spreading out from [xFrac] as [age] runs 0→1. */
private class Surge(val xFrac: Float) {
    var age: Float = 0f
}

/** Deterministic LCG so stars/silt/spray are stable frame-to-frame (mirrors the mockup's seeded rng). */
private class Lcg(seed: Int) {
    private var s: Long = (seed.toLong() % 2147483647L).let { if (it <= 0L) it + 2147483646L else it }
    fun next(): Float {
        s = (s * 16807L) % 2147483647L
        return (s.toDouble() / 2147483647.0).toFloat()
    }
}

private fun ease(t: Float): Float =
    if (t < .5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f

/**
 * Full-bleed sea. [sea] is sampled once per screen entry (and again after each wash), so the level,
 * foam and murk hold still while the user taps; [wash] runs 0→1 during the wave that takes over at
 * 100. Taps add [ripples] and [surges] — surface detail only, they never move the waterline.
 * Ported from `docs/zabad-sea-v2.html`.
 *//**
 * Full-bleed sea. [sea] is sampled once per screen entry (and again after each wash), so the level,
 * foam and murk hold still while the user taps; [wash] runs 0→1 during the wave that takes over at
 * 100. Taps add [ripples] and [surges] — surface detail only, they never move the waterline.
 *
 * Ported from `docs/zabad-sea-v2.html`, whose canvas is ~332 CSS px wide. A [DrawScope] measures in
 * device pixels, so every length authored against the mockup is multiplied by [DrawScope.density]
 * (and every wave frequency divided by it) — without that the whole sea draws ~3x too small and the
 * waterline grows 3x too many ripples on a real phone.
 */
private fun DrawScope.drawZabadSea(
    sea: ZabadSeaState,
    phase: Float,
    wash: Float,
    ripples: List<Ripple>,
    surges: List<Surge>,
) {
    val w = size.width
    val h = size.height
    val dp = density // px per dp: the mockup's units → this screen's units
    val murkT = sea.murk
    val clarity = if (wash > 0f) ease(wash) else 0f
    // water drains from its frozen level back to the floor as the wave clears
    val base = h * (sea.waterLevel + (SEA_FLOOR - sea.waterLevel) * clarity)
    val halfPi = (PI / 2.0).toFloat()

    // ---- surface geometry: three sine layers, the wash crest, and the bump each tap sends out ----
    val amp = (2.6f + 1.9f * sin(phase * .55f) + murkT * 1.8f) * dp
    val crestW = 150f * dp
    val crestX = if (wash > 0f) w + 100f * dp - ease(wash) * (w + 240f * dp) else -9999f
    val crestH = if (wash > 0f) sin(wash * PI.toFloat()) * h * 0.16f else 0f
    fun surf(x: Float) = base +
        sin(x * .030f / dp + phase * 1.35f) * amp +
        sin(x * .012f / dp - phase * .85f) * amp * .65f +
        sin(x * .061f / dp + phase * 2.1f) * amp * .22f
    fun surfW(x: Float): Float {
        var y = surf(x)
        if (wash > 0f) {
            val d = x - crestX
            if (d > -crestW && d < crestW) y -= crestH * cos((d / crestW) * halfPi)
        }
        for (surge in surges) {
            val sx = surge.xFrac * w
            val spread = (26f + surge.age * 190f) * dp
            val d = abs(x - sx)
            if (d < spread) {
                val fall = cos((d / spread) * halfPi)
                y -= 13f * dp * (1f - surge.age) * fall * fall
            }
        }
        return y
    }

    // ---- sky: darkens with murk, warms as the sea clears ----
    val skyBottom = lerp(lerp(Color(0xFF0E3446), Color(0xFF0A2634), murkT), Color(0xFF15525C), clarity)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF04121C), skyBottom)))

    // ---- stars: twinkle, and fade as the night thickens with foam ----
    val sr = Lcg(7)
    repeat(26) {
        val x = sr.next() * w
        val y = sr.next() * h * .42f
        val r = (sr.next() * 1.2f + .4f) * dp
        val tw = sr.next() * 6.28f
        val a = (.22f + .32f * abs(sin(phase * .7f + tw))) * (1f - murkT * .55f)
        drawCircle(Color(0xFFEAF6F4).copy(alpha = a.coerceIn(0f, 1f)), r, Offset(x, y))
    }

    // ---- moon: dims as the sea rises, blazes when it clears ----
    val moonC = Offset(w * .79f, h * .155f)
    drawCircle(Color(0xFFE9C46A).copy(alpha = (.10f + clarity * .20f).coerceIn(0f, 1f)), 27f * dp, moonC)
    val moonA = (.55f - murkT * .35f) + clarity * .5f
    drawCircle(Color(0xFFE9C46A).copy(alpha = moonA.coerceIn(.2f, 1f)), 9.5f * dp, moonC)

    // ---- back swell: a slower wave behind the body, so the sea has depth, not one flat face ----
    val swell = Path().apply {
        moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            lineTo(x, base + 13f * dp + sin(x * .017f / dp - phase * .62f) * (amp * 1.5f + 3f * dp))
            x += 6f
        }
        lineTo(w, h)
        close()
    }
    drawPath(swell, Color(0xFF14414E).copy(alpha = (.55f + murkT * .25f).coerceIn(0f, 1f)))

    // ---- water body: gradient tracks murk (down) and clarity (up) ----
    val water = Path().apply {
        moveTo(0f, h)
        var x = 0f
        while (x <= w) { lineTo(x, surfW(x)); x += 4f }
        lineTo(w, h)
        close()
    }
    val top = lerp(lerp(Color(0xFF1D6072), Color(0xFF14414E), murkT), Color(0xFF3FE0CF), clarity)
    val mid = lerp(lerp(Color(0xFF154A5A), Color(0xFF0F3441), murkT), Color(0xFF1FA5A0), clarity)
    val bottom = lerp(Color(0xFF061E2A), Color(0xFF0B5A60), clarity)
    drawPath(
        water,
        Brush.verticalGradient(
            0f to top, .45f to mid, 1f to bottom,
            startY = (base - 30f * dp).coerceAtLeast(0f),
            endY = h,
        ),
    )

    // ---- suspended silt: density tracks the frozen murk, vanishes in the wash ----
    clipPath(water) {
        val gr = Lcg(99)
        val n = (14f + 34f * murkT).toInt()
        repeat(n) { i ->
            val x = gr.next() * w
            val y = base + 14f * dp + gr.next() * (h - base)
            val r = (1.4f + gr.next() * 2f) * dp
            drawCircle(
                Color(0xFF5C6B70).copy(alpha = (.30f * (1f - clarity)).coerceIn(0f, 1f)),
                r,
                Offset(x, y + sin(phase * .5f + i) * 2f * dp),
            )
        }
    }

    // ---- surface line ----
    val surfaceLine = Path().apply {
        moveTo(0f, surfW(0f))
        var x = 0f
        while (x <= w) { lineTo(x, surfW(x)); x += 4f }
    }
    drawPath(
        surfaceLine,
        Color(0xFFEAF6F4).copy(alpha = (.30f + clarity * .45f).coerceIn(0f, 1f)),
        style = Stroke(width = 1.5f * dp),
    )

    // ---- whitecaps: a lick of white wherever the surface is climbing steeply ----
    run {
        var x = 0f
        while (x <= w) {
            // normalised back to dp so the threshold means the same thing on every screen
            val slope = (surfW(x + 5f * dp) - surfW(x - 5f * dp)) / dp
            if (slope < -1.5f) {
                val a = ((-slope - 1.5f) * .16f).coerceAtMost(.42f) * (1f - clarity * .5f)
                drawCircle(
                    Color(0xFFEAF6F4).copy(alpha = a.coerceIn(0f, 1f)),
                    1.7f * dp,
                    Offset(x, surfW(x) - 1f * dp),
                )
            }
            x += 9f * dp
        }
    }

    // ---- foam = the sins: dark rafts of scum lying on the waterline, swept out by the wave ----
    val driftScale = 1f + 2.4f * murkT
    val fr = Lcg(2024)
    val span = 0.94f
    val flat = .46f // squashed: foam lies on the water, it does not billow above it
    val n = sea.foamCount
    repeat(n) { i ->
        // stratified across the width, so a handful of clumps spread out instead of bunching up
        val fx0 = .03f + ((i + fr.next()) / n) * span
        val r = (10f + fr.next() * 14f) * dp
        val dir = if (fr.next() < .5f) -1f else 1f
        val speed = .0002f + fr.next() * .0004f
        val bob = fr.next() * 6.28f
        // ping-pong drift within [.03, .97] so clumps wander without leaving the frame
        val raw = fx0 + dir * speed * driftScale * 60f * phase
        val loop = ((raw - .03f) % (2f * span) + 2f * span) % (2f * span)
        val fx = .03f + (if (loop <= span) loop else 2f * span - loop) - clarity * 1.1f
        val alpha = (1f - clarity)
        if (alpha <= 0f || fx < -.05f) return@repeat
        val px = fx * w
        val py = surfW(px) - 2f * dp + sin(phase * 1.15f + bob) * 1.6f * dp
        for (k in -1..1) { // three squashed lobes read as one ragged raft
            val rx = r * (if (k == 0) 1f else .66f)
            val ry = rx * flat
            val cx = px + k * r * .78f
            val cy = py - ry * .5f
            drawOval(
                Color(0xFF5C6B70).copy(alpha = .88f * alpha),
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f),
            )
        }
        for (k in 0..4) { // bubbles catching the moon — this is what reads as scum, not cloud
            val bx = px + (fr.next() - .5f) * r * 2.1f
            val by = py - fr.next() * r * flat * 1.1f - 1f * dp
            drawCircle(
                Color(0xFFA3B6BB).copy(alpha = .50f * alpha),
                (1f + fr.next() * 2.4f) * dp,
                Offset(bx, by),
            )
        }
        if (clarity in 0.01f..0.99f) { // it doesn't just vanish — it goes up as light
            for (k in 0..4) {
                drawCircle(
                    Color(0xFFEAF6F4).copy(alpha = ((1f - clarity) * .75f).coerceIn(0f, 1f)),
                    (2.1f * (1f - clarity) + .5f) * dp,
                    Offset(px + (k - 2) * 8f * dp, py - clarity * 52f * dp - k * 4f * dp),
                )
            }
        }
    }

    // ---- crest of the wave that takes over, plus its spray ----
    if (wash > 0f && crestX > -crestW && crestX < w + crestW) {
        val crest = Path()
        val x0 = (crestX - crestW).coerceAtLeast(0f)
        val x1 = (crestX + crestW).coerceAtMost(w)
        var x = x0
        crest.moveTo(x0, surfW(x0))
        while (x <= x1) { crest.lineTo(x, surfW(x)); x += 2f }
        drawPath(crest, Color(0xFFEAF6F4).copy(alpha = .95f), style = Stroke(width = 3.2f * dp))
        val pr = Lcg(5)
        repeat(26) {
            val sx = crestX - 70f * dp + pr.next() * 190f * dp
            val sy = surfW(sx) - pr.next() * 30f * dp
            drawCircle(
                Color(0xFFEAF6F4).copy(alpha = (.2f + pr.next() * .55f).coerceIn(0f, 1f)),
                (pr.next() * 2.1f + .5f) * dp,
                Offset(sx, sy),
            )
        }
    }

    // ---- ripples from taps ----
    for (r in ripples) {
        val cx = r.xFrac * w
        val cy = surfW(cx)
        val rx = r.age * 72f * dp
        val ry = r.age * 11f * dp
        drawOval(
            Color(0xFFEAF6F4).copy(alpha = ((1f - r.age) * .5f).coerceIn(0f, 1f)),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 1.2f * dp),
        )
    }
}
