package tools.mo3ta.salo.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.notif_message_cta
import tools.mo3ta.salo.generated.resources.notif_message_cta_none
import tools.mo3ta.salo.notification.NotificationAction
import tools.mo3ta.salo.notification.NotificationMessage
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@Composable
internal fun NotificationMessageDialog(
    message: NotificationMessage,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(MohamedLoversPalette.SkyTop, MohamedLoversPalette.SkyBottom)
                    ),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 22.dp, vertical = 28.dp),
        ) {
            Text(
                text = message.title,
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(10.dp))
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohamedLoversPalette.GoldBase.copy(alpha = 0.2f))
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = message.body,
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 15.sp,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(22.dp))
            val ctaLabel = if (message.action == NotificationAction.NONE)
                stringResource(Res.string.notif_message_cta_none)
            else
                stringResource(Res.string.notif_message_cta)
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldBase,
                    contentColor = MohamedLoversPalette.DeepBlue,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = ctaLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
