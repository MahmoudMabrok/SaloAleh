package tools.mo3ta.salo.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * mushaf-ui 0.4.x does not bundle the page images: they must all be in the
 * on-demand :mushaf-assets pack (pack "mushaf_assets"), at
 * quran-images/{page}/{line}.png, which MushafPackController reads from
 * AssetPackLocation.assetsPath()/quran-images. Otherwise the reader shows verse
 * markers with no script. This checks every page line exists and is a 1440x232
 * PNG, and that the pack is on-demand so it stays out of the install size.
 */
class MushafAssetPackImagesTest {

    private val dir = File(
        System.getProperty("mushaf.assets.dir")
            ?: "../mushaf-assets/src/main/assets/quran-images",
    )

    private val packGradle = File(
        System.getProperty("mushaf.assets.gradle") ?: "../mushaf-assets/build.gradle.kts",
    )

    @Test
    fun assetPackIsOnDemandAndNamedAsTheReaderExpects() {
        assertTrue("missing $packGradle", packGradle.isFile)
        val text = packGradle.readText()
        assertTrue(
            "mushaf-assets must be on-demand (not in install size)",
            Regex("""deliveryType\.set\("on-demand"\)""").containsMatchIn(text),
        )
        assertTrue(
            "pack name must match MUSHAF_PACK",
            text.contains("packName.set(\"$MUSHAF_PACK\")"),
        )
        assertEquals("quran-images", MUSHAF_IMAGES_DIR)
        assertEquals("quran-images", dir.name)
    }

    @Test
    fun everyPageHasAllLineImagesInTheAssetPack() {
        assertTrue("asset pack images dir missing: $dir", dir.isDirectory)
        val missing = mutableListOf<String>()
        var count = 0
        for (page in 1..604) {
            val lines = File(dir, "$page").listFiles { f -> f.name.endsWith(".png") }.orEmpty()
            if (lines.isEmpty()) missing += "page $page"
            for (png in lines) {
                count++
                val (w, h) = pngSize(png)
                if (w != 1440 || h != 232) missing += "${png.path} is ${w}x$h"
            }
        }
        assertTrue("problems: ${missing.take(20)}", missing.isEmpty())
        assertEquals("line image count", 9060, count)
        for (page in 1..604) {
            for (line in 1..15) {
                assertTrue("missing $page/$line.png", File(dir, "$page/$line.png").isFile)
            }
        }
    }

    private fun pngSize(file: File): Pair<Int, Int> = RandomAccessFile(file, "r").use { raf ->
        val sig = ByteArray(8).also { raf.readFully(it) }
        val pngSig = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        if (!sig.contentEquals(pngSig)) return -1 to -1
        raf.seek(16)
        raf.readInt() to raf.readInt()
    }
}
