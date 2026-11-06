package tools.mo3ta.salo.mushaf

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.mushafimad.ui.mushaf.AssetLineImageSource
import com.mushafimad.ui.mushaf.FallbackLineImageSource
import com.mushafimad.ui.mushaf.FileLineImageSource
import com.mushafimad.ui.mushaf.LineImageSource
import com.mushafimad.ui.mushaf.MushafLineImages
import java.io.File

/** Name of the on-demand asset pack in :mushaf-assets. */
internal const val MUSHAF_PACK = "mushaf_assets"

/** Folder inside the pack's assets with `{page}/{line}.png`. */
internal const val MUSHAF_IMAGES_DIR = "quran-images"

/** What the pack screen shows. Pure, so it is unit tested (MushafPackUiTest). */
internal sealed interface PackUi {
    data object Checking : PackUi
    data object Ready : PackUi

    /** [percent] is null while the size is not known yet (PENDING). */
    data class Downloading(
        val percent: Int?,
        val downloaded: Long,
        val total: Long,
        val transferring: Boolean,
    ) : PackUi

    /** Play waits for the user: Wi-Fi only ([wifi]) or REQUIRES_USER_CONFIRMATION. */
    data class NeedsConfirmation(val wifi: Boolean) : PackUi

    data class Failed(val errorCode: Int, val canceled: Boolean) : PackUi

    data object NotInstalled : PackUi
}

internal fun packUiFor(
    status: Int,
    downloaded: Long,
    total: Long,
    transferPercent: Int,
    errorCode: Int,
): PackUi = when (status) {
    AssetPackStatus.COMPLETED -> PackUi.Ready
    AssetPackStatus.PENDING -> PackUi.Downloading(null, downloaded, total, transferring = false)
    AssetPackStatus.DOWNLOADING -> PackUi.Downloading(
        percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else null,
        downloaded = downloaded,
        total = total,
        transferring = false,
    )
    AssetPackStatus.TRANSFERRING ->
        PackUi.Downloading(transferPercent.coerceIn(0, 100), total, total, transferring = true)
    AssetPackStatus.WAITING_FOR_WIFI -> PackUi.NeedsConfirmation(wifi = true)
    AssetPackStatus.REQUIRES_USER_CONFIRMATION -> PackUi.NeedsConfirmation(wifi = false)
    AssetPackStatus.FAILED -> PackUi.Failed(errorCode, canceled = false)
    AssetPackStatus.CANCELED -> PackUi.Failed(errorCode, canceled = true)
    else -> PackUi.NotInstalled // NOT_INSTALLED, UNKNOWN
}

/**
 * The page images are in the on-demand `mushaf_assets` pack. They are not in
 * `context.assets`: Play extracts them to [AssetPackManager.getPackLocation]
 * `.assetsPath()`. This checks for the pack, fetches it with progress, asks
 * for confirmation when Play needs it, and points [MushafLineImages] at the
 * files once it is there. Nothing here throws: without Play or the pack the
 * screen shows an error with a retry button and the rest of the app is fine.
 *
 * Create in `onCreate` (it registers an activity result launcher), call
 * [start] once and [release] in `onDestroy`.
 */
internal class MushafPackController(private val activity: ComponentActivity) {
    private val context: Context = activity.applicationContext
    private val manager: AssetPackManager? =
        runCatching { AssetPackManagerFactory.getInstance(context) }.getOrNull()

    var ui: PackUi by mutableStateOf(PackUi.Checking)
        private set

    private var lastStatus = AssetPackStatus.UNKNOWN
    private var autoDialogShown = false
    private var listening = false

    private val confirmLauncher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // Accepted: Play resumes the download and the listener reports it.
            // Declined: keep the confirmation screen so the user can ask again.
            if (result.resultCode == Activity.RESULT_OK) refreshState()
        }

    private val listener = AssetPackStateUpdateListener { state ->
        if (state.name() == MUSHAF_PACK) onState(state)
    }

    fun start() {
        if (useImagesIfPresent()) {
            ui = PackUi.Ready
            return
        }
        val m = manager ?: run {
            ui = PackUi.Failed(AssetPackErrorCode.API_NOT_AVAILABLE, canceled = false)
            return
        }
        runCatching { m.registerListener(listener) }.onSuccess { listening = true }
        fetch()
    }

    fun retry() {
        val m = manager ?: return start()
        if (lastStatus == AssetPackStatus.COMPLETED) {
            // Play says installed but the files are not readable: drop and refetch.
            ui = PackUi.Checking
            runCatching {
                m.removePack(MUSHAF_PACK).addOnCompleteListener { fetch() }
            }.onFailure { fetch() }
        } else {
            fetch()
        }
    }

    fun cancel() {
        val m = manager ?: return
        runCatching { m.cancel(listOf(MUSHAF_PACK)).packStates()[MUSHAF_PACK]?.let(::onState) }
    }

    /** Wi-Fi / user confirmation dialog from Play. */
    fun confirm() {
        val m = manager ?: return
        val shown = runCatching { m.showConfirmationDialog(confirmLauncher) }.getOrDefault(false)
        if (!shown) refreshState()
    }

    fun release() {
        if (listening) runCatching { manager?.unregisterListener(listener) }
        listening = false
    }

    private fun fetch() {
        val m = manager ?: return
        if (ui !is PackUi.Downloading) ui = PackUi.Downloading(null, 0, 0, transferring = false)
        runCatching {
            m.fetch(listOf(MUSHAF_PACK))
                .addOnSuccessListener { states -> states.packStates()[MUSHAF_PACK]?.let(::onState) }
                .addOnFailureListener(::onError)
        }.onFailure(::onError)
    }

    private fun refreshState() {
        val m = manager ?: return
        runCatching {
            m.getPackStates(listOf(MUSHAF_PACK))
                .addOnSuccessListener { states -> states.packStates()[MUSHAF_PACK]?.let(::onState) }
                .addOnFailureListener(::onError)
        }.onFailure(::onError)
    }

    private fun onError(e: Throwable) {
        if (ui == PackUi.Ready) return
        val code = (e as? AssetPackException)?.errorCode ?: AssetPackErrorCode.INTERNAL_ERROR
        ui = PackUi.Failed(code, canceled = false)
    }

    private fun onState(state: AssetPackState) {
        if (ui == PackUi.Ready) return
        lastStatus = state.status()
        val next = packUiFor(
            status = state.status(),
            downloaded = state.bytesDownloaded(),
            total = state.totalBytesToDownload(),
            transferPercent = state.transferProgressPercentage(),
            errorCode = state.errorCode(),
        )
        ui = when (next) {
            PackUi.Ready -> if (useImagesIfPresent()) {
                PackUi.Ready
            } else {
                PackUi.Failed(AssetPackErrorCode.INTERNAL_ERROR, canceled = false)
            }
            // Fetch was requested, Play has not started yet.
            PackUi.NotInstalled -> PackUi.Downloading(null, 0, 0, transferring = false)
            else -> next
        }
        if (ui == PackUi.Ready) release()
        if (next is PackUi.NeedsConfirmation && !autoDialogShown) {
            autoDialogShown = true
            confirm()
        }
    }

    /** Points [MushafLineImages] at the pack (or bundled assets) when page 1 is readable. */
    private fun useImagesIfPresent(): Boolean {
        val sources = mutableListOf<LineImageSource>()
        val dir = runCatching { manager?.getPackLocation(MUSHAF_PACK)?.assetsPath() }.getOrNull()
            ?.let { File(it, MUSHAF_IMAGES_DIR) }
        if (dir != null) sources += FileLineImageSource(dir)
        // Builds that bundle the images in assets (e.g. mushaf-images) still work.
        sources += AssetLineImageSource()
        val source = FallbackLineImageSource(sources)
        val ok = runCatching { source.open(context, 1, 1)?.use { true } ?: false }.getOrDefault(false)
        if (ok) MushafLineImages.source = source
        return ok
    }
}
