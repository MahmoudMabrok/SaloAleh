package tools.mo3ta.salo.ui.ghars

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ---- palette (dawn over the grove) ----
private val PALM = Color(0xFF0B2A22)
private val FROND = Color(0xFF3E8F63)
private val DATE = Color(0xFFC4762A)
private val GOLD = Color(0xFFF5D97A)

// Sky stops as fractions of the sky band (0 = top of screen, 1 = horizon).
private val SKY_STOPS: Array<Pair<Float, Color>> = arrayOf(
    0.00f to Color(0xFF14103A), // NightIndigo
    0.30f to Color(0xFF3B2352),
    0.62f to Color(0xFF8C4A54), // DawnRose
    0.88f to Color(0xFFE09A62), // HorizonApricot
    1.00f to Color(0xFFFFC98A),
)

private const val BASE_H = 168f

/**
 * The whole signature: a grove drawn palm by palm at first light. Only Path/arc/oval and
 * linear/radial Brush — no blur anywhere — so it ports 1:1 from the HTML mockup and the
 * render cost stays a constant (never more than [GROVE_SIZE] palms on screen).
 */
@Composable
fun PalmGroveCanvas(
    count: Int,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val grow = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val recede = remember { Animatable(0f) }
    var timePhase by remember { mutableFloatStateOf(0f) }

    // Every tap sprouts the newest palm and bursts the soil; a grove-completing tap
    // also sends the finished grove receding to the horizon.
    LaunchedEffect(count) {
        if (count <= 0) return@LaunchedEffect
        if (reduceMotion) {
            grow.snapTo(1f); burst.snapTo(0f); recede.snapTo(0f)
            return@LaunchedEffect
        }
        launch { grow.snapTo(0f); grow.animateTo(1f, tween(620, easing = LinearEasing)) }
        launch { burst.snapTo(1f); burst.animateTo(0f, tween(700, easing = LinearEasing)) }
        if (completesGrove(count)) {
            launch { recede.snapTo(1f); recede.animateTo(0f, tween(1200, easing = LinearEasing)) }
        }
    }

    // Continuous sway clock (delta-time driven, clamped — never per-frame constants).
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        var previous = 0L
        while (true) withInfiniteAnimationFrameNanos { now ->
            if (previous != 0L) {
                val dt = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                timePhase += dt
            }
            previous = now
        }
    }

    Canvas(modifier) {
        drawGrove(count, grow.value, burst.value, recede.value, timePhase, reduceMotion)
    }
}

private fun mix(a: Color, b: Color, t: Float): Color = lerp(a, b, t.coerceIn(0f, 1f))

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

private fun skyAt(t: Float): Color {
    val tc = t.coerceIn(0f, 1f)
    if (tc <= SKY_STOPS.first().first) return SKY_STOPS.first().second
    for (i in 1 until SKY_STOPS.size) {
        if (tc <= SKY_STOPS[i].first) {
            val (p0, c0) = SKY_STOPS[i - 1]
            val (p1, c1) = SKY_STOPS[i]
            val p = (tc - p0) / (p1 - p0)
            return lerp(c0, c1, p)
        }
    }
    return SKY_STOPS.last().second
}

private fun DrawScope.drawGrove(
    count: Int,
    grow: Float,
    burst: Float,
    recede: Float,
    timePhase: Float,
    reduceMotion: Boolean,
) {
    val w = size.width
    val h = size.height
    val hz = h * 0.545f            // horizon line
    val nr = h - 16f               // near/foreground soil line (the sand sheet sits below)
    val margin = w * 0.04f

    // ---- sky ----
    drawRect(
        Brush.verticalGradient(*SKY_STOPS, startY = 0f, endY = hz),
        topLeft = Offset(0f, 0f),
        size = Size(w, hz + 1f),
    )

    // ---- stars, fading with dawn ----
    run {
        for (i in 0 until 26) {
            val r = Mulberry32((((i + 77).toLong() * 99991L) and 0xFFFFFFFFL).toInt())
            val sx = r.next() * w
            val sy = r.next() * hz * 0.52f
            val sa = ((0.10f + r.next() * 0.42f) * (1f - sy / (hz * 0.62f))).coerceAtLeast(0f)
            val rad = r.next() * 0.9f + 0.4f
            drawCircle(Color(0xFFFFF4DC).copy(alpha = sa), rad, Offset(sx, sy))
        }
    }

    // ---- sun bloom + disc (radial gradients only) ----
    drawRect(
        Brush.radialGradient(
            0f to Color(0xFFFFD696).copy(alpha = 0.55f),
            0.45f to Color(0xFFFFAF78).copy(alpha = 0.18f),
            1f to Color(0xFFFFAF78).copy(alpha = 0f),
            center = Offset(w * 0.5f, hz),
            radius = w * 0.62f,
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, hz + 1f),
    )
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFF3D8).copy(alpha = 0.95f),
            0.75f to Color(0xFFFFDEA8).copy(alpha = 0.75f),
            1f to Color(0xFFFFDEA8).copy(alpha = 0f),
            center = Offset(w * 0.5f, hz),
            radius = 26f,
        ),
        26f,
        Offset(w * 0.5f, hz),
    )

    // ---- ground: lit at the horizon, falling into shadow in the foreground ----
    drawRect(
        Brush.verticalGradient(
            0.00f to Color(0xFFC69E70),
            0.24f to Color(0xFF8C6646),
            0.62f to Color(0xFF563C2A),
            1.00f to Color(0xFF302119),
            startY = hz,
            endY = nr + 40f,
        ),
        topLeft = Offset(0f, hz),
        size = Size(w, h - hz),
    )

    val n = count
    val groves = completedGroves(n)
    val shown = shownPalms(n)
    val start = groveStartIndex(n)

    // ---- tree-line: every grove finished today, hazed onto the horizon as distant canopy ----
    if (groves > 0) {
        val dens = min(1f, log10(groves * 6f + 1f) / 2.0f)
        val mc = mix(PALM, skyAt(1f), 0.78f)
        val cnt = (14 + dens * 30).toInt()
        val step = w / cnt
        val baseY = hz + 2f
        val canopy = Path().apply {
            moveTo(-4f, baseY)
            for (i in 0..cnt) {
                val r2 = Mulberry32((((i + 301).toLong() * 40503L) and 0xFFFFFFFFL).toInt())
                val cxm = i * step
                val rad = step * (0.42f + r2.next() * 0.30f)
                val crown = (4.5f + r2.next() * 7.5f) * (0.6f + dens * 0.9f)
                lineTo(cxm - rad, baseY)
                quadraticBezierTo(cxm, baseY - crown * 2.1f, cxm + rad, baseY)
            }
            lineTo(w + 4f, baseY)
            close()
        }
        drawPath(canopy, mc.copy(alpha = 0.44f + 0.34f * dens))
        drawRect(
            mix(mc, Color.Black, 0.18f).copy(alpha = 0.5f),
            topLeft = Offset(0f, baseY - 2f),
            size = Size(w, 3.5f),
        )
    }

    // ---- band occupancy — row 0 always holds the most recent palms ----
    val take = rowOccupancy(shown)
    fun slotX(j: Int, band: Int): Float {
        val frac = palmXFraction(j, band, palmParams(start + j).jitter)
        return margin + frac * (w - 2f * margin)
    }
    fun drawBand(band: Int) {
        if (band >= take.size || take[band] == 0) return
        val by = hz + (nr - hz) * rowScale(band)
        val s = rowScale(band)
        val haze = rowHaze(band)
        val sky = skyAt(((by - 56f) / hz).coerceIn(0f, 1f))
        val lo = rowStart(take, shown, band)
        val hi = lo + take[band]
        for (j in lo until hi) {
            val p = palmParams(start + j)
            val px = slotX(j, band)
            val g = if (j == shown - 1) grow else 1f
            // soil shadow under the palm
            drawOval(
                Brush.radialGradient(
                    0f to Color(0xFF160C07).copy(alpha = 0.42f),
                    1f to Color(0xFF160C07).copy(alpha = 0f),
                    center = Offset(px, by),
                    radius = 18f * s,
                ),
                topLeft = Offset(px - 18f * s, by - 4.5f * s),
                size = Size(36f * s, 9f * s),
            )
            drawPalm(px, by, s, p, g, timePhase, haze, sky, reduceMotion)
        }
    }

    // ---- the grove just completed, receding into the tree-line (never erased, just distant) ----
    if (recede > 0f) {
        val rc = recede // 1 -> 0
        val a = min(1f, rc * 1.4f)
        val pStart = start - GROVE_SIZE
        val pTake = rowOccupancy(GROVE_SIZE)
        val paint = Paint().apply { alpha = a }
        drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), paint)
        for (band in ROW_CAPACITY.indices.reversed()) {
            val pby = hz + (nr - hz) * rowScale(band) * (0.10f + 0.90f * rc)
            val ps = rowScale(band) * (0.16f + 0.84f * rc)
            val phz = min(0.9f, (1f - HAZE_FALLOFF.pow(band)) + (1f - rc) * 0.75f)
            val psky = skyAt(((pby - 56f) / hz).coerceIn(0f, 1f))
            var plo = 0
            for (q in 0 until band) plo += pTake[q]
            for (j2 in plo until plo + pTake[band]) {
                val pp = palmParams(pStart + j2)
                drawPalm(slotX(j2, band), pby, ps, pp, 1f, timePhase, phz, psky, reduceMotion)
            }
        }
        drawContext.canvas.restore()
    }

    // far rows first, front row last
    for (band in take.indices.reversed()) drawBand(band)

    // ---- planting burst — expanding soil ring + light bloom ----
    if (burst > 0f && shown > 0) {
        val bx = slotX(shown - 1, 0)
        val bp = 1f - burst
        val rr = 10f + bp * 54f
        drawOval(
            Color(0xFFFFE0B0).copy(alpha = burst * 0.6f),
            topLeft = Offset(bx - rr, nr - rr * 0.28f),
            size = Size(2f * rr, 2f * rr * 0.28f),
            style = Stroke(1.5f),
        )
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFE2AA).copy(alpha = burst * 0.26f),
                1f to Color(0xFFFFE2AA).copy(alpha = 0f),
                center = Offset(bx, nr - 46f),
                radius = 96f,
            ),
            96f,
            Offset(bx, nr - 46f),
        )
    }

    // ---- foreground shadow — grounds the tasbeeh so it reads over the soil ----
    drawRect(
        Brush.verticalGradient(
            0.00f to Color(0xFF120B07).copy(alpha = 0f),
            0.62f to Color(0xFF120B07).copy(alpha = 0.42f),
            1.00f to Color(0xFF120B07).copy(alpha = 0.66f),
            startY = nr - 130f,
            endY = h,
        ),
        topLeft = Offset(0f, nr - 130f),
        size = Size(w, h - (nr - 130f)),
    )
}

/** One palm — a tapered trunk, arching fronds, and (on a grove-completing palm) date clusters. */
private fun DrawScope.drawPalm(
    x: Float,
    by: Float,
    s: Float,
    p: PalmParams,
    grow: Float,
    timePhase: Float,
    haze: Float,
    sky: Color,
    reduceMotion: Boolean,
) {
    if (grow <= 0.001f) return
    val e = easeOutCubic(grow)
    val h = BASE_H * s * p.heightScale * e
    val sway = if (reduceMotion) 0f else sin(timePhase * 0.7f + p.swayPhase) * 0.022f
    val lean = p.lean + sway

    val dark = mix(PALM, sky, haze)
    val lit = mix(FROND, sky, min(0.88f, haze + 0.06f))

    // trunk: tapered path along a bowed spine
    val tx = x + sin(lean) * h
    val ty = by - h * cos(lean)
    val cx = x + (tx - x) * 0.5f + p.trunkBow * h * 0.16f
    val cy = by - h * 0.5f
    val wB = max(0.8f, 3.9f * s)
    val wT = max(0.5f, 2.1f * s)
    val steps = 7
    val left = ArrayList<Offset>(steps + 1)
    val right = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val u = i.toFloat() / steps
        val iu = 1f - u
        val px = iu * iu * x + 2f * iu * u * cx + u * u * tx
        val py = iu * iu * by + 2f * iu * u * cy + u * u * ty
        val dx = 2f * iu * (cx - x) + 2f * u * (tx - cx)
        val dy = 2f * iu * (cy - by) + 2f * u * (ty - cy)
        val m = sqrt(dx * dx + dy * dy).let { if (it == 0f) 1f else it }
        val nx = -dy / m
        val ny = dx / m
        val ww = wB + (wT - wB) * u
        left.add(Offset(px + nx * ww, py + ny * ww))
        right.add(Offset(px - nx * ww, py - ny * ww))
    }
    val trunk = Path().apply {
        moveTo(left[0].x, left[0].y)
        for (i in 1..steps) lineTo(left[i].x, left[i].y)
        for (i in steps downTo 0) lineTo(right[i].x, right[i].y)
        close()
    }
    drawPath(
        trunk,
        Brush.linearGradient(
            0f to mix(dark, Color.Black, 0.25f),
            1f to mix(dark, Color.White, 0.10f),
            start = Offset(x, by),
            end = Offset(tx, ty),
        ),
    )

    // trunk scale-rings
    if (s > 0.32f) {
        val ringColor = mix(dark, Color.White, 0.16f).copy(alpha = 0.5f)
        val ringW = max(0.5f, 0.9f * s)
        for (i in 1 until steps) drawLine(ringColor, left[i], right[i], strokeWidth = ringW)
    }

    // crown — thin arching fronds, heavy droop on the outer ones (date palm, not papyrus)
    val fl = h * 0.46f * (0.30f + 0.70f * e)
    val spread = 0.22f + (1f - 0.22f) * e
    for (k in 0 until p.frondCount) {
        val u2 = if (p.frondCount == 1) 0.5f else k.toFloat() / (p.frondCount - 1)
        val base = -PI_F * 1.04f + u2 * PI_F * 1.08f
        val ang = base * spread - (1f - spread) * PI_F * 0.5f + lean * 0.5f
        val out = abs(cos(ang))
        val len = fl * (0.72f + 0.28f * sin(u2 * PI_F))
        val droop = len * (0.16f + 0.55f * out * out)
        val ex = tx + cos(ang) * len
        val ey = ty + sin(ang) * len + droop
        val mx = tx + cos(ang) * len * 0.52f
        val my = ty + sin(ang) * len * 0.52f - len * 0.06f
        val w2 = max(0.6f, len * 0.062f)
        val up = max(0f, -sin(ang))
        val col = mix(dark, lit, 0.14f + 0.52f * up)
        val frond = Path().apply {
            moveTo(tx, ty)
            quadraticBezierTo(mx, my - w2, ex, ey)
            quadraticBezierTo(mx, my + w2, tx, ty)
            close()
        }
        drawPath(frond, col)
        if (s > 0.42f) {
            val spine = Path().apply {
                moveTo(tx, ty)
                quadraticBezierTo(mx, my, ex, ey)
            }
            drawPath(spine, mix(col, Color.Black, 0.30f).copy(alpha = 0.5f), style = Stroke(max(0.35f, 0.6f * s)))
        }
    }

    // heart of the crown
    drawCircle(mix(dark, Color.Black, 0.25f), max(0.7f, 1.6f * s), Offset(tx, ty))

    // milestone fruit
    if (p.bearsDates && e > 0.65f && s > 0.24f) {
        val dc = mix(DATE, sky, haze * 0.7f)
        for (q in 0 until 3) {
            val a2 = -PI_F * 0.72f + q * 0.52f
            val bx = tx + cos(a2) * fl * 0.22f
            val byy = ty + sin(a2) * fl * 0.22f + fl * 0.16f
            for (nn in 0 until 7) {
                val rr = Mulberry32((2 * p.frondCount + q * 13 + nn * 7)).next()
                drawCircle(
                    dc.copy(alpha = 0.95f),
                    max(0.7f, 1.6f * s),
                    Offset(bx + (rr - 0.5f) * 7f * s, byy + nn * 2.1f * s),
                )
            }
        }
        // gold hairline halo — radial gradient, never blur
        drawCircle(
            Brush.radialGradient(
                0f to GOLD.copy(alpha = 0.20f),
                1f to GOLD.copy(alpha = 0f),
                center = Offset(tx, ty),
                radius = fl * 0.9f,
            ),
            fl * 0.9f,
            Offset(tx, ty),
        )
    }
}

private const val PI_F = 3.1415927f
