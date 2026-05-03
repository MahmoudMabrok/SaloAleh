package tools.mo3ta.salo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.mohamed_lovers_counter_hint
import tools.mo3ta.salo.generated.resources.mohamed_lovers_counter_pending
import tools.mo3ta.salo.generated.resources.mohamed_lovers_all_time_total_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_round_total_label
import tools.mo3ta.salo.generated.resources.mohamed_lovers_friday_bonus_label

@Composable
internal fun MohamedLoversCounter(
    allTimeTotal: Long,
    roundTotal: Int,
    pending: Int,
    isFridayBonus: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(allTimeTotal) {
        scale.snapTo(1f)
        scale.animateTo(1.08f, tween(120))
        scale.animateTo(1f, tween(200))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = allTimeTotal.formatCount(),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.display,
                fontWeight = FontWeight.W500,
                fontSize = 42.sp,
                letterSpacing = 0.3.sp,
            ),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.9f),
            modifier = Modifier.scale(scale.value),
        )
        Text(
            text = stringResource(Res.string.mohamed_lovers_all_time_total_label),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.arabic,
                fontWeight = FontWeight.W400,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
            ),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = roundTotal.toLong().formatCount(),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.display,
                fontWeight = FontWeight.W400,
                fontSize = 20.sp,
                letterSpacing = 0.3.sp,
            ),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.75f),
        )
        Text(
            text = stringResource(Res.string.mohamed_lovers_round_total_label),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.arabic,
                fontWeight = FontWeight.W400,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
            ),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.4f),
        )
        if (isFridayBonus) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.mohamed_lovers_friday_bonus_label),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.arabic,
                    fontWeight = FontWeight.W700,
                    fontSize = 11.sp,
                ),
                color = MohamedLoversPalette.GoldHighlight,
            )
        }
        if (pending > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.mohamed_lovers_counter_pending, pending),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.display,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.mohamed_lovers_counter_hint),
                style = TextStyle(
                    fontFamily = MohamedLoversFonts.display,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.4f),
            )
        }
    }
}

private fun Long.formatCount(): String {
    val s = toString()
    val result = StringBuilder()
    s.reversed().forEachIndexed { i, c -> if (i > 0 && i % 3 == 0) result.append(','); result.append(c) }
    return result.reverse().toString()
}
