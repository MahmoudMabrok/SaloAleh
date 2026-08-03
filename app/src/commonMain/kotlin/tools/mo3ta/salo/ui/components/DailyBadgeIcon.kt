package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.badge_crown
import tools.mo3ta.salo.generated.resources.badge_crescent
import tools.mo3ta.salo.generated.resources.badge_dome
import tools.mo3ta.salo.generated.resources.badge_heart
import tools.mo3ta.salo.generated.resources.badge_lantern
import tools.mo3ta.salo.generated.resources.badge_mihrab
import tools.mo3ta.salo.generated.resources.badge_spark
import tools.mo3ta.salo.generated.resources.badge_sprout
import tools.mo3ta.salo.generated.resources.badge_star
import tools.mo3ta.salo.generated.resources.badge_tasbih
import tools.mo3ta.salo.generated.resources.badge_spark_title
import tools.mo3ta.salo.generated.resources.badge_sprout_title
import tools.mo3ta.salo.generated.resources.badge_heart_title
import tools.mo3ta.salo.generated.resources.badge_tasbih_title
import tools.mo3ta.salo.generated.resources.badge_dome_title
import tools.mo3ta.salo.generated.resources.badge_crescent_title
import tools.mo3ta.salo.generated.resources.badge_lantern_title
import tools.mo3ta.salo.generated.resources.badge_crown_title
import tools.mo3ta.salo.generated.resources.badge_mihrab_title
import tools.mo3ta.salo.generated.resources.badge_star_title
import tools.mo3ta.salo.generated.resources.badge_spark_desc
import tools.mo3ta.salo.generated.resources.badge_sprout_desc
import tools.mo3ta.salo.generated.resources.badge_heart_desc
import tools.mo3ta.salo.generated.resources.badge_tasbih_desc
import tools.mo3ta.salo.generated.resources.badge_dome_desc
import tools.mo3ta.salo.generated.resources.badge_crescent_desc
import tools.mo3ta.salo.generated.resources.badge_lantern_desc
import tools.mo3ta.salo.generated.resources.badge_crown_desc
import tools.mo3ta.salo.generated.resources.badge_mihrab_desc
import tools.mo3ta.salo.generated.resources.badge_star_desc
import tools.mo3ta.salo.domain.DailyBadge

fun badgeDrawableResource(badge: DailyBadge): DrawableResource? = when (badge) {
    DailyBadge.SPARK -> Res.drawable.badge_spark
    DailyBadge.SPROUT -> Res.drawable.badge_sprout
    DailyBadge.HEART -> Res.drawable.badge_heart
    DailyBadge.TASBIH -> Res.drawable.badge_tasbih
    DailyBadge.DOME -> Res.drawable.badge_dome
    DailyBadge.CRESCENT -> Res.drawable.badge_crescent
    DailyBadge.LANTERN -> Res.drawable.badge_lantern
    DailyBadge.CROWN -> Res.drawable.badge_crown
    DailyBadge.MIHRAB -> Res.drawable.badge_mihrab
    DailyBadge.STAR -> Res.drawable.badge_star
}

@Composable
fun badgeTitleString(badge: DailyBadge): String = when (badge) {
    DailyBadge.SPARK -> stringResource(Res.string.badge_spark_title)
    DailyBadge.SPROUT -> stringResource(Res.string.badge_sprout_title)
    DailyBadge.HEART -> stringResource(Res.string.badge_heart_title)
    DailyBadge.TASBIH -> stringResource(Res.string.badge_tasbih_title)
    DailyBadge.DOME -> stringResource(Res.string.badge_dome_title)
    DailyBadge.CRESCENT -> stringResource(Res.string.badge_crescent_title)
    DailyBadge.LANTERN -> stringResource(Res.string.badge_lantern_title)
    DailyBadge.CROWN -> stringResource(Res.string.badge_crown_title)
    DailyBadge.MIHRAB -> stringResource(Res.string.badge_mihrab_title)
    DailyBadge.STAR -> stringResource(Res.string.badge_star_title)
}

@Composable
fun badgeDescriptionString(badge: DailyBadge): String = when (badge) {
    DailyBadge.SPARK -> stringResource(Res.string.badge_spark_desc, badge.threshold)
    DailyBadge.SPROUT -> stringResource(Res.string.badge_sprout_desc, badge.threshold)
    DailyBadge.HEART -> stringResource(Res.string.badge_heart_desc, badge.threshold)
    DailyBadge.TASBIH -> stringResource(Res.string.badge_tasbih_desc, badge.threshold)
    DailyBadge.DOME -> stringResource(Res.string.badge_dome_desc, badge.threshold)
    DailyBadge.CRESCENT -> stringResource(Res.string.badge_crescent_desc, badge.threshold)
    DailyBadge.LANTERN -> stringResource(Res.string.badge_lantern_desc, badge.threshold)
    DailyBadge.CROWN -> stringResource(Res.string.badge_crown_desc, badge.threshold)
    DailyBadge.MIHRAB -> stringResource(Res.string.badge_mihrab_desc, badge.threshold)
    DailyBadge.STAR -> stringResource(Res.string.badge_star_desc, badge.threshold)
}

@Composable
fun DailyBadgeIcon(
    badgeKey: String?,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    val badge = badgeKey?.let { DailyBadge.fromKey(it) } ?: return
    val res = badgeDrawableResource(badge) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = badgeTitleString(badge),
        modifier = modifier.size(size),
    )
}

@Composable
fun DailyBadgeInfoDialog(
    badge: DailyBadge,
    onDismiss: () -> Unit,
) {
    val res = badgeDrawableResource(badge)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MohamedLoversPalette.SkyTop,
        shape = RoundedCornerShape(20.dp),
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (res != null) {
                    Image(
                        painter = painterResource(res),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = badgeTitleString(badge),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = badgeDescriptionString(badge),
                    color = MohamedLoversPalette.GoldBase.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "حسنًا",
                    color = MohamedLoversPalette.GoldHighlight,
                )
            }
        },
    )
}
