package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.domain.AccountRestoreResult
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.presentation.AccountBackupViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.copyToClipboard
import tools.mo3ta.salo.ui.shareText
import tools.mo3ta.salo.ui.showPlatformToast

/**
 * Export the device's backup code and restore an account from one.
 *
 * The code is the raw uid; the app has no accounts, so this screen is the only path back into an
 * identity after a reinstall on a device the OS did not restore (always the case on iOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBackupScreen(onBack: () -> Unit) {
    val viewModel: AccountBackupViewModel = koinViewModel()
    val analyticsManager: AnalyticsManager = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmVisible by remember { mutableStateOf(false) }
    val copiedToast = stringResource(Res.string.backup_copied_toast)

    LaunchedEffect(Unit) { analyticsManager.logView("AccountBackupScreen") }

    Scaffold(
        containerColor = MohamedLoversPalette.DeepBlue,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.backup_title),
                        color = MohamedLoversPalette.GoldGlow,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.mohamed_lovers_back_cd),
                            tint = MohamedLoversPalette.GoldGlow,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16213e)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionHeader(stringResource(Res.string.backup_code_header))

            Text(
                text = stringResource(Res.string.backup_code_desc),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = state.backupCode,
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, MohamedLoversPalette.GoldGlow.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        copyToClipboard(state.backupCode)
                        showPlatformToast(copiedToast)
                        analyticsManager.logAction(AppAnalytics.BACKUP_CODE_COPIED)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MohamedLoversPalette.GoldGlow,
                        contentColor = Color(0xFF0f0f1a),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.backup_copy), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        shareText(state.backupCode)
                        analyticsManager.logAction(AppAnalytics.BACKUP_CODE_SHARED)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.backup_share), color = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.backup_code_warning),
                color = Color(0xFFFFC857),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )

            // The code and the id it derives to look nothing alike, so they are shown together:
            // the id (and its last 6 characters, the leaderboard name) is what the user already
            // recognises, and seeing it here is the only way to confirm the code is really theirs.
            SectionHeader(stringResource(Res.string.backup_public_id_header))

            Text(
                text = stringResource(Res.string.backup_public_id_desc, state.publicId.takeLast(6)),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = state.publicId,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    copyToClipboard(state.publicId)
                    showPlatformToast(copiedToast)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.backup_public_id_copy), color = Color.White)
            }

            SectionHeader(stringResource(Res.string.backup_restore_header))

            Text(
                text = stringResource(Res.string.backup_restore_desc),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.codeInput,
                onValueChange = viewModel::onCodeInputChange,
                label = { Text(stringResource(Res.string.backup_restore_field_label)) },
                singleLine = true,
                enabled = !state.restoring,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MohamedLoversPalette.GoldGlow,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = MohamedLoversPalette.GoldGlow,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = MohamedLoversPalette.GoldGlow,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { confirmVisible = true },
                enabled = !state.restoring && state.codeInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldGlow,
                    contentColor = Color(0xFF0f0f1a),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.restoring) {
                    CircularProgressIndicator(
                        color = Color(0xFF0f0f1a),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(stringResource(Res.string.backup_restore_button), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            containerColor = Color(0xFF16213e),
            title = {
                Text(
                    text = stringResource(Res.string.backup_restore_confirm_title),
                    color = MohamedLoversPalette.GoldGlow,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.backup_restore_confirm_body),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmVisible = false
                    analyticsManager.logAction(AppAnalytics.ACCOUNT_RESTORE_STARTED)
                    viewModel.restore()
                }) {
                    Text(
                        text = stringResource(Res.string.backup_restore_confirm_yes),
                        color = MohamedLoversPalette.GoldGlow,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVisible = false }) {
                    Text(
                        text = stringResource(Res.string.backup_restore_cancel),
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            },
        )
    }

    state.result?.let { result ->
        RestoreResultDialog(result = result, onDismiss = viewModel::dismissResult)
    }
}

@Composable
private fun RestoreResultDialog(result: AccountRestoreResult, onDismiss: () -> Unit) {
    val analyticsManager: AnalyticsManager = koinInject()
    val restored = result as? AccountRestoreResult.Restored

    LaunchedEffect(result) {
        analyticsManager.logAction(
            if (restored != null) AppAnalytics.ACCOUNT_RESTORE_SUCCESS else AppAnalytics.ACCOUNT_RESTORE_FAILED,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213e),
        title = {
            Text(
                text = stringResource(
                    if (restored != null) Res.string.backup_restore_success_title
                    else Res.string.backup_error_title,
                ),
                color = MohamedLoversPalette.GoldGlow,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (restored != null) {
                    Text(
                        text = stringResource(
                            Res.string.backup_restore_success_body,
                            restored.summary.totalCount,
                            restored.summary.achievements,
                            restored.summary.roundStreak,
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                    Text(
                        text = stringResource(Res.string.backup_restore_restart_note),
                        color = Color(0xFFFFC857),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                } else {
                    Text(
                        text = stringResource(errorMessageFor(result)),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.backup_restore_ok),
                    color = MohamedLoversPalette.GoldGlow,
                )
            }
        },
    )
}

private fun errorMessageFor(result: AccountRestoreResult) = when (result) {
    AccountRestoreResult.InvalidCode -> Res.string.backup_error_invalid
    AccountRestoreResult.PublicIdGiven -> Res.string.backup_error_public_id
    AccountRestoreResult.SameAccount -> Res.string.backup_error_same
    AccountRestoreResult.NotFound -> Res.string.backup_error_not_found
    else -> Res.string.backup_error_failed
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}
