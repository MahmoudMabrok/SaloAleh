package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.achievements_challenge_streak_days

/**
 * Small "🔥 N days in a row" pill surfacing a single challenge's live daily streak, shown on that
 * challenge's own screen. Renders nothing when [streak] is 0 (no active streak). Each challenge
 * passes its own accent [color] so the pill sits inside the screen's palette.
 */
@Composable
fun ChallengeStreakChip(
    streak: Int,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF5D97A),
) {
    if (streak <= 0) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = "🔥 " + stringResource(Res.string.achievements_challenge_streak_days, streak),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
