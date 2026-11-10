package tools.mo3ta.salo.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.experiences_intro_overlay_body
import tools.mo3ta.salo.generated.resources.experiences_intro_overlay_cta
import tools.mo3ta.salo.generated.resources.experiences_intro_overlay_dismiss
import tools.mo3ta.salo.generated.resources.experiences_intro_overlay_title
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
fun ExperiencesIntroDialog(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MohamedLoversPalette.DeepBlue)
                .border(
                    width = 1.dp,
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.experiences_intro_overlay_title),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.experiences_intro_overlay_body),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldGlow,
                    contentColor = MohamedLoversPalette.DeepBlue,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.experiences_intro_overlay_cta),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.experiences_intro_overlay_dismiss),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                )
            }
        }
    }
}
