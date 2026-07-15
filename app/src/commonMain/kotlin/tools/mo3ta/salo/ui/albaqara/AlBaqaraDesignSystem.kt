package tools.mo3ta.salo.ui.albaqara

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object AlBaqaraColors {
    val Ink = Color(0xFF25275A)
    val Accent = Color(0xFF5A63C4)
    val LightAccent = Color(0xFF8B93E0)
    val Cream = Color(0xFFF8F9FF)
    val Card = Color(0xFFFCFCFF)
    val Stroke = Color(0xFFDDE0F2)
    val Track = Color(0xFFDADDEF)
    val Muted = Color(0xFF787CA0)
}

internal val AlBaqaraHeroBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF171B3D),
        Color(0xFF272C63),
        Color(0xFF322A52),
        Color(0xFF0E1830),
    ),
)

internal object AlBaqaraSpacing {
    val ScreenHorizontal = 16.dp
    val PanelPadding = 18.dp
    val PanelGap = 14.dp
}

internal object AlBaqaraShapes {
    val Panel = RoundedCornerShape(24.dp)
}

@Composable
internal fun AlBaqaraPanel(
    modifier: Modifier = Modifier,
    topOverlap: Boolean = false,
    contentPadding: Dp = AlBaqaraSpacing.PanelPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(top = if (topOverlap) 0.dp else AlBaqaraSpacing.PanelGap)
            .clip(AlBaqaraShapes.Panel),
        color = AlBaqaraColors.Card,
        shape = AlBaqaraShapes.Panel,
        border = BorderStroke(1.dp, AlBaqaraColors.Stroke),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
internal fun AlBaqaraProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = AlBaqaraColors.Track,
    fillColor: Color = AlBaqaraColors.Accent,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
                sweepAngle = sweepAngle * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
        }
        content()
    }
}
