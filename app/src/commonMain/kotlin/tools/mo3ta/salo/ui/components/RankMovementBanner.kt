package tools.mo3ta.salo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.rank_climbed
import tools.mo3ta.salo.generated.resources.rank_dropped
import tools.mo3ta.salo.generated.resources.since_last_visit

@Composable
fun RankMovementBanner(
    delta: Int?,
    oldRank: Int,
    newRank: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(delta) {
        if (delta != null) {
            visible = true
            delay(4000)
            visible = false
            delay(300)
            onDismiss()
        }
    }

    val isClimb = (delta ?: 0) > 0
    val bgColor = if (isClimb) Color(0xFF1A2A1A) else Color(0xFF2A1A1A)
    val borderColor = if (isClimb) Color(0xFF4A8A4A) else Color(0xFF8A4A4A)
    val textColor = if (isClimb) Color(0xFF4AEE4A) else Color(0xFFEE4A4A)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(
                    color = bgColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    visible = false
                    onDismiss()
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(Res.string.since_last_visit),
                style = MaterialTheme.typography.bodySmall,
                color = borderColor,
            )
            Text(
                text = if (isClimb) {
                    stringResource(Res.string.rank_climbed, delta ?: 0)
                } else {
                    stringResource(Res.string.rank_dropped, -(delta ?: 0))
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            Text(
                text = "#$oldRank → #$newRank",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
            )
        }
    }
}
