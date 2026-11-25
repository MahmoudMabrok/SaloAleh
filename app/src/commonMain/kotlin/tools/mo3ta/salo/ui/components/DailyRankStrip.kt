package tools.mo3ta.salo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.DailyBadge
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.daily_rank_strip_today_label
import tools.mo3ta.salo.generated.resources.daily_rank_strip_rank_of
import tools.mo3ta.salo.ui.areSystemAnimationsEnabled

private const val PULSE_HALF_CYCLE_MS = 700
private const val RESTING_BORDER_ALPHA = 0.2f
private val RankStripShape = RoundedCornerShape(16.dp)

@Composable
fun DailyRankStrip(
    rank: Int,
    totalPlayers: Int,
    todayTaps: Int,
    currentBadgeKey: String?,
    onStripClick: () -> Unit,
    onBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    val currentBadge = currentBadgeKey?.let { DailyBadge.fromKey(it) }
        ?: DailyBadge.fromTapCount(todayTaps)
    val motion = rankStripMotion(pulse)

    Row(
        modifier = modifier
            // Scale is a draw transform, so the pulse never changes measured size.
            // Left off when idle so the resting card matches the unscaled layout.
            .then(
                if (motion.pulsing) {
                    Modifier.graphicsLayer {
                        scaleX = motion.scale
                        scaleY = motion.scale
                    }
                } else {
                    Modifier
                },
            )
            .clip(RankStripShape)
            .border(
                width = 1.5.dp,
                color = if (motion.pulsing) {
                    MohamedLoversPalette.GoldHighlight.copy(alpha = motion.borderAlpha)
                } else {
                    MohamedLoversPalette.GoldBase.copy(alpha = RESTING_BORDER_ALPHA)
                },
                shape = RankStripShape,
            )
            .clickable { onStripClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentBadge != null) {
            val res = badgeDrawableResource(currentBadge)
            if (res != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MohamedLoversPalette.GoldHighlight.copy(alpha = 0.1f))
                        .border(1.5.dp, MohamedLoversPalette.GoldHighlight.copy(alpha = 0.3f), CircleShape)
                        .clickable { onBadgeClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(res),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
        }

        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatCount(todayTaps),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.daily_rank_strip_today_label),
                    color = MohamedLoversPalette.GoldBase.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                )
            }
            Text(
                text = stringResource(Res.string.daily_rank_strip_rank_of, rank, totalPlayers),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private data class RankStripMotion(
    val scale: Float,
    val borderAlpha: Float,
    val pulsing: Boolean,
)

@Composable
private fun rankStripMotion(pulse: Boolean): RankStripMotion {
    // Always read the scale so the remember inside it is not conditional on [pulse].
    val animationsEnabled = areSystemAnimationsEnabled()
    if (!pulse || !animationsEnabled) {
        return RankStripMotion(scale = 1f, borderAlpha = RESTING_BORDER_ALPHA, pulsing = false)
    }
    return pulsingRankStripMotion()
}

@Composable
private fun pulsingRankStripMotion(): RankStripMotion {
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_HALF_CYCLE_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val borderAlpha by transition.animateFloat(
        initialValue = RESTING_BORDER_ALPHA,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_HALF_CYCLE_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    return RankStripMotion(scale = scale, borderAlpha = borderAlpha, pulsing = true)
}

private fun formatCount(n: Int): String {
    if (n < 1000) return "$n"
    val thousands = n / 1000
    val remainder = n % 1000
    return if (remainder == 0) "$thousands,000"
    else "$thousands,${remainder.toString().padStart(3, '0')}"
}
