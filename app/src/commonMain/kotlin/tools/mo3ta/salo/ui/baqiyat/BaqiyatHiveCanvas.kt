package tools.mo3ta.salo.ui.baqiyat

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The hadith, drawn: "…they circle around the Throne, they have a hum like the hum of bees,
 * they mention their companion."
 *
 * Every completed cycle launches one spark per dhikr from the reciter at the foot of the canvas.
 * Each spark carries its dhikr *and the reciter's own name* (nickname, or the last six of the uid
 * — whatever the leaderboard shows), spirals up, and joins the swarm orbiting the Throne arc.
 * The name is the loud part of the pair: that is the whole point of the narration.
 *
 * Nothing here is driven by the count. The swarm runs off its own frame clock, and a new cycle
 * arrives as a signal on [cycleSignal] collected inside this composable — so a tap never
 * recomposes the screen (or even this composable): it mutates [HiveState] and invalidates the
 * draw phase only. See the challenge-screen rule in CLAUDE.md.
 */
@Composable
fun BaqiyatHiveCanvas(
    playerName: String,
    phrases: List<String>,
    cycleSignal: StateFlow<Int>,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val measurer = rememberTextMeasurer()

    // Measured once per string, then re-drawn every frame at varying alpha/position. Measuring
    // per mote per frame would be the single most expensive thing on the screen.
    val phraseLayouts = remember(measurer, phrases) {
        phrases.map { measurer.measure(it, PHRASE_STYLE) }
    }
    val nameLayout = remember(measurer, playerName) { measurer.measure(playerName, NAME_STYLE) }

    val hive = remember(phrases.size) { HiveState().apply { seedAmbient(phrases.size) } }
    var clock by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(hive, cycleSignal) {
        // Serial 0 is "no cycle yet this session" — only real taps/manual entries bump it.
        cycleSignal.collect { serial -> if (serial > 0) hive.spawnCycle(phrases.size) }
    }

    LaunchedEffect(hive, reduceMotion) {
        if (reduceMotion) {
            hive.settle()
            clock = hive.clock
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) hive.step(min((now - last) / 1_000_000_000f, MAX_FRAME_SECONDS))
                last = now
                clock = hive.clock
            }
        }
    }

    Canvas(modifier) {
        // `clock` is read here, inside the draw lambda, on purpose: that read is what re-issues
        // a frame without ever invalidating composition.
        drawHive(hive, clock, phraseLayouts, nameLayout)
    }
}

// ---- palette (honey over the night sky) ----
private val HONEY = Color(0xFFE8A33D)
private val GOLD = Color(0xFFF5D97A)
private val GOLD_WARM = Color(0xFFFFE9A5)
private val GLOW = Color(0xFFFFF6D2)

private val PHRASE_STYLE = TextStyle(fontSize = 11.sp)
private val NAME_STYLE = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold)

/** Motes on screen at once. The oldest fade out past this, so a long session never grows unbounded. */
private const val MAX_MOTES = 110
private const val AMBIENT_MOTES = 30

/** Seconds a fresh spark stays readable, then how long it takes to settle into a plain dot. */
private const val LIT_SECONDS = 7f
private const val FADE_SECONDS = 5f

private const val MAX_FRAME_SECONDS = 0.05f

/** Layout constants are in dp-units; multiplied by [DrawScope.density] at draw time. */
private const val THRONE_RX = 122f
private const val THRONE_RY = 42f
private const val HALO_RADIUS = 170f
private const val ORBIT_FLATTEN = 0.5f

private class Mote(
    var angle: Float,
    val radius: Float,
    val angularVelocity: Float,
    val wobble: Float,
    val dotSize: Float,
    /** Index into the phrase list, or -1 for a wordless spark. */
    val phrase: Int,
    var rise: Float,
    val launchX: Float,
    var age: Float,
)

private class Ring(var radius: Float, var alpha: Float)

private class HiveState {
    val motes = mutableListOf<Mote>()
    val rings = mutableListOf<Ring>()
    var clock = 0f
        private set
    var pulse = 0f
        private set

    private fun mote(rising: Boolean, phrase: Int) = Mote(
        angle = Random.nextFloat() * TWO_PI,
        radius = Random.nextFloat() * 82f + 36f,
        angularVelocity = (Random.nextFloat() * 0.22f + 0.24f) * if (Random.nextFloat() < 0.86f) 1f else -1f,
        wobble = Random.nextFloat() * TWO_PI,
        dotSize = Random.nextFloat() * 1.2f + 1.1f,
        phrase = phrase,
        rise = if (rising) 0f else 1f,
        launchX = Random.nextFloat() * 112f - 56f,
        age = 0f,
    )

    /** A swarm that is already circling when the screen opens — the dhikr did not start just now. */
    fun seedAmbient(phraseCount: Int) {
        repeat(AMBIENT_MOTES) { i ->
            val phrase = if (phraseCount > 0 && i % 7 == 0) i % phraseCount else -1
            motes += mote(rising = false, phrase = phrase).also { it.age = Random.nextFloat() * 6f + 3f }
        }
    }

    /** One completed cycle: one named spark per dhikr, a wordless one, and a ring of hum. */
    fun spawnCycle(phraseCount: Int) {
        repeat(phraseCount) { motes += mote(rising = true, phrase = it) }
        motes += mote(rising = true, phrase = -1)
        rings += Ring(radius = 12f, alpha = 0.55f)
        pulse = 1f
        while (motes.size > MAX_MOTES) motes.removeAt(0)
    }

    fun step(dt: Float) {
        clock += dt
        pulse = (pulse - dt * 2.1f).coerceAtLeast(0f)
        for (m in motes) {
            m.angle += m.angularVelocity * dt
            m.age += dt
            if (m.rise < 1f) m.rise = min(1f, m.rise + dt * 0.8f)
        }
        for (i in rings.indices.reversed()) {
            val ring = rings[i]
            ring.radius += dt * 118f
            ring.alpha -= dt * 0.6f
            if (ring.alpha <= 0f) rings.removeAt(i)
        }
    }

    /** Compose a still, settled frame for reduced-motion users — no clock, no repaint. */
    fun settle() {
        repeat(300) { step(1f / 60f) }
    }
}

private const val TWO_PI = (2 * PI).toFloat()

private fun easeOutCubic(t: Float): Float {
    val inv = 1f - t
    return 1f - inv * inv * inv
}

private fun DrawScope.drawHive(
    hive: HiveState,
    clock: Float,
    phraseLayouts: List<TextLayoutResult>,
    nameLayout: TextLayoutResult,
) {
    val d = density
    val cx = size.width / 2f
    val cy = size.height * 0.40f

    drawHalo(cx, cy, d, hive.pulse)
    drawThrone(cx, cy, d, hive.pulse)
    drawHum(hive, cx, cy, d)
    drawSwarm(hive, clock, cx, cy, d, phraseLayouts, nameLayout)
    drawReciter(cx, d, nameLayout)
}

private fun DrawScope.drawHalo(cx: Float, cy: Float, d: Float, pulse: Float) {
    drawRect(
        brush = Brush.radialGradient(
            0f to HONEY.copy(alpha = 0.17f + pulse * 0.1f),
            0.5f to HONEY.copy(alpha = 0.05f),
            1f to Color.Transparent,
            center = Offset(cx, cy),
            radius = HALO_RADIUS * d,
        ),
    )
}

private fun DrawScope.drawThrone(cx: Float, cy: Float, d: Float, pulse: Float) {
    // Two arcs, not a closed ellipse: the Throne is suggested, never depicted.
    drawArc(
        color = GOLD.copy(alpha = 0.48f + pulse * 0.35f),
        startAngle = 192.6f,
        sweepAngle = 154.8f,
        useCenter = false,
        topLeft = Offset(cx - THRONE_RX * d, cy - THRONE_RY * d),
        size = Size(THRONE_RX * 2 * d, THRONE_RY * 2 * d),
        style = Stroke(width = 2f * d),
    )
    drawArc(
        color = GOLD.copy(alpha = 0.15f),
        startAngle = 185.4f,
        sweepAngle = 169.2f,
        useCenter = false,
        topLeft = Offset(cx - 139f * d, cy - 50f * d),
        size = Size(139f * 2 * d, 50f * 2 * d),
        style = Stroke(width = 1f * d),
    )
}

private fun DrawScope.drawHum(hive: HiveState, cx: Float, cy: Float, d: Float) {
    for (ring in hive.rings) {
        val rx = ring.radius * d
        val ry = ring.radius * ORBIT_FLATTEN * d
        drawOval(
            color = HONEY.copy(alpha = ring.alpha * 0.38f),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2, ry * 2),
            style = Stroke(width = 1f * d),
        )
    }
}

private fun DrawScope.drawSwarm(
    hive: HiveState,
    clock: Float,
    cx: Float,
    cy: Float,
    d: Float,
    phraseLayouts: List<TextLayoutResult>,
    nameLayout: TextLayoutResult,
) {
    val reciterY = size.height * 0.93f
    for (m in hive.motes) {
        val orbitX = cx + cos(m.angle) * m.radius * d
        val orbitY = cy + sin(m.angle) * m.radius * ORBIT_FLATTEN * d +
            sin(clock * 6f + m.wobble) * 2.4f * d

        var x = orbitX
        var y = orbitY
        var alpha = 1f
        if (m.rise < 1f) {
            // Launched from the reciter, curling outward before it falls into orbit.
            val p = easeOutCubic(m.rise)
            val startX = cx + m.launchX * d
            x = startX + (orbitX - startX) * p +
                sin(p * PI.toFloat()) * (if (m.launchX > 0f) 32f else -32f) * d
            y = reciterY + (orbitY - reciterY) * p
            alpha = min(1f, m.rise * 2.4f)
        }

        val depth = 0.62f + 0.38f * (sin(m.angle) + 1f) / 2f
        val lit = if (m.phrase < 0 || m.phrase >= phraseLayouts.size) 0f else {
            (1f - ((m.age - LIT_SECONDS).coerceAtLeast(0f) / FADE_SECONDS)).coerceIn(0f, 1f)
        }

        if (lit > 0.02f) {
            val phrase = phraseLayouts[m.phrase]
            drawText(
                textLayoutResult = phrase,
                color = GLOW.copy(alpha = alpha * lit * (0.52f + 0.28f * depth)),
                topLeft = Offset(
                    x - phrase.size.width / 2f,
                    y - 8f * d - phrase.size.height / 2f,
                ),
            )
            // The name barely dims with depth — it has to stay readable all the way round.
            drawText(
                textLayoutResult = nameLayout,
                color = GOLD_WARM.copy(alpha = alpha * lit * (0.86f + 0.14f * depth)),
                topLeft = Offset(
                    x - nameLayout.size.width / 2f,
                    y + 8f * d - nameLayout.size.height / 2f,
                ),
            )
        }
        if (lit < 1f) {
            val dot = m.dotSize * depth * d
            drawCircle(
                color = GOLD.copy(alpha = alpha * depth * 0.82f * (1f - lit)),
                radius = dot,
                center = Offset(x, y),
            )
            if (m.phrase < 0) {
                drawCircle(
                    color = HONEY.copy(alpha = alpha * 0.12f),
                    radius = dot * 3.1f,
                    center = Offset(x, y),
                )
            }
        }
    }
}

/** The reciter: the source of every spark, wearing the same name the sparks carry up. */
private fun DrawScope.drawReciter(cx: Float, d: Float, nameLayout: TextLayoutResult) {
    val y = size.height * 0.93f
    val glowRadius = 62f * d
    drawRect(
        brush = Brush.radialGradient(
            0f to HONEY.copy(alpha = 0.26f),
            1f to Color.Transparent,
            center = Offset(cx, y),
            radius = glowRadius,
        ),
        topLeft = Offset(0f, y - glowRadius),
        size = Size(size.width, glowRadius + 8f * d),
    )

    val pillW = nameLayout.size.width + 26f * d
    val pillH = nameLayout.size.height + 10f * d
    val topLeft = Offset(cx - pillW / 2f, y - pillH / 2f)
    val corner = CornerRadius(pillH / 2f, pillH / 2f)
    drawRoundRect(color = HONEY.copy(alpha = 0.16f), topLeft = topLeft, size = Size(pillW, pillH), cornerRadius = corner)
    drawRoundRect(
        color = HONEY.copy(alpha = 0.62f),
        topLeft = topLeft,
        size = Size(pillW, pillH),
        cornerRadius = corner,
        style = Stroke(width = 1f * d),
    )
    drawText(
        textLayoutResult = nameLayout,
        color = GOLD_WARM.copy(alpha = 0.96f),
        topLeft = Offset(cx - nameLayout.size.width / 2f, y - nameLayout.size.height / 2f),
    )
}
