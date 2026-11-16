package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.domain.AhlAdhikrChallenge
import tools.mo3ta.salo.domain.AhlAdhikrEntry
import tools.mo3ta.salo.domain.AhlAdhikrMilestone
import tools.mo3ta.salo.domain.AhlAdhikrSnapshot
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.presentation.AhlAdhikrViewModel

// Same visual language as the other dark screens (HadithList / Achievements).
private val AANavy = Color(0xFF09142B)
private val AAMidNavy = Color(0xFF0F2040)
private val AACardNavy = Color(0xFF132545)
private val AAGold = Color(0xFFD4AF37)
private val AACream = Color(0xFFFFF8E7)
private val AAMuted = Color(0xFF9FAFC9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AhlAdhikrScreen(
    onBack: () -> Unit,
    viewModel: AhlAdhikrViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = AANavy,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.ahl_adhikr_title),
                        color = AACream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = AACream)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = AAGold, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.experiences_retry), tint = AACream)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AAMidNavy),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            ) {
                if (state.showingStale) {
                    StaleBanner(state.snapshotDate)
                }

                Text(
                    stringResource(Res.string.ahl_adhikr_filter_hint),
                    color = AAMuted,
                    fontSize = 12.sp,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )

                MilestoneSelector(
                    selected = state.selectedMilestone,
                    onSelect = viewModel::selectMilestone,
                )

                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AAGold)
                    }
                    state.snapshot == null -> EmptyState()
                    else -> {
                        val snapshot = state.snapshot!!
                        val visible = AhlAdhikrChallenge.entries.sumOf { snapshot.listFor(it, state.selectedMilestone).size }
                        if (visible == 0) {
                            EmptyState()
                        } else {
                            Text(
                                stringResource(Res.string.ahl_adhikr_summary, visible, state.snapshotDate),
                                color = AAMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            )
                            ChallengeSections(
                                snapshot = snapshot,
                                milestone = state.selectedMilestone,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaleBanner(snapshotDate: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF3A2E10))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            if (snapshotDate.isBlank()) stringResource(Res.string.ahl_adhikr_stale_undated)
            else stringResource(Res.string.ahl_adhikr_stale, snapshotDate),
            color = AAGold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MilestoneSelector(
    selected: AhlAdhikrMilestone,
    onSelect: (AhlAdhikrMilestone) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
    ) {
        AhlAdhikrMilestone.entries.forEach { milestone ->
            ChallengeChip(
                label = stringResource(Res.string.ahl_adhikr_milestone_days, milestone.minDays),
                selected = milestone == selected,
                onClick = { onSelect(milestone) },
            )
        }
    }
}

@Composable
private fun ChallengeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AAGold else AACardNavy)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) AANavy else AACream,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        )
    }
}

/** One section per challenge — every challenge is always shown, filtered to `milestone`. */
@Composable
private fun ChallengeSections(
    snapshot: AhlAdhikrSnapshot,
    milestone: AhlAdhikrMilestone,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
    ) {
        items(AhlAdhikrChallenge.entries) { challenge ->
            val entries = snapshot.listFor(challenge, milestone)
            if (entries.isEmpty()) {
                EmptySectionRow(challenge)
            } else {
                ChallengeSection(challenge = challenge, entries = entries)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Challenges with nobody in this tier collapse into one quiet row. */
@Composable
private fun EmptySectionRow(challenge: AhlAdhikrChallenge) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AANavy)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            stringResource(challengeLabel(challenge)),
            color = AAMuted,
            fontSize = 14.sp,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChallengeSection(
    challenge: AhlAdhikrChallenge,
    entries: List<AhlAdhikrEntry>,
) {
    Column(Modifier.fillMaxWidth()) {
        // Header: just the challenge name — the per-tier participant count is
        // noise here, every row already carries its own rank.
        Text(
            stringResource(challengeLabel(challenge)),
            color = AACream,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AAMidNavy)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )

        Spacer(Modifier.height(4.dp))
        entries
            .sortedByDescending { it.streak }
            .forEachIndexed { index, entry ->
                EntryRow(rank = index + 1, entry = entry)
                Spacer(Modifier.height(6.dp))
            }
    }
}

@Composable
private fun EntryRow(rank: Int, entry: AhlAdhikrEntry) {
    // One quiet line under the name: the public id and/or country code, only
    // when they add something the name does not already say.
    val meta = ahlAdhikrMetaLine(entry.name, entry.id, entry.countryCode)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AACardNavy)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AAMidNavy)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // Podium colors for the top three; muted beyond that.
            Text(
                "$rank",
                color = when (rank) {
                    1 -> AAGold
                    2 -> Color(0xFFC8D1E0)
                    3 -> Color(0xFFCE8A54)
                    else -> AAMuted
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Country flag at the head of the row, the way every leaderboard row
        // leads with it, so the row reads rank → country → name → streak.
        Spacer(Modifier.width(8.dp))
        FlagAvatar(entry.countryCode)
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                color = AACream,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            )
            if (meta != null) {
                Text(meta, color = AAMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
        Text(
            stringResource(Res.string.ahl_adhikr_entry_streak, entry.streak),
            color = AAGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Small round flag chip; a missing/short code shows the globe instead. */
@Composable
private fun FlagAvatar(countryCode: String) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(AAMidNavy),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = countryCodeToFlag(countryCode),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The secondary line of a row: `id · CODE`, or just whichever of the two is
 * meaningful. Empty (null) when the name already is the public id and there is
 * no country — never a duplicated code under the name.
 */
internal fun ahlAdhikrMetaLine(name: String, id: String, countryCode: String): String? {
    val showsId = id.isNotBlank() && !name.equals(id, ignoreCase = true)
    val code = countryCode.trim()
    return listOf(if (showsId) id else null, code.ifBlank { null })
        .filterNotNull()
        .joinToString(" · ")
        .ifBlank { null }
}

internal fun countryCodeToFlag(code: String): String {
    if (code.length < 2) return "🌍"
    val base = 0x1F1E6 - 'A'.code
    return buildString {
        for (char in code.uppercase().take(2)) {
            val cp = base + char.code
            val high = ((cp - 0x10000) ushr 10) + 0xD800
            val low = ((cp - 0x10000) and 0x3FF) + 0xDC00
            append(high.toChar())
            append(low.toChar())
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.ahl_adhikr_empty),
            color = AAMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

// Same names as the challenge cards on the Challenges screen (challenge_*_title),
// so أهل الذكر and the challenges screen can't drift apart. Salawat has no card
// there — it keeps its own name.
private fun challengeLabel(challenge: AhlAdhikrChallenge) = when (challenge) {
    AhlAdhikrChallenge.SALAWAT -> Res.string.ahl_adhikr_challenge_salawat
    AhlAdhikrChallenge.DHIKR -> Res.string.challenge_dhikr_title
    AhlAdhikrChallenge.BAQIYAT -> Res.string.challenge_baqiyat_title
    AhlAdhikrChallenge.ISTIGHFAR -> Res.string.challenge_istighfar_title
    AhlAdhikrChallenge.ZABAD -> Res.string.challenge_zabad_title
    AhlAdhikrChallenge.QURAN -> Res.string.challenge_quran_title
    AhlAdhikrChallenge.GHARS -> Res.string.challenge_ghars_title
    AhlAdhikrChallenge.ALF_HASANA -> Res.string.challenge_alf_hasana_title
    AhlAdhikrChallenge.KALIMAT -> Res.string.challenge_kalimat_title
    AhlAdhikrChallenge.HAWQALA -> Res.string.challenge_hawqala_title
}
