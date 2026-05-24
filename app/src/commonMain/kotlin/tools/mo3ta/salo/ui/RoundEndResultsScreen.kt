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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.achievement_celebration_rank_subtitle
import tools.mo3ta.salo.generated.resources.achievement_celebration_rank_title
import tools.mo3ta.salo.generated.resources.recap_cta
import tools.mo3ta.salo.generated.resources.recap_personal_best
import tools.mo3ta.salo.generated.resources.recap_rank_label
import tools.mo3ta.salo.generated.resources.recap_taps_down
import tools.mo3ta.salo.generated.resources.recap_taps_up
import tools.mo3ta.salo.generated.resources.winners_barakah
import tools.mo3ta.salo.generated.resources.winners_subtitle
import tools.mo3ta.salo.generated.resources.winners_title
import tools.mo3ta.salo.presentation.MohamedLoversLeaderboardEntry
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
internal fun RoundEndResultsScreen(
    winnersTop3: List<MohamedLoversLeaderboardEntry>,
    recapRank: Int,
    recapTotalPlayers: Int,
    isPersonalBest: Boolean,
    tapsDelta: Int,
    achievement: Achievement.RankAchievement?,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F1235), MohamedLoversPalette.DeepBlue, Color(0xFF100830))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Trophy
            Text(text = "🏆", fontSize = 52.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.winners_title),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.winners_subtitle),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )

            // Podium
            if (winnersTop3.size >= 3) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    ResultPodiumColumn(entry = winnersTop3[1], rank = 2, barHeight = 60.dp)
                    Spacer(Modifier.width(8.dp))
                    ResultPodiumColumn(entry = winnersTop3[0], rank = 1, barHeight = 80.dp)
                    Spacer(Modifier.width(8.dp))
                    ResultPodiumColumn(entry = winnersTop3[2], rank = 3, barHeight = 44.dp)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.winners_barakah),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            // Divider
            if (recapRank > 0) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.15f))
                Spacer(Modifier.height(20.dp))

                // User rank recap
                Text(
                    text = stringResource(Res.string.recap_rank_label, recapRank, recapTotalPlayers),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
                if (isPersonalBest) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.recap_personal_best),
                        color = MohamedLoversPalette.GoldHighlight,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (tapsDelta != 0) {
                    Spacer(Modifier.height(4.dp))
                    val absDelta = kotlin.math.abs(tapsDelta)
                    val deltaStr = if (tapsDelta > 0)
                        stringResource(Res.string.recap_taps_up, absDelta)
                    else
                        stringResource(Res.string.recap_taps_down, absDelta)
                    Text(
                        text = deltaStr,
                        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Achievement badge
            if (achievement != null) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.15f))
                Spacer(Modifier.height(20.dp))

                val rankEmoji = when (achievement.rank) {
                    1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "🏆"
                }
                Text(text = rankEmoji, fontSize = 40.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.achievement_celebration_rank_title, achievement.rank),
                    color = MohamedLoversPalette.Gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.achievement_celebration_rank_subtitle, achievement.roundKey),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            // CTA button
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldHighlight,
                    contentColor = MohamedLoversPalette.DeepBlue,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.recap_cta),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultPodiumColumn(
    entry: MohamedLoversLeaderboardEntry,
    rank: Int,
    barHeight: Dp,
) {
    val rankColor = when (rank) {
        1 -> MohamedLoversPalette.GoldHighlight
        2 -> MohamedLoversPalette.RankSilver
        else -> MohamedLoversPalette.RankBronze
    }
    val avatarSize = if (rank == 1) 52.dp else 42.dp

    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delay = when (rank) { 1 -> 200; 2 -> 400; else -> 600 }
        kotlinx.coroutines.delay(delay.toLong())
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(scale.value),
    ) {
        if (rank == 1) {
            Text(text = "👑", fontSize = 20.sp)
            Spacer(Modifier.height(2.dp))
        }
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(rankColor.copy(alpha = 0.9f), rankColor))),
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
        Text(
            text = formatResultScore(entry.totalCount),
            color = rankColor,
            fontSize = if (rank == 1) 16.sp else 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(if (rank == 1) 84.dp else 76.dp)
                .height(barHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(rankColor.copy(alpha = 0.3f), rankColor.copy(alpha = 0.06f))
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

private fun formatResultScore(score: Int): String {
    if (score < 1000) return score.toString()
    val thousands = score / 1000
    val remainder = (score % 1000) / 100
    return if (remainder > 0) "$thousands,${(score % 1000).toString().padStart(3, '0')}"
    else "$thousands,000"
}
