package tools.mo3ta.salo.ui.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object QuranColors {
    val Ink = Color(0xFF1B3A2E)
    val Teal = Color(0xFF1F7A5C)
    val LightTeal = Color(0xFF3EAA82)
    val Cream = Color(0xFFF4FBF8)
    val Card = Color(0xFFFBFEFC)
    val Stroke = Color(0xFFC3E0D4)
    val Track = Color(0xFFCADAD2)
    val Muted = Color(0xFF688A7A)
    val Star = Color(0xFF78C4A0)
    val StarStem = Color(0xFF5CA882)
}

internal object QuranBrushes {
    val Header = Brush.horizontalGradient(
        listOf(Color(0xFF1A4035), Color(0xFF247A5C), Color(0xFF1A4035)),
    )

    val Progress = Brush.horizontalGradient(
        listOf(QuranColors.LightTeal, QuranColors.Teal),
    )
}

internal object QuranSpacing {
    val ScreenHorizontal = 16.dp
    val PanelPadding = 18.dp
    val PanelGap = 14.dp
}

internal object QuranShapes {
    val Panel = RoundedCornerShape(24.dp)
    val Hero = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

@Composable
internal fun QuranPanel(
    modifier: Modifier = Modifier,
    topOverlap: Boolean = false,
    contentPadding: Dp = QuranSpacing.PanelPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(top = if (topOverlap) 0.dp else QuranSpacing.PanelGap)
            .clip(QuranShapes.Panel),
        color = QuranColors.Card,
        shape = QuranShapes.Panel,
        border = BorderStroke(1.dp, QuranColors.Stroke),
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
internal fun QuranProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = QuranColors.Track,
    fillColor: Color = QuranColors.Teal,
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

@Composable
internal fun QuranLinearProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(CircleShape)
            .background(QuranColors.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(CircleShape)
                .background(QuranBrushes.Progress),
        )
    }
}

internal fun Modifier.quranCardBorder(shape: RoundedCornerShape = QuranShapes.Panel): Modifier =
    border(1.dp, QuranColors.Stroke, shape)
