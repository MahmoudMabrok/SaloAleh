package tools.mo3ta.salo.ui.zabad

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

internal object ZabadColors {
    val Ink = Color(0xFF4A2E1B)
    val Amber = Color(0xFF95611F)
    val LightAmber = Color(0xFFC08A3E)
    val Cream = Color(0xFFFFFBF4)
    val Card = Color(0xFFFFFEFB)
    val Stroke = Color(0xFFEBDBC3)
    val Track = Color(0xFFE3DACA)
    val Muted = Color(0xFF8A7A68)
    val Star = Color(0xFFE0B978)
    val StarStem = Color(0xFFC79A5C)
}

internal object ZabadBrushes {
    val Header = Brush.horizontalGradient(
        listOf(Color(0xFF5C3A1F), Color(0xFF7A4B24), Color(0xFF5C3A1F)),
    )

    val Progress = Brush.horizontalGradient(
        listOf(ZabadColors.LightAmber, ZabadColors.Amber),
    )
}

internal object ZabadSpacing {
    val ScreenHorizontal = 16.dp
    val PanelPadding = 18.dp
    val PanelGap = 14.dp
}

internal object ZabadShapes {
    val Panel = RoundedCornerShape(24.dp)
    val Hero = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

@Composable
internal fun ZabadPanel(
    modifier: Modifier = Modifier,
    topOverlap: Boolean = false,
    contentPadding: Dp = ZabadSpacing.PanelPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(top = if (topOverlap) 0.dp else ZabadSpacing.PanelGap)
            .clip(ZabadShapes.Panel),
        color = ZabadColors.Card,
        shape = ZabadShapes.Panel,
        border = BorderStroke(1.dp, ZabadColors.Stroke),
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
internal fun ZabadRoundAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled) { onClick() },
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, ZabadColors.Stroke),
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) ZabadColors.Ink else ZabadColors.Muted,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (enabled) ZabadColors.Ink else ZabadColors.Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ZabadProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = ZabadColors.Track,
    fillColor: Color = ZabadColors.Amber,
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
internal fun ZabadLinearProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(CircleShape)
            .background(ZabadColors.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(CircleShape)
                .background(ZabadBrushes.Progress),
        )
    }
}

internal fun Modifier.zabadCardBorder(shape: RoundedCornerShape = ZabadShapes.Panel): Modifier =
    border(1.dp, ZabadColors.Stroke, shape)
