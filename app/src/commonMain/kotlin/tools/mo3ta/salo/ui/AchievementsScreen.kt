package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

private data class BadgeSpec(
    val id: String,
    val emoji: String,
    val title: String,
    val howToEarn: String,
    val canRepeat: Boolean,
)

private val ALL_BADGES = listOf(
    BadgeSpec(
        id = "streak_7",
        emoji = "🏅",
        title = "المداومة",
        howToEarn = "افتح التطبيق 7 أيام متتالية دون انقطاع",
        canRepeat = false,
    ),
    BadgeSpec(
        id = "streak_30",
        emoji = "🌟",
        title = "الوفي",
        howToEarn = "افتح التطبيق 30 يوماً متتالياً دون انقطاع",
        canRepeat = false,
    ),
    BadgeSpec(
        id = "top_rank",
        emoji = "🏆",
        title = "من أهل القمة",
        howToEarn = "احصل على مركز ضمن أفضل 10 مشاركين في أي جولة تنافسية",
        canRepeat = true,
    ),
)

private data class BadgeDisplayItem(val spec: BadgeSpec, val count: Int)

@Composable
fun AchievementsScreen(
    achievements: List<Achievement>,
    onBack: () -> Unit,
) {
    val items = remember(achievements) {
        ALL_BADGES.map { spec ->
            val count = when (spec.id) {
                "streak_7" -> achievements.count { it is Achievement.StreakBadge && it.type == BadgeType.STREAK_7 }
                "streak_30" -> achievements.count { it is Achievement.StreakBadge && it.type == BadgeType.STREAK_30 }
                "top_rank" -> achievements.count { it is Achievement.RankAchievement }
                else -> 0
            }
            BadgeDisplayItem(spec, count)
        }
    }

    var selectedSpec by remember { mutableStateOf<BadgeSpec?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MohamedLoversPalette.DeepBlue)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items) { item ->
                BadgeCard(item, onClick = { selectedSpec = item.spec })
            }
        }
    }

    selectedSpec?.let { spec ->
        AlertDialog(
            onDismissRequest = { selectedSpec = null },
            containerColor = MohamedLoversPalette.DeepBlue,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "${spec.emoji}  ${spec.title}",
                    color = MohamedLoversPalette.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "كيف تحصل عليها:",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = spec.howToEarn,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                    if (spec.canRepeat) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "✨ يمكن الحصول عليها أكثر من مرة",
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

@Composable
private fun BadgeCard(item: BadgeDisplayItem, onClick: () -> Unit) {
    val achieved = item.count > 0
    Box(
        modifier = Modifier
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
            Text(text = item.spec.emoji, fontSize = 36.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.spec.title,
                color = if (achieved) MohamedLoversPalette.Gold else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }

        // Count badge — top-left — shown only when earned 2+ times
        if (item.count >= 2) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(MohamedLoversPalette.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${item.count}",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Lock icon — top-right — shown only when unachieved
        if (!achieved) {
            Text(
                text = "🔒",
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
    }
}
