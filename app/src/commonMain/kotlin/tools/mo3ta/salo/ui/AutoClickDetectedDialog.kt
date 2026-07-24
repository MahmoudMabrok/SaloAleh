package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.auto_click_detected_body
import tools.mo3ta.salo.generated.resources.auto_click_detected_cta
import tools.mo3ta.salo.generated.resources.auto_click_detected_title
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

/**
 * Shown once, the first time injected taps are detected. Every detection after this is silent —
 * see `AutoClickGuardStore`.
 */
@Composable
fun AutoClickDetectedDialog(
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // The user has to acknowledge this one. Back press and outside-tap are both off, so it
        // cannot be swiped away unread — the CTA below is the only way out.
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MohamedLoversPalette.DeepBlue, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "🤖",
                fontSize = 48.sp,
            )
            Text(
                text = stringResource(Res.string.auto_click_detected_title),
                color = MohamedLoversPalette.Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.auto_click_detected_body),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MohamedLoversPalette.Gold),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    stringResource(Res.string.auto_click_detected_cta),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
