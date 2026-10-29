package tools.mo3ta.salo.mushaf

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.play.core.assetpacks.model.AssetPackErrorCode

/** Download screen for the on-demand page images (Arabic, RTL). */
@Composable
internal fun MushafPackScreen(
    ui: PackUi,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE6EFDC))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.mushaf_pack_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mushaf_pack_note),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            when (ui) {
                PackUi.Checking, PackUi.Ready, PackUi.NotInstalled -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.mushaf_pack_checking), textAlign = TextAlign.Center)
                }
                is PackUi.Downloading -> {
                    val percent = ui.percent
                    if (percent == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.mushaf_pack_pending), textAlign = TextAlign.Center)
                    } else {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(
                                if (ui.transferring) R.string.mushaf_pack_transferring else R.string.mushaf_pack_downloading,
                                percent,
                            ),
                            textAlign = TextAlign.Center,
                        )
                        if (!ui.transferring && ui.total > 0) {
                            Text(
                                stringResource(
                                    R.string.mushaf_pack_progress_bytes,
                                    Formatter.formatShortFileSize(context, ui.downloaded),
                                    Formatter.formatShortFileSize(context, ui.total),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (!ui.transferring) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.mushaf_pack_cancel)) }
                    }
                }
                is PackUi.NeedsConfirmation -> {
                    Text(
                        stringResource(if (ui.wifi) R.string.mushaf_pack_wifi else R.string.mushaf_pack_confirm),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onConfirm) {
                        Text(
                            stringResource(
                                if (ui.wifi) R.string.mushaf_pack_wifi_button else R.string.mushaf_pack_confirm_button,
                            ),
                        )
                    }
                }
                is PackUi.Failed -> {
                    val message = when {
                        ui.canceled -> stringResource(R.string.mushaf_pack_canceled)
                        ui.errorCode == AssetPackErrorCode.NETWORK_ERROR ->
                            stringResource(R.string.mushaf_pack_failed_network)
                        ui.errorCode == AssetPackErrorCode.INSUFFICIENT_STORAGE ->
                            stringResource(R.string.mushaf_pack_failed_storage)
                        ui.errorCode == AssetPackErrorCode.API_NOT_AVAILABLE ||
                            ui.errorCode == AssetPackErrorCode.APP_NOT_OWNED ||
                            ui.errorCode == AssetPackErrorCode.UNRECOGNIZED_INSTALLATION ||
                            ui.errorCode == AssetPackErrorCode.APP_UNAVAILABLE ->
                            stringResource(R.string.mushaf_pack_failed_play)
                        else -> stringResource(R.string.mushaf_pack_failed, ui.errorCode)
                    }
                    Text(message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.mushaf_pack_retry)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text(stringResource(R.string.mushaf_pack_back)) }
        }
    }
}
