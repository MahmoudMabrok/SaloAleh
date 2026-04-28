package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.mohamed_lovers_banner
import tools.mo3ta.salo.generated.resources.mohamed_lovers_country_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_info_sheet_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_leaderboard_empty
import tools.mo3ta.salo.generated.resources.mohamed_lovers_leaderboard_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_network_time
import tools.mo3ta.salo.generated.resources.mohamed_lovers_rank_pending_top
import tools.mo3ta.salo.generated.resources.mohamed_lovers_self_tag_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_status_firebase_off
import tools.mo3ta.salo.generated.resources.mohamed_lovers_status_open
import tools.mo3ta.salo.generated.resources.mohamed_lovers_status_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_status_waiting_network
import tools.mo3ta.salo.generated.resources.mohamed_lovers_winner_placeholder
import tools.mo3ta.salo.generated.resources.mohamed_lovers_winner_title
import tools.mo3ta.salo.presentation.MohamedLoversLeaderboardEntry
import tools.mo3ta.salo.presentation.MohamedLoversStatus
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
            StatusCard(state = state)
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
private fun StatusCard(state: MohamedLoversUiState) {
    SheetCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.mohamed_lovers_status_title),
                style = TextStyle(fontFamily = MohamedLoversFonts.display, fontSize = 14.sp, fontWeight = FontWeight.W500),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.95f),
                modifier = Modifier.weight(1f),
            )
        }
        val statusText = when (state.status) {
            MohamedLoversStatus.WaitingNetwork -> stringResource(Res.string.mohamed_lovers_status_waiting_network)
            MohamedLoversStatus.FirebaseOff -> stringResource(Res.string.mohamed_lovers_status_firebase_off)
            MohamedLoversStatus.Open -> stringResource(Res.string.mohamed_lovers_status_open)
        }
        Text(text = statusText, style = bodyStyle(), color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.78f))
        if (state.networkTimeLabel.isNotBlank()) {
            Text(
                text = stringResource(Res.string.mohamed_lovers_network_time, state.networkTimeLabel),
                style = bodyStyle().copy(fontSize = 12.sp),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
            )
        }
        if (state.countryCode.isNotBlank()) {
            Text(
                text = stringResource(Res.string.mohamed_lovers_country_label, state.countryCode),
                style = bodyStyle().copy(fontSize = 12.sp),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
            )
        }
        if (state.selfDisplayTag.isNotBlank()) {
            Text(
                text = stringResource(Res.string.mohamed_lovers_self_tag_label, state.selfDisplayTag),
                style = bodyStyle().copy(fontSize = 12.sp),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
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
        Text(
            text = stringResource(Res.string.mohamed_lovers_leaderboard_title),
            style = TextStyle(fontFamily = MohamedLoversFonts.display, fontSize = 14.sp, fontWeight = FontWeight.W500),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.95f),
        )
        if (selfEntry != null && !selfInTop) {
            LeaderboardRow(entry = selfEntry, pinned = true)
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
    ) {
        Text(
            text = if (entry.rank > 0) "#${entry.rank} ${entry.displayTag}" else entry.displayTag,
            style = bodyStyle().copy(fontWeight = if (entry.isCurrentUser) FontWeight.W700 else FontWeight.W400),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.92f),
        )
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

private fun bodyStyle() = TextStyle(
    fontFamily = MohamedLoversFonts.body,
    fontWeight = FontWeight.W400,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)
