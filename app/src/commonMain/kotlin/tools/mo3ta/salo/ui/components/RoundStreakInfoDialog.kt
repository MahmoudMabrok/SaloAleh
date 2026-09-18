package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.ROUND_STREAK_TARGET
import tools.mo3ta.salo.domain.STREAK_FREEZE_MAX_DAYS
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.achievements_dialog_ok
import tools.mo3ta.salo.generated.resources.leaderboard_streak_info_desc
import tools.mo3ta.salo.generated.resources.leaderboard_streak_info_title
import tools.mo3ta.salo.generated.resources.streak_freeze_cta
import tools.mo3ta.salo.generated.resources.streak_freeze_day
import tools.mo3ta.salo.generated.resources.streak_freeze_days
import tools.mo3ta.salo.generated.resources.streak_freeze_desc
import tools.mo3ta.salo.generated.resources.streak_freeze_remaining

@Composable
fun RoundStreakInfoDialog(
    streak: Int,
    onDismiss: () -> Unit,
    isSelf: Boolean = false,
    freezeRemaining: Int = 0,
    missedDays: Int = 0,
    freezeUntil: String? = null,
    onFreeze: ((Int) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MohamedLoversPalette.SkyTop,
        shape = RoundedCornerShape(20.dp),
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🔥 $streak",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MohamedLoversPalette.GoldHighlight,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.leaderboard_streak_info_title),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.leaderboard_streak_info_desc, ROUND_STREAK_TARGET),
                    color = MohamedLoversPalette.GoldBase.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                )
                if (isSelf) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.streak_freeze_desc, STREAK_FREEZE_MAX_DAYS),
                        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    FreezeSlots(remaining = freezeRemaining, missed = missedDays)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            Res.string.streak_freeze_remaining,
                            freezeRemaining,
                            STREAK_FREEZE_MAX_DAYS,
                        ),
                        color = MohamedLoversPalette.GoldBase.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (freezeUntil != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = freezeUntil,
                            color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                        )
                    }
                    if (onFreeze != null && freezeRemaining > 0) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(Res.string.streak_freeze_cta),
                            color = MohamedLoversPalette.GoldGlow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (days in 1..freezeRemaining) {
                                FreezeDayChip(
                                    days = days,
                                    onClick = { onFreeze(days) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.achievements_dialog_ok),
                    color = MohamedLoversPalette.GoldHighlight,
                )
            }
        },
    )
}

@Composable
private fun FreezeSlots(remaining: Int, missed: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(STREAK_FREEZE_MAX_DAYS) { index ->
            val used = index < missed
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (used) MohamedLoversPalette.GoldBase.copy(alpha = 0.25f)
                        else MohamedLoversPalette.GoldHighlight,
                    ),
            )
        }
    }
}

@Composable
private fun FreezeDayChip(days: Int, onClick: () -> Unit) {
    val label = if (days == 1) {
        stringResource(Res.string.streak_freeze_day, days)
    } else {
        stringResource(Res.string.streak_freeze_days, days)
    }
    Text(
        text = label,
        color = MohamedLoversPalette.SkyTop,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MohamedLoversPalette.GoldHighlight)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
