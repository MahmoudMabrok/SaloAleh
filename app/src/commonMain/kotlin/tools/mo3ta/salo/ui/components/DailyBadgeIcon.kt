package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.badge_crown
import tools.mo3ta.salo.generated.resources.badge_crescent
import tools.mo3ta.salo.generated.resources.badge_dome
import tools.mo3ta.salo.generated.resources.badge_heart
import tools.mo3ta.salo.generated.resources.badge_sprout
import tools.mo3ta.salo.generated.resources.badge_tasbih
import tools.mo3ta.salo.generated.resources.badge_sprout_title
import tools.mo3ta.salo.generated.resources.badge_heart_title
import tools.mo3ta.salo.generated.resources.badge_tasbih_title
import tools.mo3ta.salo.generated.resources.badge_dome_title
import tools.mo3ta.salo.generated.resources.badge_crescent_title
import tools.mo3ta.salo.generated.resources.badge_crown_title
import tools.mo3ta.salo.domain.DailyBadge

fun badgeDrawableResource(badge: DailyBadge): DrawableResource? = when (badge) {
    DailyBadge.SPROUT -> Res.drawable.badge_sprout
    DailyBadge.HEART -> Res.drawable.badge_heart
    DailyBadge.TASBIH -> Res.drawable.badge_tasbih
    DailyBadge.DOME -> Res.drawable.badge_dome
    DailyBadge.CRESCENT -> Res.drawable.badge_crescent
    DailyBadge.CROWN -> Res.drawable.badge_crown
}

@Composable
fun badgeTitleString(badge: DailyBadge): String = when (badge) {
    DailyBadge.SPROUT -> stringResource(Res.string.badge_sprout_title)
    DailyBadge.HEART -> stringResource(Res.string.badge_heart_title)
    DailyBadge.TASBIH -> stringResource(Res.string.badge_tasbih_title)
    DailyBadge.DOME -> stringResource(Res.string.badge_dome_title)
    DailyBadge.CRESCENT -> stringResource(Res.string.badge_crescent_title)
    DailyBadge.CROWN -> stringResource(Res.string.badge_crown_title)
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
