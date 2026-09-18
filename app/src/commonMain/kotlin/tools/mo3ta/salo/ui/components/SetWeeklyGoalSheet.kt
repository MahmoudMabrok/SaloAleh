package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.data.engagement.WEEKLY_GOAL_MAX
import tools.mo3ta.salo.data.engagement.WEEKLY_GOAL_PRESETS
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.weekly_goal_cancel
import tools.mo3ta.salo.generated.resources.weekly_goal_clear
import tools.mo3ta.salo.generated.resources.weekly_goal_hint
import tools.mo3ta.salo.generated.resources.weekly_goal_save
import tools.mo3ta.salo.generated.resources.weekly_goal_subtitle
import tools.mo3ta.salo.generated.resources.weekly_goal_title
import tools.mo3ta.salo.generated.resources.weekly_goal_week_note

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetWeeklyGoalSheet(
    isOpen: Boolean,
    currentGoal: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onClear: () -> Unit,
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPreset by remember(currentGoal) {
        mutableStateOf(WEEKLY_GOAL_PRESETS.firstOrNull { it == currentGoal })
    }
    var customText by remember(currentGoal) {
        mutableStateOf(if (currentGoal > 0 && currentGoal !in WEEKLY_GOAL_PRESETS) currentGoal.toString() else "")
    }

    val effective = selectedPreset ?: customText.toIntOrNull() ?: 0
    val canSave = effective in 1..WEEKLY_GOAL_MAX

    val cream = Color(0xFFEFE2CB)
    val ink = Color(0xFF3A2A1C)
    val muted = Color(0xFF8A7256)
    val stroke = Color(0xFFD8C7A9)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cream,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(stroke),
            )
            Text(
                text = stringResource(Res.string.weekly_goal_title),
                color = ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.weekly_goal_subtitle),
                color = muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WEEKLY_GOAL_PRESETS.forEach { preset ->
                    val selected = selectedPreset == preset
                    Text(
                        text = formatWeeklyNumber(preset),
                        color = if (selected) Color.White else ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) accent else Color.Transparent)
                            .border(1.dp, if (selected) accent else stroke, RoundedCornerShape(999.dp))
                            .clickable {
                                selectedPreset = if (selected) null else preset
                                customText = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            OutlinedTextField(
                value = customText,
                onValueChange = { raw ->
                    customText = raw.filter { it.isDigit() }.take(7)
                    selectedPreset = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(Res.string.weekly_goal_hint), color = muted)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = stroke,
                    focusedTextColor = ink,
                    unfocusedTextColor = ink,
                    cursorColor = accent,
                ),
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                text = stringResource(Res.string.weekly_goal_week_note),
                color = muted.copy(alpha = 0.85f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SheetTextButton(
                    label = stringResource(Res.string.weekly_goal_cancel),
                    filled = false,
                    accent = accent,
                    ink = ink,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                SheetTextButton(
                    label = stringResource(Res.string.weekly_goal_save),
                    filled = true,
                    accent = accent,
                    ink = Color.White,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    onClick = { if (canSave) onSave(effective) },
                )
            }
            if (currentGoal > 0) {
                Text(
                    text = stringResource(Res.string.weekly_goal_clear),
                    color = muted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetTextButton(
    label: String,
    filled: Boolean,
    accent: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> accent.copy(alpha = 0.35f)
        filled -> accent
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (filled) Color.White else ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
