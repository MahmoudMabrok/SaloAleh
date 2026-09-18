package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.data.engagement.WeeklyGoalProgress
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.weekly_goal_completed
import tools.mo3ta.salo.generated.resources.weekly_goal_days_left
import tools.mo3ta.salo.generated.resources.weekly_goal_ends_today
import tools.mo3ta.salo.generated.resources.weekly_goal_progress
import tools.mo3ta.salo.generated.resources.weekly_goal_set_cta
import tools.mo3ta.salo.generated.resources.weekly_goal_this_week
import tools.mo3ta.salo.generated.resources.weekly_goal_title

@Composable
fun WeeklyGoalBar(
    progress: WeeklyGoalProgress,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDark: Boolean = true,
) {
    val ink = if (onDark) Color.White else Color(0xFF3A2A1C)
    val muted = ink.copy(alpha = if (onDark) 0.62f else 0.55f)
    val surface = if (onDark) accent.copy(alpha = 0.14f) else accent.copy(alpha = 0.10f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
            .background(surface)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (progress.completed) Icons.Default.Check else Icons.Default.Flag,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (progress.completed) {
                    stringResource(Res.string.weekly_goal_completed)
                } else if (progress.hasGoal) {
                    stringResource(Res.string.weekly_goal_title)
                } else {
                    stringResource(Res.string.weekly_goal_set_cta)
                },
                color = if (progress.hasGoal) ink else accent,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (progress.hasGoal) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(Res.string.weekly_goal_title),
                    tint = muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (progress.hasGoal) {
            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(
                        Res.string.weekly_goal_progress,
                        formatWeeklyNumber(progress.count),
                        formatWeeklyNumber(progress.goal),
                    ),
                    color = accent,
                    fontSize = if (compact) 13.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (progress.daysLeft <= 0) {
                        stringResource(Res.string.weekly_goal_ends_today)
                    } else {
                        stringResource(Res.string.weekly_goal_days_left, progress.daysLeft)
                    },
                    color = muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
            Spacer(Modifier.height(7.dp))
            WeeklyGoalTrack(fraction = progress.fraction, accent = accent, onDark = onDark)
        } else if (!compact) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.weekly_goal_this_week),
                color = muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
internal fun WeeklyGoalTrack(
    fraction: Float,
    accent: Color,
    onDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = if (onDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(CircleShape)
                .background(accent),
        )
    }
}

internal fun formatWeeklyNumber(value: Int): String {
    val negative = value < 0
    val digits = (if (negative) -value else value).toString()
    val grouped = buildString {
        for ((index, char) in digits.withIndex()) {
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(char)
        }
    }
    return if (negative) "-$grouped" else grouped
}
