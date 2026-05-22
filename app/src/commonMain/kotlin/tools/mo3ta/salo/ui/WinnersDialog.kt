package tools.mo3ta.salo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.presentation.MohamedLoversLeaderboardEntry
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
internal fun WinnersDialog(
    top3: List<MohamedLoversLeaderboardEntry>,
    onDismiss: () -> Unit,
) {
    if (top3.size < 3) return

    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(400))
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha.value)
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0F1235),
                            MohamedLoversPalette.DeepBlue,
                            Color(0xFF100830),
                        )
                    ),
                    RoundedCornerShape(24.dp),
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "🏆", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.winners_title),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.winners_subtitle),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            // Podium: 2nd - 1st - 3rd
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                PodiumColumn(entry = top3[1], rank = 2, barHeight = 60.dp)
                Spacer(Modifier.width(8.dp))
                PodiumColumn(entry = top3[0], rank = 1, barHeight = 80.dp)
                Spacer(Modifier.width(8.dp))
                PodiumColumn(entry = top3[2], rank = 3, barHeight = 44.dp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.winners_barakah),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldBase,
                    contentColor = MohamedLoversPalette.DeepBlue,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.winners_cta),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    entry: MohamedLoversLeaderboardEntry,
    rank: Int,
    barHeight: androidx.compose.ui.unit.Dp,
) {
    val rankColor = when (rank) {
        1 -> MohamedLoversPalette.GoldHighlight
        2 -> MohamedLoversPalette.RankSilver
        else -> MohamedLoversPalette.RankBronze
    }
    val avatarSize = if (rank == 1) 52.dp else 42.dp
    val scoreFontSize = if (rank == 1) 16.sp else 14.sp

    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delay = when (rank) {
            1 -> 200
            2 -> 400
            else -> 600
        }
        kotlinx.coroutines.delay(delay.toLong())
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(scale.value),
    ) {
        // Crown for 1st
        if (rank == 1) {
            Text(text = "👑", fontSize = 20.sp)
            Spacer(Modifier.height(2.dp))
        }

        // Avatar circle
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(rankColor.copy(alpha = 0.9f), rankColor)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when (rank) { 1 -> "١"; 2 -> "٢"; else -> "٣" },
                color = MohamedLoversPalette.DeepBlue,
                fontSize = if (rank == 1) 22.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(Modifier.height(6.dp))

        // Player tag
        Text(
            text = entry.displayTag,
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center,
        )

        // Score
        Text(
            text = formatScore(entry.totalCount),
            color = rankColor,
            fontSize = scoreFontSize,
            fontWeight = FontWeight.ExtraBold,
        )

        Spacer(Modifier.height(6.dp))

        // Podium bar
        Box(
            modifier = Modifier
                .width(if (rank == 1) 84.dp else 76.dp)
                .height(barHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            rankColor.copy(alpha = 0.3f),
                            rankColor.copy(alpha = 0.06f),
                        )
                    ),
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = when (rank) { 1 -> "١"; 2 -> "٢"; else -> "٣" },
                color = rankColor.copy(alpha = 0.25f),
                fontSize = if (rank == 1) 32.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun formatScore(score: Int): String {
    if (score < 1000) return score.toString()
    val thousands = score / 1000
    val remainder = (score % 1000) / 100
    return if (remainder > 0) "$thousands,${(score % 1000).toString().padStart(3, '0')}"
    else "$thousands,000"
}
