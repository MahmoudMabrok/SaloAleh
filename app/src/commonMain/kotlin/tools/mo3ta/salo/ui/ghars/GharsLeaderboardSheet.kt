package tools.mo3ta.salo.ui.ghars

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.GharsLeaderboardEntry
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.ghars_unit
import tools.mo3ta.salo.generated.resources.zabad_leaderboard_empty
import tools.mo3ta.salo.generated.resources.zabad_leaderboard_rank_new
import tools.mo3ta.salo.generated.resources.zabad_leaderboard_title
import tools.mo3ta.salo.generated.resources.zabad_rank_subtitle

private val Gold = Color(0xFFC9A84C)
private val Silver = Color(0xFF9BA8C0)
private val Bronze = Color(0xFFB07A50)
private val RankUp = Color(0xFF5FA882)
private val RankDown = Color(0xFFC47A6B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GharsLeaderboardSheet(
    entries: List<GharsLeaderboardEntry>,
    currentUid: String,
    participantCount: Int,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GharsColors.SandPale,
        contentColor = GharsColors.SheetInk,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(participantCount = participantCount)

            HorizontalDivider(color = GharsColors.SheetStroke, modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = GharsColors.Accent,
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
                            text = stringResource(Res.string.zabad_leaderboard_empty),
                            color = GharsColors.SheetMuted,
                            fontSize = 14.sp,
                            fontFamily = ibmPlexArabicFamily(),
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
            text = stringResource(Res.string.zabad_leaderboard_title),
            color = GharsColors.SheetInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = arefRuqaaFamily(),
        )
        if (participantCount > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GharsColors.Accent.copy(alpha = 0.09f),
            ) {
                Text(
                    text = stringResource(Res.string.zabad_rank_subtitle, participantCount),
                    color = GharsColors.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ibmPlexArabicFamily(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    entry: GharsLeaderboardEntry,
    isCurrentUser: Boolean,
) {
    val bgColor = if (isCurrentUser) GharsColors.Accent.copy(alpha = 0.07f) else Color.Transparent
    val borderColor = if (isCurrentUser) GharsColors.Accent.copy(alpha = 0.35f) else Color.Transparent
    val rankColor = when (entry.rank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> if (isCurrentUser) GharsColors.Accent else GharsColors.SheetInk.copy(alpha = 0.45f)
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
        Column(
            modifier = Modifier.width(38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = entry.rank.toString(),
                color = rankColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ibmPlexArabicFamily(),
                lineHeight = 20.sp,
            )
            RankChangeLabel(entry.rankChange)
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GharsColors.SheetTrack)
                .then(
                    if (entry.rank == 1) Modifier.border(1.5.dp, Gold, CircleShape)
                    else if (isCurrentUser) Modifier.border(1.5.dp, GharsColors.Accent.copy(alpha = 0.5f), CircleShape)
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

        if (entry.nickname.isNotBlank()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.nickname,
                    color = if (isCurrentUser) GharsColors.Accent else GharsColors.SheetInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ibmPlexArabicFamily(),
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = entry.count.toString(),
                color = if (isCurrentUser) GharsColors.Accent else GharsColors.SheetInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ibmPlexArabicFamily(),
            )
            Text(
                text = stringResource(Res.string.ghars_unit),
                color = GharsColors.SheetMuted,
                fontSize = 10.sp,
                fontFamily = ibmPlexArabicFamily(),
            )
        }
    }
}

@Composable
private fun RankChangeLabel(rankChange: String) {
    when (rankChange) {
        "up" -> Text(
            text = "▲",
            color = RankUp,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
        )
        "down" -> Text(
            text = "▼",
            color = RankDown,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
        )
        "new" -> Text(
            text = stringResource(Res.string.zabad_leaderboard_rank_new),
            color = GharsColors.Accent,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ibmPlexArabicFamily(),
            lineHeight = 10.sp,
        )
        else -> Text(
            text = "—",
            color = GharsColors.SheetMuted,
            fontSize = 9.sp,
            lineHeight = 10.sp,
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
