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
import tools.mo3ta.salo.generated.resources.mohamed_lovers_counter_tag
import tools.mo3ta.salo.generated.resources.mohamed_lovers_friday_bonus_label

@Composable
internal fun MohamedLoversCounter(
    total: Int,
    pending: Int,
    isFridayBonus: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(total) {
        scale.snapTo(1f)
        scale.animateTo(1.08f, tween(120))
        scale.animateTo(1f, tween(200))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = total.toString(),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.display,
                fontWeight = FontWeight.W500,
                fontSize = 34.sp,
                letterSpacing = 0.3.sp,
            ),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.9f),
            modifier = Modifier.scale(scale.value),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(Res.string.mohamed_lovers_counter_tag),
            style = TextStyle(
                fontFamily = MohamedLoversFonts.arabic,
                fontWeight = FontWeight.W400,
                fontSize = 12.sp,
                letterSpacing = 2.5.sp,
            ),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
        )
        if (isFridayBonus) {
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
