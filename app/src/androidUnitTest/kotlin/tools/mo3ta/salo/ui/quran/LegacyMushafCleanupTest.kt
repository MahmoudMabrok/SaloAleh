package tools.mo3ta.salo.ui.quran

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyMushafCleanupTest {

    @Test
    fun deletesOldMushafFilesAndKeepsTheRest() {
        val root = Files.createTempDirectory("cleanup").toFile()
        val noBackup = File(root, "no_backup").apply { mkdirs() }
        val files = File(root, "files").apply { mkdirs() }
        File(noBackup, "mushaf/quran-images/1").apply { mkdirs() }.resolve("p1.png").writeText("x")
        File(files, "assetpacks/mushaf_assets/1").apply { mkdirs() }.resolve("a").writeText("x")
        val legacy = listOf("quran-v3.realm", "quran-v3.realm.lock", "quran.realm", "userdata.realm", "userdata.realm.lock")
            .map { File(files, it).apply { writeText("x") } }
        File(files, "quran-v3.realm.management").mkdirs()
        val keep = File(files, "datastore.preferences_pb").apply { writeText("x") }

        assertTrue(LegacyMushafCleanup.clean(noBackup, files))

        assertFalse(File(noBackup, "mushaf").exists())
        assertFalse(File(files, "assetpacks").exists())
        assertFalse(File(files, "quran-v3.realm.management").exists())
        legacy.forEach { assertFalse(it.exists(), it.name) }
        assertTrue(keep.exists())
        assertTrue(LegacyMushafCleanup.clean(noBackup, files))
        root.deleteRecursively()
    }
}
