package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.recap_cta
import tools.mo3ta.salo.generated.resources.recap_personal_best
import tools.mo3ta.salo.generated.resources.recap_rank_label
import tools.mo3ta.salo.generated.resources.recap_taps_down
import tools.mo3ta.salo.generated.resources.recap_taps_up
import tools.mo3ta.salo.generated.resources.recap_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoundRecapSheet(
    rank: Int,
    totalPlayers: Int,
    isPersonalBest: Boolean,
    tapsDelta: Int,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MohamedLoversPalette.SkyTop,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.recap_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MohamedLoversPalette.GoldHighlight,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.recap_rank_label, rank, totalPlayers),
                fontSize = 18.sp,
                color = MohamedLoversPalette.GoldGlow,
                textAlign = TextAlign.Center,
            )
            if (isPersonalBest) {
                Text(
                    text = stringResource(Res.string.recap_personal_best),
                    fontSize = 15.sp,
                    color = MohamedLoversPalette.GoldHighlight,
                    textAlign = TextAlign.Center,
                )
            }
            if (tapsDelta != 0) {
                val absDelta = kotlin.math.abs(tapsDelta)
                val deltaStr = if (tapsDelta > 0)
                    stringResource(Res.string.recap_taps_up, absDelta)
                else
                    stringResource(Res.string.recap_taps_down, absDelta)
                Text(
                    text = deltaStr,
                    fontSize = 14.sp,
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldHighlight,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.recap_cta),
                    fontSize = 16.sp,
                    color = MohamedLoversPalette.SkyTop,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
