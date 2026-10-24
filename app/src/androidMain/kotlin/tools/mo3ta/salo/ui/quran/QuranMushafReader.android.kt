package tools.mo3ta.salo.ui.quran

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus as SessionStatus

actual val mushafReaderAvailable: Boolean = true

private const val MUSHAF_MODULE = "mushaf"

@Composable
actual fun rememberMushafAccess(): MushafAccess {
    val context = LocalContext.current
    val manager = remember {
        runCatching { SplitInstallManagerFactory.create(context.applicationContext) }.getOrNull()
    }
    if (manager == null) {
        return MushafAccess(MushafStatus.Unavailable, null, startDownload = {})
    }
    val installedAtStart = remember {
        runCatching { manager.installedModules.contains(MUSHAF_MODULE) }.getOrDefault(false)
    }
    var status by remember {
        mutableStateOf(
            runCatching {
                if (manager.installedModules.contains(MUSHAF_MODULE)) MushafStatus.Ready
                else MushafStatus.NeedsDownload
            }.getOrDefault(MushafStatus.Unavailable),
        )
    }
    var progress by remember { mutableStateOf<Float?>(null) }
    val promptedSessions = remember { mutableSetOf<Int>() }
    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* The session listener reports the outcome. */ }

    DisposableEffect(manager) {
        val listener = SplitInstallStateUpdatedListener { state ->
            if (!state.moduleNames().contains(MUSHAF_MODULE)) return@SplitInstallStateUpdatedListener
            when (state.status()) {
                SessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    status = MushafStatus.Downloading
                    if (promptedSessions.add(state.sessionId())) {
                        runCatching { manager.startConfirmationDialogForResult(state, confirmLauncher) }
                    }
                }
                SessionStatus.PENDING,
                SessionStatus.DOWNLOADING,
                SessionStatus.DOWNLOADED,
                SessionStatus.INSTALLING,
                SessionStatus.CANCELING,
                -> {
                    status = MushafStatus.Downloading
                    val total = state.totalBytesToDownload()
                    progress = if (total > 0L) state.bytesDownloaded().toFloat() / total else null
                }
                SessionStatus.INSTALLED -> {
                    // Opening the new split in this same process crashes once.
                    // A fresh process loads it cleanly.
                    status = if (installedAtStart) MushafStatus.Ready else MushafStatus.NeedsRestart
                    progress = null
                }
                SessionStatus.FAILED,
                SessionStatus.CANCELED,
                -> {
                    status = runCatching {
                        when {
                            !manager.installedModules.contains(MUSHAF_MODULE) -> MushafStatus.Failed
                            installedAtStart -> MushafStatus.Ready
                            else -> MushafStatus.NeedsRestart
                        }
                    }.getOrDefault(MushafStatus.Failed)
                    progress = null
                }
            }
        }
        manager.registerListener(listener)
        onDispose { manager.unregisterListener(listener) }
    }

    return MushafAccess(
        status = status,
        progress = progress,
        startDownload = {
            val installed = runCatching { manager.installedModules.contains(MUSHAF_MODULE) }.getOrDefault(false)
            if (installed) {
                status = if (installedAtStart) MushafStatus.Ready else MushafStatus.NeedsRestart
            } else if (status != MushafStatus.Downloading) {
                status = MushafStatus.Downloading
                progress = null
                runCatching {
                    manager.startInstall(
                        SplitInstallRequest.newBuilder().addModule(MUSHAF_MODULE).build(),
                    )
                }.onFailure { status = MushafStatus.Failed }
                    .onSuccess { task ->
                        task.addOnFailureListener { status = MushafStatus.Failed }
                    }
            }
        },
        restartApp = { restartProcess(context) },
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
actual fun QuranMushafReader(
    initialPage: Int?,
    alreadyCounted: Set<Int>,
    onPageQualified: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onClose()
    }
    DisposableEffect(onPageQualified) {
        MushafPageBridge.onPageQualified = onPageQualified
        onDispose { MushafPageBridge.onPageQualified = null }
    }
    LaunchedEffect(initialPage) {
        val activity = context.findActivity()
        if (activity == null) {
            onClose()
            return@LaunchedEffect
        }
        val intent = Intent().setClassName(activity, "tools.mo3ta.salo.mushaf.MushafReaderActivity")
            .putExtra("tools.mo3ta.salo.mushaf.PAGE", initialPage ?: 0)
            .putExtra("tools.mo3ta.salo.mushaf.COUNTED", alreadyCounted.toIntArray())
        runCatching { SplitCompat.install(activity) }
        if (runCatching { launcher.launch(intent) }.isFailure) onClose()
    }
}

private fun restartProcess(context: Context) {
    val activity = context.findActivity() ?: return
    val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    activity.startActivity(launch)
    activity.finishAffinity()
    Process.killProcess(Process.myPid())
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
