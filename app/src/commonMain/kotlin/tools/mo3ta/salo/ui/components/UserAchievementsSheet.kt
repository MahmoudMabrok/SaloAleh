package tools.mo3ta.salo.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.domain.UserAchievement
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.achievements_empty_history
import tools.mo3ta.salo.generated.resources.achievements_round_label
import tools.mo3ta.salo.generated.resources.achievements_round_rank
import tools.mo3ta.salo.generated.resources.user_achievements_sheet_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserAchievementsSheet(
    uid: String,
    displayTag: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val repository: MohamedLoversRepository = koinInject()

    var isLoading by remember(uid) { mutableStateOf(true) }
    var entries by remember(uid) { mutableStateOf<List<Pair<String, UserAchievement>>>(emptyList()) }

    LaunchedEffect(uid) {
        repository.fetchUserAchievements(uid)
            .onSuccess { map ->
                entries = map.entries
                    .map { it.key to it.value }
                    .sortedByDescending { it.first }
            }
        isLoading = false
    }

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
                text = stringResource(Res.string.user_achievements_sheet_title),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.display,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W500,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = displayTag,
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.body,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W400,
                    letterSpacing = 0.5.sp,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MohamedLoversPalette.GoldHighlight)
                    }
                }
                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.achievements_empty_history),
                            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    entries.forEach { (roundKey, achievement) ->
                        AchievementRow(roundKey = roundKey, achievement = achievement)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(roundKey: String, achievement: UserAchievement) {
    val medalEmoji = when (achievement.rank) {
        1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "🏅"
    }
    val isTop3 = achievement.rank in 1..3
    val dotColor = when (achievement.rank) {
        1 -> MohamedLoversPalette.GoldHighlight
        2 -> MohamedLoversPalette.RankSilver
        3 -> MohamedLoversPalette.RankBronze
        else -> MohamedLoversPalette.GoldGlow.copy(alpha = 0.18f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isTop3) MohamedLoversPalette.GoldBase.copy(alpha = 0.08f)
                else MohamedLoversPalette.GoldGlow.copy(alpha = 0.04f),
            )
            .border(
                1.dp,
                if (isTop3) MohamedLoversPalette.GoldBase.copy(alpha = 0.28f)
                else MohamedLoversPalette.GoldBase.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(dotColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = medalEmoji, fontSize = 14.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.achievements_round_rank, achievement.rank),
                color = MohamedLoversPalette.GoldHighlight,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.achievements_round_label),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f),
                    fontSize = 10.sp,
                )
                Text(
                    text = roundKey,
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
