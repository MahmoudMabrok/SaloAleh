package tools.mo3ta.salo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.mohamed_lovers_blocked_firebase_off
import tools.mo3ta.salo.generated.resources.mohamed_lovers_blocked_waiting_network
import tools.mo3ta.salo.generated.resources.mohamed_lovers_code_copied
import tools.mo3ta.salo.generated.resources.mohamed_lovers_connection_error
import tools.mo3ta.salo.generated.resources.mohamed_lovers_info_cd
import tools.mo3ta.salo.generated.resources.mohamed_lovers_prayer_text
import tools.mo3ta.salo.generated.resources.mohamed_lovers_reward_text
import tools.mo3ta.salo.presentation.MohamedLoversError
import tools.mo3ta.salo.presentation.MohamedLoversStatus
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversArchShrine
import tools.mo3ta.salo.ui.components.MohamedLoversCounter
import tools.mo3ta.salo.ui.components.MohamedLoversHadithBanner
import tools.mo3ta.salo.ui.components.MohamedLoversInfoSheet
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.components.MohamedLoversPrayerOverlay
import tools.mo3ta.salo.ui.components.MohamedLoversSkyBackground

@Composable
fun MohamedLoversScreen(
    onOpenAchievements: () -> Unit = {},
    viewModel: MohamedLoversViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyticsManager: AnalyticsManager = koinInject()

    val codeCopiedLabel = stringResource(Res.string.mohamed_lovers_code_copied)
    val connectionErrorLabel = stringResource(Res.string.mohamed_lovers_connection_error)
    val prayerText = stringResource(Res.string.mohamed_lovers_prayer_text)
    val rewardText = stringResource(Res.string.mohamed_lovers_reward_text)
    val waitingNetworkLabel = stringResource(Res.string.mohamed_lovers_blocked_waiting_network)
    val firebaseOffLabel = stringResource(Res.string.mohamed_lovers_blocked_firebase_off)
    val infoCd = stringResource(Res.string.mohamed_lovers_info_cd)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushPendingSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPendingSession()
        }
    }

    LaunchedEffect(state.error) {
        val message = when (val err = state.error) {
            MohamedLoversError.Connection -> connectionErrorLabel
            is MohamedLoversError.Raw -> err.message
            null -> null
        }
        if (!message.isNullOrBlank()) {
            showPlatformToast(message)
            viewModel.clearError()
        }
    }

    var archCenter by remember { mutableStateOf<Offset?>(null) }
    var isLit by remember { mutableStateOf(false) }
    var infoSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isLit) {
        if (isLit) { delay(1600); isLit = false }
    }

    val blockedMessage = when (state.status) {
        MohamedLoversStatus.WaitingNetwork -> waitingNetworkLabel
        MohamedLoversStatus.FirebaseOff -> firebaseOffLabel
        MohamedLoversStatus.Open -> ""
    }
    val tapsEnabled = state.status == MohamedLoversStatus.Open && state.canCount && !state.isLoading

    Box(modifier = Modifier.fillMaxSize()) {
        MohamedLoversSkyBackground()
        MohamedLoversPrayerOverlay(
            archCenter = archCenter,
            enabled = tapsEnabled,
            prayerText = prayerText,
            rewardText = rewardText,
            blockedMessage = blockedMessage,
            onBlessing = { isLit = true; viewModel.onCountClick() },
            onTap = {
                analyticsManager.logAction(
                    "mohamed_lovers_sky_tap",
                    mapOf("status" to state.status.name, "enabled" to tapsEnabled.toString()),
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            MohamedLoversHadithBanner(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
            )
            MohamedLoversArchShrine(
                isLit = isLit,
                onArchCenterPositioned = { archCenter = it },
                modifier = Modifier.align(Alignment.Center),
            )
            MohamedLoversCounter(
                total = state.syncedTotal + state.sessionClicks,
                pending = state.sessionClicks,
                isFridayBonus = state.isFridayBonus,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            )
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 36.dp)) {
                IconButton(onClick = { infoSheetOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = infoCd,
                        tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                    )
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MohamedLoversPalette.GoldHighlight,
                )
            }
        }
        MohamedLoversInfoSheet(
            isOpen = infoSheetOpen,
            state = state,
            onDismiss = { infoSheetOpen = false },
            onCopyWinnerCode = { code ->
                copyToClipboard(code)
                showPlatformToast(codeCopiedLabel)
            },
        )
    }
}
