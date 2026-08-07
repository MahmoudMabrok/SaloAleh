package tools.mo3ta.salo.ui.hawqala

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.hawqala_reward_hadith
import tools.mo3ta.salo.generated.resources.hawqala_reward_hadith_ref
import tools.mo3ta.salo.generated.resources.manual_challenge_cap_error
import tools.mo3ta.salo.generated.resources.manual_hawqala_count
import tools.mo3ta.salo.generated.resources.manual_hawqala_enter_count
import tools.mo3ta.salo.generated.resources.manual_hawqala_submit
import tools.mo3ta.salo.generated.resources.manual_hawqala_subtitle
import tools.mo3ta.salo.generated.resources.manual_hawqala_title
import tools.mo3ta.salo.generated.resources.manual_hawqala_witness
import tools.mo3ta.salo.generated.resources.manual_mode_add
import tools.mo3ta.salo.generated.resources.manual_mode_subtract
import tools.mo3ta.salo.generated.resources.manual_subtract_hint
import tools.mo3ta.salo.generated.resources.manual_subtract_submit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualHawqalaSheet(
    isOpen: Boolean,
    remaining: Int = CHALLENGE_MANUAL_DAILY_CAP,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit,
    onSubtract: (Int) -> Unit = {},
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customText by remember { mutableStateOf("") }
    var witnessChecked by remember { mutableStateOf(false) }
    var isSubtractMode by remember { mutableStateOf(false) }

    val effectiveCount = customText.toIntOrNull() ?: 0
    val exceedsCap = !isSubtractMode && effectiveCount > remaining
    val canSubmit = effectiveCount > 0 && (isSubtractMode || (witnessChecked && !exceedsCap))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HawqalaColors.Cream,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HawqalaColors.Stroke)
                    .align(Alignment.CenterHorizontally),
            )

            Text(
                text = stringResource(Res.string.manual_hawqala_title),
                color = HawqalaColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(Res.string.manual_hawqala_subtitle),
                color = HawqalaColors.Muted,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            ManualModeToggle(
                isSubtractMode = isSubtractMode,
                onModeChange = { isSubtractMode = it },
            )

            if (!isSubtractMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HawqalaColors.Accent.copy(alpha = 0.07f))
                        .border(
                            width = 1.dp,
                            color = HawqalaColors.LightAccent.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.hawqala_reward_hadith),
                        color = HawqalaColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 27.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.hawqala_reward_hadith_ref),
                        color = HawqalaColors.Accent.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.manual_hawqala_count),
                    color = HawqalaColors.Muted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { raw -> customText = raw.filter { it.isDigit() } },
                    placeholder = {
                        Text(
                            stringResource(Res.string.manual_hawqala_enter_count),
                            color = HawqalaColors.Muted.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = HawqalaColors.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HawqalaColors.Accent,
                        unfocusedBorderColor = HawqalaColors.Stroke,
                        cursorColor = HawqalaColors.Accent,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            if (exceedsCap) {
                Text(
                    text = stringResource(Res.string.manual_challenge_cap_error, CHALLENGE_MANUAL_DAILY_CAP),
                    color = Color(0xFFDC503C),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }

            if (isSubtractMode) {
                Text(
                    text = stringResource(Res.string.manual_subtract_hint),
                    color = HawqalaColors.Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            } else {
                val witnessBackground by animateColorAsState(
                    targetValue = if (witnessChecked)
                        HawqalaColors.Accent.copy(alpha = 0.1f)
                    else
                        Color(0xFFDC503C).copy(alpha = 0.08f),
                    animationSpec = tween(300),
                )
                val witnessBorder by animateColorAsState(
                    targetValue = if (witnessChecked)
                        HawqalaColors.LightAccent.copy(alpha = 0.6f)
                    else
                        Color(0xFFDC503C).copy(alpha = 0.3f),
                    animationSpec = tween(300),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(witnessBackground)
                        .border(1.dp, witnessBorder, RoundedCornerShape(12.dp))
                        .clickable { witnessChecked = !witnessChecked }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                2.dp,
                                if (witnessChecked) HawqalaColors.Accent else HawqalaColors.Stroke,
                                RoundedCornerShape(6.dp),
                            )
                            .then(
                                if (witnessChecked) Modifier.background(HawqalaColors.Accent)
                                else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (witnessChecked) {
                            Text(text = "✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W700)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(Res.string.manual_hawqala_witness),
                        color = HawqalaColors.Ink,
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                    )
                }
            }

            val submitAlpha = if (canSubmit) 1f else 0.35f
            val submitStart = if (isSubtractMode) Color(0xFFE07A5F) else HawqalaColors.LightAccent
            val submitEnd = if (isSubtractMode) Color(0xFFB23A2A) else HawqalaColors.Accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(listOf(submitStart, submitEnd)).takeIf { canSubmit }
                            ?: Brush.linearGradient(
                                listOf(submitStart.copy(alpha = 0.3f), submitEnd.copy(alpha = 0.3f)),
                            ),
                    )
                    .border(1.dp, submitEnd.copy(alpha = submitAlpha), RoundedCornerShape(14.dp))
                    .then(
                        if (canSubmit) {
                            Modifier.clickable {
                                if (isSubtractMode) onSubtract(effectiveCount) else onSubmit(effectiveCount)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (isSubtractMode) Res.string.manual_subtract_submit else Res.string.manual_hawqala_submit,
                    ),
                    color = if (canSubmit) Color.White else HawqalaColors.Muted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ManualModeToggle(
    isSubtractMode: Boolean,
    onModeChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HawqalaColors.Accent.copy(alpha = 0.05f))
            .border(1.dp, HawqalaColors.Stroke, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ManualModeSegment(
            label = stringResource(Res.string.manual_mode_add),
            isSelected = !isSubtractMode,
            selectedColor = HawqalaColors.Accent,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(false) },
        )
        ManualModeSegment(
            label = stringResource(Res.string.manual_mode_subtract),
            isSelected = isSubtractMode,
            selectedColor = Color(0xFFB23A2A),
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(true) },
        )
    }
}

@Composable
private fun ManualModeSegment(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) selectedColor else Color.Transparent,
        animationSpec = tween(200),
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else HawqalaColors.Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.W700,
        )
    }
}
