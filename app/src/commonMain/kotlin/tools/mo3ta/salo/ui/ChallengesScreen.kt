package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.challenge_dhikr_body
import tools.mo3ta.salo.generated.resources.challenge_dhikr_title
import tools.mo3ta.salo.generated.resources.challenge_takbeer_body
import tools.mo3ta.salo.generated.resources.challenge_ten_days_body
import tools.mo3ta.salo.generated.resources.challenges_subtitle
import tools.mo3ta.salo.generated.resources.challenges_title
import tools.mo3ta.salo.generated.resources.takbeer_session_title
import tools.mo3ta.salo.generated.resources.tendays_title
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

private data class ChallengeItem(
    val titleRes: StringResource,
    val bodyRes: StringResource,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit,
)

@Composable
fun ChallengesScreen(
    onOpenDhikrChallenge: () -> Unit,
    onOpenTenDays: () -> Unit,
    onOpenTakbeerSession: () -> Unit,
) {
    val analyticsManager: AnalyticsManager = koinInject()

    LaunchedEffect(Unit) {
        analyticsManager.logView("ChallengesScreen")
    }

    val items = listOf(
        ChallengeItem(
            titleRes = Res.string.challenge_dhikr_title,
            bodyRes = Res.string.challenge_dhikr_body,
            icon = Icons.Default.Spa,
            accent = Color(0xFF7DD3A8),
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_DHIKR_REWARDS,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenDhikrChallenge()
            },
        ),
//        ChallengeItem(
//            titleRes = Res.string.tendays_title,
//            bodyRes = Res.string.challenge_ten_days_body,
//            icon = Icons.Default.EmojiEvents,
//            accent = MohamedLoversPalette.GoldHighlight,
//            onClick = onOpenTenDays,
//        ),
//        ChallengeItem(
//            titleRes = Res.string.takbeer_session_title,
//            bodyRes = Res.string.challenge_takbeer_body,
//            icon = Icons.Default.Groups,
//            accent = Color(0xFF7DD3FC),
//            onClick = {
//                analyticsManager.logAction(
//                    AppAnalytics.OPEN_TAKBEER_SESSION,
//                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
//                )
//                onOpenTakbeerSession()
//            },
//        ),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MohamedLoversPalette.DeepBlue)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.challenges_title),
                    color = MohamedLoversPalette.GoldHighlight,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.challenges_subtitle),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        items(items) { item ->
            ChallengeCard(item = item)
        }
    }
}

@Composable
private fun ChallengeCard(item: ChallengeItem) {
    Surface(
        onClick = item.onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101C33),
        border = BorderStroke(1.dp, item.accent.copy(alpha = 0.22f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(item.accent.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.accent,
                    modifier = Modifier.size(23.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(item.bodyRes),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.4f),
            )
        }
    }
}
