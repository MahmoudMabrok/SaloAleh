package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.achievement_celebration_dismiss
import tools.mo3ta.salo.generated.resources.achievement_celebration_rank_subtitle
import tools.mo3ta.salo.generated.resources.achievement_celebration_rank_title
import tools.mo3ta.salo.generated.resources.achievement_celebration_streak_30_subtitle
import tools.mo3ta.salo.generated.resources.achievement_celebration_streak_30_title
import tools.mo3ta.salo.generated.resources.achievement_celebration_streak_7_subtitle
import tools.mo3ta.salo.generated.resources.achievement_celebration_streak_7_title
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
fun AchievementCelebrationDialog(
    achievement: Achievement,
    onDismiss: () -> Unit,
) {
    val (emoji, title, subtitle) = when (achievement) {
        is Achievement.StreakBadge -> when (achievement.type) {
            BadgeType.STREAK_7 -> Triple(
                "🏅",
                stringResource(Res.string.achievement_celebration_streak_7_title),
                stringResource(Res.string.achievement_celebration_streak_7_subtitle),
            )
            BadgeType.STREAK_30 -> Triple(
                "🌟",
                stringResource(Res.string.achievement_celebration_streak_30_title),
                stringResource(Res.string.achievement_celebration_streak_30_subtitle),
            )
        }
        is Achievement.RankAchievement -> {
            val rankEmoji = when (achievement.rank) {
                1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "🏆"
            }
            Triple(
                rankEmoji,
                stringResource(Res.string.achievement_celebration_rank_title, achievement.rank),
                stringResource(Res.string.achievement_celebration_rank_subtitle, achievement.roundKey),
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MohamedLoversPalette.DeepBlue, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = emoji, fontSize = 56.sp)
            Text(
                text = title,
                color = MohamedLoversPalette.Gold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MohamedLoversPalette.Gold),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(Res.string.achievement_celebration_dismiss), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
