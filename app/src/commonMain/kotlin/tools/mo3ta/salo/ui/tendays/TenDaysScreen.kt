package tools.mo3ta.salo.ui.tendays

import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.data.tendays.DhikrType
import tools.mo3ta.salo.ui.TakbeerOverlayButton
import tools.mo3ta.salo.presentation.TenDaysDayState
import tools.mo3ta.salo.presentation.TenDaysUiState
import tools.mo3ta.salo.presentation.TenDaysViewModel

@Composable
fun TenDaysScreen(
    onBack: () -> Unit = {},
    onOpenTakbeerSession: () -> Unit = {},
    viewModel: TenDaysViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()

    LaunchedEffect(Unit) {
        analyticsManager.logView("TenDaysScreen")
    }

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
                    contentDescription = stringResource(Res.string.mohamed_lovers_back_cd),
                    tint = TenDaysPalette.TextPrimary,
                )
            }

            TenDaysHeader(state)
            Spacer(Modifier.height(12.dp))

            if (state.periodEnded) {
                CongratsBanner(
                    daysCompleted = state.daysCompleted,
                    totalScore = state.totalScore,
                    selfRank = state.selfRank,
                    playerCount = state.playerCount,
                )
                Spacer(Modifier.height(14.dp))

                val currentDayState = state.days.getOrNull(state.currentDay - 1)
                    ?: TenDaysDayState(day = state.currentDay)

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
                TakbeerOverlayButton(
                    autoRemind = state.autoPlayTakbeer,
                    intervalMinutes = state.takbeerIntervalMinutes,
                    repeatCount = state.takbeerRepeatCount,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenTakbeerSession,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TenDaysPalette.Gold),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(Res.string.tendays_group_takbeer_session), color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = stringResource(Res.string.tendays_group_takbeer_hint),
                    fontSize = 11.sp,
                    color = TenDaysPalette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
            } else {
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
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenTakbeerSession,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TenDaysPalette.Gold),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(Res.string.tendays_group_takbeer_session), color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = stringResource(Res.string.tendays_group_takbeer_hint),
                    fontSize = 11.sp,
                    color = TenDaysPalette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                MiniLeaderboard(state)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CongratsBanner(
    daysCompleted: Int,
    totalScore: Int,
    selfRank: Int,
    playerCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        TenDaysPalette.Gold.copy(alpha = 0.2f),
                        TenDaysPalette.Green.copy(alpha = 0.15f),
                    )
                )
            )
            .border(1.dp, TenDaysPalette.Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.tendays_congrats_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TenDaysPalette.Gold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.tendays_congrats_subtitle, daysCompleted),
            fontSize = 14.sp,
            color = TenDaysPalette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(Res.string.tendays_congrats_score),
            fontSize = 13.sp,
            color = TenDaysPalette.TextSecondary,
        )
        Text(
            text = "$totalScore",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TenDaysPalette.Gold,
        )
        if (selfRank > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (playerCount > 0)
                    stringResource(Res.string.tendays_self_rank_full, selfRank, playerCount)
                else
                    stringResource(Res.string.tendays_congrats_rank, selfRank),
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                color = TenDaysPalette.Green,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.tendays_congrats_keep_takbeer),
            fontSize = 13.sp,
            color = TenDaysPalette.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TenDaysHeader(state: TenDaysUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.tendays_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TenDaysPalette.Gold,
        )
        if (!state.periodEnded) {
            Text(
                text = stringResource(Res.string.tendays_day_of_total, state.currentDay, state.totalDays),
                fontSize = 14.sp,
                color = TenDaysPalette.TextSecondary,
            )
        }
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
                text = stringResource(Res.string.tendays_total_score),
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
                text = stringResource(Res.string.tendays_baqiyat_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TenDaysPalette.TextPrimary,
            )
            Text(
                text = stringResource(Res.string.tendays_baqiyat_points),
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
private fun FastingRow(isFasting: Boolean, onToggle: () -> Unit) {
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
                    text = stringResource(Res.string.tendays_fasting_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TenDaysPalette.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(Res.string.tendays_fasting_points), fontSize = 11.sp, color = TenDaysPalette.Gold)
            }
            Checkbox(
                checked = isFasting,
                onCheckedChange = { if (!isFasting) onToggle() },
                enabled = !isFasting,
                colors = CheckboxDefaults.colors(
                    checkedColor = TenDaysPalette.Green,
                    uncheckedColor = TenDaysPalette.GrayBorder,
                    checkmarkColor = Color.White,
                ),
            )
        }
    }
}

private val INTERVAL_OPTIONS = listOf(1, 5, 10, 30, 60)
private val REPEAT_OPTIONS = listOf(1, 2, 3)

@Composable
private fun intervalLabel(minutes: Int): String = when (minutes) {
    1 -> stringResource(Res.string.tendays_interval_1)
    5 -> stringResource(Res.string.tendays_interval_5)
    10 -> stringResource(Res.string.tendays_interval_10)
    30 -> stringResource(Res.string.tendays_interval_30)
    60 -> stringResource(Res.string.tendays_interval_60)
    else -> stringResource(Res.string.tendays_interval_other, minutes)
}

@Composable
private fun repeatLabel(count: Int): String = when (count) {
    1 -> stringResource(Res.string.tendays_repeat_once)
    2 -> stringResource(Res.string.tendays_repeat_twice)
    3 -> stringResource(Res.string.tendays_repeat_three)
    else -> stringResource(Res.string.tendays_repeat_other, count)
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
                    text = stringResource(Res.string.tendays_takbeer_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TenDaysPalette.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(Res.string.tendays_takbeer_points), fontSize = 11.sp, color = TenDaysPalette.Gold)
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
                    Text(stringResource(Res.string.tendays_takbeer_button), color = if (cooldown) Color.Black.copy(alpha = 0.4f) else Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                text = stringResource(Res.string.tendays_auto_play),
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
                    text = stringResource(Res.string.tendays_every),
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
                    text = stringResource(Res.string.tendays_repeat_count_label),
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
    ActionRow(title = stringResource(Res.string.tendays_sadaqah_title), pointsLabel = stringResource(Res.string.tendays_sadaqah_points)) {
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.tendays_leaderboard_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                color = TenDaysPalette.TextPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.mohamed_lovers_leaderboard_daily),
                fontSize = 10.sp,
                color = TenDaysPalette.GoldDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(TenDaysPalette.Gold.copy(alpha = 0.12f))
                    .border(1.dp, TenDaysPalette.Gold.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.tendays_refresh_hourly),
                fontSize = 11.sp,
                color = TenDaysPalette.TextSecondary,
            )
        }

        if (state.playerCount > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.tendays_player_count, state.playerCount),
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
                color = TenDaysPalette.GoldDim,
            )
        }

        Spacer(Modifier.height(10.dp))

        if (state.leaderboard.isEmpty()) {
            Text(
                text = stringResource(Res.string.tendays_no_data_yet),
                fontSize = 12.sp,
                color = TenDaysPalette.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            if (!selfInTop && state.totalScore > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TenDaysPalette.Gold.copy(alpha = 0.15f))
                        .border(
                            width = 1.dp,
                            color = TenDaysPalette.Gold.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            state.selfRank > 0 && state.playerCount > 0 ->
                                stringResource(Res.string.tendays_self_rank_full, state.selfRank, state.playerCount)
                            state.selfRank > 0 ->
                                stringResource(Res.string.tendays_self_rank_only, state.selfRank)
                            else ->
                                stringResource(Res.string.tendays_you_label)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W700,
                        color = TenDaysPalette.TextPrimary,
                    )
                    Text(
                        text = "${state.totalScore}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W700,
                        color = TenDaysPalette.Gold,
                    )
                }
                Text(
                    text = stringResource(Res.string.tendays_leaderboard_encourage),
                    fontSize = 11.sp,
                    color = TenDaysPalette.TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
            }

            state.leaderboard.forEachIndexed { index, entry ->
                val rank = index + 1
                val isMe = entry.uid == state.currentUid
                val tag = buildTenDaysDisplayTag(entry.uid, entry.countryCode)
                LeaderboardEntryRow(
                    rank = rank,
                    displayTag = tag,
                    score = entry.totalScore,
                    isCurrentUser = isMe,
                )
            }
        }
    }
}

private fun buildTenDaysDisplayTag(uid: String, countryCode: String): String {
    val tag = uid.takeLast(6).uppercase().ifBlank { "------" }
    val country = countryCode.uppercase().ifBlank { "--" }
    return "$country • $tag"
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
        else -> TenDaysPalette.Gold.copy(alpha = 0.45f)
    }
    val bgColor = when {
        isCurrentUser -> TenDaysPalette.Gold.copy(alpha = 0.1f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "──",
                fontSize = 9.sp,
                color = TenDaysPalette.Gold.copy(alpha = 0.35f),
            )
            if (rank > 0) {
                Text(
                    text = "#$rank",
                    fontSize = if (rank <= 3) 15.sp else 13.sp,
                    fontWeight = FontWeight.W700,
                    color = rankColor,
                )
            }
            Text(
                text = displayTag,
                fontSize = 13.sp,
                fontWeight = if (isCurrentUser) FontWeight.W700 else FontWeight.W400,
                color = if (isCurrentUser) TenDaysPalette.TextPrimary else TenDaysPalette.TextPrimary.copy(alpha = 0.75f),
            )
        }
        val isFriday = Clock.System.now()
            .toLocalDateTime(TimeZone.of("Africa/Cairo"))
            .dayOfWeek == DayOfWeek.FRIDAY
        if (isCurrentUser || !isFriday) {
            Text(
                text = "$score",
                fontSize = 13.sp,
                fontWeight = if (isCurrentUser) FontWeight.W700 else FontWeight.W400,
                color = if (isCurrentUser) TenDaysPalette.Gold else TenDaysPalette.TextSecondary,
            )
        } else {
            TenDaysMaskedScore(score)
        }
    }
}

@Composable
private fun TenDaysMaskedScore(score: Int) {
    val str = score.toString()
    val keep = if (str.length >= 4) 2 else 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = str.take(keep),
            fontSize = 13.sp,
            color = TenDaysPalette.TextSecondary,
        )
        Text(
            text = str.drop(keep),
            modifier = Modifier.blur(6.dp),
            fontSize = 13.sp,
            color = TenDaysPalette.TextSecondary.copy(alpha = 0.5f),
        )
    }
}
