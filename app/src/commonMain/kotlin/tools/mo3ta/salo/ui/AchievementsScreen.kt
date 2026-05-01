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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.BadgeType
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
import tools.mo3ta.salo.presentation.AchievementsViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

// ── Badge definitions ──────────────────────────────────────────────────────────

private data class BadgeSpec(
    val id: String,
    val title: String,
    val howToEarn: String,
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
        id = "streak_7", emoji = "🏅", title = "المداومة",
        howToEarn = "افتح التطبيق 7 أيام متتالية دون انقطاع",
        canRepeat = false, streakTarget = 7,
        drawable = Res.drawable.badge_streak_7_day,
    ))
    add(BadgeSpec(
        id = "streak_30", emoji = "🌟", title = "المحب",
        howToEarn = "افتح التطبيق 30 يوماً متتالياً دون انقطاع",
        canRepeat = false, streakTarget = 30,
        drawable = Res.drawable.badge_streak_30_day,
    ))
    for (rank in 1..10) {
        add(BadgeSpec(
            id = "rank_$rank", rankPosition = rank,
            title = "المركز $rank",
            howToEarn = "احصل على المركز $rank في أي جولة تنافسية",
            canRepeat = true,
            drawable = rankDrawable(rank),
        ))
    }
}

private data class BadgeDisplayItem(val spec: BadgeSpec, val count: Int)

// ── Screen ─────────────────────────────────────────────────────────────────────

@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = koinViewModel(),
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val currentStreak by viewModel.currentStreak.collectAsStateWithLifecycle()

    val items = remember(achievements) {
        ALL_BADGES.map { spec ->
            val count = when {
                spec.id == "streak_7" -> achievements.count { it is Achievement.StreakBadge && it.type == BadgeType.STREAK_7 }
                spec.id == "streak_30" -> achievements.count { it is Achievement.StreakBadge && it.type == BadgeType.STREAK_30 }
                spec.rankPosition != null -> achievements.count { it is Achievement.RankAchievement && it.rank == spec.rankPosition }
                else -> 0
            }
            BadgeDisplayItem(spec, count)
        }
    }

    val roundHistory = remember(achievements) {
        achievements
            .filterIsInstance<Achievement.RankAchievement>()
            .sortedByDescending { it.earnedDate }
    }

    var selectedSpec by remember { mutableStateOf<BadgeSpec?>(null) }

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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            Text(
                text = "إنجازاتي",
                color = MohamedLoversPalette.Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(48.dp))
        }

        // ── Badges grid ──
        SectionLabel("الشارات")
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        BadgeCard(
                            item = item,
                            currentStreak = currentStreak,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedSpec = item.spec },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // ── Round history ──
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
        SectionLabel("إنجازات الجولات")

        if (roundHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "لم تحصل على مركز ضمن أفضل 10 بعد\nاستمر في المشاركة! 🌟",
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                roundHistory.forEach { RoundHistoryCard(it) }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── Tap-to-explain dialog ──
    selectedSpec?.let { spec ->
        AlertDialog(
            onDismissRequest = { selectedSpec = null },
            containerColor = MohamedLoversPalette.DeepBlue,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = buildString {
                        spec.emoji?.let { append("$it  ") }
                        append(spec.title)
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
                    Text("كيف تحصل عليها:", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    Text(spec.howToEarn, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                    if (spec.canRepeat) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "✨ يمكن الحصول عليها أكثر من مرة",
                            color = MohamedLoversPalette.Gold.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSpec = null }) {
                    Text("حسناً", color = MohamedLoversPalette.Gold, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

// ── Badge card ─────────────────────────────────────────────────────────────────

@Composable
private fun BadgeCard(
    item: BadgeDisplayItem,
    currentStreak: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val achieved = item.count > 0
    val spec = item.spec

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
                contentDescription = spec.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = spec.title,
                color = if (achieved) MohamedLoversPalette.Gold else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (!achieved && spec.streakTarget != null) {
                val progress = currentStreak.coerceAtMost(spec.streakTarget)
                Text(
                    text = "$progress / ${spec.streakTarget}",
                    color = MohamedLoversPalette.Gold.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Count badge — top-left — 2+ earned
        if (item.count >= 2) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .background(MohamedLoversPalette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${item.count}",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Lock — top-right — unachieved
        if (!achieved) {
            Text(
                text = "🔒",
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

// ── Round history card ─────────────────────────────────────────────────────────

@Composable
private fun RoundHistoryCard(achievement: Achievement.RankAchievement) {
    val medalEmoji = when (achievement.rank) {
        1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "🏅"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = medalEmoji, fontSize = 28.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "المركز ${achievement.rank}",
                color = MohamedLoversPalette.Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                text = achievement.roundKey,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
            )
        }
        Text(
            text = achievement.earnedDate.toString(),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

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
