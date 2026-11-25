package tools.mo3ta.salo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tools.mo3ta.salo.TestApplication
import tools.mo3ta.salo.ui.components.MohamedLoversInfoSheetHeader
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

/** Dedication banner above the lovers sheet title (snapshot: app/build/screenshots/dedication-banner.png). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class, qualifiers = "ar-w411dp-h220dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class MohamedLoversDedicationBannerTest {

    @Test
    fun headerShowsDedicationAboveTitle() = runAndroidComposeUiTest<ComponentActivity> {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MohamedLoversPalette.SkyTop)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    MohamedLoversInfoSheetHeader()
                }
            }
        }
        waitForIdle()

        val line1 = onNodeWithText("هذا التطبيق صدقة جارية")
        val line2 = onNodeWithText("عن أرواح آباء وأمهات كل من يستخدمه")
        val title = onNodeWithText("محبو محمد", substring = true)
        line1.assertIsDisplayed()
        line2.assertIsDisplayed()
        title.assertIsDisplayed()

        val line1Bounds = line1.getUnclippedBoundsInRoot()
        val line2Bounds = line2.getUnclippedBoundsInRoot()
        val titleBounds = title.getUnclippedBoundsInRoot()
        assertTrue(line2Bounds.top >= line1Bounds.top)
        assertTrue(titleBounds.top >= line2Bounds.bottom)

        // Best effort: PixelCopy-based captureToImage() is unsupported under Robolectric.
        runCatching {
            val view = activity!!.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/screenshots/dedication-banner.png").apply { parentFile?.mkdirs() }
                .outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
