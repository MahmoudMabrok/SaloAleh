package tools.mo3ta.salo.ui.baqiyat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.data.baqiyat.BaqiyatPhrase
import tools.mo3ta.salo.domain.BaqiyatLeaderboardEntry
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.baqiyat_add_cd
import tools.mo3ta.salo.generated.resources.baqiyat_ayah
import tools.mo3ta.salo.generated.resources.baqiyat_ayah_ref
import tools.mo3ta.salo.generated.resources.baqiyat_cycles_label
import tools.mo3ta.salo.generated.resources.baqiyat_extra_phrase_label
import tools.mo3ta.salo.generated.resources.baqiyat_hadith
import tools.mo3ta.salo.generated.resources.baqiyat_hadith_label
import tools.mo3ta.salo.generated.resources.baqiyat_hadith_sanad
import tools.mo3ta.salo.generated.resources.baqiyat_manual_entry_button
import tools.mo3ta.salo.generated.resources.baqiyat_phrases_title
import tools.mo3ta.salo.generated.resources.baqiyat_tap_hint
import tools.mo3ta.salo.generated.resources.challenge_baqiyat_title
import tools.mo3ta.salo.generated.resources.dhikr_back_cd
import tools.mo3ta.salo.generated.resources.dhikr_rank_number
import tools.mo3ta.salo.generated.resources.dhikr_rank_subtitle
import tools.mo3ta.salo.generated.resources.dhikr_rank_unranked
import tools.mo3ta.salo.generated.resources.dhikr_times
import tools.mo3ta.salo.presentation.BaqiyatViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette


private val BaqiyatGold = Color(0xFFF5D97A)
private val BaqiyatHoney = Color(0xFFE8A33D)

/** Height of the hive: the hadith playing out, and the only animated thing on the screen. */
private val HiveHeight = 288.dp

@Composable
fun BaqiyatScreen(
    onBack: () -> Unit,
    openLeaderboard: Boolean = false,
    onLeaderboardAutoOpened: () -> Unit = {},
    manualEntryEnabled: Boolean = true,
    viewModel: BaqiyatViewModel = koinViewModel(),
) {
    // Deliberately not the full state: `shell` never emits on a tap, so a completed cycle
    // repaints the hive and the counter and nothing else. See BaqiyatViewModel.shell.
    val shell by viewModel.shell.collectAsStateWithLifecycle()
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
            .background(MohamedLoversPalette.DeepBlue)
            // One tap anywhere = one completed cycle. No ripple: a tap only updates the counter
            // and its related parts, never the whole screen.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !shell.isLoading,
                onClickLabel = stringResource(Res.string.baqiyat_add_cd),
                role = Role.Button,
                onClick = {
                    viewModel.onCycleTap()
                    analyticsManager.logAction(AppAnalytics.BAQIYAT_PHRASE_TAP)
                },
            ),
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
                rank = shell.rank,
                participantCount = shell.participantCount,
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
            Spacer(Modifier.height(6.dp))
            // The screen is the button, so the affordance is a line of guidance, not a control.
            Text(
                text = stringResource(Res.string.baqiyat_tap_hint),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.66f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(4.dp))

            BaqiyatHiveCanvas(
                playerName = shell.playerName,
                phrases = hadithPhrases(),
                cycleSignal = viewModel.cycleSerial,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HiveHeight),
            )

            // Sits under the reciter's name pill rather than over it — the foot of the hive is
            // already occupied by the figure the sparks launch from.
            CyclesCounter(cycles = viewModel.cycles)

            Spacer(Modifier.height(16.dp))

            HadithCard()

            Spacer(Modifier.height(14.dp))

            PhrasesOfTheCycle()

            Spacer(Modifier.height(14.dp))

            Ayah()

            Spacer(Modifier.height(18.dp))

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

    if (shell.showLeaderboard) {
        BaqiyatLeaderboardHost(
            entries = viewModel.leaderboardEntries,
            currentUid = shell.currentUid,
            participantCount = shell.participantCount,
            isLoading = shell.isLeaderboardLoading,
            onDismiss = { viewModel.onLeaderboardClosed() },
        )
    }

    ManualBaqiyatSheet(
        isOpen = shell.showManualBaqiyatSheet,
        remaining = shell.manualRemainingToday,
        onDismiss = { viewModel.dismissManualBaqiyatSheet() },
        onSubmit = { count -> viewModel.submitManualBaqiyat(count) },
        onSubtract = { count -> viewModel.subtractManualBaqiyat(count) },
    )
}

/** The four the hadith names, in cycle order — what the sparks carry around the Throne. */
@Composable
private fun hadithPhrases(): List<String> =
    BaqiyatPhrase.entries.filter { it.inHadith }.map { stringResource(it.labelRes) }

/**
 * The running count, in its own leaf so a tap recomposes this and nothing above it.
 */
@Composable
private fun CyclesCounter(
    cycles: StateFlow<Int>,
    modifier: Modifier = Modifier,
) {
    val value by cycles.collectAsStateWithLifecycle()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value.toString(),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.dhikr_times),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = stringResource(Res.string.baqiyat_cycles_label),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
            fontSize = 11.sp,
        )
    }
}

/**
 * Collects the board on its own so the tap-by-tap re-ranking stays inside the sheet instead of
 * reaching the screen behind it.
 */
@Composable
private fun BaqiyatLeaderboardHost(
    entries: StateFlow<List<BaqiyatLeaderboardEntry>>,
    currentUid: String,
    participantCount: Int,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    val list by entries.collectAsStateWithLifecycle()
    BaqiyatLeaderboardSheet(
        entries = list,
        currentUid = currentUid,
        participantCount = participantCount,
        isLoading = isLoading,
        onDismiss = onDismiss,
    )
}

/** The narration the hive draws, with its chain — every challenge screen carries its transcript. */
@Composable
private fun HadithCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, BaqiyatGold.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = stringResource(Res.string.baqiyat_hadith_label),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
            Text(
                text = stringResource(Res.string.baqiyat_hadith),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.92f),
                fontSize = 14.sp,
                lineHeight = 27.sp,
            )
            Text(
                text = stringResource(Res.string.baqiyat_hadith_sanad),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.46f),
                fontSize = 10.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

/**
 * The phrases as plain text rather than cards: the four of the hadith on one bi-coloured line —
 * the divine name in the brighter gold, the act of dhikr a step behind it — and the fifth phrase
 * of the cycle named underneath so nothing silently drops out of what the user recites.
 */
@Composable
private fun PhrasesOfTheCycle() {
    val named = hadithPhrases()
    val extras = BaqiyatPhrase.entries.filterNot { it.inHadith }.map { stringResource(it.labelRes) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.baqiyat_phrases_title),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.42f),
            fontSize = 10.sp,
        )
        Text(
            text = buildAnnotatedString {
                named.forEachIndexed { index, phrase ->
                    if (index > 0) {
                        withStyle(SpanStyle(color = BaqiyatGold.copy(alpha = 0.28f))) { append("  ·  ") }
                    }
                    appendBiColored(phrase)
                }
            },
            fontSize = 15.sp,
            lineHeight = 30.sp,
            textAlign = TextAlign.Center,
        )
        if (extras.isNotEmpty()) {
            Text(
                text = "${stringResource(Res.string.baqiyat_extra_phrase_label)}: ${extras.joinToString("  ·  ")}",
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f),
                fontSize = 12.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Words carrying the divine name are lifted to the brighter gold. Matching is by token so it holds
 * across the locales the app ships: Arabic (الله / لله / بالله), Urdu (اللہ), transliteration
 * (Allah / billah) and Chinese (安拉).
 */
private fun AnnotatedString.Builder.appendBiColored(phrase: String) {
    phrase.split(" ").forEachIndexed { index, word ->
        if (index > 0) append(" ")
        val color = if (word.isDivineName()) BaqiyatGold else BaqiyatHoney.copy(alpha = 0.92f)
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(word) }
    }
}

private fun String.isDivineName(): Boolean {
    val lower = lowercase()
    return endsWith("الله") || endsWith("لله") ||
        endsWith("اللہ") || endsWith("للہ") ||
        lower.endsWith("allah") || contains("安拉")
}

@Composable
private fun Ayah() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.baqiyat_ayah),
            color = BaqiyatGold.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
        Text(
            text = stringResource(Res.string.baqiyat_ayah_ref),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.42f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
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
