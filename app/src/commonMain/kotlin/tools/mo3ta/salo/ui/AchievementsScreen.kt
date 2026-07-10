package tools.mo3ta.salo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.badge_10th_place
import tools.mo3ta.salo.generated.resources.badge_1st_place
import tools.mo3ta.salo.generated.resources.badge_2nd_place
import tools.mo3ta.salo.generated.resources.badge_3rd_place
import tools.mo3ta.salo.generated.resources.badge_4th_place
import tools.mo3ta.salo.generated.resources.badge_5th_place
import tools.mo3ta.salo.generated.resources.badge_6th_place
import tools.mo3ta.salo.generated.resources.badge_7th_place
import tools.mo3ta.salo.generated.resources.badge_8th_place
import tools.mo3ta.salo.generated.resources.badge_9th_place
import tools.mo3ta.salo.generated.resources.badge_streak_30_day
import tools.mo3ta.salo.generated.resources.badge_streak_7_day
import tools.mo3ta.salo.generated.resources.achievements_back_cd
import tools.mo3ta.salo.generated.resources.achievements_badge_how_label
import tools.mo3ta.salo.generated.resources.achievements_badge_rank_how
import tools.mo3ta.salo.generated.resources.achievements_badge_rank_title
import tools.mo3ta.salo.generated.resources.achievements_badge_repeatable
import tools.mo3ta.salo.generated.resources.achievements_badge_challenge_baqiyat_how
import tools.mo3ta.salo.generated.resources.achievements_badge_challenge_dhikr_how
import tools.mo3ta.salo.generated.resources.achievements_badge_challenge_istighfar_how
import tools.mo3ta.salo.generated.resources.achievements_badge_round_streak_how
import tools.mo3ta.salo.generated.resources.achievements_badge_round_streak_title
import tools.mo3ta.salo.generated.resources.achievements_section_challenge_badges
import tools.mo3ta.salo.generated.resources.achievements_section_round_streak
import tools.mo3ta.salo.generated.resources.challenge_baqiyat_title
import tools.mo3ta.salo.generated.resources.challenge_dhikr_title
import tools.mo3ta.salo.generated.resources.challenge_istighfar_title
import tools.mo3ta.salo.generated.resources.achievements_badge_streak_30_how
import tools.mo3ta.salo.generated.resources.achievements_badge_streak_30_title
import tools.mo3ta.salo.generated.resources.achievements_badge_streak_7_how
import tools.mo3ta.salo.generated.resources.achievements_badge_streak_7_title
import tools.mo3ta.salo.generated.resources.achievements_copy_winner_code_cd
import tools.mo3ta.salo.generated.resources.achievements_dialog_ok
import tools.mo3ta.salo.generated.resources.achievements_empty_history
import tools.mo3ta.salo.generated.resources.achievements_medal_info_body
import tools.mo3ta.salo.generated.resources.achievements_medal_info_cd
import tools.mo3ta.salo.generated.resources.achievements_medal_info_title
import tools.mo3ta.salo.generated.resources.achievements_prayer_count_label
import tools.mo3ta.salo.generated.resources.achievements_round_label
import tools.mo3ta.salo.generated.resources.achievements_round_rank
import tools.mo3ta.salo.generated.resources.achievements_section_rank_badges
import tools.mo3ta.salo.generated.resources.achievements_section_round_history
import tools.mo3ta.salo.generated.resources.achievements_section_streak_badges
import tools.mo3ta.salo.generated.resources.achievements_stat_badges
import tools.mo3ta.salo.generated.resources.achievements_stat_best_rank
import tools.mo3ta.salo.generated.resources.achievements_stat_rounds
import tools.mo3ta.salo.generated.resources.achievements_streak_complete
import tools.mo3ta.salo.generated.resources.achievements_streak_progress
import tools.mo3ta.salo.generated.resources.achievements_title
import tools.mo3ta.salo.generated.resources.mohamed_lovers_code_copied
import tools.mo3ta.salo.presentation.AchievementsViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

// ── Badge definitions ──────────────────────────────────────────────────────────

private data class BadgeSpec(
    val id: String,
    val titleRes: StringResource,
    val titleArgs: List<Any> = emptyList(),
    val howToEarnRes: StringResource,
    val howToEarnArgs: List<Any> = emptyList(),
    val canRepeat: Boolean,
    val drawable: DrawableResource,
    val emoji: String? = null,
    val rankPosition: Int? = null,
    val streakTarget: Int? = null,
)

private fun rankDrawable(rank: Int): DrawableResource = when (rank) {
    1 -> Res.drawable.badge_1st_place
    2 -> Res.drawable.badge_2nd_place
    3 -> Res.drawable.badge_3rd_place
    4 -> Res.drawable.badge_4th_place
    5 -> Res.drawable.badge_5th_place
    6 -> Res.drawable.badge_6th_place
    7 -> Res.drawable.badge_7th_place
    8 -> Res.drawable.badge_8th_place
    9 -> Res.drawable.badge_9th_place
    else -> Res.drawable.badge_10th_place
}

private val ALL_BADGES: List<BadgeSpec> = buildList {
    add(BadgeSpec(
        id = "streak_7", emoji = "🏅",
        titleRes = Res.string.achievements_badge_streak_7_title,
        howToEarnRes = Res.string.achievements_badge_streak_7_how,
        canRepeat = false, streakTarget = 7,
        drawable = Res.drawable.badge_streak_7_day,
    ))
    add(BadgeSpec(
        id = "streak_30", emoji = "🌟",
        titleRes = Res.string.achievements_badge_streak_30_title,
        howToEarnRes = Res.string.achievements_badge_streak_30_how,
        canRepeat = false, streakTarget = 30,
        drawable = Res.drawable.badge_streak_30_day,
    ))
    for (rank in 1..10) {
        add(BadgeSpec(
            id = "rank_$rank", rankPosition = rank,
            titleRes = Res.string.achievements_badge_rank_title,
            titleArgs = listOf(rank),
            howToEarnRes = Res.string.achievements_badge_rank_how,
            howToEarnArgs = listOf(rank),
            canRepeat = true,
            drawable = rankDrawable(rank),
        ))
    }
}

private data class BadgeDisplayItem(val spec: BadgeSpec, val count: Int)

// ── Challenge badges: one per daily challenge; every win (daily goal reached) adds 1 ──

private data class ChallengeBadgeSpec(
    val type: ChallengeType,
    val emoji: String,
    val titleRes: StringResource,
    val howToEarnRes: StringResource,
)

private val CHALLENGE_BADGES = listOf(
    ChallengeBadgeSpec(ChallengeType.DHIKR, "📿", Res.string.challenge_dhikr_title, Res.string.achievements_badge_challenge_dhikr_how),
    ChallengeBadgeSpec(ChallengeType.BAQIYAT, "✨", Res.string.challenge_baqiyat_title, Res.string.achievements_badge_challenge_baqiyat_how),
    ChallengeBadgeSpec(ChallengeType.ISTIGHFAR, "🤲", Res.string.challenge_istighfar_title, Res.string.achievements_badge_challenge_istighfar_how),
)

// ── Screen ─────────────────────────────────────────────────────────────────────

@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    viewModel: AchievementsViewModel = koinViewModel(),
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val currentStreak by viewModel.currentStreak.collectAsStateWithLifecycle()
    val challengeBadgeCounts by viewModel.challengeBadgeCounts.collectAsStateWithLifecycle()

    val analyticsManager: AnalyticsManager = koinInject()
    LaunchedEffect(Unit) { analyticsManager.logView("AchievementsScreen") }

    val streakItems = remember(achievements) {
        ALL_BADGES.filter { it.streakTarget != null }.map { spec ->
            val count = when (spec.id) {
                "streak_7" -> achievements.count { it is Achievement.StreakBadge && it.type == BadgeType.STREAK_7 }
                "streak_30" -> achievements.count { it is Achievement.StreakBadge && it.type == BadgeType.STREAK_30 }
                else -> 0
            }
            BadgeDisplayItem(spec, count)
        }
    }

    val rankItems = remember(achievements) {
        ALL_BADGES.filter { it.rankPosition != null }.map { spec ->
            val count = achievements.count { it is Achievement.RankAchievement && it.rank == spec.rankPosition }
            BadgeDisplayItem(spec, count)
        }
    }

    val roundHistory = remember(achievements) {
        achievements
            .filterIsInstance<Achievement.RankAchievement>()
            .sortedByDescending { it.earnedDate }
    }

    val roundStreakCount = remember(achievements) {
        achievements.count { it is Achievement.RoundStreakBadge }
    }

    val earnedBadgesCount = remember(streakItems, rankItems, roundStreakCount, challengeBadgeCounts) {
        (streakItems + rankItems).count { it.count > 0 } +
            (if (roundStreakCount > 0) 1 else 0) +
            challengeBadgeCounts.values.count { it > 0 }
    }
    val bestRank = remember(roundHistory) { roundHistory.minOfOrNull { it.rank } }

    var selectedSpec by remember { mutableStateOf<BadgeSpec?>(null) }
    var selectedChallengeBadge by remember { mutableStateOf<ChallengeBadgeSpec?>(null) }
    var showMedalInfo by remember { mutableStateOf(false) }
    var showRoundStreakInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MohamedLoversPalette.DeepBlue)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.achievements_back_cd), tint = Color.White)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            Text(
                text = stringResource(Res.string.achievements_title),
                color = MohamedLoversPalette.Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(48.dp))
        }

        // ── Stats trophy bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard("🏅", earnedBadgesCount.toString(), stringResource(Res.string.achievements_stat_badges), Modifier.weight(1f))
            StatCard("🗓️", roundHistory.size.toString(), stringResource(Res.string.achievements_stat_rounds), Modifier.weight(1f))
            StatCard("🥇", if (bestRank != null) "#$bestRank" else "—", stringResource(Res.string.achievements_stat_best_rank), Modifier.weight(1f))
        }

        // ── Streak badges (horizontal row cards) ──
        SectionLabel(stringResource(Res.string.achievements_section_streak_badges))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            streakItems.forEach { item ->
                StreakBadgeCard(
                    item = item,
                    currentStreak = currentStreak,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedSpec = item.spec },
                )
            }
        }

        // ── Perfect-week (round streak) badge ──
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(Res.string.achievements_section_round_streak))
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            RoundStreakBadgeCard(
                count = roundStreakCount,
                onClick = { showRoundStreakInfo = true },
            )
        }

        // ── Challenge badges (one per daily challenge, +1 per win) ──
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(Res.string.achievements_section_challenge_badges))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CHALLENGE_BADGES.forEach { spec ->
                ChallengeBadgeCard(
                    spec = spec,
                    count = challengeBadgeCounts[spec.type] ?: 0,
                    onClick = { selectedChallengeBadge = spec },
                )
            }
        }

        // ── Rank badges ──
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.achievements_section_rank_badges),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            IconButton(
                onClick = { showMedalInfo = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(Res.string.achievements_medal_info_cd),
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rankItems.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        BadgeCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedSpec = item.spec },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // ── Round history timeline ──
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
        SectionLabel(stringResource(Res.string.achievements_section_round_history))

        if (roundHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.achievements_empty_history),
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
        } else {
            val lineColor = MohamedLoversPalette.Gold.copy(alpha = 0.30f)
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .drawBehind {
                        if (roundHistory.size > 1) {
                            drawLine(
                                color = lineColor,
                                start = Offset(size.width - 10.dp.toPx(), 22.dp.toPx()),
                                end = Offset(size.width - 10.dp.toPx(), size.height - 22.dp.toPx()),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                roundHistory.forEach { RoundHistoryCard(it) }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── Badge tap-to-explain dialog ──
    selectedSpec?.let { spec ->
        val resolvedTitle = stringResource(spec.titleRes, *spec.titleArgs.toTypedArray())
        val resolvedHowToEarn = stringResource(spec.howToEarnRes, *spec.howToEarnArgs.toTypedArray())
        AlertDialog(
            onDismissRequest = { selectedSpec = null },
            containerColor = MohamedLoversPalette.DeepBlue,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = buildString {
                        spec.emoji?.let { append("$it  ") }
                        append(resolvedTitle)
                    },
                    color = MohamedLoversPalette.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.achievements_badge_how_label), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    Text(resolvedHowToEarn, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                    if (spec.canRepeat) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "✨ " + stringResource(Res.string.achievements_badge_repeatable),
                            color = MohamedLoversPalette.Gold.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSpec = null }) {
                    Text(stringResource(Res.string.achievements_dialog_ok), color = MohamedLoversPalette.Gold, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    // ── Challenge badge info dialog ──
    selectedChallengeBadge?.let { spec ->
        AlertDialog(
            onDismissRequest = { selectedChallengeBadge = null },
            containerColor = MohamedLoversPalette.DeepBlue,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "${spec.emoji}  " + stringResource(spec.titleRes),
                    color = MohamedLoversPalette.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.achievements_badge_how_label), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    Text(stringResource(spec.howToEarnRes, spec.type.dailyGoal), color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✨ " + stringResource(Res.string.achievements_badge_repeatable),
                        color = MohamedLoversPalette.Gold.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedChallengeBadge = null }) {
                    Text(stringResource(Res.string.achievements_dialog_ok), color = MohamedLoversPalette.Gold, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    // ── Round-streak badge info dialog ──
    if (showRoundStreakInfo) {
        AlertDialog(
            onDismissRequest = { showRoundStreakInfo = false },
            containerColor = MohamedLoversPalette.DeepBlue,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "🔥  " + stringResource(Res.string.achievements_badge_round_streak_title),
                    color = MohamedLoversPalette.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.achievements_badge_how_label), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    Text(stringResource(Res.string.achievements_badge_round_streak_how), color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✨ " + stringResource(Res.string.achievements_badge_repeatable),
                        color = MohamedLoversPalette.Gold.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRoundStreakInfo = false }) {
                    Text(stringResource(Res.string.achievements_dialog_ok), color = MohamedLoversPalette.Gold, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    // ── Medal info dialog ──
    if (showMedalInfo) {
        AlertDialog(
            onDismissRequest = { showMedalInfo = false },
            containerColor = MohamedLoversPalette.DeepBlue,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "🏅  " + stringResource(Res.string.achievements_medal_info_title),
                    color = MohamedLoversPalette.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.achievements_medal_info_body),
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                TextButton(onClick = { showMedalInfo = false }) {
                    Text(stringResource(Res.string.achievements_dialog_ok), color = MohamedLoversPalette.Gold, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

// ── Badge card (rank badges) ──────────────────────────────────────────────────

@Composable
private fun BadgeCard(
    item: BadgeDisplayItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val achieved = item.count > 0
    val spec = item.spec
    val title = stringResource(spec.titleRes, *spec.titleArgs.toTypedArray())

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                color = if (achieved) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f),
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = if (achieved) MohamedLoversPalette.Gold.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(if (achieved) 1f else 0.3f),
        ) {
            Image(
                painter = painterResource(spec.drawable),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                color = if (achieved) MohamedLoversPalette.Gold else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }

        if (item.count >= 2) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .background(MohamedLoversPalette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "${item.count}", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (!achieved) {
            Text(text = "🔒", fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }
}

// ── Streak badge card (horizontal, with progress ring) ────────────────────────

@Composable
private fun StreakBadgeCard(
    item: BadgeDisplayItem,
    currentStreak: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val achieved = item.count > 0
    val spec = item.spec
    val title = stringResource(spec.titleRes, *spec.titleArgs.toTypedArray())
    val target = spec.streakTarget ?: return
    val progress = if (achieved) target else currentStreak.coerceAtMost(target)

    Row(
        modifier = modifier
            .background(
                if (achieved) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (achieved) MohamedLoversPalette.Gold.copy(alpha = 0.42f)
                else MohamedLoversPalette.Gold.copy(alpha = 0.16f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(42.dp)) {
                val strokePx = 3.dp.toPx()
                val r = (size.minDimension - strokePx) / 2
                val tl = Offset((size.width - r * 2) / 2, (size.height - r * 2) / 2)
                val sz = Size(r * 2, r * 2)
                drawArc(
                    color = Color.White.copy(alpha = 0.10f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    topLeft = tl, size = sz,
                )
                if (progress > 0) {
                    drawArc(
                        color = MohamedLoversPalette.Gold.copy(alpha = if (achieved) 0.90f else 0.50f),
                        startAngle = -90f,
                        sweepAngle = 360f * progress / target,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        topLeft = tl, size = sz,
                    )
                }
            }
            Image(
                painter = painterResource(spec.drawable),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(26.dp).alpha(if (!achieved && progress == 0) 0.3f else 1f),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (achieved) MohamedLoversPalette.Gold else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (achieved) "✓ " + stringResource(Res.string.achievements_streak_complete) else stringResource(Res.string.achievements_streak_progress, progress, target),
                color = MohamedLoversPalette.Gold.copy(alpha = if (achieved) 0.7f else 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Round-streak (perfect week) badge card ────────────────────────────────────

@Composable
private fun RoundStreakBadgeCard(
    count: Int,
    onClick: () -> Unit,
) {
    val achieved = count > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (achieved) MohamedLoversPalette.Gold.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (achieved) MohamedLoversPalette.Gold.copy(alpha = 0.42f)
                else MohamedLoversPalette.Gold.copy(alpha = 0.16f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (achieved) MohamedLoversPalette.Gold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔥", fontSize = 22.sp, modifier = Modifier.alpha(if (achieved) 1f else 0.4f))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.achievements_badge_round_streak_title),
                color = if (achieved) MohamedLoversPalette.Gold else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(Res.string.achievements_badge_round_streak_how),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        if (count >= 2) {
            Box(
                modifier = Modifier.size(22.dp).background(MohamedLoversPalette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "$count", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (!achieved) {
            Text(text = "🔒", fontSize = 13.sp)
        }
    }
}

// ── Challenge badge card (one per daily challenge, count grows with each win) ─

@Composable
private fun ChallengeBadgeCard(
    spec: ChallengeBadgeSpec,
    count: Int,
    onClick: () -> Unit,
) {
    val achieved = count > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (achieved) MohamedLoversPalette.Gold.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (achieved) MohamedLoversPalette.Gold.copy(alpha = 0.42f)
                else MohamedLoversPalette.Gold.copy(alpha = 0.16f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (achieved) MohamedLoversPalette.Gold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = spec.emoji, fontSize = 22.sp, modifier = Modifier.alpha(if (achieved) 1f else 0.4f))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(spec.titleRes),
                color = if (achieved) MohamedLoversPalette.Gold else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(spec.howToEarnRes, spec.type.dailyGoal),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        if (achieved) {
            Box(
                modifier = Modifier.size(22.dp).background(MohamedLoversPalette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "$count", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(text = "🔒", fontSize = 13.sp)
        }
    }
}

// ── Round history card (timeline, Variation A style) ──────────────────────────

@Composable
private fun RoundHistoryCard(achievement: Achievement.RankAchievement) {
    val codeCopiedLabel = stringResource(Res.string.mohamed_lovers_code_copied)
    val winnerCode = remember(achievement.winnerCode) { winnerCodeForCopyButton(achievement) }
    val medalEmoji = when (achievement.rank) {
        1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "🏅"
    }
    val isTop3 = achievement.rank <= 3
    val dotColor = when (achievement.rank) {
        1 -> MohamedLoversPalette.Gold
        2 -> MohamedLoversPalette.RankSilver
        3 -> MohamedLoversPalette.RankBronze
        else -> Color.White.copy(alpha = 0.15f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Dot (first → appears on RIGHT in RTL, aligns with timeline line)
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(dotColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = medalEmoji, fontSize = 10.sp)
        }
        // Card
        Row(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (isTop3) MohamedLoversPalette.Gold.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(14.dp),
                )
                .border(
                    1.dp,
                    if (isTop3) MohamedLoversPalette.Gold.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.achievements_round_rank, achievement.rank),
                    color = MohamedLoversPalette.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(Res.string.achievements_round_label), color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                    Text(achievement.roundKey, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                }
            }
            if ((achievement.score != null && achievement.score > 0) || winnerCode != null) {
                Column(horizontalAlignment = Alignment.End) {
                    if (achievement.score != null && achievement.score > 0) {
                        Text(
                            text = stringResource(Res.string.achievements_prayer_count_label),
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 9.sp,
                            letterSpacing = 0.3.sp,
                        )
                        Text(
                            text = "${achievement.score} 🤍",
                            color = MohamedLoversPalette.Gold.copy(alpha = 0.88f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (winnerCode != null) {
                        Spacer(Modifier.height(4.dp))
                        IconButton(
                            onClick = {
                                copyToClipboard(winnerCode)
                                showPlatformToast(codeCopiedLabel)
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(Res.string.achievements_copy_winner_code_cd),
                                tint = MohamedLoversPalette.Gold,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun winnerCodeForCopyButton(achievement: Achievement.RankAchievement): String? =
    achievement.winnerCode.trim().takeIf { it.isNotBlank() }

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .border(1.dp, MohamedLoversPalette.Gold.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = icon, fontSize = 18.sp)
        Text(text = value, color = MohamedLoversPalette.Gold, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
