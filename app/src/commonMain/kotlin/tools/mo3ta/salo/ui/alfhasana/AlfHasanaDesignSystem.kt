package tools.mo3ta.salo.ui.alfhasana

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Warm gold-on-night palette — evokes "ألف حسنة" (a thousand written good deeds). */
internal object AlfHasanaColors {
    val Ink = Color(0xFF2A2410)
    val Gold = Color(0xFFB88A1E)
    val LightGold = Color(0xFFE9C462)
    val Cream = Color(0xFFFFFDF6)
    val Card = Color(0xFFFFFEFA)
    val Stroke = Color(0xFFE6DABD)
    val Track = Color(0xFFE3DCC7)
    val Muted = Color(0xFF8A8064)
}

internal val AlfHasanaHeroBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF241B36),
        Color(0xFF3A2C52),
        Color(0xFF4A3A2A),
        Color(0xFF0E1B33),
    ),
)

internal object AlfHasanaSpacing {
    val ScreenHorizontal = 16.dp
}

/**
 * Progress ring that reads its fill fraction lazily from [fractionProvider] inside the draw scope, so
 * a changing count triggers a redraw rather than a recomposition of the ring composable.
 */
@Composable
internal fun AlfHasanaProgressRing(
    fractionProvider: () -> Float,
    modifier: Modifier = Modifier,
    trackColor: Color = AlfHasanaColors.Track,
    fillColor: Color = AlfHasanaColors.Gold,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - arcSize) / 2f, (size.height - arcSize) / 2f)
            val startAngle = -96f
            val sweepAngle = 324f
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = fillColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * fractionProvider().coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
        }
        content()
    }
}
