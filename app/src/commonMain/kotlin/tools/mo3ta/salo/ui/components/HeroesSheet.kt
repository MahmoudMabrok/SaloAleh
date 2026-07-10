package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.HeroChallenge
import tools.mo3ta.salo.domain.HeroChallengeType
import tools.mo3ta.salo.domain.HeroEntry
import tools.mo3ta.salo.domain.HeroesBoard
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.heroes_challenge_baqiyat
import tools.mo3ta.salo.generated.resources.heroes_challenge_dhikr
import tools.mo3ta.salo.generated.resources.heroes_challenge_empty
import tools.mo3ta.salo.generated.resources.heroes_challenge_istighfar
import tools.mo3ta.salo.generated.resources.heroes_challenge_salawat
import tools.mo3ta.salo.generated.resources.heroes_sheet_subtitle
import tools.mo3ta.salo.generated.resources.heroes_sheet_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroesSheet(
    board: HeroesBoard,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MohamedLoversPalette.SkyTop,
        contentColor = MohamedLoversPalette.GoldGlow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.heroes_sheet_title),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.heroes_sheet_subtitle),
                color = MohamedLoversPalette.GoldBase.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            if (board.date.isNotBlank()) {
                Text(
                    text = board.date,
                    color = MohamedLoversPalette.GoldBase.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }

            board.challenges.forEach { challenge ->
                ChallengeSection(challenge)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ChallengeSection(challenge: HeroChallenge) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MohamedLoversPalette.GoldBase.copy(alpha = 0.05f))
            .border(
                1.dp,
                MohamedLoversPalette.GoldBase.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(challengeTitleRes(challenge.type)),
            color = MohamedLoversPalette.GoldHighlight,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))

        if (challenge.entries.isEmpty()) {
            Text(
                text = stringResource(Res.string.heroes_challenge_empty),
                color = MohamedLoversPalette.GoldBase.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        } else {
            challenge.entries.forEach { entry ->
                HeroRow(entry)
            }
        }
    }
}

@Composable
private fun HeroRow(entry: HeroEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = medalFor(entry.rank),
            fontSize = 16.sp,
        )
        Text(
            text = entry.name,
            color = MohamedLoversPalette.GoldGlow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (entry.countryCode.isNotBlank()) {
            Text(
                text = entry.countryCode.uppercase(),
                color = MohamedLoversPalette.GoldBase.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = entry.count.toString(),
            color = MohamedLoversPalette.GoldHighlight,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun challengeTitleRes(type: HeroChallengeType): StringResource = when (type) {
    HeroChallengeType.Salawat -> Res.string.heroes_challenge_salawat
    HeroChallengeType.Dhikr -> Res.string.heroes_challenge_dhikr
    HeroChallengeType.Baqiyat -> Res.string.heroes_challenge_baqiyat
    HeroChallengeType.Istighfar -> Res.string.heroes_challenge_istighfar
}

private fun medalFor(rank: Int): String = when (rank) {
    1 -> "🥇"
    2 -> "🥈"
    3 -> "🥉"
    else -> "#$rank"
}
