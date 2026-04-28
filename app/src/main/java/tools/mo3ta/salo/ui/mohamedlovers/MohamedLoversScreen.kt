package com.elsharif.dailyseventy.presentation.mohamedlovers

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsharif.dailyseventy.R
import com.elsharif.dailyseventy.di.AnalyticsEntryPoint
import com.elsharif.dailyseventy.presentation.mohamedlovers.components.MohamedLoversArchShrine
import dagger.hilt.android.EntryPointAccessors
import com.elsharif.dailyseventy.presentation.mohamedlovers.components.MohamedLoversCounter
import com.elsharif.dailyseventy.presentation.mohamedlovers.components.MohamedLoversHadithBanner
import com.elsharif.dailyseventy.presentation.mohamedlovers.components.MohamedLoversInfoSheet
import com.elsharif.dailyseventy.presentation.mohamedlovers.components.MohamedLoversPalette
import com.elsharif.dailyseventy.presentation.mohamedlovers.components.MohamedLoversPrayerOverlay
import com.elsharif.dailyseventy.presentation.mohamedlovers.components.MohamedLoversSkyBackground
import kotlinx.coroutines.delay

@Composable
fun MohamedLoversScreen(
    onBackClick: () -> Unit,
    viewModel: MohamedLoversViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val analyticsManager = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, AnalyticsEntryPoint::class.java)
            .analyticsManager()
    }
    val codeCopiedLabel = stringResource(R.string.mohamed_lovers_code_copied)
    val connectionErrorLabel = stringResource(R.string.mohamed_lovers_connection_error)
    val prayerText = stringResource(R.string.mohamed_lovers_prayer_text)
    val rewardText = stringResource(R.string.mohamed_lovers_reward_text)
    val waitingNetworkLabel = stringResource(R.string.mohamed_lovers_blocked_waiting_network)
    val firebaseOffLabel = stringResource(R.string.mohamed_lovers_blocked_firebase_off)
    val backCd = stringResource(R.string.mohamed_lovers_back_cd)
    val infoCd = stringResource(R.string.mohamed_lovers_info_cd)

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
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    var archCenter by remember { mutableStateOf<Offset?>(null) }
    var isLit by remember { mutableStateOf(false) }
    var infoSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isLit) {
        if (isLit) {
            delay(1600)
            isLit = false
        }
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
            onBlessing = {
                isLit = true
                viewModel.onCountClick()
            },
            onTap = {
                analyticsManager.logAction(
                    name = "mohamed_lovers_sky_tap",
                    params = mapOf(
                        "status" to state.status.name,
                        "enabled" to tapsEnabled.toString(),
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            MohamedLoversHadithBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 36.dp),
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backCd,
                        tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 14.dp, top = 36.dp),
            ) {
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
                clipboardManager.setText(AnnotatedString(code))
                Toast.makeText(context, codeCopiedLabel, Toast.LENGTH_SHORT).show()
            },
        )
    }
}
