package tools.mo3ta.salo.ui.ghars

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

// ---- palette (dawn over the grove) ----
private val PALM = Color(0xFF0B2A22)
private val FROND = Color(0xFF3E8F63)
private val DATE = Color(0xFFC4762A)
private val GOLD = Color(0xFFF5D97A)
private val SOIL_SHADOW = Color(0xFF160C07)

// Sky stops as fractions of the sky band (0 = top of screen, 1 = horizon).
private val SKY_STOPS: Array<Pair<Float, Color>> = arrayOf(
    0.00f to Color(0xFF14103A), // NightIndigo
    0.30f to Color(0xFF3B2352),
    0.62f to Color(0xFF8C4A54), // DawnRose
    0.88f to Color(0xFFE09A62), // HorizonApricot
    1.00f to Color(0xFFFFC98A),
)

private const val BASE_H = 168f
private const val TRUNK_STEPS = 7

/**
 * Logical height (dp) kept clear at the foot of the canvas so the front row is planted
 * above the tasbeeh overlay instead of behind it. The mockup reserved its in-canvas dock
 * (`nr = H - SHEET_H - 16`); here the sheet lives outside the canvas, so we only reserve
 * the tasbeeh + hint block that floats over the grove.
 */
private const val FOREGROUND_RESERVE = 140f

private const val GROW_MS = 1500
private const val BURST_MS = 1000
private const val RECEDE_MS = 1600

/**
 * Everything the renderer reuses across frames.
 *
 * The grove is fill-heavy: at full size the crowns overlap into a canopy that covers most of
 * the screen, so a repaint is tens of millions of shaded pixels. The old canvas paid that
 * every single frame, forever, to animate a ~1.3° sway — plus ~700 fresh `Path`/`Brush`
 * objects per frame. The sway is gone (nothing animates between taps, so the canvas now
 * issues no frames at all when idle) and what still gets drawn reuses the buffers below.
 */
internal class GroveScratch {
    // The backdrop (sky, stars, sun, ground, tree-line) only changes when the canvas resizes
    // or a grove completes, so it is rasterised once and blitted after that.
    var backdrop: ImageBitmap? = null
    var backdropW = 0
    var backdropH = 0
    var backdropGroves = -1

    val trunk = Path()
    val frond = Path()
    val spine = Path()
    val leftX = FloatArray(TRUNK_STEPS + 1)
    val leftY = FloatArray(TRUNK_STEPS + 1)
    val rightX = FloatArray(TRUNK_STEPS + 1)
    val rightY = FloatArray(TRUNK_STEPS + 1)

    // One soil-shadow brush per depth row: it is drawn in the palm's local space and moved
    // into place with translate(), so the same shader serves every palm in the row.
    val soilShadow = arrayOfNulls<Brush>(ROW_CAPACITY.size)
}

/**
 * The whole signature: a grove drawn palm by palm at first light. Only Path/arc/oval and
 * linear/radial Brush — no blur anywhere — so it ports 1:1 from the HTML mockup and the
 * render cost stays a constant (never more than [GROVE_SIZE] palms on screen).
 */
@Composable
fun PalmGroveCanvas(
    count: Int,
    modifier: Modifier = Modifier,
    plantSerial: Int = 0,
    reduceMotion: Boolean = false,
) {
    // The sprout animation is keyed on [plantSerial] — bumped only on a real user plant (tap /
    // manual entry) — never on [count] alone. Opening the screen (or a remote-baseline sync)
    // changes count without a new plant, so the grove settles in fully grown instead of
    // re-growing its newest palm every time. A brand-new Animatable per plant, born at 0, keeps
    // the newest palm from flashing full-size for a frame before it starts growing.
    val grow = remember(plantSerial, reduceMotion) {
        Animatable(if (plantSerial == 0 || reduceMotion) 1f else 0f)
    }
    val burst = remember { Animatable(0f) }
    val recede = remember { Animatable(0f) }
    val scratch = remember { GroveScratch() }
    var recedeTrigger by remember { mutableIntStateOf(0) }

    // Every plant sprouts the newest palm and bursts the soil; a grove-completing plant
    // also sends the finished grove receding to the horizon. Between plants nothing animates,
    // so the canvas issues no frames at all — see [GroveScratch] on why the old sway clock
    // (which repainted every palm 60x a second, forever) had to go.
    LaunchedEffect(plantSerial, reduceMotion) {
        if (plantSerial == 0 || reduceMotion || count <= 0) {
            burst.snapTo(0f)
            return@LaunchedEffect
        }
        launch { grow.animateTo(1f, tween(GROW_MS, easing = LinearEasing)) }
        launch { burst.snapTo(1f); burst.animateTo(0f, tween(BURST_MS, easing = LinearEasing)) }
        if (completesGrove(count)) recedeTrigger++
    }

    // The recede has to outlive the tap that started it. Run inside the effect above and the
    // very next tap cancels it mid-flight, stranding `recede` at whatever value it had reached —
    // and a stranded recede paints the finished grove on top of the new one, forever.
    LaunchedEffect(recedeTrigger, reduceMotion) {
        if (recedeTrigger == 0 || reduceMotion) {
            recede.snapTo(0f)
            return@LaunchedEffect
        }
        recede.snapTo(1f)
        recede.animateTo(0f, tween(RECEDE_MS, easing = LinearEasing))
    }

    // Only the sprouting palm changes while it grows — but it has to stay correctly interleaved
    // with the grove, since a palm sprouting in a back row must be painted *under* the rows in
    // front of it. So the scene is cut into three layers around it: everything drawn before it,
    // the palm itself, and everything drawn after it. The two grove layers depend only on
    // `count`, so during the 1.5s of growth they are never re-recorded — they sit in cached
    // compositing layers and are simply re-composited, while the live layer redraws one palm.
    Box(modifier) {
        Canvas(Modifier.matchParentSize().offscreenLayer()) {
            drawGroveLayer(count, front = false, scratch = scratch)
        }
        Canvas(Modifier.matchParentSize()) {
            drawLiveLayer(count, grow.value, burst.value, recede.value, scratch)
        }
        Canvas(Modifier.matchParentSize().offscreenLayer()) {
            drawGroveLayer(count, front = true, scratch = scratch)
        }
    }
}

/** Keeps the layer's painted output in its own buffer so an unchanged layer is re-composited, not redrawn. */
private fun Modifier.offscreenLayer(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }

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

internal fun horizonY(h: Float): Float = h * 0.545f

/**
 * The mockup draws in CSS pixels (`g.setTransform(dpr, …)`), so every constant in this file
 * is a logical dp. Scaling the scene by [DrawScope.density] — and measuring the canvas in the
 * same logical units — is what keeps a palm the size it was designed to be instead of
 * shrinking it by the device's pixel ratio.
 */
/**
 * The settled grove, split at the sprouting palm.
 *
 * Palms paint back row first so the front row overlaps it. The sprouting palm is always the last
 * one in its own row, so everything that must sit *behind* it is "the rows further back, plus the
 * older palms of its own row" ([front] = false) and everything that must sit *in front* of it is
 * "the rows nearer the viewer" ([front] = true). Neither half reads the growth animation, so
 * neither is re-recorded while a palm grows.
 */
private fun DrawScope.drawGroveLayer(count: Int, front: Boolean, scratch: GroveScratch) {
    val wPx = size.width.toInt()
    val hPx = size.height.toInt()
    if (wPx <= 0 || hPx <= 0) return

    if (!front) drawBackdropImage(count, scratch)

    scale(density, density, pivot = Offset.Zero) {
        val w = size.width / density
        val h = size.height / density
        val g = GroveGeometry(w, h)
        val shown = shownPalms(count)
        if (shown == 0) {
            if (front) drawForegroundShadow(g, w, h)
            return@scale
        }
        val start = groveStartIndex(count)
        val newest = shown - 1
        val newestBand = rowOfSlot(newest)
        val take = rowOccupancy(shown)

        // far rows first, front row last
        for (band in take.indices.reversed()) {
            if (take[band] == 0) continue
            val inFrontOfSprout = band < newestBand
            if (inFrontOfSprout != front) continue
            val lo = rowStart(band)
            // the sprouting palm itself belongs to the live layer, never to a cached one
            val hi = if (band == newestBand) newest else lo + take[band]
            for (j in lo until hi) {
                drawPlantedPalm(g, j, start, band, 1f, scratch)
            }
        }
        if (front) drawForegroundShadow(g, w, h)
    }
}

/** The sprouting palm, its soil burst, and a completed grove receding to the horizon. */
private fun DrawScope.drawLiveLayer(
    count: Int,
    grow: Float,
    burst: Float,
    recede: Float,
    scratch: GroveScratch,
) {
    if (size.width <= 0f || size.height <= 0f) return
    scale(density, density, pivot = Offset.Zero) {
        val w = size.width / density
        val h = size.height / density
        val g = GroveGeometry(w, h)
        val shown = shownPalms(count)
        if (shown == 0) return@scale
        val start = groveStartIndex(count)
        val newest = shown - 1
        val band = rowOfSlot(newest)

        // ---- the grove just completed, receding into the tree-line (never erased, just distant) ----
        if (recede > 0f) {
            val rc = recede // 1 -> 0
            val pStart = start - GROVE_SIZE
            val pTake = rowOccupancy(GROVE_SIZE)
            val paint = Paint().apply { alpha = min(1f, rc * 1.4f) }
            drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), paint)
            for (b in ROW_CAPACITY.indices.reversed()) {
                val pby = g.hz + (g.nr - g.hz) * rowScale(b) * (0.10f + 0.90f * rc)
                val ps = rowScale(b) * (0.16f + 0.84f * rc)
                val phz = min(0.9f, (1f - HAZE_FALLOFF.pow(b)) + (1f - rc) * 0.75f)
                val psky = skyAt(((pby - 56f) / g.hz).coerceIn(0f, 1f))
                val plo = rowStart(b)
                for (j2 in plo until plo + pTake[b]) {
                    drawPalm(g.slotX(j2, pStart), pby, ps, palmParams(pStart + j2), 1f, phz, psky, scratch)
                }
            }
            drawContext.canvas.restore()
        }

        drawPlantedPalm(g, newest, start, band, grow, scratch)

        // ---- planting burst — expanding soil ring + light bloom, at the palm just planted ----
        if (burst > 0f) {
            val bs = rowScale(band)
            val bx = g.slotX(newest, start)
            val by = g.baseY(band)
            val rr = (10f + (1f - burst) * 54f) * bs
            drawOval(
                Color(0xFFFFE0B0).copy(alpha = burst * 0.6f),
                topLeft = Offset(bx - rr, by - rr * 0.28f),
                size = Size(2f * rr, 2f * rr * 0.28f),
                style = Stroke(max(0.8f, 1.5f * bs)),
            )
            val bloom = 96f * bs
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFFFFE2AA).copy(alpha = burst * 0.26f),
                    1f to Color(0xFFFFE2AA).copy(alpha = 0f),
                    center = Offset(bx, by - 46f * bs),
                    radius = bloom,
                ),
                bloom,
                Offset(bx, by - 46f * bs),
            )
        }
    }
}

/**
 * Where the grove stands, in logical dp. [reserve] is the space kept clear at the foot of the
 * canvas; the full-screen grove reserves room for the tasbeeh overlay, while a small thumbnail
 * passes a shorter reserve so the soil line stays below the horizon on a short canvas.
 */
internal class GroveGeometry(w: Float, h: Float, reserve: Float = FOREGROUND_RESERVE) {
    val hz = horizonY(h)
    val nr = h - reserve
    private val margin = w * 0.04f
    private val span = w - 2f * margin

    fun slotX(j: Int, groveStart: Int): Float =
        margin + palmXFraction(j, palmParams(groveStart + j).jitter) * span

    fun baseY(band: Int): Float = hz + (nr - hz) * rowScale(band)
}

/** One palm at its fixed place in the grove, with the soil shadow it casts. */
internal fun DrawScope.drawPlantedPalm(
    g: GroveGeometry,
    slot: Int,
    groveStart: Int,
    band: Int,
    grow: Float,
    scratch: GroveScratch,
) {
    val by = g.baseY(band)
    val s = rowScale(band)
    val px = g.slotX(slot, groveStart)
    val shadow = scratch.soilShadow[band] ?: Brush.radialGradient(
        0f to SOIL_SHADOW.copy(alpha = 0.42f),
        1f to SOIL_SHADOW.copy(alpha = 0f),
        center = Offset.Zero,
        radius = 18f * s,
    ).also { scratch.soilShadow[band] = it }
    if (grow > 0.001f) {
        translate(px, by) {
            drawOval(shadow, topLeft = Offset(-18f * s, -4.5f * s), size = Size(36f * s, 9f * s))
        }
    }
    val sky = skyAt(((by - 56f) / g.hz).coerceIn(0f, 1f))
    drawPalm(px, by, s, palmParams(groveStart + slot), grow, rowHaze(band), sky, scratch)
}

/** Grounds the tasbeeh so it reads over the soil. */
private fun DrawScope.drawForegroundShadow(g: GroveGeometry, w: Float, h: Float) {
    val top = g.nr - 130f
    drawRect(
        Brush.verticalGradient(
            0.00f to Color(0xFF120B07).copy(alpha = 0f),
            0.62f to Color(0xFF120B07).copy(alpha = 0.42f),
            1.00f to Color(0xFF120B07).copy(alpha = 0.66f),
            startY = top,
            endY = h,
        ),
        topLeft = Offset(0f, top),
        size = Size(w, h - top),
    )
}

private fun DrawScope.drawBackdropImage(count: Int, scratch: GroveScratch) {
    val wPx = size.width.toInt()
    val hPx = size.height.toInt()
    val groves = completedGroves(count)
    val backdrop = scratch.backdrop.takeIf {
        it != null && scratch.backdropW == wPx && scratch.backdropH == hPx && scratch.backdropGroves == groves
    } ?: rasterizeBackdrop(wPx, hPx, density, layoutDirection, groves).also {
        scratch.backdrop = it
        scratch.backdropW = wPx
        scratch.backdropH = hPx
        scratch.backdropGroves = groves
    }
    drawImage(backdrop)
}

/**
 * Sky, stars, sun, ground and tree-line, rasterised once. None of it reacts to the sway clock,
 * so redrawing four full-screen gradients and forty-odd seeded shapes every frame was pure waste.
 */
private fun rasterizeBackdrop(
    widthPx: Int,
    heightPx: Int,
    density: Float,
    layoutDirection: LayoutDirection,
    groves: Int,
): ImageBitmap {
    val image = ImageBitmap(widthPx, heightPx)
    CanvasDrawScope().draw(
        Density(density),
        layoutDirection,
        GraphicsCanvas(image),
        Size(widthPx.toFloat(), heightPx.toFloat()),
    ) {
        scale(density, density, pivot = Offset.Zero) {
            drawBackdrop(widthPx / density, heightPx / density, groves)
        }
    }
    return image
}

internal fun DrawScope.drawBackdrop(w: Float, h: Float, groves: Int, reserve: Float = FOREGROUND_RESERVE) {
    val hz = horizonY(h)
    val nr = h - reserve

    // ---- sky ----
    drawRect(
        Brush.verticalGradient(*SKY_STOPS, startY = 0f, endY = hz),
        topLeft = Offset(0f, 0f),
        size = Size(w, hz + 1f),
    )

    // ---- stars, fading with dawn ----
    for (i in 0 until 26) {
        val r = Mulberry32((((i + 77).toLong() * 99991L) and 0xFFFFFFFFL).toInt())
        val sx = r.next() * w
        val sy = r.next() * hz * 0.52f
        val sa = ((0.10f + r.next() * 0.42f) * (1f - sy / (hz * 0.62f))).coerceAtLeast(0f)
        val rad = r.next() * 0.9f + 0.4f
        drawCircle(Color(0xFFFFF4DC).copy(alpha = sa), rad, Offset(sx, sy))
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
}

/** One palm — a tapered trunk, arching fronds, and (on a grove-completing palm) date clusters. */
private fun DrawScope.drawPalm(
    x: Float,
    by: Float,
    s: Float,
    p: PalmParams,
    grow: Float,
    haze: Float,
    sky: Color,
    scratch: GroveScratch,
) {
    if (grow <= 0.001f) return
    val e = easeOutCubic(grow)
    val h = BASE_H * s * p.heightScale * e
    val lean = p.lean

    val dark = mix(PALM, sky, haze)
    val lit = mix(FROND, sky, min(0.88f, haze + 0.06f))

    // trunk: tapered path along a bowed spine
    val tx = x + sin(lean) * h
    val ty = by - h * cos(lean)
    val cx = x + (tx - x) * 0.5f + p.trunkBow * h * 0.16f
    val cy = by - h * 0.5f
    val wB = max(0.8f, 3.9f * s)
    val wT = max(0.5f, 2.1f * s)
    val leftX = scratch.leftX
    val leftY = scratch.leftY
    val rightX = scratch.rightX
    val rightY = scratch.rightY
    for (i in 0..TRUNK_STEPS) {
        val u = i.toFloat() / TRUNK_STEPS
        val iu = 1f - u
        val px = iu * iu * x + 2f * iu * u * cx + u * u * tx
        val py = iu * iu * by + 2f * iu * u * cy + u * u * ty
        val dx = 2f * iu * (cx - x) + 2f * u * (tx - cx)
        val dy = 2f * iu * (cy - by) + 2f * u * (ty - cy)
        val m = sqrt(dx * dx + dy * dy).let { if (it == 0f) 1f else it }
        val nx = -dy / m
        val ny = dx / m
        val ww = wB + (wT - wB) * u
        leftX[i] = px + nx * ww
        leftY[i] = py + ny * ww
        rightX[i] = px - nx * ww
        rightY[i] = py - ny * ww
    }
    val trunk = scratch.trunk
    trunk.rewind()
    trunk.moveTo(leftX[0], leftY[0])
    for (i in 1..TRUNK_STEPS) trunk.lineTo(leftX[i], leftY[i])
    for (i in TRUNK_STEPS downTo 0) trunk.lineTo(rightX[i], rightY[i])
    trunk.close()
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
        for (i in 1 until TRUNK_STEPS) {
            drawLine(
                ringColor,
                Offset(leftX[i], leftY[i]),
                Offset(rightX[i], rightY[i]),
                strokeWidth = ringW,
            )
        }
    }

    // crown — thin arching fronds, heavy droop on the outer ones (date palm, not papyrus)
    val fl = h * 0.46f * (0.30f + 0.70f * e)
    val spread = 0.22f + (1f - 0.22f) * e
    val frond = scratch.frond
    val spine = scratch.spine
    val drawSpines = s > 0.42f
    val spineWidth = max(0.35f, 0.6f * s)
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
        frond.rewind()
        frond.moveTo(tx, ty)
        frond.quadraticBezierTo(mx, my - w2, ex, ey)
        frond.quadraticBezierTo(mx, my + w2, tx, ty)
        frond.close()
        drawPath(frond, col)
        if (drawSpines) {
            spine.rewind()
            spine.moveTo(tx, ty)
            spine.quadraticBezierTo(mx, my, ex, ey)
            drawPath(spine, mix(col, Color.Black, 0.30f).copy(alpha = 0.5f), style = Stroke(spineWidth))
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
