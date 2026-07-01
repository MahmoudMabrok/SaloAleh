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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun NicknamePromptDialog(
    initialName: String = "",
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var nickname by remember(initialName) { mutableStateOf(initialName.take(20)) }
    val canSave = nickname.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MohamedLoversPalette.DeepBlue, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.nickname_prompt_title),
                color = MohamedLoversPalette.Gold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.nickname_prompt_body),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { value ->
                    if (value.length <= 20) nickname = value
                },
                label = { Text(stringResource(Res.string.settings_nickname_label)) },
                placeholder = { Text(stringResource(Res.string.settings_nickname_placeholder)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MohamedLoversPalette.GoldGlow,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = MohamedLoversPalette.GoldGlow,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
                    cursorColor = MohamedLoversPalette.GoldGlow,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(nickname.trim()) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.Gold,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.16f),
                    disabledContentColor = Color.White.copy(alpha = 0.45f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.nickname_prompt_save),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.nickname_prompt_later),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}
