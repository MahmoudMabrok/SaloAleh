package tools.mo3ta.salo.ui.quran

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
import tools.mo3ta.salo.ui.manual.clampToRemaining
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.manual_challenge_cap_reached
import tools.mo3ta.salo.generated.resources.manual_challenge_cap_remaining
import tools.mo3ta.salo.generated.resources.manual_quran_count
import tools.mo3ta.salo.generated.resources.manual_quran_enter_count
import tools.mo3ta.salo.generated.resources.manual_quran_submit
import tools.mo3ta.salo.generated.resources.manual_quran_subtitle
import tools.mo3ta.salo.generated.resources.manual_quran_title
import tools.mo3ta.salo.generated.resources.manual_quran_witness

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualQuranSheet(
    isOpen: Boolean,
    remaining: Int = CHALLENGE_MANUAL_DAILY_CAP,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit,
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customText by remember { mutableStateOf("") }
    var witnessChecked by remember { mutableStateOf(false) }

    // Front-end cap: never let a manual batch exceed what's left for the day.
    val effectiveCount = (customText.toIntOrNull() ?: 0).coerceAtMost(remaining)
    val canSubmit = effectiveCount > 0 && witnessChecked

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = QuranColors.Cream,
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
                    .background(QuranColors.Stroke)
                    .align(Alignment.CenterHorizontally),
            )

            // Title
            Text(
                text = stringResource(Res.string.manual_quran_title),
                color = QuranColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Subtitle
            Text(
                text = stringResource(Res.string.manual_quran_subtitle),
                color = QuranColors.Muted,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Quranic verse card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(QuranColors.Teal.copy(alpha = 0.07f))
                    .border(
                        width = 1.dp,
                        color = QuranColors.LightTeal.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "﴿إِنَّ قَوْمِي اتَّخَذُوا هَٰذَا الْقُرْآنَ مَهْجُورًا﴾",
                    color = QuranColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                )
                Text(
                    text = "سورة الفرقان — آية ٣٠",
                    color = QuranColors.Teal.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(2.dp))

            // Count input
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.manual_quran_count),
                    color = QuranColors.Muted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        customText = clampToRemaining(digits, remaining)
                    },
                    placeholder = {
                        Text(
                            stringResource(Res.string.manual_quran_enter_count),
                            color = QuranColors.Muted.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = QuranColors.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QuranColors.Teal,
                        unfocusedBorderColor = QuranColors.Stroke,
                        cursorColor = QuranColors.Teal,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            // Daily manual-entry cap hint
            Text(
                text = if (remaining > 0) {
                    stringResource(Res.string.manual_challenge_cap_remaining, remaining)
                } else {
                    stringResource(Res.string.manual_challenge_cap_reached)
                },
                color = if (remaining > 0) QuranColors.Muted else Color(0xFFDC503C),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            // Witness checkbox
            val witnessBackground by animateColorAsState(
                targetValue = if (witnessChecked)
                    QuranColors.Teal.copy(alpha = 0.1f)
                else
                    Color(0xFFDC503C).copy(alpha = 0.08f),
                animationSpec = tween(300),
            )
            val witnessBorder by animateColorAsState(
                targetValue = if (witnessChecked)
                    QuranColors.LightTeal.copy(alpha = 0.5f)
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
                            if (witnessChecked) QuranColors.Teal else QuranColors.Stroke,
                            RoundedCornerShape(6.dp),
                        )
                        .then(
                            if (witnessChecked) Modifier.background(QuranColors.Teal)
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
                    text = stringResource(Res.string.manual_quran_witness),
                    color = QuranColors.Ink,
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
                            listOf(QuranColors.LightTeal, QuranColors.Teal),
                        ).takeIf { canSubmit }
                            ?: Brush.linearGradient(
                                listOf(
                                    QuranColors.LightTeal.copy(alpha = 0.3f),
                                    QuranColors.Teal.copy(alpha = 0.3f),
                                ),
                            ),
                    )
                    .border(
                        1.dp,
                        QuranColors.Teal.copy(alpha = submitAlpha),
                        RoundedCornerShape(14.dp),
                    )
                    .then(if (canSubmit) Modifier.clickable { onSubmit(effectiveCount) } else Modifier)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.manual_quran_submit),
                    color = if (canSubmit) Color.White else QuranColors.Muted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
