package tools.mo3ta.salo.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tools.mo3ta.salo.ui.quran.MushafLocalImages
import java.io.File

class MushafLocalImagesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun pack(): File = tmp.newFolder("pack", "quran-images").also { dir ->
        for (page in 1..3) for (line in 1..15) {
            File(dir, "$page/$line.png").apply { parentFile!!.mkdirs() }.writeText("p$page-l$line")
        }
    }

    @Test
    fun copiesEveryImageAndMarksComplete() {
        val root = tmp.newFolder("root")
        assertFalse(MushafLocalImages.isReady(root))
        val progress = mutableListOf<Int>()

        assertTrue(MushafLocalImages.copyImages(pack(), root) { progress += it })

        val dir = MushafLocalImages.imagesDir(root)
        assertTrue(MushafLocalImages.isReady(root))
        assertEquals("p2-l7", File(dir, "2/7.png").readText())
        assertEquals(45, dir.walkTopDown().count { it.isFile && it.extension == "png" })
        assertEquals(100, progress.last())
        assertFalse(File(root, "quran-images-tmp").exists())
    }

    @Test
    fun emptySourceIsNotMarkedReady() {
        val root = tmp.newFolder("root")
        assertFalse(MushafLocalImages.copyImages(tmp.newFolder("empty"), root))
        assertFalse(MushafLocalImages.isReady(root))
    }

    @Test
    fun removesOlderVersionsAndPartialCopies() {
        val root = tmp.newFolder("root")
        File(root, "quran-images-v0/1/1.png").apply { parentFile!!.mkdirs() }.writeText("old")
        File(root, "quran-images-tmp/1/1.png").apply { parentFile!!.mkdirs() }.writeText("partial")

        assertTrue(MushafLocalImages.copyImages(pack(), root))

        assertEquals(
            listOf(MushafLocalImages.imagesDir(root).name),
            root.listFiles()!!.map { it.name },
        )
    }
}
