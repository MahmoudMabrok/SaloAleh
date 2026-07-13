package tools.mo3ta.salo.ui.dhikr

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
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.dhikr_manual_entry_button

internal object DhikrColors {
    val Ink = Color(0xFF064B2A)
    val Green = Color(0xFF18753F)
    val LightGreen = Color(0xFF2D9B55)
    val Cream = Color(0xFFFFFCF7)
    val Card = Color(0xFFFFFEFB)
    val Stroke = Color(0xFFEBDDC8)
    val Track = Color(0xFFE1DED0)
    val Muted = Color(0xFF7D8878)
    val Leaf = Color(0xFF74BF75)
    val LeafStem = Color(0xFF58A968)
}

internal object DhikrBrushes {
    val Header = Brush.horizontalGradient(
        listOf(Color(0xFF0B6135), Color(0xFF176A3E), Color(0xFF0B6135)),
    )

    val Progress = Brush.horizontalGradient(
        listOf(DhikrColors.LightGreen, DhikrColors.Green),
    )
}

internal object DhikrSpacing {
    val ScreenHorizontal = 16.dp
    val PanelPadding = 18.dp
    val PanelGap = 14.dp
}

internal object DhikrShapes {
    val Panel = RoundedCornerShape(24.dp)
    val Hero = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

@Composable
internal fun DhikrPanel(
    modifier: Modifier = Modifier,
    topOverlap: Boolean = false,
    contentPadding: Dp = DhikrSpacing.PanelPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(top = if (topOverlap) 0.dp else DhikrSpacing.PanelGap)
            .clip(DhikrShapes.Panel),
        color = DhikrColors.Card,
        shape = DhikrShapes.Panel,
        border = BorderStroke(1.dp, DhikrColors.Stroke),
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
internal fun DhikrRoundAction(
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
            border = BorderStroke(1.dp, DhikrColors.Stroke),
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) DhikrColors.Ink else DhikrColors.Muted,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (enabled) DhikrColors.Ink else DhikrColors.Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun DhikrProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = DhikrColors.Track,
    fillColor: Color = DhikrColors.Green,
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
internal fun DhikrLinearProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(CircleShape)
            .background(DhikrColors.Track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(CircleShape)
                .background(DhikrBrushes.Progress),
        )
    }
}

internal fun Modifier.dhikrCardBorder(shape: RoundedCornerShape = DhikrShapes.Panel): Modifier =
    border(1.dp, DhikrColors.Stroke, shape)

@Composable
internal fun DhikrManualEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onDarkBackground: Boolean = false,
) {
    val containerColor = if (onDarkBackground) {
        Color.White.copy(alpha = 0.14f)
    } else {
        DhikrColors.Green.copy(alpha = 0.08f)
    }
    val borderColor = if (onDarkBackground) {
        Color.White.copy(alpha = 0.24f)
    } else {
        DhikrColors.LightGreen.copy(alpha = 0.4f)
    }
    val textColor = if (onDarkBackground) {
        Color.White.copy(alpha = 0.92f)
    } else {
        DhikrColors.Green
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = stringResource(Res.string.dhikr_manual_entry_button),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
        )
    }
}
