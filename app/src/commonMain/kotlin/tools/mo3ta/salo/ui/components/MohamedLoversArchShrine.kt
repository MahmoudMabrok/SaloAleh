package tools.mo3ta.salo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.mohamed_lovers_shrine_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_shrine_sub

@Composable
internal fun MohamedLoversArchShrine(
    isLit: Boolean,
    modifier: Modifier = Modifier,
    onArchCenterPositioned: (Offset) -> Unit = {},
) {
    val glow by animateFloatAsState(
        targetValue = if (isLit) 1f else 0f,
        animationSpec = tween(durationMillis = if (isLit) 300 else 1600),
        label = "arch-glow",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.mohamed_lovers_shrine_label),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.arabic,
                fontWeight = FontWeight.W700,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
            ),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(width = 140.dp, height = 150.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    onArchCenterPositioned(
                        Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f),
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                drawArch(glow = glow)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.mohamed_lovers_shrine_sub),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.display,
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center,
            ),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f),
        )
    }
}

private fun DrawScope.drawArch(glow: Float) {
    val w = size.width
    val h = size.height
    val archTopY = h * 0.02f
    val archBottomY = h * 0.95f
    val baseY = h * 0.98f
    val cornerX = w * 0.45f

    val archPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = w * 0.05f,
                top = archTopY,
                right = w * 0.95f,
                bottom = archBottomY,
                topLeftCornerRadius = CornerRadius(cornerX, cornerX),
                topRightCornerRadius = CornerRadius(cornerX, cornerX),
                bottomLeftCornerRadius = CornerRadius(4f, 4f),
                bottomRightCornerRadius = CornerRadius(4f, 4f),
            ),
        )
    }

    drawPath(
        path = archPath,
        brush = Brush.radialGradient(
            0f to MohamedLoversPalette.ForestCore.copy(alpha = 0.9f),
            0.55f to MohamedLoversPalette.ForestOuter.copy(alpha = 0.95f),
            1f to Color.Transparent,
            center = Offset(w / 2f, h * 0.95f),
            radius = w * 0.75f,
        ),
    )

    val outerColor = lerpColor(MohamedLoversPalette.GoldBase, MohamedLoversPalette.GoldHighlight, glow)
    drawPath(path = archPath, color = outerColor, style = Stroke(width = 2.dp.toPx()))

    val innerPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = w * 0.08f,
                top = archTopY + 6.dp.toPx(),
                right = w * 0.08f + w * 0.84f,
                bottom = archBottomY - 4.dp.toPx(),
                topLeftCornerRadius = CornerRadius(w * 0.42f, w * 0.42f),
                topRightCornerRadius = CornerRadius(w * 0.42f, w * 0.42f),
                bottomLeftCornerRadius = CornerRadius(6f, 6f),
                bottomRightCornerRadius = CornerRadius(6f, 6f),
            ),
        )
    }
    clipPath(innerPath) {
        val strokePx = 1.dp.toPx()
        val step = 14.dp.toPx()
        val lineColor = MohamedLoversPalette.GoldBase.copy(alpha = 0.2f + glow * 0.3f)
        var d = -h
        val maxD = w + h
        while (d < maxD) {
            drawLine(lineColor, Offset(d, 0f), Offset(d + h, h), strokePx)
            drawLine(lineColor, Offset(d + h, 0f), Offset(d, h), strokePx)
            d += step
        }
    }

    val keyCx = w / 2f
    val keyCy = archTopY - 4.dp.toPx()
    drawCircle(color = MohamedLoversPalette.GoldBase, radius = 8.dp.toPx(), center = Offset(keyCx, keyCy))
    drawCircle(
        color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.5f + glow * 0.5f),
        radius = 8.dp.toPx() + 4.dp.toPx() * glow,
        center = Offset(keyCx, keyCy),
    )

    drawRect(
        color = MohamedLoversPalette.GoldBase,
        topLeft = Offset(w * 0.04f - 2.dp.toPx(), archBottomY - 2.dp.toPx()),
        size = Size(10.dp.toPx(), baseY - archBottomY + 6.dp.toPx()),
    )
    drawRect(
        color = MohamedLoversPalette.GoldBase,
        topLeft = Offset(w * 0.92f, archBottomY - 2.dp.toPx()),
        size = Size(10.dp.toPx(), baseY - archBottomY + 6.dp.toPx()),
    )

    if (glow > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to MohamedLoversPalette.GoldHighlight.copy(alpha = 0.7f * glow),
                0.4f to MohamedLoversPalette.GoldBase.copy(alpha = 0.35f * glow),
                1f to Color.Transparent,
            ),
            radius = w * 0.9f,
            center = Offset(w / 2f, archBottomY),
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color =
    Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
