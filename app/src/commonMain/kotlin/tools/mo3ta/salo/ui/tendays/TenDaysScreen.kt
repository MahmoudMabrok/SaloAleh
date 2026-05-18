package tools.mo3ta.salo.ui.tendays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.data.tendays.DhikrType
import tools.mo3ta.salo.ui.TakbeerOverlayButton
import tools.mo3ta.salo.presentation.TenDaysDayState
import tools.mo3ta.salo.presentation.TenDaysUiState
import tools.mo3ta.salo.presentation.TenDaysViewModel

@Composable
fun TenDaysScreen(
    onBack: () -> Unit = {},
    viewModel: TenDaysViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TenDaysPalette.BackgroundDark,
                        TenDaysPalette.BackgroundMid,
                        TenDaysPalette.BackgroundLight,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = TenDaysPalette.TextPrimary,
                )
            }

            TenDaysHeader(state)
            Spacer(Modifier.height(12.dp))
            ScoreBanner(state.totalScore)
            Spacer(Modifier.height(12.dp))
            DaySelector(
                currentDay = state.currentDay,
                days = state.days,
                onDaySelected = viewModel::onDaySelected,
            )
            Spacer(Modifier.height(14.dp))

            val currentDayState = state.days.getOrNull(state.currentDay - 1)
                ?: TenDaysDayState(day = state.currentDay)

            BaqiyatSection(dayState = currentDayState, onDhikrTap = viewModel::onDhikrTap)
            Spacer(Modifier.height(10.dp))
            FastingRow(
                isFasting = currentDayState.isFasting,
                canToggle = state.canFast,
                onToggle = viewModel::onFastingToggle,
            )
            Spacer(Modifier.height(10.dp))
            TakbeerRow(
                count = currentDayState.takbeerCount,
                onTap = viewModel::onTakbeerTap,
                autoPlay = state.autoPlayTakbeer,
                onAutoPlayToggle = viewModel::onAutoPlayToggle,
                intervalMinutes = state.takbeerIntervalMinutes,
                onIntervalChanged = viewModel::onTakbeerIntervalChanged,
                repeatCount = state.takbeerRepeatCount,
                onRepeatChanged = viewModel::onTakbeerRepeatChanged,
            )
            Spacer(Modifier.height(10.dp))
            SadaqahRow(isSadaqah = currentDayState.isSadaqah, onToggle = viewModel::onSadaqahToggle)
            Spacer(Modifier.height(10.dp))
            TakbeerOverlayButton(
                autoRemind = state.autoPlayTakbeer,
                intervalMinutes = state.takbeerIntervalMinutes,
                repeatCount = state.takbeerRepeatCount,
            )
            Spacer(Modifier.height(14.dp))
            MiniLeaderboard(state)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TenDaysHeader(state: TenDaysUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "عشر ذي الحجة",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TenDaysPalette.Gold,
        )
        Text(
            text = "اليوم ${state.currentDay} من ${state.totalDays}",
            fontSize = 14.sp,
            color = TenDaysPalette.TextSecondary,
        )
    }
}

@Composable
private fun ScoreBanner(totalScore: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(TenDaysPalette.Gold, TenDaysPalette.GoldDim)
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "مجموع نقاطك",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Text(
                text = "$totalScore",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
    }
}

@Composable
private fun DaySelector(
    currentDay: Int,
    days: List<TenDaysDayState>,
    onDaySelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        days.forEach { dayState ->
            val isCurrent = dayState.day == currentDay
            val isCompleted = dayState.dayScore > 0 && dayState.day < currentDay
            val bgColor = when {
                isCurrent -> TenDaysPalette.Gold
                isCompleted -> TenDaysPalette.Green
                else -> TenDaysPalette.SurfaceDark
            }
            val textColor = when {
                isCurrent -> Color.Black
                isCompleted -> Color.White
                else -> TenDaysPalette.GrayBorder
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${dayState.day}",
                    fontSize = 12.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun BaqiyatSection(
    dayState: TenDaysDayState,
    onDhikrTap: (DhikrType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "الباقيات الصالحات",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TenDaysPalette.TextPrimary,
            )
            Text(
                text = "+١ لكل ذكر",
                fontSize = 11.sp,
                color = TenDaysPalette.Gold,
            )
        }

        Spacer(Modifier.height(10.dp))

        val topRow = listOf(DhikrType.SubhanAllah, DhikrType.Alhamdulillah)
        val midRow = listOf(DhikrType.AllahuAkbar, DhikrType.LaIlahaIllallah)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            topRow.forEach { dhikr ->
                DhikrCell(
                    dhikr = dhikr,
                    count = dayState.dhikrCounts[dhikr] ?: 0,
                    onClick = { onDhikrTap(dhikr) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            midRow.forEach { dhikr ->
                DhikrCell(
                    dhikr = dhikr,
                    count = dayState.dhikrCounts[dhikr] ?: 0,
                    onClick = { onDhikrTap(dhikr) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        DhikrCell(
            dhikr = DhikrType.LaHawla,
            count = dayState.dhikrCounts[DhikrType.LaHawla] ?: 0,
            onClick = { onDhikrTap(DhikrType.LaHawla) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DhikrCell(
    dhikr: DhikrType,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TenDaysPalette.SurfaceDark.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dhikr.label,
            fontSize = 12.sp,
            color = TenDaysPalette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$count",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) TenDaysPalette.Green else TenDaysPalette.GrayBorder,
        )
    }
}

@Composable
private fun FastingRow(isFasting: Boolean, canToggle: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "الصيام",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TenDaysPalette.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "+١٠٠ نقطة", fontSize = 11.sp, color = TenDaysPalette.Gold)
            }
            Switch(
                checked = isFasting,
                onCheckedChange = { if (!isFasting && canToggle) onToggle() },
                enabled = canToggle || isFasting,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = TenDaysPalette.Green,
                    uncheckedTrackColor = TenDaysPalette.GrayBorder,
                ),
            )
        }
        if (!canToggle && !isFasting) {
            Text(
                text = "متاح بعد الساعة ٨ مساءً",
                fontSize = 11.sp,
                color = TenDaysPalette.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val INTERVAL_OPTIONS = listOf(1, 5, 10, 30, 60)
private val REPEAT_OPTIONS = listOf(1, 2, 3)

private fun intervalLabel(minutes: Int): String = when (minutes) {
    1 -> "١ دقيقة"
    5 -> "٥ دقائق"
    10 -> "١٠ دقائق"
    30 -> "٣٠ دقيقة"
    60 -> "٦٠ دقيقة"
    else -> "$minutes د"
}

private fun repeatLabel(count: Int): String = when (count) {
    1 -> "مرة"
    2 -> "مرتين"
    3 -> "٣ مرات"
    else -> "$count مرات"
}

@Composable
private fun TakbeerRow(
    count: Int,
    onTap: () -> Unit,
    autoPlay: Boolean,
    onAutoPlayToggle: () -> Unit,
    intervalMinutes: Int,
    onIntervalChanged: (Int) -> Unit,
    repeatCount: Int,
    onRepeatChanged: (Int) -> Unit,
) {
    var cooldown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var intervalExpanded by remember { mutableStateOf(false) }
    var repeatExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "التكبير",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TenDaysPalette.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "+١٠ نقاط", fontSize = 11.sp, color = TenDaysPalette.Gold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$count",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TenDaysPalette.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (!cooldown) {
                            onTap()
                            cooldown = true
                            scope.launch {
                                delay(5000)
                                cooldown = false
                            }
                        }
                    },
                    enabled = !cooldown,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TenDaysPalette.Gold,
                        disabledContainerColor = TenDaysPalette.Gold.copy(alpha = 0.35f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("كبّر", color = if (cooldown) Color.Black.copy(alpha = 0.4f) else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "الله أكبر، الله أكبر، الله أكبر، لا إله إلا الله، والله أكبر، الله أكبر، الله أكبر، ولله الحمد",
            fontSize = 12.sp,
            color = TenDaysPalette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "تشغيل تلقائي",
                fontSize = 12.sp,
                color = TenDaysPalette.TextSecondary,
            )
            Switch(
                checked = autoPlay,
                onCheckedChange = { onAutoPlayToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = TenDaysPalette.Gold,
                    uncheckedTrackColor = TenDaysPalette.GrayBorder,
                ),
            )
        }

        if (autoPlay) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "كل",
                    fontSize = 12.sp,
                    color = TenDaysPalette.TextSecondary,
                )
                Box {
                    Button(
                        onClick = { intervalExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TenDaysPalette.SurfaceDark,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = intervalLabel(intervalMinutes),
                            fontSize = 13.sp,
                            color = TenDaysPalette.Gold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    DropdownMenu(
                        expanded = intervalExpanded,
                        onDismissRequest = { intervalExpanded = false },
                    ) {
                        INTERVAL_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(intervalLabel(option)) },
                                onClick = {
                                    onIntervalChanged(option)
                                    intervalExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "عدد مرات التشغيل",
                    fontSize = 12.sp,
                    color = TenDaysPalette.TextSecondary,
                )
                Box {
                    Button(
                        onClick = { repeatExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TenDaysPalette.SurfaceDark,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = repeatLabel(repeatCount),
                            fontSize = 13.sp,
                            color = TenDaysPalette.Gold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    DropdownMenu(
                        expanded = repeatExpanded,
                        onDismissRequest = { repeatExpanded = false },
                    ) {
                        REPEAT_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(repeatLabel(option)) },
                                onClick = {
                                    onRepeatChanged(option)
                                    repeatExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SadaqahRow(isSadaqah: Boolean, onToggle: () -> Unit) {
    ActionRow(title = "الصدقة", pointsLabel = "+١٥٠ نقطة") {
        Switch(
            checked = isSadaqah,
            onCheckedChange = { if (!isSadaqah) onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = TenDaysPalette.Green,
                uncheckedTrackColor = TenDaysPalette.GrayBorder,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    pointsLabel: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TenDaysPalette.TextPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(text = pointsLabel, fontSize = 11.sp, color = TenDaysPalette.Gold)
        }
        trailing()
    }
}

@Composable
private fun MiniLeaderboard(state: TenDaysUiState) {
    val selfInTop = state.leaderboard.any { it.uid == state.currentUid }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "المتصدرين",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TenDaysPalette.Gold,
            )
            if (state.selfRank > 0) {
                Text(
                    text = "ترتيبك: #${state.selfRank}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TenDaysPalette.Gold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(TenDaysPalette.Gold.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (state.leaderboard.isEmpty()) {
            Text(
                text = "لا يوجد بيانات بعد",
                fontSize = 12.sp,
                color = TenDaysPalette.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            if (!selfInTop && state.selfRank > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TenDaysPalette.Gold.copy(alpha = 0.10f))
                        .border(
                            width = 1.dp,
                            color = TenDaysPalette.Gold.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "#${state.selfRank}  أنت",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TenDaysPalette.Gold,
                    )
                    Text(
                        text = "${state.totalScore}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TenDaysPalette.Gold,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            state.leaderboard.forEachIndexed { index, entry ->
                val rank = index + 1
                val isMe = entry.uid == state.currentUid
                LeaderboardEntryRow(
                    rank = rank,
                    displayTag = entry.uid.take(8),
                    score = entry.totalScore,
                    isCurrentUser = isMe,
                )
            }
        }
    }
}

@Composable
private fun LeaderboardEntryRow(
    rank: Int,
    displayTag: String,
    score: Int,
    isCurrentUser: Boolean,
) {
    val rankColor = when (rank) {
        1 -> TenDaysPalette.Gold
        2 -> TenDaysPalette.RankSilver
        3 -> TenDaysPalette.RankBronze
        else -> TenDaysPalette.TextSecondary
    }
    val medal = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> null
    }
    val bgColor = if (isCurrentUser) TenDaysPalette.Gold.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (medal != null) {
                Text(
                    text = medal,
                    fontSize = 16.sp,
                )
            } else {
                Text(
                    text = "#$rank",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = rankColor,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = if (isCurrentUser) "أنت" else displayTag,
                fontSize = 13.sp,
                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentUser) TenDaysPalette.Gold else TenDaysPalette.TextPrimary,
            )
        }
        Text(
            text = "$score",
            fontSize = 13.sp,
            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrentUser) TenDaysPalette.Gold else TenDaysPalette.TextSecondary,
        )
    }
}
