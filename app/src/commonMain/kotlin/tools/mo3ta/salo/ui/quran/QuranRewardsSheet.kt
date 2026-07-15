package tools.mo3ta.salo.ui.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.quran_reward_cure
import tools.mo3ta.salo.generated.resources.quran_reward_intercession
import tools.mo3ta.salo.generated.resources.quran_reward_light
import tools.mo3ta.salo.generated.resources.quran_reward_rank
import tools.mo3ta.salo.generated.resources.quran_reward_sunnah
import tools.mo3ta.salo.generated.resources.quran_reward_sunnah_label
import tools.mo3ta.salo.generated.resources.quran_rewards_close
import tools.mo3ta.salo.generated.resources.quran_rewards_title

private data class QuranRewardEntry(
    val icon: String,
    val text: StringResource,
    val isHero: Boolean = false,
)

/**
 * The Quran virtues, presented on demand as a slide-up sheet — the "🎁 الفضائل" button on
 * [tools.mo3ta.salo.ui.QuranChallengeScreen] opens it, mirroring the 100-dhikr gains sheet so the
 * counter stays immersive instead of the rewards filling the screen.
 */
@Composable
internal fun QuranRewardsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            enter = slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            QuranRewardsCard(onClose = onDismiss)
        }
    }
}

@Composable
private fun QuranRewardsCard(onClose: () -> Unit) {
    val rewards = listOf(
        QuranRewardEntry("🌟", Res.string.quran_reward_light),
        QuranRewardEntry("💊", Res.string.quran_reward_cure),
        QuranRewardEntry("🤝", Res.string.quran_reward_intercession),
        QuranRewardEntry("📈", Res.string.quran_reward_rank),
        QuranRewardEntry("📖", Res.string.quran_reward_sunnah, isHero = true),
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
        color = QuranColors.Cream,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(QuranColors.Stroke),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(Res.string.quran_rewards_title),
                color = QuranColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            rewards.forEach { reward ->
                QuranRewardRow(reward = reward)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            Surface(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                color = QuranColors.Teal,
            ) {
                Text(
                    text = stringResource(Res.string.quran_rewards_close),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 13.dp),
                )
            }
        }
    }
}

@Composable
private fun QuranRewardRow(reward: QuranRewardEntry) {
    val shape = RoundedCornerShape(14.dp)
    val rowModifier = if (reward.isHero) {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(QuranColors.Teal.copy(alpha = 0.09f))
            .border(1.dp, QuranColors.LightTeal.copy(alpha = 0.35f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(QuranColors.Card)
            .border(1.dp, QuranColors.Stroke, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = reward.icon,
            fontSize = 26.sp,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (reward.isHero) {
                Text(
                    text = stringResource(Res.string.quran_reward_sunnah_label),
                    color = QuranColors.Teal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = stringResource(reward.text),
                color = QuranColors.Ink,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = if (reward.isHero) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}
