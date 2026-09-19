package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import tools.mo3ta.salo.ui.ghars.ibmPlexArabicFamily

/** Palette for the shared challenge count card (the Ghars bottom sheet). */
data class ChallengeSheetPalette(
    val background: Color,
    val accent: Color,
    val muted: Color,
    val stroke: Color,
    val track: Color,
    val progressStart: Color,
    val progressEnd: Color,
    val primaryButton: Color,
    val primaryButtonText: Color,
    val secondaryButton: Color,
    val secondaryButtonText: Color,
)

fun creamChallengeSheet(
    accent: Color,
    cream: Color,
    muted: Color,
    stroke: Color,
    track: Color,
    primaryButton: Color = accent,
    primaryButtonText: Color = Color.White,
    progressStart: Color = accent,
    progressEnd: Color = accent,
) = ChallengeSheetPalette(
    background = cream,
    accent = accent,
    muted = muted,
    stroke = stroke,
    track = track,
    progressStart = progressStart,
    progressEnd = progressEnd,
    primaryButton = primaryButton,
    primaryButtonText = primaryButtonText,
    secondaryButton = cream,
    secondaryButtonText = muted,
)

data class ChallengeSheetAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Bottom card used on every tap challenge: today's count, daily goal, lifetime total,
 * a progress bar, the weekly-goal chip, and up to two actions.
 *
 * Collect [todayCountFlow] internally so a tap recomposes this card without the hero.
 */
@Composable
fun ChallengeCountSheet(
    todayCountFlow: StateFlow<Int>,
    dailyGoal: Int,
    lifetimeCount: Int?,
    todayLabel: String,
    goalLabel: String,
    lifetimeLabel: String?,
    unitLabel: String,
    colors: ChallengeSheetPalette,
    challengeId: String,
    modifier: Modifier = Modifier,
    lifetimeCountFlow: StateFlow<Int>? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {},
    primaryAction: ChallengeSheetAction? = null,
    secondaryAction: ChallengeSheetAction? = null,
) {
    val todayCount by todayCountFlow.collectAsStateWithLifecycle()
    val fallbackLifetime = remember { MutableStateFlow(lifetimeCount ?: 0) }
    LaunchedEffect(lifetimeCount) { fallbackLifetime.value = lifetimeCount ?: 0 }
    val liveLifetime by (lifetimeCountFlow ?: fallbackLifetime).collectAsStateWithLifecycle()
    ChallengeCountSheet(
        todayCount = todayCount,
        dailyGoal = dailyGoal,
        lifetimeCount = if (lifetimeCountFlow != null || lifetimeCount != null) liveLifetime else null,
        todayLabel = todayLabel,
        goalLabel = goalLabel,
        lifetimeLabel = lifetimeLabel,
        unitLabel = unitLabel,
        colors = colors,
        challengeId = challengeId,
        modifier = modifier,
        extraContent = extraContent,
        primaryAction = primaryAction,
        secondaryAction = secondaryAction,
    )
}

@Composable
fun ChallengeCountSheet(
    todayCount: Int,
    dailyGoal: Int,
    lifetimeCount: Int?,
    todayLabel: String,
    goalLabel: String,
    lifetimeLabel: String?,
    unitLabel: String,
    colors: ChallengeSheetPalette,
    challengeId: String,
    modifier: Modifier = Modifier,
    extraContent: @Composable ColumnScope.() -> Unit = {},
    primaryAction: ChallengeSheetAction? = null,
    secondaryAction: ChallengeSheetAction? = null,
) {
    val progress = if (dailyGoal > 0) {
        (todayCount.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(colors.background)
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = todayLabel,
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ibmPlexArabicFamily(),
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = todayCount.toString(),
                        color = colors.accent,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = ibmPlexArabicFamily(),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = unitLabel,
                        color = colors.muted,
                        fontSize = 14.sp,
                        fontFamily = ibmPlexArabicFamily(),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(colors.stroke),
            )
            Spacer(Modifier.width(18.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = goalLabel,
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ibmPlexArabicFamily(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dailyGoal.toString(),
                    color = colors.muted,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = ibmPlexArabicFamily(),
                )
            }
        }

        if (lifetimeCount != null && lifetimeLabel != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.track.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = lifetimeLabel,
                    color = colors.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ibmPlexArabicFamily(),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = lifetimeCount.toString(),
                        color = colors.accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ibmPlexArabicFamily(),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unitLabel,
                        color = colors.muted,
                        fontSize = 12.sp,
                        fontFamily = ibmPlexArabicFamily(),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }

        extraContent()

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(colors.progressStart, colors.progressEnd))),
            )
        }

        Spacer(Modifier.height(14.dp))
        WeeklyGoalSection(
            challengeId = challengeId,
            todayCount = todayCount,
            accent = colors.accent,
            compact = true,
            onDark = false,
        )

        if (primaryAction != null || secondaryAction != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (primaryAction != null) {
                    SheetAction(
                        label = primaryAction.label,
                        primary = true,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = primaryAction.onClick,
                    )
                }
                if (secondaryAction != null) {
                    SheetAction(
                        label = secondaryAction.label,
                        primary = false,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = secondaryAction.onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    primary: Boolean,
    colors: ChallengeSheetPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (primary) colors.primaryButton else colors.secondaryButton)
            .then(
                if (!primary) Modifier.border(1.dp, colors.stroke, RoundedCornerShape(13.dp))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) colors.primaryButtonText else colors.secondaryButtonText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = ibmPlexArabicFamily(),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
