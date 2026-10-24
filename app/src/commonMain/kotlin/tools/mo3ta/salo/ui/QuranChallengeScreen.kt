package tools.mo3ta.salo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.ui.components.ChallengeCountSheet
import tools.mo3ta.salo.ui.components.ChallengeSheetAction
import tools.mo3ta.salo.ui.components.creamChallengeSheet
import tools.mo3ta.salo.data.quran.parseMushafPage
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.challenge_sheet_unit
import tools.mo3ta.salo.generated.resources.challenge_sheet_today
import tools.mo3ta.salo.generated.resources.challenge_sheet_lifetime
import tools.mo3ta.salo.generated.resources.challenge_sheet_goal
import tools.mo3ta.salo.generated.resources.quran_back_cd
import tools.mo3ta.salo.generated.resources.quran_daily_goal
import tools.mo3ta.salo.generated.resources.quran_manual_entry_button
import tools.mo3ta.salo.generated.resources.quran_open_mushaf
import tools.mo3ta.salo.generated.resources.quran_download_mushaf
import tools.mo3ta.salo.generated.resources.quran_mushaf_downloading
import tools.mo3ta.salo.generated.resources.quran_mushaf_download_hint
import tools.mo3ta.salo.generated.resources.quran_mushaf_failed
import tools.mo3ta.salo.generated.resources.quran_mushaf_hint
import tools.mo3ta.salo.generated.resources.quran_mushaf_restart_action
import tools.mo3ta.salo.generated.resources.quran_mushaf_restart_body
import tools.mo3ta.salo.generated.resources.quran_mushaf_restart_title
import tools.mo3ta.salo.generated.resources.quran_page_prompt_title
import tools.mo3ta.salo.generated.resources.quran_page_prompt_hint
import tools.mo3ta.salo.generated.resources.quran_page_prompt_open
import tools.mo3ta.salo.generated.resources.quran_page_prompt_resume
import tools.mo3ta.salo.generated.resources.quran_page_prompt_invalid
import tools.mo3ta.salo.generated.resources.quran_page_add
import tools.mo3ta.salo.generated.resources.quran_pages_label
import tools.mo3ta.salo.generated.resources.quran_pages_read
import tools.mo3ta.salo.generated.resources.quran_phrase
import tools.mo3ta.salo.generated.resources.quran_progress_count
import tools.mo3ta.salo.generated.resources.quran_rank_number
import tools.mo3ta.salo.generated.resources.quran_rank_subtitle
import tools.mo3ta.salo.generated.resources.quran_rank_unranked
import tools.mo3ta.salo.generated.resources.quran_today
import tools.mo3ta.salo.generated.resources.quran_tap_hint
import tools.mo3ta.salo.generated.resources.quran_view_rewards
import tools.mo3ta.salo.presentation.QuranChallengeViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.quran.QuranColors
import tools.mo3ta.salo.ui.quran.QuranLeaderboardSheet
import tools.mo3ta.salo.ui.quran.QuranMilestoneCelebration
import tools.mo3ta.salo.ui.quran.QuranRewardsSheet
import tools.mo3ta.salo.ui.quran.QuranSpacing
import tools.mo3ta.salo.ui.quran.ManualQuranSheet
import tools.mo3ta.salo.ui.quran.MushafStatus
import tools.mo3ta.salo.ui.quran.QuranMushafReader
import tools.mo3ta.salo.ui.quran.mushafReaderAvailable
import tools.mo3ta.salo.ui.quran.rememberMushafAccess


private val QuranHeroBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F2318),
        Color(0xFF1A4035),
        Color(0xFF2E4A3F),
        MohamedLoversPalette.DeepBlue,
    ),
)

@Composable
fun QuranChallengeScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    manualEntryEnabled: Boolean = true,
    viewModel: QuranChallengeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.QURAN_SCREEN_VIEW)
    }

    LaunchedEffect(openLeaderboard) {
        if (openLeaderboard) {
            viewModel.onLeaderboardOpened()
            onLeaderboardAutoOpened()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenEntered()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenLeft()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenLeft()
        }
    }

    var showRewardsSheet by remember { mutableStateOf(false) }
    var showMushaf by remember { mutableStateOf(false) }
    var askPage by remember { mutableStateOf(false) }
    var pageDraft by remember { mutableStateOf("") }
    var pageError by remember { mutableStateOf(false) }
    var initialPage by remember { mutableStateOf<Int?>(null) }
    val mushaf = rememberMushafAccess()
    val mushafReady = mushaf.status == MushafStatus.Ready
    val mushafDownloading = mushaf.status == MushafStatus.Downloading
    val offerMushaf = mushafReaderAvailable && mushaf.status != MushafStatus.Unavailable
    val primaryLabel = when {
        !offerMushaf -> stringResource(Res.string.quran_page_add)
        mushafDownloading -> stringResource(Res.string.quran_mushaf_downloading)
        mushaf.status == MushafStatus.NeedsRestart -> stringResource(Res.string.quran_mushaf_restart_action)
        mushafReady -> stringResource(Res.string.quran_open_mushaf)
        else -> stringResource(Res.string.quran_download_mushaf)
    }
    val hint = when {
        !offerMushaf -> stringResource(Res.string.quran_tap_hint)
        mushaf.status == MushafStatus.Failed -> stringResource(Res.string.quran_mushaf_failed)
        mushaf.status == MushafStatus.NeedsRestart -> stringResource(Res.string.quran_mushaf_restart_body)
        mushafReady -> stringResource(Res.string.quran_mushaf_hint)
        mushafDownloading && mushaf.progress != null ->
            stringResource(Res.string.quran_mushaf_downloading) + " " + ((mushaf.progress * 100).toInt()) + "%"
        else -> stringResource(Res.string.quran_mushaf_download_hint)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QuranHeroBackground),
    ) {
        Column(Modifier.fillMaxSize()) {
            QuranImmersiveZone(
                count = state.todayCount,
                target = state.dailyGoal,
                rank = state.rank,
                participantCount = state.participantCount,
                canCount = !state.isLoading && !mushafDownloading,
                primaryLabel = primaryLabel,
                hint = hint,
                compactButton = offerMushaf,
                onPrimaryClick = {
                    when {
                        mushafDownloading -> Unit
                        mushaf.status == MushafStatus.NeedsRestart -> mushaf.restartApp()
                        offerMushaf && !mushafReady -> mushaf.startDownload()
                        offerMushaf -> {
                            pageDraft = ""
                            pageError = false
                            askPage = true
                        }
                        else -> {
                            viewModel.onQuranPageTap()
                            analyticsManager.logAction(
                                AppAnalytics.QURAN_TAP,
                                mapOf(AppAnalytics.PARAM_COUNT to (state.todayCount + 1).toString()),
                            )
                        }
                    }
                },
                onBack = onBack,
                onRankClick = { viewModel.onLeaderboardOpened() },
                onManualEntryClick = {
                    analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_QURAN)
                    viewModel.showManualQuranSheet()
                },
                manualEntryVisible = false,
                onViewRewards = { showRewardsSheet = true },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            ChallengeCountSheet(
                todayCount = state.todayCount,
                dailyGoal = state.dailyGoal,
                lifetimeCount = state.lifetimeCount,
                todayLabel = stringResource(Res.string.challenge_sheet_today),
                goalLabel = stringResource(Res.string.challenge_sheet_goal),
                lifetimeLabel = stringResource(Res.string.challenge_sheet_lifetime),
                unitLabel = stringResource(Res.string.challenge_sheet_unit),
                colors = creamChallengeSheet(
                    accent = QuranColors.Teal,
                    cream = QuranColors.Cream,
                    muted = QuranColors.Muted,
                    stroke = QuranColors.Stroke,
                    track = QuranColors.Track,
                    progressStart = QuranColors.LightTeal,
                    progressEnd = QuranColors.Teal,
                    primaryButton = QuranColors.Ink,
                ),
                challengeId = ChallengeType.QURAN.id,
                primaryAction = ChallengeSheetAction(
                    label = stringResource(Res.string.quran_view_rewards),
                    onClick = { showRewardsSheet = true },
                ),
                secondaryAction = if (manualEntryEnabled) {
                    ChallengeSheetAction(
                        label = stringResource(Res.string.quran_manual_entry_button),
                        onClick = {
                            analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_QURAN)
                            viewModel.showManualQuranSheet()
                        },
                    )
                } else null,
            )
        }

        if (showMushaf) {
            QuranMushafReader(
                initialPage = initialPage,
                alreadyCounted = viewModel.countedPagesToday(),
                onPageQualified = { page ->
                    viewModel.onMushafPageQualified(page)
                    analyticsManager.logAction(
                        AppAnalytics.QURAN_PAGE_COUNTED,
                        mapOf(AppAnalytics.PARAM_COUNT to page.toString()),
                    )
                },
                onClose = { showMushaf = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (askPage) {
            MushafPageDialog(
                draft = pageDraft,
                showError = pageError,
                onDraftChange = {
                    pageDraft = it
                    pageError = false
                },
                onOpen = {
                    val page = parseMushafPage(pageDraft)
                    if (page == null) {
                        pageError = true
                    } else {
                        initialPage = page
                        askPage = false
                        analyticsManager.logAction(AppAnalytics.OPEN_MUSHAF)
                        showMushaf = true
                    }
                },
                onResume = {
                    initialPage = null
                    askPage = false
                    analyticsManager.logAction(AppAnalytics.OPEN_MUSHAF)
                    showMushaf = true
                },
                onDismiss = { askPage = false },
            )
        }

        if (mushaf.status == MushafStatus.NeedsRestart) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(Res.string.quran_mushaf_restart_title)) },
                text = { Text(stringResource(Res.string.quran_mushaf_restart_body)) },
                confirmButton = {
                    TextButton(onClick = mushaf.restartApp) {
                        Text(stringResource(Res.string.quran_mushaf_restart_action))
                    }
                },
            )
        }

        QuranMilestoneCelebration(
            milestone = state.celebrationMilestone,
            visible = state.showCelebration,
            onDismiss = { viewModel.onCelebrationDismissed() },
            modifier = Modifier.fillMaxSize(),
        )

        QuranRewardsSheet(
            visible = showRewardsSheet,
            onDismiss = { showRewardsSheet = false },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (state.showLeaderboard) {
        QuranLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = { viewModel.onLeaderboardClosed() },
        )
    }

    ManualQuranSheet(
        isOpen = state.showManualQuranSheet,
        remaining = state.manualRemainingToday,
        onDismiss = { viewModel.dismissManualQuranSheet() },
        onSubmit = { count -> viewModel.submitManualQuran(count) },
        onSubtract = { count -> viewModel.subtractManualQuran(count) },
    )
}

@Composable
private fun MushafPageDialog(
    draft: String,
    showError: Boolean,
    onDraftChange: (String) -> Unit,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.quran_page_prompt_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = { Text(stringResource(Res.string.quran_page_prompt_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = showError,
                )
                if (showError) {
                    Text(
                        text = stringResource(Res.string.quran_page_prompt_invalid),
                        color = Color(0xFF8C3A3A),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text(stringResource(Res.string.quran_page_prompt_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onResume) {
                Text(stringResource(Res.string.quran_page_prompt_resume))
            }
        },
    )
}

@Composable
private fun QuranManualEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
    ) {
        Text(
            text = stringResource(Res.string.quran_manual_entry_button),
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
        )
    }
}

@Composable
private fun QuranPageButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.then(
            if (compact) Modifier.widthIn(min = 148.dp, max = 220.dp) else Modifier.fillMaxWidth(),
        ),
        enabled = enabled,
        shape = RoundedCornerShape(if (compact) 24.dp else 16.dp),
        color = QuranColors.Teal.copy(alpha = if (enabled) 0.85f else 0.3f),
        border = BorderStroke(1.dp, QuranColors.LightTeal.copy(alpha = if (enabled) 0.6f else 0.2f)),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = if (compact) 15.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .then(if (compact) Modifier else Modifier.fillMaxWidth())
                .padding(horizontal = if (compact) 18.dp else 0.dp, vertical = if (compact) 10.dp else 14.dp),
        )
    }
}

@Composable
private fun QuranImmersiveZone(
    count: Int,
    target: Int,
    rank: Int,
    participantCount: Int,
    canCount: Boolean,
    primaryLabel: String,
    hint: String,
    compactButton: Boolean,
    onPrimaryClick: () -> Unit,
    onBack: () -> Unit,
    onRankClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    manualEntryVisible: Boolean = true,
    onViewRewards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(QuranHeroBackground),
    ) {
        LeafCluster(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(86.dp),
            mirror = false,
        )
        LeafCluster(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(86.dp),
            mirror = true,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = QuranSpacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.quran_back_cd),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
                Surface(
                    onClick = onRankClick,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    val chipText = if (rank > 0 && participantCount > 0) {
                        "${stringResource(Res.string.quran_rank_number, rank)} · ${stringResource(Res.string.quran_rank_subtitle, participantCount)}"
                    } else {
                        stringResource(Res.string.quran_rank_unranked)
                    }
                    Text(
                        text = chipText,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            if (manualEntryVisible) {
                QuranManualEntryButton(onClick = onManualEntryClick)
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.quran_phrase),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 2.dp, bottom = 16.dp),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            QuranPageButton(
                label = primaryLabel,
                onClick = onPrimaryClick,
                enabled = canCount,
                compact = compactButton,
                modifier = Modifier,
            )

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun LeafCluster(
    modifier: Modifier = Modifier,
    mirror: Boolean,
) {
    Canvas(modifier = modifier) {
        val stemX = if (mirror) size.width * 0.75f else size.width * 0.25f
        val endX = if (mirror) size.width * 0.38f else size.width * 0.62f
        drawLine(
            color = QuranColors.StarStem.copy(alpha = 0.35f),
            start = Offset(stemX, size.height * 0.92f),
            end = Offset(endX, size.height * 0.22f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        repeat(5) { i ->
            val y = size.height * (0.78f - i * 0.12f)
            val x = stemX + (endX - stemX) * (i + 1) / 6f
            val direction = if ((i + if (mirror) 1 else 0) % 2 == 0) -1f else 1f
            drawOval(
                color = QuranColors.Star.copy(alpha = 0.22f),
                topLeft = Offset(x + direction * 4.dp.toPx(), y),
                size = Size(28.dp.toPx(), 12.dp.toPx()),
            )
        }
    }
}
