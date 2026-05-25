package tools.mo3ta.salo.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.DailyBadge
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.daily_rank_strip_today_label
import tools.mo3ta.salo.generated.resources.daily_rank_strip_rank_of

@Composable
fun DailyRankStrip(
    rank: Int,
    totalPlayers: Int,
    todayTaps: Int,
    currentBadgeKey: String?,
    onStripClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBadge = currentBadgeKey?.let { DailyBadge.fromKey(it) }
        ?: DailyBadge.fromTapCount(todayTaps)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = MohamedLoversPalette.GoldBase.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
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
                        .border(1.5.dp, MohamedLoversPalette.GoldHighlight.copy(alpha = 0.3f), CircleShape),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.daily_rank_strip_rank_of, rank, totalPlayers),
                    color = MohamedLoversPalette.GoldHighlight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (currentBadge != null) {
                    Text(
                        text = " · ",
                        color = MohamedLoversPalette.GoldBase.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                    )
                    Text(
                        text = badgeTitleString(currentBadge),
                        color = MohamedLoversPalette.GoldWarm,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Info button
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MohamedLoversPalette.GoldHighlight.copy(alpha = 0.12f))
                .border(1.dp, MohamedLoversPalette.GoldHighlight.copy(alpha = 0.25f), CircleShape)
                .clickable { onInfoClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ⓘ",
                color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
    }
}

private fun formatCount(n: Int): String {
    if (n < 1000) return "$n"
    val thousands = n / 1000
    val remainder = n % 1000
    return if (remainder == 0) "$thousands,000"
    else "$thousands,${remainder.toString().padStart(3, '0')}"
}
