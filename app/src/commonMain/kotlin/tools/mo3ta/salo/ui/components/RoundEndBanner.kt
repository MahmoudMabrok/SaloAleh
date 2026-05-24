package tools.mo3ta.salo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.round_end_banner_title
import tools.mo3ta.salo.generated.resources.round_end_banner_subtitle

@Composable
internal fun RoundEndBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val borderAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MohamedLoversPalette.GoldHighlight.copy(alpha = borderAlpha)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "🏆", fontSize = 28.sp)
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.round_end_banner_title),
                    color = MohamedLoversPalette.GoldHighlight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.round_end_banner_subtitle),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
            Text(text = "←", color = MohamedLoversPalette.GoldHighlight, fontSize = 20.sp)
        }
    }
}
