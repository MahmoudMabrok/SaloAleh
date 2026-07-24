package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.achievements_dialog_ok
import tools.mo3ta.salo.generated.resources.challenge_medals_info_desc
import tools.mo3ta.salo.generated.resources.challenge_medals_info_title
import tools.mo3ta.salo.generated.resources.leaderboard_medals_info_bronze
import tools.mo3ta.salo.generated.resources.leaderboard_medals_info_gold
import tools.mo3ta.salo.generated.resources.leaderboard_medals_info_silver

private val MedalGold = Color(0xFFC9A84C)
private val MedalSilver = Color(0xFF9BA8C0)
private val MedalBronze = Color(0xFFB07A50)

/**
 * Cumulative daily-podium medals (🥇/🥈/🥉 + count) shown next to a challenge
 * participant's name. Self-contained: tapping any pill opens an explanation
 * dialog. Renders nothing when the participant has no medals. Shared across
 * every challenge leaderboard sheet so the styling stays identical.
 */
@Composable
fun ChallengeMedalPills(
    goldMedals: Int,
    silverMedals: Int,
    bronzeMedals: Int,
    modifier: Modifier = Modifier,
) {
    if (goldMedals <= 0 && silverMedals <= 0 && bronzeMedals <= 0) return

    var showInfo by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (goldMedals > 0) MedalPill("🥇", goldMedals, MedalGold) { showInfo = true }
        if (silverMedals > 0) MedalPill("🥈", silverMedals, MedalSilver) { showInfo = true }
        if (bronzeMedals > 0) MedalPill("🥉", bronzeMedals, MedalBronze) { showInfo = true }
    }

    if (showInfo) {
        ChallengeMedalInfoDialog(onDismiss = { showInfo = false })
    }
}

@Composable
private fun MedalPill(emoji: String, count: Int, color: Color, onClick: () -> Unit) {
    Text(
        text = "$emoji$count",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Explains the daily challenge podium medals (🥇/🥈/🥉). */
@Composable
private fun ChallengeMedalInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🏅", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.challenge_medals_info_title),
                    color = MedalGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.challenge_medals_info_desc),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(14.dp))
                MedalInfoRow("🥇", Res.string.leaderboard_medals_info_gold, MedalGold)
                Spacer(Modifier.height(8.dp))
                MedalInfoRow("🥈", Res.string.leaderboard_medals_info_silver, MedalSilver)
                Spacer(Modifier.height(8.dp))
                MedalInfoRow("🥉", Res.string.leaderboard_medals_info_bronze, MedalBronze)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.achievements_dialog_ok), color = MedalGold)
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
