package tools.mo3ta.salo.ui.albaqara

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.ALBAQARA_PAGE_COUNT
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.albaqara_quran_credit_after
import tools.mo3ta.salo.generated.resources.albaqara_quran_credit_body
import tools.mo3ta.salo.generated.resources.albaqara_quran_credit_confirm
import tools.mo3ta.salo.generated.resources.albaqara_quran_credit_current
import tools.mo3ta.salo.generated.resources.albaqara_quran_credit_dismiss
import tools.mo3ta.salo.generated.resources.albaqara_quran_credit_title

/**
 * Offered after each Al-Baqara reading. The before→after row is the load-bearing element:
 * a user who has already logged the 48 pages sees the double-count and dismisses on sight.
 */
@Composable
internal fun AlBaqaraQuranCreditDialog(
    quranTodayCount: Int,
    isCrediting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AlBaqaraColors.Ink, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "📖",
                fontSize = 44.sp,
            )
            Text(
                text = stringResource(Res.string.albaqara_quran_credit_title),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
            )
            Text(
                text = stringResource(Res.string.albaqara_quran_credit_body, ALBAQARA_PAGE_COUNT),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            BeforeAfterRow(
                before = quranTodayCount,
                after = quranTodayCount + ALBAQARA_PAGE_COUNT,
            )

            Spacer(Modifier.height(2.dp))

            Button(
                onClick = onConfirm,
                enabled = !isCrediting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AlBaqaraColors.LightAccent),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.albaqara_quran_credit_confirm, ALBAQARA_PAGE_COUNT),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            TextButton(
                onClick = onDismiss,
                enabled = !isCrediting,
            ) {
                Text(
                    text = stringResource(Res.string.albaqara_quran_credit_dismiss),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun BeforeAfterRow(
    before: Int,
    after: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CountColumn(
            label = stringResource(Res.string.albaqara_quran_credit_current),
            value = before,
            valueColor = Color.White.copy(alpha = 0.7f),
        )
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "←",
                color = AlBaqaraColors.LightAccent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        CountColumn(
            label = stringResource(Res.string.albaqara_quran_credit_after),
            value = after,
            valueColor = Color.White,
        )
    }
}

@Composable
private fun CountColumn(
    label: String,
    value: Int,
    valueColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value.toString(),
            color = valueColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
