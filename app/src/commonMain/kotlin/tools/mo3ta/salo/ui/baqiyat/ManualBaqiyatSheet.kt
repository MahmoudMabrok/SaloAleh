package tools.mo3ta.salo.ui.baqiyat

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
import androidx.compose.foundation.shape.CircleShape
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
import tools.mo3ta.salo.domain.BAQIYAT_MANUAL_DAILY_CAP
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.manual_baqiyat_count
import tools.mo3ta.salo.generated.resources.manual_baqiyat_enter_count
import tools.mo3ta.salo.generated.resources.manual_baqiyat_submit
import tools.mo3ta.salo.generated.resources.manual_baqiyat_subtitle
import tools.mo3ta.salo.generated.resources.manual_baqiyat_title
import tools.mo3ta.salo.generated.resources.manual_baqiyat_witness
import tools.mo3ta.salo.generated.resources.manual_challenge_cap_error
import tools.mo3ta.salo.generated.resources.manual_mode_add
import tools.mo3ta.salo.generated.resources.manual_mode_subtract
import tools.mo3ta.salo.generated.resources.manual_subtract_hint
import tools.mo3ta.salo.generated.resources.manual_subtract_submit
import tools.mo3ta.salo.ui.components.MohamedLoversFonts
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualBaqiyatSheet(
    isOpen: Boolean,
    remaining: Int = BAQIYAT_MANUAL_DAILY_CAP,
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
    // Front-end cap: a manual batch that would exceed the day's remaining allowance is rejected.
    // The cap only applies when adding — a correction can subtract any amount.
    val exceedsCap = !isSubtractMode && effectiveCount > remaining
    // In subtract (correction) mode there is nothing to witness — the gate is just a positive amount.
    val canSubmit = effectiveCount > 0 && (isSubtractMode || (witnessChecked && !exceedsCap))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MohamedLoversPalette.SkyTop,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MohamedLoversPalette.GoldGlow.copy(alpha = 0.2f))
                    .align(Alignment.CenterHorizontally),
            )

            // Title
            Text(
                text = stringResource(Res.string.manual_baqiyat_title),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                fontFamily = MohamedLoversFonts.display,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Subtitle — why this feature exists
            Text(
                text = stringResource(Res.string.manual_baqiyat_subtitle),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontFamily = MohamedLoversFonts.body,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Add / Subtract mode toggle — subtract lets the user correct a mistaken count.
            ManualModeToggle(
                isSubtractMode = isSubtractMode,
                onModeChange = { isSubtractMode = it },
            )

            // Tasbih beads decoration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) {
                    TasbihBead()
                    if (it < 2) Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, MohamedLoversPalette.GoldBase, Color.Transparent),
                            ),
                        ),
                )
                Spacer(Modifier.width(12.dp))
                repeat(3) {
                    TasbihBead()
                    if (it < 2) Spacer(Modifier.width(6.dp))
                }
            }

            Spacer(Modifier.height(2.dp))

            // Count input
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.manual_baqiyat_count),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontFamily = MohamedLoversFonts.body,
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { raw ->
                        customText = raw.filter { it.isDigit() }
                    },
                    placeholder = {
                        Text(
                            stringResource(Res.string.manual_baqiyat_enter_count),
                            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.3f),
                            fontSize = 14.sp,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MohamedLoversPalette.GoldGlow,
                        fontSize = 18.sp,
                        fontFamily = MohamedLoversFonts.display,
                        textAlign = TextAlign.Center,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MohamedLoversPalette.GoldHighlight,
                        unfocusedBorderColor = MohamedLoversPalette.GoldBase.copy(alpha = 0.3f),
                        cursorColor = MohamedLoversPalette.GoldHighlight,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            // Daily manual-entry cap error — shown only when the entry exceeds the limit
            if (exceedsCap) {
                Text(
                    text = stringResource(Res.string.manual_challenge_cap_error, BAQIYAT_MANUAL_DAILY_CAP),
                    color = Color(0xFFDC503C),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }

            if (isSubtractMode) {
                // Correction hint — replaces the witness gate when subtracting.
                Text(
                    text = stringResource(Res.string.manual_subtract_hint),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = MohamedLoversFonts.body,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            } else {
                // Witness checkbox
                val witnessBackground by animateColorAsState(
                    targetValue = if (witnessChecked)
                        MohamedLoversPalette.ForestCore.copy(alpha = 0.3f)
                    else
                        Color(0xFFDC503C).copy(alpha = 0.1f),
                    animationSpec = tween(300),
                )
                val witnessBorder by animateColorAsState(
                    targetValue = if (witnessChecked)
                        MohamedLoversPalette.ForestCore.copy(alpha = 0.6f)
                    else
                        Color(0xFFDC503C).copy(alpha = 0.35f),
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
                                if (witnessChecked) MohamedLoversPalette.GoldHighlight
                                else MohamedLoversPalette.GoldBase,
                                RoundedCornerShape(6.dp),
                            )
                            .then(
                                if (witnessChecked) Modifier.background(MohamedLoversPalette.GoldBase)
                                else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (witnessChecked) {
                            Text(
                                text = "✓",
                                color = MohamedLoversPalette.SkyTop,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W700,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(Res.string.manual_baqiyat_witness),
                        color = MohamedLoversPalette.GoldGlow,
                        fontSize = 14.sp,
                        fontFamily = MohamedLoversFonts.body,
                        lineHeight = 24.sp,
                    )
                }
            }

            // Submit button
            val submitAlpha = if (canSubmit) 1f else 0.35f
            val submitStart = if (isSubtractMode) Color(0xFFB23A2A) else MohamedLoversPalette.GoldBase
            val submitEnd = if (isSubtractMode) Color(0xFF7A2618) else Color(0xFF8B6914)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(submitStart, submitEnd),
                        ).takeIf { canSubmit }
                            ?: Brush.linearGradient(
                                listOf(
                                    submitStart.copy(alpha = 0.3f),
                                    submitEnd.copy(alpha = 0.3f),
                                ),
                            ),
                    )
                    .border(
                        1.dp,
                        MohamedLoversPalette.GoldHighlight.copy(alpha = submitAlpha),
                        RoundedCornerShape(14.dp),
                    )
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
                        if (isSubtractMode) Res.string.manual_subtract_submit else Res.string.manual_baqiyat_submit,
                    ),
                    color = if (canSubmit) MohamedLoversPalette.SkyTop else MohamedLoversPalette.GoldGlow.copy(alpha = 0.4f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    fontFamily = MohamedLoversFonts.display,
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
            .background(MohamedLoversPalette.GoldGlow.copy(alpha = 0.05f))
            .border(1.dp, MohamedLoversPalette.GoldBase.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ManualModeSegment(
            label = stringResource(Res.string.manual_mode_add),
            isSelected = !isSubtractMode,
            selectedColor = MohamedLoversPalette.GoldBase,
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
            color = if (isSelected) MohamedLoversPalette.SkyTop else MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
            fontSize = 15.sp,
            fontWeight = FontWeight.W700,
            fontFamily = MohamedLoversFonts.body,
        )
    }
}

@Composable
private fun TasbihBead() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(MohamedLoversPalette.GoldHighlight, MohamedLoversPalette.GoldBase),
                ),
            ),
    )
}
