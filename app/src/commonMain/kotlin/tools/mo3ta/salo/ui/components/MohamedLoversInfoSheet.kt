package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tools.mo3ta.salo.ui.shareBitmap
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.mohamed_lovers_banner
import tools.mo3ta.salo.generated.resources.mohamed_lovers_competition_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_info_sheet_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_ornament_divider
import tools.mo3ta.salo.generated.resources.mohamed_lovers_rank_number
import tools.mo3ta.salo.generated.resources.mohamed_lovers_all_time_total_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_leaderboard_empty
import tools.mo3ta.salo.generated.resources.mohamed_lovers_leaderboard_refresh_note
import tools.mo3ta.salo.generated.resources.mohamed_lovers_leaderboard_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_round_player_count_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_round_total_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_round_end_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_rank_pending_top
import tools.mo3ta.salo.generated.resources.mohamed_lovers_winner_placeholder
import tools.mo3ta.salo.generated.resources.mohamed_lovers_winner_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_share_rank
import tools.mo3ta.salo.presentation.MohamedLoversLeaderboardEntry
import tools.mo3ta.salo.presentation.MohamedLoversUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MohamedLoversInfoSheet(
    isOpen: Boolean,
    state: MohamedLoversUiState,
    onDismiss: () -> Unit,
    onCopyWinnerCode: (String) -> Unit,
) {
    if (!isOpen) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MohamedLoversPalette.SkyTop,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MohamedLoversPalette.GoldGlow.copy(alpha = 0.25f)),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.mohamed_lovers_info_sheet_title),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.display,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W500,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.95f),
            )
            CompetitionCard(roundEndLabel = state.roundEndLabel, roundPlayerCount = state.roundPlayerCount)
            TotalsCard(roundTotal = state.roundTotal, allTimeTotal = state.allTimeTotal)
            ShareButton(state = state)
            LeaderboardCard(
                topPlayers = state.topPlayers,
                selfEntry = state.selfEntry,
                selfInTop = state.selfInTop,
            )
            if (state.isWinner) {
                WinnerCard(winnerCode = state.winnerCode, onCopyWinnerCode = onCopyWinnerCode)
            }
            FridayNoteCard()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ShareButton(state: MohamedLoversUiState) {
    val selfEntry = state.selfEntry ?: state.topPlayers.firstOrNull { it.isCurrentUser }
    val graphicsLayer = rememberGraphicsLayer()
    var capturing by remember { mutableStateOf(false) }

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val dateLabel = "${now.dayOfMonth}/${now.monthNumber}/${now.year}"

    val shareData = ShareCardData(
        displayTag = selfEntry?.displayTag ?: "",
        rank = selfEntry?.rank ?: 0,
        userScore = selfEntry?.totalCount ?: 0,
        roundTotal = state.roundTotal,
        roundPlayerCount = state.roundPlayerCount,
        dateLabel = dateLabel,
    )

    // Render for one frame on tap, then capture.
    // layout(0,0)+place(0,0)+alpha(0.002f) ensures Compose draws (not optimized away) but stays invisible.
    if (capturing) {
        Box(
            modifier = Modifier
                .layout { measurable, _ ->
                    val w = 400.dp.roundToPx()
                    val h = 620.dp.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(0, 0) { placeable.place(0, 0) }
                }
                .graphicsLayer { alpha = 0.002f }
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawContent()
                }
        ) {
            ShareCard(data = shareData)
        }
        LaunchedEffect(Unit) {
            withFrameMillis { }
            val bitmap = graphicsLayer.toImageBitmap()
            capturing = false
            shareBitmap(bitmap)
        }
    }

    Button(
        onClick = { capturing = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MohamedLoversPalette.GoldGlow,
            contentColor = MohamedLoversPalette.SkyTop,
        ),
    ) {
        Text(
            text = stringResource(Res.string.mohamed_lovers_share_rank),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.display,
                fontSize = 15.sp,
                fontWeight = FontWeight.W500,
            ),
        )
    }
}

@Composable
private fun CompetitionCard(roundEndLabel: String, roundPlayerCount: Int) {
    val dotAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(animation = tween(1800), repeatMode = RepeatMode.Reverse),
    )
    SheetCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.mohamed_lovers_competition_title),
                style = TextStyle(fontFamily = MohamedLoversFonts.display, fontSize = 14.sp, fontWeight = FontWeight.W500),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f),
            )
            if (roundPlayerCount > 0) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MohamedLoversPalette.GoldBase.copy(alpha = 0.1f))
                        .border(
                            width = 1.dp,
                            color = MohamedLoversPalette.GoldBase.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MohamedLoversPalette.GoldBase.copy(alpha = dotAlpha)),
                    )
                    Text(
                        text = stringResource(Res.string.mohamed_lovers_round_player_count_label, roundPlayerCount),
                        style = TextStyle(
                            fontFamily = MohamedLoversFonts.arabic,
                            fontWeight = FontWeight.W400,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                        ),
                        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.75f),
                    )
                }
            }
        }
        if (roundEndLabel.isNotBlank()) {
            Text(
                text = stringResource(Res.string.mohamed_lovers_round_end_label, roundEndLabel),
                style = bodyStyle().copy(fontSize = 12.sp),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun TotalsCard(roundTotal: Int, allTimeTotal: Long) {
    val easeOutExpo = remember { CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }
    val combinedTotal = allTimeTotal + roundTotal
    val animatedCombined = remember { Animatable(0f) }
    val animatedRound = remember { Animatable(0f) }
    LaunchedEffect(combinedTotal) {
        val delta = combinedTotal - animatedCombined.value.toLong()
        val duration = if (delta > 5) 2000 else 200
        animatedCombined.animateTo(combinedTotal.toFloat(), tween(duration, easing = easeOutExpo))
    }
    LaunchedEffect(roundTotal) {
        val delta = roundTotal - animatedRound.value.roundToInt()
        val duration = if (delta > 5) 2000 else 200
        animatedRound.animateTo(roundTotal.toFloat(), tween(duration, easing = easeOutExpo))
    }

    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(1600), repeatMode = RepeatMode.Reverse),
    )
    SheetCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = animatedCombined.value.toLong().formatCount(),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.display,
                    fontWeight = FontWeight.W600,
                    fontSize = 52.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = glowAlpha),
            )
            Text(
                text = stringResource(Res.string.mohamed_lovers_all_time_total_label),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.arabic,
                    fontWeight = FontWeight.W400,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(14.dp))
            // Ornament divider: line ◆ line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(0.6f),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, MohamedLoversPalette.GoldBase.copy(alpha = 0.5f))
                            )
                        )
                )
                Text(
                    text = stringResource(Res.string.mohamed_lovers_ornament_divider),
                    fontSize = 8.sp,
                    color = MohamedLoversPalette.GoldBase,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(MohamedLoversPalette.GoldBase.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = animatedRound.value.roundToInt().toLong().formatCount(),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.display,
                    fontWeight = FontWeight.W400,
                    fontSize = 26.sp,
                    letterSpacing = 0.sp,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.65f),
            )
            Text(
                text = stringResource(Res.string.mohamed_lovers_round_total_label),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.arabic,
                    fontWeight = FontWeight.W400,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun LeaderboardCard(
    topPlayers: List<MohamedLoversLeaderboardEntry>,
    selfEntry: MohamedLoversLeaderboardEntry?,
    selfInTop: Boolean,
) {
    SheetCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.mohamed_lovers_leaderboard_title),
                style = TextStyle(fontFamily = MohamedLoversFonts.display, fontSize = 14.sp, fontWeight = FontWeight.W500),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.95f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.mohamed_lovers_leaderboard_refresh_note),
                style = bodyStyle().copy(fontSize = 11.sp),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f),
            )
        }
        if (selfEntry != null && !selfInTop) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MohamedLoversPalette.GoldBase.copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        color = MohamedLoversPalette.GoldBase.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selfEntry.displayedRank}${selfEntry.displayTag}",
                    style = bodyStyle().copy(fontWeight = FontWeight.W700),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.95f),
                )
                Text(
                    text = selfEntry.totalCount.toString(),
                    style = bodyStyle().copy(fontWeight = FontWeight.W700),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                )
            }
            Text(
                text = stringResource(Res.string.mohamed_lovers_rank_pending_top),
                style = bodyStyle().copy(fontSize = 11.sp),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
            )
        }
        if (topPlayers.isEmpty() && selfEntry == null) {
            Text(
                text = stringResource(Res.string.mohamed_lovers_leaderboard_empty),
                style = bodyStyle(),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.65f),
            )
        } else {
            topPlayers.forEach { entry -> LeaderboardRow(entry = entry, pinned = false) }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: MohamedLoversLeaderboardEntry, pinned: Boolean) {
    val rankColor = when (entry.rank) {
        1 -> MohamedLoversPalette.GoldHighlight
        2 -> MohamedLoversPalette.RankSilver
        3 -> MohamedLoversPalette.RankBronze
        else -> MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f)
    }
    val backgroundColor = when {
        pinned -> MohamedLoversPalette.GoldBase.copy(alpha = 0.2f)
        entry.isCurrentUser -> MohamedLoversPalette.GoldBase.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entry.rank > 0) {
                Text(
                    text = stringResource(Res.string.mohamed_lovers_rank_number, entry.rank),
                    style = bodyStyle().copy(
                        fontWeight = FontWeight.W700,
                        fontSize = if (entry.rank <= 3) 15.sp else 13.sp,
                    ),
                    color = rankColor,
                )
            }
            Text(
                text = entry.displayTag,
                style = bodyStyle().copy(
                    fontWeight = if (entry.isCurrentUser) FontWeight.W700 else FontWeight.W400,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.92f),
            )
        }
        Text(
            text = entry.totalCount.toString(),
            style = bodyStyle(),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun WinnerCard(winnerCode: String, onCopyWinnerCode: (String) -> Unit) {
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = MohamedLoversPalette.GoldHighlight)
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(Res.string.mohamed_lovers_winner_title),
                style = TextStyle(fontFamily = MohamedLoversFonts.display, fontSize = 14.sp, fontWeight = FontWeight.W500),
                color = MohamedLoversPalette.GoldGlow,
            )
        }
        Text(
            text = winnerCode.ifBlank { stringResource(Res.string.mohamed_lovers_winner_placeholder) },
            style = bodyStyle(),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
        )
        if (winnerCode.isNotBlank()) {
            IconButton(onClick = { onCopyWinnerCode(winnerCode) }) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = MohamedLoversPalette.GoldBase)
            }
        }
    }
}

@Composable
private fun FridayNoteCard() {
    SheetCard {
        Text(
            text = stringResource(Res.string.mohamed_lovers_banner),
            style = bodyStyle(),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MohamedLoversPalette.GoldGlow.copy(alpha = 0.04f))
            .border(width = 1.dp, color = MohamedLoversPalette.GoldBase.copy(alpha = 0.25f), shape = RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private fun Long.formatCount(): String {
    val s = toString()
    val result = StringBuilder()
    s.reversed().forEachIndexed { i, c -> if (i > 0 && i % 3 == 0) result.append(','); result.append(c) }
    return result.reverse().toString()
}

private fun bodyStyle() = TextStyle(
    fontFamily = MohamedLoversFonts.body,
    fontWeight = FontWeight.W400,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)
