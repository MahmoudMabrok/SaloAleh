package tools.mo3ta.salo.ui.kalimat

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

/** Rose-on-twilight palette — evokes the balance scales tipping toward the four beloved words. */
internal object KalimatColors {
    val Ink = Color(0xFF2C1622)
    val Gold = Color(0xFFB84E76)
    val LightGold = Color(0xFFE79BB6)
    val Cream = Color(0xFFFFF7FA)
    val Card = Color(0xFFFFFCFD)
    val Stroke = Color(0xFFEBD3DC)
    val Track = Color(0xFFE8D3DB)
    val Muted = Color(0xFF8A6C77)
}

internal val KalimatHeroBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF241A33),
        Color(0xFF3B2246),
        Color(0xFF52294A),
        Color(0xFF141026),
    ),
)

internal object KalimatSpacing {
    val ScreenHorizontal = 16.dp
}

/**
 * Progress ring that reads its fill fraction lazily from [fractionProvider] inside the draw scope, so
 * a changing count triggers a redraw rather than a recomposition of the ring composable.
 */
@Composable
internal fun KalimatProgressRing(
    fractionProvider: () -> Float,
    modifier: Modifier = Modifier,
    trackColor: Color = KalimatColors.Track,
    fillColor: Color = KalimatColors.Gold,
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
