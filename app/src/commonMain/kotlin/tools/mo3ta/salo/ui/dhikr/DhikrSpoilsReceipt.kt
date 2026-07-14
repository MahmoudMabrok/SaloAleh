package tools.mo3ta.salo.ui.dhikr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.dhikr_gain_erased
import tools.mo3ta.salo.generated.resources.dhikr_gain_erased_qty
import tools.mo3ta.salo.generated.resources.dhikr_gain_freed
import tools.mo3ta.salo.generated.resources.dhikr_gain_freed_qty
import tools.mo3ta.salo.generated.resources.dhikr_gain_shield
import tools.mo3ta.salo.generated.resources.dhikr_gain_shield_qty
import tools.mo3ta.salo.generated.resources.dhikr_gain_written
import tools.mo3ta.salo.generated.resources.dhikr_gain_written_qty
import tools.mo3ta.salo.generated.resources.dhikr_gains_crown
import tools.mo3ta.salo.generated.resources.dhikr_gains_receipt_sub
import tools.mo3ta.salo.generated.resources.dhikr_gains_receipt_title
import tools.mo3ta.salo.generated.resources.dhikr_gains_share_text
import tools.mo3ta.salo.generated.resources.dhikr_keep_going
import tools.mo3ta.salo.generated.resources.dhikr_share_gains
import tools.mo3ta.salo.ui.ghars.arefRuqaaFamily
import tools.mo3ta.salo.ui.ghars.ibmPlexArabicFamily
import tools.mo3ta.salo.ui.shareText

private object ReceiptColors {
    val Background = Color(0xFF0A2417)
    val Accent = Color(0xFFE9C97F)
    val Ink = Color(0xFFF1EAD8)
}

private data class Gain(val label: StringResource, val qty: StringResource)

/**
 * غنائم اليوم — the day's spoils. The shared completion artifact for the emancipation engine:
 * an itemized receipt of the hadith's four gains that slides up when the hundred is reached, and
 * doubles as the share card.
 */
@Composable
internal fun DhikrSpoilsReceipt(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shareBody = stringResource(Res.string.dhikr_gains_share_text)

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(300)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(550, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SpoilsCard(
                onShare = {
                    shareText(shareBody)
                    onDismiss()
                },
                onContinue = onDismiss,
            )
        }
    }
}

@Composable
private fun SpoilsCard(
    onShare: () -> Unit,
    onContinue: () -> Unit,
) {
    val gains = listOf(
        Gain(Res.string.dhikr_gain_freed, Res.string.dhikr_gain_freed_qty),
        Gain(Res.string.dhikr_gain_written, Res.string.dhikr_gain_written_qty),
        Gain(Res.string.dhikr_gain_erased, Res.string.dhikr_gain_erased_qty),
        Gain(Res.string.dhikr_gain_shield, Res.string.dhikr_gain_shield_qty),
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Block taps from falling through to the scrim / counter behind.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        color = ReceiptColors.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.dhikr_gains_receipt_title),
                color = ReceiptColors.Accent,
                fontSize = 24.sp,
                fontFamily = arefRuqaaFamily(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.dhikr_gains_receipt_sub),
                color = ReceiptColors.Ink.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = ibmPlexArabicFamily(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            gains.forEachIndexed { index, gain ->
                GainRow(
                    label = stringResource(gain.label),
                    qty = stringResource(gain.qty),
                )
                if (index != gains.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ReceiptColors.Accent.copy(alpha = 0.25f)),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReceiptColors.Accent.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.dhikr_gains_crown),
                    color = ReceiptColors.Ink,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    fontFamily = ibmPlexArabicFamily(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReceiptButton(
                    text = stringResource(Res.string.dhikr_share_gains),
                    filled = true,
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                )
                ReceiptButton(
                    text = stringResource(Res.string.dhikr_keep_going),
                    filled = false,
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GainRow(label: String, qty: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .rotate(45f)
                .background(ReceiptColors.Accent),
        )
        Text(
            text = label,
            color = ReceiptColors.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = ibmPlexArabicFamily(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = qty,
            color = ReceiptColors.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ibmPlexArabicFamily(),
        )
    }
}

@Composable
private fun ReceiptButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = if (filled) ReceiptColors.Accent else Color.Transparent,
        border = BorderStroke(1.dp, ReceiptColors.Accent),
    ) {
        Text(
            text = text,
            color = if (filled) Color(0xFF1A2415) else ReceiptColors.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ibmPlexArabicFamily(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
        )
    }
}
