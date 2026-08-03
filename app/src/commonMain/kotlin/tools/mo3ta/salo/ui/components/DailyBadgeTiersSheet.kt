package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.DailyBadge
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.daily_badge_sheet_title
import tools.mo3ta.salo.generated.resources.daily_badge_sheet_subtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBadgeTiersSheet(
    todayTaps: Int,
    currentBadgeKey: String?,
    onDismiss: () -> Unit,
) {
    val currentBadge = currentBadgeKey?.let { DailyBadge.fromKey(it) }
        ?: DailyBadge.fromTapCount(todayTaps)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MohamedLoversPalette.SkyTop,
        contentColor = MohamedLoversPalette.GoldGlow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.daily_badge_sheet_title),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.daily_badge_sheet_subtitle),
                color = MohamedLoversPalette.GoldBase.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            val badges = DailyBadge.entries
            val columns = 3
            val rows = badges.chunked(columns)
            rows.forEachIndexed { rowIndex, rowBadges ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (badge in rowBadges) {
                        val isCurrent = badge == currentBadge
                        val isEarned = currentBadge != null && badge.threshold <= currentBadge.threshold
                        val isLocked = !isEarned && !isCurrent

                        val prevThreshold = badge.ordinal.let { idx ->
                            if (idx > 0) badges[idx - 1].threshold else 0
                        }
                        val badgeProgress = when {
                            isEarned -> 1f
                            todayTaps >= prevThreshold -> {
                                ((todayTaps - prevThreshold).toFloat() /
                                    (badge.threshold - prevThreshold).toFloat()).coerceIn(0f, 1f)
                            }
                            else -> 0f
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) MohamedLoversPalette.GoldHighlight.copy(alpha = 0.12f)
                                    else MohamedLoversPalette.GoldBase.copy(alpha = 0.05f)
                                )
                                .border(
                                    1.dp,
                                    if (isCurrent) MohamedLoversPalette.GoldHighlight.copy(alpha = 0.3f)
                                    else MohamedLoversPalette.GoldBase.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val alpha = if (isLocked) 0.35f else 1f
                            val res = badgeDrawableResource(badge)
                            if (res != null) {
                                BadgeWithProgress(
                                    badgeRes = res,
                                    progress = badgeProgress,
                                    isEarned = isEarned,
                                    alpha = alpha,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = badgeTitleString(badge),
                                color = MohamedLoversPalette.GoldGlow.copy(alpha = alpha),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = formatBadgeCount(badge.threshold),
                                color = MohamedLoversPalette.GoldBase.copy(alpha = 0.5f * alpha),
                                fontSize = 10.sp,
                            )
                        }
                    }
                    // pad the final row so tiles keep a consistent width
                    repeat(columns - rowBadges.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                if (rowIndex < rows.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BadgeWithProgress(
    badgeRes: DrawableResource,
    progress: Float,
    isEarned: Boolean,
    alpha: Float,
) {
    val trackColor = MohamedLoversPalette.GoldBase.copy(alpha = 0.15f)
    val progressColor = if (isEarned)
        MohamedLoversPalette.GoldHighlight
    else
        MohamedLoversPalette.GoldHighlight.copy(alpha = 0.7f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp),
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val strokeWidth = 3.dp.toPx()
            val arcSize = size.width - strokeWidth
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Image(
            painter = painterResource(badgeRes),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            alpha = alpha,
        )
    }
}

private fun formatBadgeCount(n: Int): String {
    if (n < 1000) return "$n"
    val thousands = n / 1000
    val remainder = n % 1000
    return if (remainder == 0) "$thousands,000"
    else "$thousands,${remainder.toString().padStart(3, '0')}"
}
