package tools.mo3ta.salo.ui.quran

import android.content.Context
import java.io.File

/**
 * Removes what the old image Mushaf (Mushaf Imad reader + Play asset pack) left on the
 * phone. The new reader draws the Mushaf from text inside the app, so none of it is used:
 * - noBackupFilesDir/mushaf: the page images copied out of the asset pack (up to ~113 MB)
 * - filesDir/assetpacks: the asset pack itself, if Play left it behind
 * - filesDir/quran*.realm*: the reader's Quran text database
 * - filesDir/userdata.realm*: the old reader's own last page and bookmarks
 * - the old reader's preferences
 * Safe to call more than once; it runs again only after [VERSION] changes.
 */
object LegacyMushafCleanup {
    private const val PREFS = "mushaf_cleanup"
    private const val KEY_DONE = "done_version"
    private const val VERSION = 1

    fun runOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_DONE, 0) >= VERSION) return
        if (clean(context.noBackupFilesDir, context.filesDir)) {
            runCatching { context.deleteSharedPreferences("mushaf_reader_prefs") }
            prefs.edit().putInt(KEY_DONE, VERSION).apply()
        }
    }

    /** Returns true when nothing of the old Mushaf is left. */
    internal fun clean(noBackupDir: File, filesDir: File): Boolean {
        var ok = true
        listOf(File(noBackupDir, "mushaf"), File(filesDir, "assetpacks")).forEach { dir ->
            if (dir.exists() && !dir.deleteRecursively()) ok = false
        }
        filesDir.listFiles()?.filter { isLegacyMushafFile(it.name) }?.forEach { file ->
            if (file.exists() && !file.deleteRecursively()) ok = false
        }
        return ok
    }

    internal fun isLegacyMushafFile(name: String): Boolean =
        (name.startsWith("quran") || name.startsWith("userdata")) && name.contains(".realm")
}
