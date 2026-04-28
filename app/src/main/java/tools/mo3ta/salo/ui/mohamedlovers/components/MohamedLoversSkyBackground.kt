package com.elsharif.dailyseventy.presentation.mohamedlovers.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

@Composable
internal fun MohamedLoversSkyBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sky-twinkle")
    val twinkleA by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "twinkle-a",
    )
    val twinkleB by transition.animateFloat(
        initialValue = 0.9f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "twinkle-b",
    )

    val starsA = remember { generateStarField(seed = 11L, count = 28) }
    val starsB = remember { generateStarField(seed = 42L, count = 22) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSky()
            drawStars(starsA, alpha = twinkleA)
            drawStars(starsB, alpha = twinkleB)
        }
    }
}

private data class Star(val x: Float, val y: Float, val radiusPx: Float, val tint: Color)

private fun generateStarField(seed: Long, count: Int): List<Star> {
    val rnd = Random(seed)
    val tints = listOf(Color.White, Color(0xFFE3D7FF), Color(0xFFFFF8E0))
    return List(count) {
        Star(
            x = rnd.nextFloat(),
            y = 0.45f + rnd.nextFloat() * 0.55f,
            radiusPx = 0.8f + rnd.nextFloat() * 0.9f,
            tint = tints[rnd.nextInt(tints.size)],
        )
    }
}

private fun DrawScope.drawSky() {
    drawRect(
        brush = Brush.verticalGradient(
            0f to MohamedLoversPalette.SkyTop,
            0.55f to MohamedLoversPalette.SkyMid,
            1f to MohamedLoversPalette.SkyBottom,
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to MohamedLoversPalette.AtmosphereViolet.copy(alpha = 0.3f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.18f, size.height * 0.08f),
            radius = size.minDimension * 0.6f,
        ),
        radius = size.minDimension * 0.6f,
        center = Offset(size.width * 0.18f, size.height * 0.08f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to MohamedLoversPalette.AtmosphereBlue.copy(alpha = 0.4f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.82f, size.height * 0.88f),
            radius = size.minDimension * 0.65f,
        ),
        radius = size.minDimension * 0.65f,
        center = Offset(size.width * 0.82f, size.height * 0.88f),
    )
}

private fun DrawScope.drawStars(stars: List<Star>, alpha: Float) {
    stars.forEach { s ->
        drawCircle(
            color = s.tint.copy(alpha = alpha),
            radius = s.radiusPx,
            center = Offset(s.x * size.width, s.y * size.height),
        )
    }
}

private fun DrawScope.drawAccentMoon(yOffsetDp: Float) {
    val cx = size.width * 0.91f
    val cy = size.height * 0.075f + yOffsetDp
    val r = size.minDimension * 0.035f
    drawCircle(
        brush = Brush.radialGradient(
            0f to MohamedLoversPalette.MoonHighlight.copy(alpha = 0.45f),
            1f to Color.Transparent,
        ),
        radius = r * 2.2f,
        center = Offset(cx, cy),
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to MohamedLoversPalette.MoonHighlight,
            0.6f to MohamedLoversPalette.MoonBody,
            1f to MohamedLoversPalette.MoonShadow,
            center = Offset(cx - r * 0.3f, cy - r * 0.3f),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
}
