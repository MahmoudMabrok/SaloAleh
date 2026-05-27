package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
fun PremiumPromoDialog(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    featureNote: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "🌟", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.paywall_support_app_title),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.premium_promo_description),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            if (!featureNote.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = featureNote,
                    color = MohamedLoversPalette.GoldHighlight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MohamedLoversPalette.GoldHighlight.copy(alpha = 0.12f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "كلما زاد دعمك، زاد أجرك في الآخرة بإذن الله 🤲",
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MohamedLoversPalette.GoldGlow),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(Res.string.paywall_support_now), color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.premium_promo_later), color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
