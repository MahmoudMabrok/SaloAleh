package tools.mo3ta.salo.ui.ghars

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

/**
 * Renders a single *finished* garden — a completed grove of [GROVE_SIZE] palms — as a walkable scene.
 * Unlike [PalmGroveCanvas], which draws the day's live, partially-filled grove, this draws all the
 * palms of one specific grove by absolute index, so garden N always shows the very palms that were
 * planted when that grove was grown.
 *
 * It reuses the live screen's palm and backdrop drawing verbatim (via the `internal` primitives in
 * [PalmGroveCanvas]) so a stored garden looks identical to the grove it was grown as.
 */

// ---- walker (the user's avatar strolling the garden) ----
private const val WALKER_STRIDE = 22f // logical dp travelled per half gait cycle
private const val WALKER_HEIGHT = 96f // logical dp, at front-row scale; shrinks with depth
private val ROBE = Color(0xFF1B2A34) // cool dark robe, a shade off the palm silhouettes
private val ROBE_HEM = Color(0xFF12202A)
private val WALKER_RIM = Color(0xFFF5D97A) // dawn backlight catching the head and shoulder edge
private val WALKER_SKIN = Color(0xFF6B4A32) // face, kept in shadow

/**
 * The walkable garden: garden [gardenIndex]'s grove with the user's avatar walking through it.
 *
 * Each depth row is rasterised into its own transparent bitmap once (on resize / garden change),
 * layered over a cached backdrop. Every animation frame blits the backdrop, then the rows back-to-
 * front, dropping the avatar in at the point its depth falls between two rows — so the figure is
 * occluded by every row nearer than it and occludes every row behind it, at *any* depth. That lets
 * it stroll from the front row all the way into the haze while staying correctly among the trunks,
 * for the cost of a handful of blits plus one repainted figure.
 *
 * @param avatarFraction 0..1 position across the usable width
 * @param avatarDepth 0 = furthest row, 1 = front row
 * @param gaitDistance logical distance the walker has travelled — phases the leg swing and bob
 * @param moving whether the walker is currently in motion (feet settle together when false)
 * @param facingRight which way the walker faces
 */
@Composable
internal fun GardenWalkCanvas(
    gardenIndex: Int,
    avatarFraction: Float,
    avatarDepth: Float,
    gaitDistance: Float,
    moving: Boolean,
    facingRight: Boolean,
    modifier: Modifier = Modifier,
) {
    val layers = remember { GardenLayers() }
    Canvas(modifier) {
        val wPx = size.width.toInt()
        val hPx = size.height.toInt()
        if (wPx <= 0 || hPx <= 0) return@Canvas
        layers.ensure(gardenIndex, wPx, hPx, density, layoutDirection)

        layers.backdrop?.let { drawImage(it) }

        val w = size.width / density
        val h = size.height / density
        val g = GroveGeometry(w, h)
        val rows = layers.rows
        val n = rows.size
        if (n == 0) return@Canvas

        val nearScale = rowScale(0)
        val farScale = rowScale(n - 1)
        val scaleA = farScale + (nearScale - farScale) * avatarDepth.coerceIn(0f, 1f)
        val avatarBaseY = g.hz + (g.nr - g.hz) * scaleA

        var drawn = false
        for (band in n - 1 downTo 0) {
            if (!drawn && g.baseY(band) > avatarBaseY) {
                scale(density, density, pivot = Offset.Zero) {
                    drawWalker(g, w, avatarFraction, scaleA, gaitDistance, moving, facingRight)
                }
                drawn = true
            }
            drawImage(rows[band])
        }
        if (!drawn) {
            scale(density, density, pivot = Offset.Zero) {
                drawWalker(g, w, avatarFraction, scaleA, gaitDistance, moving, facingRight)
            }
        }
    }
}

/** Draws every palm of one grove, back rows first, for the requested depth [bands]. */
private fun DrawScope.drawWholeGrove(
    g: GroveGeometry,
    gardenIndex: Int,
    scratch: GroveScratch,
    bands: IntArray,
) {
    val start = gardenFirstPalmIndex(gardenIndex)
    for (band in bands) {
        val lo = rowStart(band)
        for (slot in lo until lo + ROW_CAPACITY[band]) {
            drawPlantedPalm(g, slot, start, band, grow = 1f, scratch = scratch)
        }
    }
}

/** Cached backdrop + one bitmap per depth row, rebuilt only when the garden or size changes. */
private class GardenLayers {
    var backdrop: ImageBitmap? = null
    var rows: List<ImageBitmap> = emptyList()
    private var w = 0
    private var h = 0
    private var garden = -1

    fun ensure(gardenIndex: Int, wPx: Int, hPx: Int, density: Float, ld: LayoutDirection) {
        if (backdrop != null && w == wPx && h == hPx && garden == gardenIndex) return
        backdrop = rasterize(wPx, hPx, density, ld) {
            drawBackdrop(size.width / density, size.height / density, groves = 0)
        }
        rows = ROW_CAPACITY.indices.map { band ->
            rasterize(wPx, hPx, density, ld) {
                val gg = GroveGeometry(size.width / density, size.height / density)
                drawWholeGrove(gg, gardenIndex, GroveScratch(), bands = intArrayOf(band))
            }
        }
        w = wPx; h = hPx; garden = gardenIndex
    }
}

/** Runs [block] into a fresh transparent bitmap, in the same scaled logical space the grove uses. */
private fun rasterize(
    wPx: Int,
    hPx: Int,
    density: Float,
    ld: LayoutDirection,
    block: DrawScope.() -> Unit,
): ImageBitmap {
    val image = ImageBitmap(wPx, hPx)
    CanvasDrawScope().draw(Density(density), ld, GraphicsCanvas(image), Size(wPx.toFloat(), hPx.toFloat())) {
        scale(density, density, pivot = Offset.Zero) { block() }
    }
    return image
}

/**
 * The avatar: a robed figure in near-silhouette, backlit by the dawn so only the crown of the head
 * and one shoulder catch a hairline of gold — the same light that rims the palms. It walks with a
 * distance-driven gait: legs swing and the body bobs in step with how far it has actually moved, so
 * it stands still the instant the user stops dragging. Its size follows [scaleA] so it shrinks as it
 * walks deeper into the grove.
 */
private fun DrawScope.drawWalker(
    g: GroveGeometry,
    w: Float,
    fraction: Float,
    scaleA: Float,
    gaitDistance: Float,
    moving: Boolean,
    facingRight: Boolean,
) {
    val margin = w * 0.06f
    val x = margin + fraction.coerceIn(0f, 1f) * (w - 2f * margin)
    val groundY = g.hz + (g.nr - g.hz) * scaleA
    val h = WALKER_HEIGHT * scaleA
    val dir = if (facingRight) 1f else -1f
    val phase = gaitDistance / WALKER_STRIDE
    // The gait phases off distance travelled, but only expresses while moving; a stopped walker
    // settles its feet together and stands still rather than freezing mid-stride.
    val swing = if (moving) sin(phase) else 0f // -1..1, front foot forward while the other trails
    val bob = if (moving) abs(sin(phase)) * h * 0.035f else 0f

    // grounding shadow
    drawOval(
        Brush.radialGradient(
            0f to Color(0xFF120B07).copy(alpha = 0.4f),
            1f to Color(0xFF120B07).copy(alpha = 0f),
            center = Offset(x, groundY),
            radius = h * 0.34f,
        ),
        topLeft = Offset(x - h * 0.34f, groundY - h * 0.06f),
        size = Size(h * 0.68f, h * 0.12f),
    )

    val hipY = groundY - h * 0.40f - bob
    val shoulderY = groundY - h * 0.82f - bob
    val headCy = groundY - h * 0.92f - bob
    val headR = h * 0.11f
    val legW = h * 0.055f
    val footReach = swing * h * 0.16f

    // legs — the trailing/leading feet swap with the gait, mirrored by facing
    drawLeg(x, hipY, x + dir * footReach, groundY, legW, ROBE_HEM)
    drawLeg(x, hipY, x - dir * footReach, groundY, legW, ROBE_HEM)

    // robe — narrow at the shoulders, flaring to a hem just above the ankles
    val shW = h * 0.15f
    val hemW = h * 0.26f
    val hemY = groundY - h * 0.10f
    val robe = Path().apply {
        moveTo(x - shW, shoulderY)
        lineTo(x + shW, shoulderY)
        quadraticBezierTo(x + hemW * 0.9f, (shoulderY + hemY) / 2f, x + hemW, hemY)
        lineTo(x - hemW, hemY)
        quadraticBezierTo(x - hemW * 0.9f, (shoulderY + hemY) / 2f, x - shW, shoulderY)
        close()
    }
    drawPath(robe, Brush.verticalGradient(listOf(ROBE, ROBE_HEM), startY = shoulderY, endY = hemY))

    // near-side arm, swinging opposite the near leg
    val armX = x + dir * (shW + h * 0.02f)
    val armEndX = armX - dir * footReach * 0.7f
    drawLeg(armX, shoulderY + h * 0.02f, armEndX, hipY + h * 0.04f, legW * 0.8f, ROBE)

    // head + a simple draped head-cover
    val leanX = dir * h * 0.02f * (if (moving) 1f else 0.4f)
    drawCircle(WALKER_SKIN, headR * 0.86f, Offset(x + leanX, headCy))
    val cover = Path().apply {
        val hx = x + leanX
        moveTo(hx - headR * 1.15f, headCy + headR * 0.1f)
        quadraticBezierTo(hx, headCy - headR * 1.5f, hx + headR * 1.15f, headCy + headR * 0.1f)
        quadraticBezierTo(hx + headR * 1.2f, headCy + headR * 1.3f, hx + headR * 0.5f, shoulderY)
        lineTo(hx - headR * 0.5f, shoulderY)
        quadraticBezierTo(hx - headR * 1.2f, headCy + headR * 1.3f, hx - headR * 1.15f, headCy + headR * 0.1f)
        close()
    }
    drawPath(cover, ROBE)

    // dawn rim — the backlight catching the crown and the facing shoulder edge
    val rim = Path().apply {
        val hx = x + leanX
        moveTo(hx - headR * 0.9f, headCy - headR * 0.2f)
        quadraticBezierTo(hx, headCy - headR * 1.45f, hx + headR * 0.9f, headCy - headR * 0.2f)
    }
    drawPath(rim, WALKER_RIM.copy(alpha = 0.7f), style = Stroke(width = maxOf(0.7f, h * 0.02f)))
    drawLine(
        WALKER_RIM.copy(alpha = 0.5f),
        Offset(x + dir * shW, shoulderY + h * 0.02f),
        Offset(x + dir * hemW * 0.9f, hemY - h * 0.06f),
        strokeWidth = maxOf(0.6f, h * 0.016f),
    )
}

private fun DrawScope.drawLeg(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Color) {
    val dx = x1 - x0
    val dy = y1 - y0
    val len = maxOf(1e-3f, sqrt(dx * dx + dy * dy))
    val nx = -dy / len * width
    val ny = dx / len * width
    val leg = Path().apply {
        moveTo(x0 + nx, y0 + ny)
        lineTo(x1 + nx * 0.7f, y1 + ny * 0.7f)
        lineTo(x1 - nx * 0.7f, y1 - ny * 0.7f)
        lineTo(x0 - nx, y0 - ny)
        close()
    }
    drawPath(leg, color, style = Fill)
    // a small foot
    drawOval(
        color,
        topLeft = Offset(x1 - width * 1.6f, y1 - width * 0.5f),
        size = Size(width * 3.2f, width * 1.1f),
    )
}
