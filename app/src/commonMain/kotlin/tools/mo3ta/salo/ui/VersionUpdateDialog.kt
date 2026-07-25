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
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.force_update_description
import tools.mo3ta.salo.generated.resources.force_update_title
import tools.mo3ta.salo.generated.resources.version_update_description
import tools.mo3ta.salo.generated.resources.version_update_later
import tools.mo3ta.salo.generated.resources.version_update_title
import tools.mo3ta.salo.generated.resources.version_update_update_now
import tools.mo3ta.salo.generated.resources.version_update_version_label
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

/**
 * Startup update dialog. When [forced] is true the running build is below the minimum
 * supported version: the dialog cannot be dismissed (no back-press, no outside tap, no
 * "Later"), so it blocks all app usage until the user updates.
 */
@Composable
fun VersionUpdateDialog(
    version: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    forced: Boolean = false,
) {
    Dialog(
        onDismissRequest = { if (!forced) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !forced,
            dismissOnClickOutside = !forced,
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
                text = if (forced) "⚠️" else "🎉",
                fontSize = 48.sp,
            )
            Text(
                text = stringResource(
                    if (forced) Res.string.force_update_title else Res.string.version_update_title,
                ),
                color = MohamedLoversPalette.Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (version.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.version_update_version_label, version),
                    color = MohamedLoversPalette.Gold.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(
                    if (forced) Res.string.force_update_description else Res.string.version_update_description,
                ),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MohamedLoversPalette.Gold),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    stringResource(Res.string.version_update_update_now),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            }
            // No "Later" on a forced update — the app stays blocked until the user updates.
            if (!forced) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(Res.string.version_update_later),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
