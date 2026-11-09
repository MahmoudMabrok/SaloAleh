package tools.mo3ta.salo.ui.quran

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tools.mo3ta.salo.AndroidAppContext
import java.io.File
import kotlin.coroutines.resume

/**
 * The Mushaf page images, kept in the app's own storage.
 *
 * The images arrive in the on-demand `mushaf_assets` asset pack, but Play
 * invalidates downloaded on-demand packs on every app update, so readers had
 * to download ~110 MB again after each release. After the first download the
 * reader copies the images here once ([copyImages]) and drops the pack. App
 * updates never touch `noBackupFilesDir`, so the images stay until the user
 * deletes them from Settings ([deleteMushafContent]) or clears the app data.
 */
object MushafLocalImages {
    /** Name of the on-demand asset pack in :mushaf-assets. */
    const val PACK = "mushaf_assets"

    /** Folder with `{page}/{line}.png`, in the pack and in the local copy. */
    const val IMAGES_DIR = "quran-images"

    /** Bump when the pack's images change, so devices copy the new set. */
    const val VERSION = 1

    internal const val COMPLETE_MARKER = ".complete"

    fun root(context: Context): File = File(context.noBackupFilesDir, "mushaf")

    fun imagesDir(context: Context): File = imagesDir(root(context))

    fun isReady(context: Context): Boolean = isReady(root(context))

    fun imagesDir(root: File): File = File(root, "$IMAGES_DIR-v$VERSION")

    fun isReady(root: File): Boolean = File(imagesDir(root), COMPLETE_MARKER).isFile

    /**
     * Copies every file under [source] into [imagesDir] of [root]. Writes to a
     * temporary folder first and marks the copy complete only at the end, so a
     * copy cut short (process killed, disk full) is never used. Removes older
     * versions. Returns false, leaving no partial copy behind, on any failure.
     */
    fun copyImages(source: File, root: File, onProgress: (Int) -> Unit = {}): Boolean = runCatching {
        val files = source.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return false
        val tmp = File(root, "$IMAGES_DIR-tmp")
        tmp.deleteRecursively()
        files.forEachIndexed { index, file ->
            val out = File(tmp, file.relativeTo(source).path)
            out.parentFile?.mkdirs()
            file.copyTo(out, overwrite = true)
            if (index % 200 == 0) onProgress(index * 100 / files.size)
        }
        val target = imagesDir(root)
        target.deleteRecursively()
        if (!tmp.renameTo(target)) {
            tmp.deleteRecursively()
            return false
        }
        File(target, COMPLETE_MARKER).writeText(files.size.toString())
        root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(IMAGES_DIR) && it != target }
            ?.forEach { it.deleteRecursively() }
        onProgress(100)
        true
    }.getOrElse {
        File(root, "$IMAGES_DIR-tmp").deleteRecursively()
        false
    }
}

private const val MUSHAF_MODULE = "mushaf"

/** Mushaf Imad's copied text database (`quran-v*.realm` and its lock/note files). Not `userdata.realm`. */
internal fun isMushafContentFile(name: String): Boolean =
    name.startsWith("quran") && name.contains(".realm")

actual suspend fun mushafStoredBytes(): Long = withContext(Dispatchers.IO) {
    runCatching {
        val context = AndroidAppContext.get()
        var bytes = MushafLocalImages.root(context).sizeOnDisk()
        bytes += packDir(context)?.sizeOnDisk() ?: 0L
        bytes += context.filesDir.listFiles()
            ?.filter { isMushafContentFile(it.name) }
            ?.sumOf { it.sizeOnDisk() } ?: 0L
        bytes += context.applicationInfo.splitSourceDirs
            ?.filter { File(it).name.contains(MUSHAF_MODULE) }
            ?.sumOf { File(it).length() } ?: 0L
        bytes
    }.getOrDefault(0L)
}

actual suspend fun deleteMushafContent(): Boolean = withContext(Dispatchers.IO) {
    val context = AndroidAppContext.get()
    var ok = runCatching { MushafLocalImages.root(context).deleteRecursively() }.getOrDefault(false)
    ok = removePack(context) && ok
    context.filesDir.listFiles()
        ?.filter { isMushafContentFile(it.name) }
        ?.forEach { runCatching { it.deleteRecursively() } }
    // The module's code goes away the next time Play cleans up (usually within a day).
    runCatching {
        SplitInstallManagerFactory.create(context).deferredUninstall(listOf(MUSHAF_MODULE))
    }
    ok
}

private fun packDir(context: Context): File? = runCatching {
    AssetPackManagerFactory.getInstance(context).getPackLocation(MushafLocalImages.PACK)?.path()
}.getOrNull()?.let(::File)

private suspend fun removePack(context: Context): Boolean {
    val manager = runCatching { AssetPackManagerFactory.getInstance(context) }.getOrNull() ?: return true
    if (runCatching { manager.getPackLocation(MushafLocalImages.PACK) }.getOrNull() == null) return true
    return suspendCancellableCoroutine { cont ->
        runCatching {
            manager.removePack(MushafLocalImages.PACK).addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(task.isSuccessful)
            }
        }.onFailure { if (cont.isActive) cont.resume(false) }
    }
}

private fun File.sizeOnDisk(): Long =
    if (!exists()) 0L else walkTopDown().filter { it.isFile }.sumOf { it.length() }
