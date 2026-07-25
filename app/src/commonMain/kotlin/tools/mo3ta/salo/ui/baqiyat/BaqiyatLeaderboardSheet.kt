package tools.mo3ta.salo.ui.baqiyat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.BaqiyatLeaderboardEntry
import tools.mo3ta.salo.ui.components.ChallengeMedalPills
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.dhikr_leaderboard_empty
import tools.mo3ta.salo.generated.resources.dhikr_leaderboard_title
import tools.mo3ta.salo.generated.resources.dhikr_rank_subtitle
import tools.mo3ta.salo.ui.dhikr.DhikrColors

private val Gold = Color(0xFFC9A84C)
private val Silver = Color(0xFF9BA8C0)
private val Bronze = Color(0xFFB07A50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaqiyatLeaderboardSheet(
    entries: List<BaqiyatLeaderboardEntry>,
    currentUid: String,
    participantCount: Int,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DhikrColors.Cream,
        contentColor = DhikrColors.Ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(participantCount = participantCount)

            HorizontalDivider(color = DhikrColors.Stroke, modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = DhikrColors.Green,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.dhikr_leaderboard_empty),
                            color = DhikrColors.Muted,
                            fontSize = 14.sp,
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(entries, key = { it.uid }) { entry ->
                            LeaderboardRow(
                                entry = entry,
                                isCurrentUser = entry.uid == currentUid,
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(participantCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.dhikr_leaderboard_title),
            color = DhikrColors.Ink,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
        )
        if (participantCount > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DhikrColors.Green.copy(alpha = 0.09f),
            ) {
                Text(
                    text = stringResource(Res.string.dhikr_rank_subtitle, participantCount),
                    color = DhikrColors.Green,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    entry: BaqiyatLeaderboardEntry,
    isCurrentUser: Boolean,
) {
    val bgColor = if (isCurrentUser) DhikrColors.Green.copy(alpha = 0.07f) else Color.Transparent
    val borderColor = if (isCurrentUser) DhikrColors.LightGreen.copy(alpha = 0.35f) else Color.Transparent
    val rankColor = when (entry.rank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> if (isCurrentUser) DhikrColors.Green else DhikrColors.Ink.copy(alpha = 0.45f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.rank.toString(),
            color = rankColor,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DhikrColors.Track)
                .then(
                    if (entry.rank == 1) Modifier.border(1.5.dp, Gold, CircleShape)
                    else if (isCurrentUser) Modifier.border(1.5.dp, DhikrColors.LightGreen.copy(alpha = 0.5f), CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = countryCodeToFlag(entry.countryCode),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (entry.nickname.isNotBlank()) {
                Text(
                    text = entry.nickname,
                    color = if (isCurrentUser) DhikrColors.Green else DhikrColors.Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (entry.streak > 0 || entry.goldMedals > 0 || entry.silverMedals > 0 || entry.bronzeMedals > 0) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (entry.streak > 0) {
                        Text(
                            text = "🔥${entry.streak}",
                            color = Gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Gold.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    ChallengeMedalPills(
                        goldMedals = entry.goldMedals,
                        silverMedals = entry.silverMedals,
                        bronzeMedals = entry.bronzeMedals,
                    )
                }
            }
        }

        Text(
            text = entry.count.toString(),
            color = if (isCurrentUser) DhikrColors.Green else DhikrColors.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
        )
    }
}

private fun countryCodeToFlag(code: String): String {
    if (code.length < 2) return "🌍"
    val base = 0x1F1E6 - 'A'.code
    return buildString {
        for (char in code.uppercase().take(2)) {
            val cp = base + char.code
            val high = ((cp - 0x10000) ushr 10) + 0xD800
            val low = ((cp - 0x10000) and 0x3FF) + 0xDC00
            append(high.toChar())
            append(low.toChar())
        }
    }
}
