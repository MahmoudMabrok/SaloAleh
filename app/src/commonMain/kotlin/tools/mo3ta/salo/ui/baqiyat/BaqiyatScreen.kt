package tools.mo3ta.salo.ui.baqiyat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.data.baqiyat.BaqiyatPhrase
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.baqiyat_ayah
import tools.mo3ta.salo.generated.resources.baqiyat_ayah_ref
import tools.mo3ta.salo.generated.resources.baqiyat_cycles_label
import tools.mo3ta.salo.generated.resources.baqiyat_manual_entry_button
import tools.mo3ta.salo.generated.resources.baqiyat_shuffle
import tools.mo3ta.salo.generated.resources.baqiyat_tap_hint
import tools.mo3ta.salo.generated.resources.challenge_baqiyat_title
import tools.mo3ta.salo.generated.resources.dhikr_back_cd
import tools.mo3ta.salo.generated.resources.dhikr_rank_number
import tools.mo3ta.salo.generated.resources.dhikr_rank_subtitle
import tools.mo3ta.salo.generated.resources.dhikr_rank_unranked
import tools.mo3ta.salo.generated.resources.dhikr_times
import tools.mo3ta.salo.presentation.BaqiyatViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.dhikr.DhikrMilestoneCelebration


private val BaqiyatGold = Color(0xFFF5D97A)
private val BaqiyatGreen = Color(0xFF57C77A)

@Composable
fun BaqiyatScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    manualEntryEnabled: Boolean = true,
    viewModel: BaqiyatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
        analyticsManager.logAction(AppAnalytics.BAQIYAT_SCREEN_VIEW)
    }

    LaunchedEffect(openLeaderboard) {
        if (openLeaderboard) {
            viewModel.onLeaderboardOpened()
            onLeaderboardAutoOpened()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onScreenLeft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenLeft()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MohamedLoversPalette.DeepBlue),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(
                rank = state.rank,
                participantCount = state.participantCount,
                onBack = onBack,
                onRankClick = { viewModel.onLeaderboardOpened() },
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.challenge_baqiyat_title),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.baqiyat_ayah),
                color = BaqiyatGold.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            Text(
                text = stringResource(Res.string.baqiyat_ayah_ref),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.baqiyat_tap_hint),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.68f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            CyclesCounterCard(cyclesCompleted = state.cyclesCompleted)

            Spacer(Modifier.height(24.dp))

            PhraseGrid(
                phraseOrder = state.phraseOrder,
                tappedPhrases = state.tappedPhrases,
                enabled = !state.isLoading,
                onTap = { phrase ->
                    viewModel.onPhraseTap(phrase)
                    analyticsManager.logAction(AppAnalytics.BAQIYAT_PHRASE_TAP)
                },
            )

            Spacer(Modifier.height(16.dp))

            ShuffleButton(
                enabled = !state.isLoading,
                onClick = { viewModel.onShuffle() },
            )

            Spacer(Modifier.height(12.dp))

            if (manualEntryEnabled) {
                BaqiyatManualEntryButton(
                    onClick = {
                        analyticsManager.logAction(AppAnalytics.OPEN_MANUAL_BAQIYAT)
                        viewModel.showManualBaqiyatSheet()
                    },
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (state.showLeaderboard) {
        BaqiyatLeaderboardSheet(
            entries = state.leaderboard,
            currentUid = state.currentUid,
            participantCount = state.participantCount,
            isLoading = state.isLeaderboardLoading,
            onDismiss = { viewModel.onLeaderboardClosed() },
        )
    }

    ManualBaqiyatSheet(
        isOpen = state.showManualBaqiyatSheet,
        remaining = state.manualRemainingToday,
        onDismiss = { viewModel.dismissManualBaqiyatSheet() },
        onSubmit = { count -> viewModel.submitManualBaqiyat(count) },
        onSubtract = { count -> viewModel.subtractManualBaqiyat(count) },
    )
}

@Composable
private fun BaqiyatManualEntryButton(
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
            text = stringResource(Res.string.baqiyat_manual_entry_button),
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
private fun TopBar(
    rank: Int,
    participantCount: Int,
    onBack: () -> Unit,
    onRankClick: () -> Unit,
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
                contentDescription = stringResource(Res.string.dhikr_back_cd),
                tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
            )
        }
        Surface(
            onClick = onRankClick,
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        ) {
            val chipText = if (rank > 0 && participantCount > 0) {
                "${stringResource(Res.string.dhikr_rank_number, rank)} · ${stringResource(Res.string.dhikr_rank_subtitle, participantCount)}"
            } else {
                stringResource(Res.string.dhikr_rank_unranked)
            }
            Text(
                text = chipText,
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CyclesCounterCard(cyclesCompleted: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, BaqiyatGold.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = cyclesCompleted.toString(),
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 46.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.dhikr_times),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.baqiyat_cycles_label),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PhraseGrid(
    phraseOrder: List<BaqiyatPhrase>,
    tappedPhrases: Set<BaqiyatPhrase>,
    enabled: Boolean,
    onTap: (BaqiyatPhrase) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            phraseOrder.take(2).forEach { phrase ->
                PhraseSlot(
                    phrase = phrase,
                    tapped = phrase in tappedPhrases,
                    enabled = enabled,
                    onTap = onTap,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            phraseOrder.drop(2).take(2).forEach { phrase ->
                PhraseSlot(
                    phrase = phrase,
                    tapped = phrase in tappedPhrases,
                    enabled = enabled,
                    onTap = onTap,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        PhraseSlot(
            phrase = BaqiyatPhrase.LaHawla,
            tapped = BaqiyatPhrase.LaHawla in tappedPhrases,
            enabled = enabled,
            onTap = onTap,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PhraseSlot(
    phrase: BaqiyatPhrase,
    tapped: Boolean,
    enabled: Boolean,
    onTap: (BaqiyatPhrase) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (tapped) BaqiyatGreen.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f),
    )
    val borderColor by animateColorAsState(
        targetValue = if (tapped) BaqiyatGreen else BaqiyatGold.copy(alpha = 0.35f),
    )
    val textColor by animateColorAsState(
        targetValue = if (tapped) BaqiyatGreen else MohamedLoversPalette.GoldGlow,
    )
    Box(modifier = modifier.height(96.dp)) {
        Surface(
            onClick = { onTap(phrase) },
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(18.dp),
            color = backgroundColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(phrase.labelRes),
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ShuffleButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, BaqiyatGold.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = null,
                tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.8f),
                modifier = Modifier.height(18.dp).width(18.dp),
            )
            Text(
                text = stringResource(Res.string.baqiyat_shuffle),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
