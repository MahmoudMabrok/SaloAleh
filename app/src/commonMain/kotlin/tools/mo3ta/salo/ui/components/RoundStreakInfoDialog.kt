package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.ROUND_STREAK_TARGET
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.achievements_dialog_ok
import tools.mo3ta.salo.generated.resources.leaderboard_streak_info_desc
import tools.mo3ta.salo.generated.resources.leaderboard_streak_info_title

@Composable
fun RoundStreakInfoDialog(
    streak: Int,
    onDismiss: () -> Unit,
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
