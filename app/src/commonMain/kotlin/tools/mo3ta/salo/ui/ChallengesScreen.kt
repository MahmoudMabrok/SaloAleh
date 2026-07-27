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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.challenge_albaqara_body
import tools.mo3ta.salo.generated.resources.challenge_albaqara_title
import tools.mo3ta.salo.generated.resources.challenge_baqiyat_body
import tools.mo3ta.salo.generated.resources.challenge_baqiyat_title
import tools.mo3ta.salo.generated.resources.challenge_alf_hasana_body
import tools.mo3ta.salo.generated.resources.challenge_alf_hasana_title
import tools.mo3ta.salo.generated.resources.challenge_dhikr_body
import tools.mo3ta.salo.generated.resources.challenge_dhikr_title
import tools.mo3ta.salo.generated.resources.challenge_istighfar_body
import tools.mo3ta.salo.generated.resources.challenge_istighfar_title
import tools.mo3ta.salo.generated.resources.challenge_zabad_body
import tools.mo3ta.salo.generated.resources.challenge_zabad_title
import tools.mo3ta.salo.generated.resources.challenge_ghars_body
import tools.mo3ta.salo.generated.resources.challenge_ghars_title
import tools.mo3ta.salo.generated.resources.challenge_quran_body
import tools.mo3ta.salo.generated.resources.challenge_quran_title
import tools.mo3ta.salo.generated.resources.challenge_takbeer_body
import tools.mo3ta.salo.generated.resources.challenge_ten_days_body
import tools.mo3ta.salo.generated.resources.challenges_all_lovers_total
import tools.mo3ta.salo.generated.resources.challenges_overall_total_inline
import tools.mo3ta.salo.generated.resources.challenges_cairo_time_note
import tools.mo3ta.salo.generated.resources.challenges_day_countdown_label
import tools.mo3ta.salo.generated.resources.challenges_subtitle
import tools.mo3ta.salo.generated.resources.challenges_title
import tools.mo3ta.salo.generated.resources.heroes_chip_label
import tools.mo3ta.salo.generated.resources.takbeer_session_title
import tools.mo3ta.salo.generated.resources.tendays_title
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.presentation.ChallengesViewModel
import tools.mo3ta.salo.ui.components.ChallengeStreakChip
import tools.mo3ta.salo.ui.components.HeroesSheet
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

private fun formatNumber(value: Int): String {
    val negative = value < 0
    val digits = (if (negative) -value else value).toString()
    val grouped = buildString {
        for ((index, char) in digits.withIndex()) {
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(char)
        }
    }
    return if (negative) "-$grouped" else grouped
}

private data class ChallengeItem(
    val titleRes: StringResource,
    val bodyRes: StringResource,
    val icon: ImageVector,
    val accent: Color,
    val total: Int,
    val overallTotal: Int = 0,
    val streak: Int = 0,
    val participatedToday: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ChallengesScreen(
    onOpenDhikrChallenge: () -> Unit,
    onOpenTenDays: () -> Unit,
    onOpenTakbeerSession: () -> Unit,
    onOpenBaqiyatChallenge: () -> Unit,
    onOpenIstighfarChallenge: () -> Unit,
    onOpenZabadChallenge: () -> Unit,
    onOpenGharsChallenge: () -> Unit,
    onOpenQuranChallenge: () -> Unit,
    onOpenAlBaqaraChallenge: () -> Unit,
    onOpenAlfHasanaChallenge: () -> Unit,
) {
    val analyticsManager: AnalyticsManager = koinInject()
    val viewModel: ChallengesViewModel = koinViewModel()
    val totals by viewModel.totals.collectAsState()
    val overallTotals by viewModel.overallTotals.collectAsState()
    val streaks by viewModel.streaks.collectAsState()
    val participatedToday by viewModel.participatedToday.collectAsState()
    val heroesBoard by viewModel.heroesBoard.collectAsState()
    val heroesLoading by viewModel.heroesLoading.collectAsState()
    val showHeroesSheet by viewModel.showHeroesSheet.collectAsState()

    LaunchedEffect(Unit) {
        analyticsManager.logView("ChallengesScreen")
        viewModel.onScreenEntered()
    }

    val items = listOf(
        ChallengeItem(
            titleRes = Res.string.challenge_ghars_title,
            bodyRes = Res.string.challenge_ghars_body,
            icon = Icons.Default.Forest,
            accent = Color(0xFF1F5C40),
            total = totals.ghars,
            overallTotal = overallTotals.ghars,
            streak = streaks[ChallengeType.GHARS] ?: 0,
            participatedToday = ChallengeType.GHARS in participatedToday,
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_GHARS_CHALLENGE,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenGharsChallenge()
            },
        ),
        ChallengeItem(
            titleRes = Res.string.challenge_dhikr_title,
            bodyRes = Res.string.challenge_dhikr_body,
            icon = Icons.Default.Spa,
            accent = Color(0xFF7DD3A8),
            total = totals.dhikr,
            overallTotal = overallTotals.dhikr,
            streak = streaks[ChallengeType.DHIKR] ?: 0,
            participatedToday = ChallengeType.DHIKR in participatedToday,
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_DHIKR_REWARDS,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenDhikrChallenge()
            },
        ),
        ChallengeItem(
            titleRes = Res.string.challenge_alf_hasana_title,
            bodyRes = Res.string.challenge_alf_hasana_body,
            icon = Icons.Default.Star,
            accent = Color(0xFFE9C462),
            total = totals.alfHasana,
            overallTotal = overallTotals.alfHasana,
            streak = streaks[ChallengeType.ALF_HASANA] ?: 0,
            participatedToday = ChallengeType.ALF_HASANA in participatedToday,
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_ALF_HASANA_CHALLENGE,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenAlfHasanaChallenge()
            },
        ),
        ChallengeItem(
            titleRes = Res.string.challenge_zabad_title,
            bodyRes = Res.string.challenge_zabad_body,
            icon = Icons.Default.Waves,
            accent = Color(0xFF2ED3C4),
            total = totals.zabad,
            overallTotal = overallTotals.zabad,
            streak = streaks[ChallengeType.ZABAD] ?: 0,
            participatedToday = ChallengeType.ZABAD in participatedToday,
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_ZABAD_CHALLENGE,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenZabadChallenge()
            },
        ),
        ChallengeItem(
            titleRes = Res.string.challenge_baqiyat_title,
            bodyRes = Res.string.challenge_baqiyat_body,
            icon = Icons.Default.AutoAwesome,
            accent = Color(0xFFB68CE0),
            total = totals.baqiyat,
            overallTotal = overallTotals.baqiyat,
            streak = streaks[ChallengeType.BAQIYAT] ?: 0,
            participatedToday = ChallengeType.BAQIYAT in participatedToday,
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_BAQIYAT_CHALLENGE,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenBaqiyatChallenge()
            },
        ),
        ChallengeItem(
            titleRes = Res.string.challenge_istighfar_title,
            bodyRes = Res.string.challenge_istighfar_body,
            icon = Icons.Default.AutoStories,
            accent = Color(0xFFC08A3E),
            total = totals.istighfar,
            overallTotal = overallTotals.istighfar,
            streak = streaks[ChallengeType.ISTIGHFAR] ?: 0,
            participatedToday = ChallengeType.ISTIGHFAR in participatedToday,
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_ISTIGHFAR_CHALLENGE,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenIstighfarChallenge()
            },
        ),
        ChallengeItem(
            titleRes = Res.string.challenge_quran_title,
            bodyRes = Res.string.challenge_quran_body,
            icon = Icons.Default.MenuBook,
            accent = Color(0xFF1F7A5C),
            total = totals.quran,
            overallTotal = overallTotals.quran,
            streak = streaks[ChallengeType.QURAN] ?: 0,
            participatedToday = ChallengeType.QURAN in participatedToday,
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_QURAN_CHALLENGE,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenQuranChallenge()
            },
        ),
        ChallengeItem(
            titleRes = Res.string.challenge_albaqara_title,
            bodyRes = Res.string.challenge_albaqara_body,
            icon = Icons.Default.Book,
            accent = Color(0xFF6C7BE0),
            total = totals.albaqara,
            overallTotal = overallTotals.albaqara,
            onClick = {
                analyticsManager.logAction(
                    AppAnalytics.OPEN_ALBAQARA_CHALLENGE,
                    mapOf(AppAnalytics.PARAM_SOURCE to "challenges"),
                )
                onOpenAlBaqaraChallenge()
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.challenges_cairo_time_note),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                // Compact top-left chips: remaining time + yesterday's hero
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DayCountdownChip()
                    if (heroesBoard != null) {
                        HeroOfYesterdayChip(onClick = { viewModel.openHeroesSheet() })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        items(items) { item ->
            ChallengeCard(item = item)
        }
    }

    if (showHeroesSheet) {
        HeroesSheet(
            board = heroesBoard,
            isLoading = heroesLoading,
            onDismiss = { viewModel.dismissHeroesSheet() },
        )
    }
}

/** Compact top-left chip linking to yesterday's heroes sheet. */
@Composable
private fun HeroOfYesterdayChip(onClick: () -> Unit) {
    val accent = MohamedLoversPalette.GoldHighlight
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(Res.string.heroes_chip_label),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Compact top-left chip: live countdown to the end of the current Cairo day (midnight
 * Africa/Cairo), when all daily challenges reset. Ticks every second while the screen is visible.
 */
@Composable
private fun DayCountdownChip() {
    val cairoZone = remember { TimeZone.of("Africa/Cairo") }
    var remainingSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Clock.System.now()
            val today = now.toLocalDateTime(cairoZone).date
            val endOfDay = today.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(cairoZone)
            val diff = (endOfDay - now).inWholeSeconds
            remainingSeconds = if (diff > 0) diff else 0
            delay(1000)
        }
    }

    val hours = (remainingSeconds / 3600).toInt()
    val minutes = ((remainingSeconds % 3600) / 60).toInt()
    val seconds = (remainingSeconds % 60).toInt()
    val timeText = "${hours.toString().padStart(2, '0')}:" +
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    val accent = MohamedLoversPalette.GoldHighlight
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = stringResource(Res.string.challenges_day_countdown_label),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = timeText,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
        border = if (item.participatedToday) {
            BorderStroke(2.dp, item.accent.copy(alpha = 0.85f))
        } else {
            BorderStroke(1.dp, item.accent.copy(alpha = 0.22f))
        },
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
                if (item.streak > 0) {
                    Spacer(Modifier.height(6.dp))
                    ChallengeStreakChip(streak = item.streak, color = item.accent)
                }
            }

            if (item.overallTotal > 0 || item.total > 0) {
                Column(
                    modifier = Modifier
                        .background(
                            item.accent.copy(alpha = 0.10f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatNumber(item.total),
                        color = item.accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(Res.string.challenges_all_lovers_total),
                        color = item.accent.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (item.overallTotal > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(Res.string.challenges_overall_total_inline, formatNumber(item.overallTotal)),
                            color = item.accent.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.4f),
                )
            }
        }
    }
}
