package tools.mo3ta.salo.ui.dhikr

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import tools.mo3ta.salo.domain.CHALLENGE_MANUAL_DAILY_CAP
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.manual_challenge_cap_error
import tools.mo3ta.salo.generated.resources.manual_dhikr_count
import tools.mo3ta.salo.generated.resources.manual_dhikr_enter_count
import tools.mo3ta.salo.generated.resources.manual_dhikr_other_count
import tools.mo3ta.salo.generated.resources.manual_dhikr_submit
import tools.mo3ta.salo.generated.resources.manual_dhikr_subtitle
import tools.mo3ta.salo.generated.resources.manual_dhikr_title
import tools.mo3ta.salo.generated.resources.manual_dhikr_witness

private val PRESET_COUNTS = listOf(33, 100, 300, 500, 1000)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ManualDhikrSheet(
    isOpen: Boolean,
    showPresetChips: Boolean = true,
    remaining: Int = CHALLENGE_MANUAL_DAILY_CAP,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit,
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPreset by remember { mutableStateOf<Int?>(null) }
    var customText by remember { mutableStateOf("") }
    var witnessChecked by remember { mutableStateOf(false) }

    val effectiveCount = if (showPresetChips) {
        selectedPreset ?: customText.toIntOrNull() ?: 0
    } else {
        customText.toIntOrNull() ?: 0
    }
    // Front-end cap: a manual batch that would exceed the day's remaining allowance is rejected.
    val exceedsCap = effectiveCount > remaining
    val canSubmit = effectiveCount > 0 && witnessChecked && !exceedsCap

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DhikrColors.Cream,
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
                    .background(DhikrColors.Stroke)
                    .align(Alignment.CenterHorizontally),
            )

            // Title
            Text(
                text = stringResource(Res.string.manual_dhikr_title),
                color = DhikrColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Subtitle — why this feature exists
            Text(
                text = stringResource(Res.string.manual_dhikr_subtitle),
                color = DhikrColors.Muted,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
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
                                listOf(Color.Transparent, DhikrColors.LightGreen, Color.Transparent),
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

            if (showPresetChips) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PRESET_COUNTS.forEach { count ->
                        CountChip(
                            count = count,
                            isSelected = selectedPreset == count,
                            onClick = {
                                selectedPreset = if (selectedPreset == count) null else count
                                customText = ""
                            },
                        )
                    }
                }
            }

            // Count input
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (showPresetChips) {
                            Res.string.manual_dhikr_other_count
                        } else {
                            Res.string.manual_dhikr_count
                        },
                    ),
                    color = DhikrColors.Muted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { raw ->
                        customText = raw.filter { it.isDigit() }
                        selectedPreset = null
                    },
                    placeholder = {
                        Text(
                            stringResource(Res.string.manual_dhikr_enter_count),
                            color = DhikrColors.Muted.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = DhikrColors.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DhikrColors.Green,
                        unfocusedBorderColor = DhikrColors.Stroke,
                        cursorColor = DhikrColors.Green,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            // Daily manual-entry cap error — shown only when the entry exceeds the limit
            if (exceedsCap) {
                Text(
                    text = stringResource(Res.string.manual_challenge_cap_error, CHALLENGE_MANUAL_DAILY_CAP),
                    color = Color(0xFFDC503C),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }

            // Quranic verse card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DhikrColors.Green.copy(alpha = 0.07f))
                    .border(
                        width = 1.dp,
                        color = DhikrColors.LightGreen.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "﴿فَاذْكُرُونِي أَذْكُرْكُمْ﴾",
                    color = DhikrColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                )
                Text(
                    text = "سورة البقرة — آية ١٥٢",
                    color = DhikrColors.Green.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }

            // Witness checkbox
            val witnessBackground by animateColorAsState(
                targetValue = if (witnessChecked)
                    DhikrColors.Green.copy(alpha = 0.1f)
                else
                    Color(0xFFDC503C).copy(alpha = 0.08f),
                animationSpec = tween(300),
            )
            val witnessBorder by animateColorAsState(
                targetValue = if (witnessChecked)
                    DhikrColors.LightGreen.copy(alpha = 0.5f)
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
                            if (witnessChecked) DhikrColors.Green else DhikrColors.Stroke,
                            RoundedCornerShape(6.dp),
                        )
                        .then(
                            if (witnessChecked) Modifier.background(DhikrColors.Green)
                            else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (witnessChecked) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.manual_dhikr_witness),
                    color = DhikrColors.Ink,
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                )
            }

            // Submit button
            val submitAlpha = if (canSubmit) 1f else 0.35f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(DhikrColors.LightGreen, DhikrColors.Green),
                        ).takeIf { canSubmit }
                            ?: Brush.linearGradient(
                                listOf(
                                    DhikrColors.LightGreen.copy(alpha = 0.3f),
                                    DhikrColors.Green.copy(alpha = 0.3f),
                                ),
                            ),
                    )
                    .border(
                        1.dp,
                        DhikrColors.Green.copy(alpha = submitAlpha),
                        RoundedCornerShape(14.dp),
                    )
                    .then(if (canSubmit) Modifier.clickable { onSubmit(effectiveCount) } else Modifier)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.manual_dhikr_submit),
                    color = if (canSubmit) Color.White else DhikrColors.Muted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
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
                    listOf(DhikrColors.LightGreen, DhikrColors.Green),
                ),
            ),
    )
}

@Composable
private fun CountChip(
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            DhikrColors.Green.copy(alpha = 0.12f)
        else
            DhikrColors.Card,
        animationSpec = tween(200),
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) DhikrColors.Green else DhikrColors.Stroke,
        animationSpec = tween(200),
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) DhikrColors.Green else DhikrColors.Ink,
        animationSpec = tween(200),
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$count",
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
