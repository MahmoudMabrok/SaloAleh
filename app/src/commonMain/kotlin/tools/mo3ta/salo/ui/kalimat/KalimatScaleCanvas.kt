package tools.mo3ta.salo.ui.kalimat

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The hadith, drawn: "…four words; if they were weighed against all you have said since this
 * morning, they would outweigh them."
 *
 * The right pan fills on its own with the dim motes of everything said since morning — it keeps
 * filling whether the user taps or not. Every tasbiha sends the four words up from the reciter into
 * the left pan, where each one stays readable for a moment and then settles into a lit coin, and the
 * beam swings over to them. Completing a round (the daily goal) tips the beam all the way, flashes
 * "لوَزَنَتْهُنَّ", and empties the words pan so the next round is weighed afresh — against a day pile
 * that is by then heavier.
 *
 * Nothing here is driven by the count in composition. The scale runs off its own frame clock, and a
 * tasbiha arrives as a signal on [tasbihSignal] collected inside this composable — so a tap mutates
 * [ScaleState] and invalidates the draw phase only, never composition. [countSignal] is read at that
 * moment (and while the user has not tapped yet) purely to know where in the round we are, so the
 * pan and the progress ring can never disagree. See the challenge-screen rule in CLAUDE.md.
 */
@Composable
fun KalimatScaleCanvas(
    words: List<String>,
    dayLabel: String,
    outweighLabel: String,
    tasbihSignal: StateFlow<Int>,
    countSignal: StateFlow<Int>,
    goal: Int,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val measurer = rememberTextMeasurer()

    // Measured once per string, then re-drawn every frame at varying alpha/position. Measuring per
    // word per frame would be the most expensive thing on the screen.
    val wordLayouts = remember(measurer, words) { words.map { measurer.measure(it, WORD_STYLE) } }
    val dayLayout = remember(measurer, dayLabel) { measurer.measure(dayLabel, LABEL_STYLE) }
    val outweighLayout = remember(measurer, outweighLabel) { measurer.measure(outweighLabel, OUTWEIGH_STYLE) }

    val scale = remember(words.size, goal) {
        ScaleState(wordCount = words.size.coerceAtLeast(1), goal = goal).apply { seedDay() }
    }
    var clock by remember { mutableFloatStateOf(0f) }

    // Until the first tasbiha of this session, the pan is seated from today's count — reopening the
    // screen mid-round shows the words already weighed. Deliberately never launches: the remote
    // baseline sync on screen enter moves the count, and it must not fire the animation.
    LaunchedEffect(scale, countSignal) {
        countSignal.collect { count -> if (!scale.hasLaunched) scale.seat(count) }
    }

    LaunchedEffect(scale, tasbihSignal) {
        // Serial 0 is "no tasbiha yet this session" — only real taps and manual entries bump it.
        tasbihSignal.collect { serial -> if (serial > 0) scale.onTasbih(countSignal.value) }
    }

    LaunchedEffect(scale, reduceMotion) {
        if (reduceMotion) {
            scale.settle()
            clock = scale.clock
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) scale.step(min((now - last) / 1_000_000_000f, MAX_FRAME_SECONDS))
                last = now
                clock = scale.clock
            }
        }
    }

    Canvas(modifier) {
        // `clock` is read here, inside the draw lambda, on purpose: that read is what re-issues a
        // frame without ever invalidating composition.
        drawScale(scale, clock, wordLayouts, dayLayout, outweighLayout)
    }
}

// ---- palette (rose over twilight, matching [KalimatColors]) ----
private val ROSE = Color(0xFFE79BB6)
private val ROSE_DEEP = Color(0xFFB84E76)
private val GLOW = Color(0xFFFFEAF1)
private val PALE = Color(0xFFFFFFFF)

private val WORD_STYLE = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
private val LABEL_STYLE = TextStyle(fontSize = 9.sp)
private val OUTWEIGH_STYLE = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)

private const val MAX_FRAME_SECONDS = 0.05f

/** Beam rest angle — negative leans toward the day pile. Positive is the four-words pan going down. */
private const val REST_ANGLE = -0.20f
private const val TIP_ANGLE = 0.26f

/** Motes of everything said since morning. Capped, and they keep arriving on their own. */
private const val MAX_DAY_MOTES = 70
private const val SEED_DAY_MOTES = 16
private const val DAY_MOTE_MIN_GAP = 0.9f
private const val DAY_MOTE_MAX_GAP = 2.2f

/** Words resting in the pan at once — a very large daily goal must not fill the canvas. */
private const val MAX_SETTLED = 40

/** Seconds a landed word stays readable, then how long it takes to melt into the heap. */
private const val LIT_SECONDS = 3.2f
private const val MELT_SECONDS = 1.4f

private const val WORD_STAGGER = 0.16f
private const val WORD_FLIGHT_SECONDS = 0.9f

/** How long the completed round stays on the scale before the pan is emptied for the next one. */
private const val ROUND_HOLD_SECONDS = 1.6f

private const val TWO_PI = (2 * PI).toFloat()

private class DayMote(val dx: Float, val dy: Float, val radius: Float, val phase: Float)

private class FlyingWord(
    /** Index into the word list. */
    val word: Int,
    /** Row this word will occupy in the pan while it is still readable. */
    val row: Int,
    /** Counts down before this word leaves the reciter, so the four go up one after the other. */
    var delay: Float,
    val launchX: Float,
    var progress: Float = 0f,
)

private class SettledWord(
    val word: Int,
    val row: Int,
    val dx: Float,
    val dy: Float,
    val phase: Float,
    var age: Float = 0f,
)

private class ScaleState(private val wordCount: Int, private val goal: Int) {
    var clock = 0f
        private set
    var flash = 0f
        private set
    var hasLaunched = false
        private set

    val day = mutableListOf<DayMote>()
    val flying = mutableListOf<FlyingWord>()
    val settled = mutableListOf<SettledWord>()

    private var angle = REST_ANGLE
    private var velocity = 0f
    private var roundPosition = 0
    private var clearAt = -1f
    private var nextDayAt = DAY_MOTE_MIN_GAP

    val beamAngle: Float get() = angle

    /** The day pile is already there when the screen opens — the morning's dhikr did not start now. */
    fun seedDay() {
        repeat(SEED_DAY_MOTES) { day += dayMote() }
    }

    private fun dayMote(): DayMote {
        val a = Random.nextFloat() * TWO_PI
        val r = Random.nextFloat()
        return DayMote(
            dx = cos(a) * r * 20f,
            dy = -Random.nextFloat() * 13f,
            radius = Random.nextFloat() * 1.1f + 1.1f,
            phase = Random.nextFloat() * TWO_PI,
        )
    }

    private fun settledWord(word: Int, row: Int) = SettledWord(
        word = word,
        row = row,
        dx = (Random.nextFloat() - 0.5f) * 30f,
        dy = -Random.nextFloat() * 12f,
        phase = Random.nextFloat() * TWO_PI,
    )

    /**
     * Fill the pan to match [count]'s position in the current round, with no flight and no
     * completion beat — used only before the user's first tasbiha of the session.
     */
    fun seat(count: Int) {
        if (hasLaunched) return
        val done = if (goal > 0) count % goal else 0
        if (done == roundPosition && settled.size == done * wordCount) return
        roundPosition = done
        settled.clear()
        flying.clear()
        repeat(done * wordCount) { i ->
            settled += settledWord(i % wordCount, i % wordCount).also { it.age = LIT_SECONDS + MELT_SECONDS }
        }
        angle = targetAngle()
        velocity = 0f
    }

    /** One tasbiha: the four words leave the reciter for the pan, one after the other. */
    fun onTasbih(count: Int) {
        hasLaunched = true
        // Position within the round is taken from the real count so the pan and the progress ring
        // can never drift — a manual entry that jumps the count by ten lands on the same round
        // position the ring shows.
        roundPosition = when {
            goal <= 0 -> roundPosition + 1
            count > 0 -> (count - 1) % goal + 1
            else -> if (roundPosition >= goal) 1 else roundPosition + 1
        }
        // A tasbiha during the hold cuts it short: the previous round is over, weigh the new one.
        if (clearAt > 0f) emptyWordsPan()
        repeat(wordCount) { i ->
            flying += FlyingWord(
                word = i,
                row = i,
                delay = i * WORD_STAGGER,
                launchX = (Random.nextFloat() - 0.5f) * 48f,
            )
        }
    }

    private fun emptyWordsPan() {
        settled.clear()
        clearAt = -1f
    }

    private fun land(word: FlyingWord) {
        settled += settledWord(word.word, word.row)
        while (settled.size > MAX_SETTLED) settled.removeAt(0)
        // The round is complete once its last word is in the pan.
        if (goal > 0 && roundPosition >= goal && flying.isEmpty()) {
            flash = 1f
            clearAt = clock + ROUND_HOLD_SECONDS
        }
    }

    fun step(dt: Float) {
        clock += dt
        flash = (flash - dt * 0.7f).coerceAtLeast(0f)

        nextDayAt -= dt
        if (nextDayAt <= 0f) {
            if (day.size < MAX_DAY_MOTES) day += dayMote()
            nextDayAt = DAY_MOTE_MIN_GAP + Random.nextFloat() * (DAY_MOTE_MAX_GAP - DAY_MOTE_MIN_GAP)
        }

        for (i in flying.indices.reversed()) {
            val w = flying[i]
            if (w.delay > 0f) {
                w.delay -= dt
                continue
            }
            w.progress += dt / WORD_FLIGHT_SECONDS
            if (w.progress >= 1f) {
                flying.removeAt(i)
                land(w)
            }
        }
        for (w in settled) w.age += dt

        if (clearAt > 0f && clock >= clearAt) {
            // The reset: the words are taken up, the pan is empty, and the next round is weighed
            // against a day pile that has kept growing.
            emptyWordsPan()
            roundPosition = 0
        }

        val target = targetAngle()
        velocity += (target - angle) * 26f * dt
        velocity *= 1f - min(1f, 4.6f * dt)
        angle += velocity * dt
    }

    private fun targetAngle(): Float = if (settled.isEmpty() && flying.isEmpty()) {
        REST_ANGLE - min(day.size, MAX_DAY_MOTES) * 0.0011f
    } else {
        TIP_ANGLE + min(settled.size, 12) * 0.004f + flash * 0.12f
    }

    /** Compose a still, settled frame for reduced-motion users — no clock, no repaint. */
    fun settle() {
        repeat(200) { step(1f / 60f) }
    }
}

private fun easeOutCubic(t: Float): Float {
    val inv = 1f - t
    return 1f - inv * inv * inv
}

private fun DrawScope.drawScale(
    scale: ScaleState,
    clock: Float,
    wordLayouts: List<TextLayoutResult>,
    dayLayout: TextLayoutResult,
    outweighLayout: TextLayoutResult,
) {
    if (wordLayouts.isEmpty()) return
    val d = density
    val cx = size.width / 2f
    val pivotY = size.height * 0.20f
    val beamHalf = min(size.width * 0.32f, 132f * d)
    val panDrop = min(size.height * 0.30f, 62f * d)
    val angle = scale.beamAngle
    val ca = cos(angle)
    val sa = sin(angle)

    // Left = the four words, right = everything said since morning.
    val leftEnd = Offset(cx - beamHalf * ca, pivotY + beamHalf * sa)
    val rightEnd = Offset(cx + beamHalf * ca, pivotY - beamHalf * sa)
    val wordsPan = Offset(leftEnd.x, leftEnd.y + panDrop)
    val dayPan = Offset(rightEnd.x, rightEnd.y + panDrop)

    drawColumn(cx, pivotY, d)
    drawBeam(leftEnd, rightEnd, cx, pivotY, d)
    drawChain(leftEnd, wordsPan, d)
    drawChain(rightEnd, dayPan, d)
    drawPan(dayPan, d, PALE.copy(alpha = 0.24f))
    drawPan(wordsPan, d, ROSE.copy(alpha = 0.55f))

    drawDayPile(scale, dayPan, clock, d)
    drawDayLabel(dayPan, dayLayout, d)
    drawWordsPan(scale, wordsPan, clock, d, wordLayouts)
    drawFlight(scale, cx, wordsPan, d, wordLayouts)
    if (scale.flash > 0.01f) drawOutweigh(scale, wordsPan, outweighLayout, d)
}

private fun DrawScope.drawColumn(cx: Float, pivotY: Float, d: Float) {
    val base = size.height * 0.9f
    drawLine(
        color = ROSE.copy(alpha = 0.26f),
        start = Offset(cx, pivotY),
        end = Offset(cx, base),
        strokeWidth = 2.4f * d,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ROSE.copy(alpha = 0.26f),
        start = Offset(cx - 17f * d, base),
        end = Offset(cx + 17f * d, base),
        strokeWidth = 2.4f * d,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawBeam(leftEnd: Offset, rightEnd: Offset, cx: Float, pivotY: Float, d: Float) {
    drawLine(
        color = PALE.copy(alpha = 0.42f),
        start = leftEnd,
        end = rightEnd,
        strokeWidth = 2.4f * d,
        cap = StrokeCap.Round,
    )
    drawCircle(color = ROSE.copy(alpha = 0.85f), radius = 3.4f * d, center = Offset(cx, pivotY))
}

private fun DrawScope.drawChain(end: Offset, pan: Offset, d: Float) {
    drawLine(
        color = PALE.copy(alpha = 0.18f),
        start = end,
        end = pan,
        strokeWidth = 1f * d,
    )
}

private fun DrawScope.drawPan(center: Offset, d: Float, color: Color) {
    val r = 23f * d
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 1.8f * d),
    )
    drawLine(
        color = color,
        start = Offset(center.x - r, center.y),
        end = Offset(center.x + r, center.y),
        strokeWidth = 1.8f * d,
    )
}

private fun DrawScope.drawDayPile(scale: ScaleState, pan: Offset, clock: Float, d: Float) {
    for (m in scale.day) {
        drawCircle(
            color = PALE.copy(alpha = 0.2f + 0.1f * sin(clock * 1.6f + m.phase)),
            radius = m.radius * d,
            center = Offset(pan.x + m.dx * d, pan.y + m.dy * d),
        )
    }
}

private fun DrawScope.drawDayLabel(pan: Offset, layout: TextLayoutResult, d: Float) {
    drawText(
        textLayoutResult = layout,
        color = PALE.copy(alpha = 0.34f),
        topLeft = Offset(
            clampX(pan.x - layout.size.width / 2f, layout.size.width.toFloat()),
            pan.y + 8f * d,
        ),
    )
}

private fun DrawScope.drawWordsPan(
    scale: ScaleState,
    pan: Offset,
    clock: Float,
    d: Float,
    wordLayouts: List<TextLayoutResult>,
) {
    for (w in scale.settled) {
        // A landed word is readable first, then melts into the heap: the text slides down into its
        // resting spot as a coin takes over.
        val lit = (1f - ((w.age - LIT_SECONDS).coerceAtLeast(0f) / MELT_SECONDS)).coerceIn(0f, 1f)
        val coin = Offset(pan.x + w.dx * d, pan.y + w.dy * d)
        val layout = wordLayouts[w.word % wordLayouts.size]

        if (lit > 0.02f) {
            val textY = pan.y - (26f + w.row * 12f) * d
            val y = textY + (coin.y - textY) * (1f - lit)
            drawText(
                textLayoutResult = layout,
                color = GLOW.copy(alpha = lit * 0.92f),
                topLeft = Offset(
                    clampX(pan.x - layout.size.width / 2f, layout.size.width.toFloat()),
                    y - layout.size.height / 2f,
                ),
            )
        }
        if (lit < 1f) {
            val settleIn = 1f - lit
            val pulse = 0.55f + 0.25f * sin(clock * 2f + w.phase)
            drawCircle(
                color = ROSE_DEEP.copy(alpha = 0.18f * settleIn),
                radius = 6.4f * d,
                center = coin,
            )
            drawCircle(
                color = GLOW.copy(alpha = pulse * settleIn),
                radius = 3f * d,
                center = coin,
            )
        }
    }
}

private fun DrawScope.drawFlight(
    scale: ScaleState,
    cx: Float,
    pan: Offset,
    d: Float,
    wordLayouts: List<TextLayoutResult>,
) {
    val reciterY = size.height * 0.95f
    for (w in scale.flying) {
        if (w.delay > 0f) continue
        val p = easeOutCubic(w.progress.coerceIn(0f, 1f))
        val startX = cx + w.launchX * d
        val x = startX + (pan.x - startX) * p + sin(p * PI.toFloat()) * 26f * d
        val y = reciterY + (pan.y - reciterY) * p
        val alpha = min(1f, w.progress * 3f) * (1f - p * 0.15f)
        val layout = wordLayouts[w.word % wordLayouts.size]

        drawCircle(color = ROSE_DEEP.copy(alpha = 0.16f * alpha), radius = 9f * d, center = Offset(x, y))
        drawCircle(color = GLOW.copy(alpha = 0.9f * alpha), radius = 3.2f * d, center = Offset(x, y))
        drawText(
            textLayoutResult = layout,
            color = GLOW.copy(alpha = alpha * 0.85f),
            topLeft = Offset(
                clampX(x - layout.size.width / 2f, layout.size.width.toFloat()),
                y - 15f * d - layout.size.height / 2f,
            ),
        )
    }
}

/** The moment the round completes: the pan flares and the hadith's own verdict is spoken over it. */
private fun DrawScope.drawOutweigh(
    scale: ScaleState,
    pan: Offset,
    layout: TextLayoutResult,
    d: Float,
) {
    val a = scale.flash
    drawRect(
        brush = Brush.radialGradient(
            0f to ROSE.copy(alpha = 0.3f * a),
            1f to Color.Transparent,
            center = pan,
            radius = 72f * d,
        ),
        topLeft = Offset(pan.x - 72f * d, pan.y - 72f * d),
        size = Size(144f * d, 144f * d),
    )
    drawText(
        textLayoutResult = layout,
        color = GLOW.copy(alpha = a),
        topLeft = Offset(
            clampX(size.width / 2f - layout.size.width / 2f, layout.size.width.toFloat()),
            size.height * 0.06f,
        ),
    )
}

/** Keep a measured line inside the canvas — the words pan sits near the edge when the beam tips. */
private fun DrawScope.clampX(x: Float, width: Float): Float =
    x.coerceIn(2f, (size.width - width - 2f).coerceAtLeast(2f))
