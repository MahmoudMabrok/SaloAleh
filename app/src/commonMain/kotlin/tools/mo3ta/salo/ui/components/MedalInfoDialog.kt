package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.achievements_dialog_ok
import tools.mo3ta.salo.generated.resources.leaderboard_medals_info_bronze
import tools.mo3ta.salo.generated.resources.leaderboard_medals_info_desc
import tools.mo3ta.salo.generated.resources.leaderboard_medals_info_gold
import tools.mo3ta.salo.generated.resources.leaderboard_medals_info_silver
import tools.mo3ta.salo.generated.resources.leaderboard_medals_info_title

/** Explains the weekly-competition podium medals (🥇/🥈/🥉) shown next to a player's name. */
@Composable
fun MedalInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MohamedLoversPalette.SkyTop,
        shape = RoundedCornerShape(20.dp),
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🏅", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.leaderboard_medals_info_title),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.leaderboard_medals_info_desc),
                    color = MohamedLoversPalette.GoldBase.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(14.dp))
                MedalInfoRow("🥇", Res.string.leaderboard_medals_info_gold, MohamedLoversPalette.GoldHighlight)
                Spacer(Modifier.height(8.dp))
                MedalInfoRow("🥈", Res.string.leaderboard_medals_info_silver, MohamedLoversPalette.RankSilver)
                Spacer(Modifier.height(8.dp))
                MedalInfoRow("🥉", Res.string.leaderboard_medals_info_bronze, MohamedLoversPalette.RankBronze)
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
private fun MedalInfoRow(emoji: String, descRes: StringResource, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text = stringResource(descRes),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp,
        )
    }
}
