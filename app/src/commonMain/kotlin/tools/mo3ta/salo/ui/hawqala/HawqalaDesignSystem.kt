package tools.mo3ta.salo.ui.hawqala

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

/** Royal amethyst-on-night palette — evokes "كنوز الجنة" (a treasure of Paradise). */
internal object HawqalaColors {
    val Ink = Color(0xFF241C33)
    val Accent = Color(0xFF6D4AC4)
    val LightAccent = Color(0xFFB79CF5)
    val Cream = Color(0xFFFDFBFF)
    val Card = Color(0xFFFFFEFF)
    val Stroke = Color(0xFFDED3F0)
    val Track = Color(0xFFDCD3EE)
    val Muted = Color(0xFF7A7090)
}

internal val HawqalaHeroBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1B1030),
        Color(0xFF2E1B4D),
        Color(0xFF3B2A63),
        Color(0xFF0E1026),
    ),
)

internal object HawqalaSpacing {
    val ScreenHorizontal = 16.dp
}

/**
 * Progress ring that reads its fill fraction lazily from [fractionProvider] inside the draw scope, so
 * a changing count triggers a redraw rather than a recomposition of the ring composable.
 */
@Composable
internal fun HawqalaProgressRing(
    fractionProvider: () -> Float,
    modifier: Modifier = Modifier,
    trackColor: Color = HawqalaColors.Track,
    fillColor: Color = HawqalaColors.Accent,
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
