package tools.mo3ta.salo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import tools.mo3ta.salo.domain.AhlAdhikrMilestone
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

                ChallengeSelector(
                    selected = state.selectedChallenge,
                    onSelect = viewModel::selectChallenge,
                )

                MilestoneSelector(
                    selected = state.selectedMilestone,
                    onSelect = viewModel::selectMilestone,
                )

                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AAGold)
                    }
                    else -> {
                        val entries = state.snapshot?.listFor(state.selectedChallenge, state.selectedMilestone).orEmpty()
                        if (entries.isEmpty()) {
                            EmptyState()
                        } else {
                            EntriesList(entries)
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
private fun ChallengeSelector(
    selected: AhlAdhikrChallenge,
    onSelect: (AhlAdhikrChallenge) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 10.dp),
    ) {
        items(AhlAdhikrChallenge.entries) { challenge ->
            ChallengeChip(
                label = stringResource(challengeLabel(challenge)),
                selected = challenge == selected,
                onClick = { onSelect(challenge) },
            )
        }
    }
}

@Composable
private fun MilestoneSelector(
    selected: AhlAdhikrMilestone,
    onSelect: (AhlAdhikrMilestone) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
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

@Composable
private fun EntriesList(entries: List<tools.mo3ta.salo.domain.AhlAdhikrEntry>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(entries) { index, entry ->
            EntryRow(rank = index + 1, entry = entry)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EntryRow(rank: Int, entry: tools.mo3ta.salo.domain.AhlAdhikrEntry) {
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
            Text("$rank", color = AAGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.padding(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                color = AACream,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            )
            Text(entry.id, color = AAMuted, fontSize = 11.sp)
        }
        Text(
            stringResource(Res.string.ahl_adhikr_entry_streak, entry.streak),
            color = AAGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
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

private fun challengeLabel(challenge: AhlAdhikrChallenge) = when (challenge) {
    AhlAdhikrChallenge.SALAWAT -> Res.string.ahl_adhikr_challenge_salawat
    AhlAdhikrChallenge.DHIKR -> Res.string.ahl_adhikr_challenge_dhikr
    AhlAdhikrChallenge.BAQIYAT -> Res.string.ahl_adhikr_challenge_baqiyat
    AhlAdhikrChallenge.ISTIGHFAR -> Res.string.ahl_adhikr_challenge_istighfar
    AhlAdhikrChallenge.ZABAD -> Res.string.ahl_adhikr_challenge_zabad
    AhlAdhikrChallenge.QURAN -> Res.string.ahl_adhikr_challenge_quran
    AhlAdhikrChallenge.GHARS -> Res.string.ahl_adhikr_challenge_ghars
    AhlAdhikrChallenge.ALF_HASANA -> Res.string.ahl_adhikr_challenge_alf_hasana
    AhlAdhikrChallenge.KALIMAT -> Res.string.ahl_adhikr_challenge_kalimat
    AhlAdhikrChallenge.HAWQALA -> Res.string.ahl_adhikr_challenge_hawqala
}
