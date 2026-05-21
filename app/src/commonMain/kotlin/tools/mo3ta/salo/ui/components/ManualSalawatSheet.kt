package tools.mo3ta.salo.ui.components

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
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*

private val PRESET_COUNTS = listOf(33, 100, 300, 500, 1000)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ManualSalawatSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit,
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPreset by remember { mutableStateOf<Int?>(null) }
    var customText by remember { mutableStateOf("") }
    var witnessChecked by remember { mutableStateOf(false) }

    val effectiveCount = selectedPreset ?: customText.toIntOrNull() ?: 0
    val canSubmit = effectiveCount > 0 && witnessChecked

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
                text = stringResource(Res.string.manual_salawat_title),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                fontFamily = MohamedLoversFonts.display,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Subtitle — why this feature exists
            Text(
                text = stringResource(Res.string.manual_salawat_subtitle),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontFamily = MohamedLoversFonts.body,
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
                                listOf(Color.Transparent, MohamedLoversPalette.GoldBase, Color.Transparent)
                            )
                        ),
                )
                Spacer(Modifier.width(12.dp))
                repeat(3) {
                    TasbihBead()
                    if (it < 2) Spacer(Modifier.width(6.dp))
                }
            }

            Spacer(Modifier.height(2.dp))

            // Preset count chips
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

            // Custom input
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.manual_salawat_other_count),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontFamily = MohamedLoversFonts.body,
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        customText = digits
                        selectedPreset = null
                    },
                    placeholder = {
                        Text(
                            stringResource(Res.string.manual_salawat_enter_count),
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

            // Quranic verse card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MohamedLoversPalette.AtmosphereViolet.copy(alpha = 0.08f),
                                MohamedLoversPalette.AtmosphereBlue.copy(alpha = 0.08f),
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MohamedLoversPalette.AtmosphereViolet.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "﴿إِنَّ اللَّهَ كَانَ عَلَيْكُمْ رَقِيبًا﴾",
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 17.sp,
                    fontFamily = MohamedLoversFonts.display,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                )
                Text(
                    text = "سورة النساء — آية ١",
                    color = MohamedLoversPalette.AtmosphereViolet.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = MohamedLoversFonts.body,
                )
            }

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
                            else Modifier
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
                    text = stringResource(Res.string.manual_salawat_witness),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 14.sp,
                    fontFamily = MohamedLoversFonts.body,
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
                            listOf(MohamedLoversPalette.GoldBase, Color(0xFF8B6914))
                        ).takeIf { canSubmit }
                            ?: Brush.linearGradient(
                                listOf(
                                    MohamedLoversPalette.GoldBase.copy(alpha = 0.3f),
                                    Color(0xFF8B6914).copy(alpha = 0.3f),
                                )
                            )
                    )
                    .border(
                        1.dp,
                        MohamedLoversPalette.GoldHighlight.copy(alpha = submitAlpha),
                        RoundedCornerShape(14.dp),
                    )
                    .then(if (canSubmit) Modifier.clickable { onSubmit(effectiveCount) } else Modifier)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.manual_salawat_submit),
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
private fun TasbihBead() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(MohamedLoversPalette.GoldHighlight, MohamedLoversPalette.GoldBase)
                )
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
            MohamedLoversPalette.GoldBase.copy(alpha = 0.15f)
        else
            MohamedLoversPalette.GoldGlow.copy(alpha = 0.04f),
        animationSpec = tween(200),
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MohamedLoversPalette.GoldHighlight
        else MohamedLoversPalette.GoldBase.copy(alpha = 0.25f),
        animationSpec = tween(200),
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MohamedLoversPalette.GoldHighlight
        else MohamedLoversPalette.GoldGlow,
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
            fontFamily = MohamedLoversFonts.display,
        )
    }
}
