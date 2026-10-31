package tools.mo3ta.salo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tools.mo3ta.salo.TestApplication
import tools.mo3ta.salo.presentation.MohamedLoversLeaderboardEntry
import tools.mo3ta.salo.ui.components.LeaderboardCard
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.components.TotalsCard

/** Renders both proposed placements of the player's own all-time total (see the PR for the PNGs). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class, qualifiers = "ar-w411dp-h1400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class MohamedLoversSelfTotalMockupTest {

    private val selfTotal = 123_456L

    @Test
    fun optionA_totalsCardShowsSelfAllTimeTotal() = runAndroidComposeUiTest<ComponentActivity> {
        setContent {
            Frame { TotalsCard(roundTotal = 2_345_678, allTimeTotal = 98_765_432L, selfAllTimeTotal = selfTotal) }
        }
        settle()
        onNodeWithText("123,456").assertIsDisplayed()
        snapshot("total-option-a.png")
    }

    @Test
    fun optionB_leaderboardChipShowsSelfAllTimeTotal() = runAndroidComposeUiTest<ComponentActivity> {
        val top10 = (1..10).map { rank ->
            MohamedLoversLeaderboardEntry(
                rank = rank,
                displayTag = "EG • لاعب $rank",
                totalCount = 20_000 - rank * 1_000,
                isCurrentUser = false,
                uid = "uid$rank",
            )
        }
        val self = MohamedLoversLeaderboardEntry(rank = 37, displayTag = "EG • أنا", totalCount = 1_234, isCurrentUser = true, uid = "me")
        setContent {
            Frame {
                LeaderboardCard(
                    topPlayers = top10,
                    selfEntry = self,
                    selfInTop = false,
                    isDaily = false,
                    isSwitching = false,
                    onToggleLeaderboardType = {},
                    isPremium = true,
                    selfAllTimeTotal = selfTotal,
                )
            }
        }
        settle()
        onNodeWithText("123,456", substring = true).assertIsDisplayed()
        snapshot("total-option-b.png")
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.width(411.dp).background(MohamedLoversPalette.SkyTop).padding(12.dp)) { content() }
        }
    }

    private fun AndroidComposeUiTest<ComponentActivity>.settle() {
        // Let the count-up animations land on their final values.
        mainClock.advanceTimeBy(3_000)
        waitForIdle()
    }

    private fun AndroidComposeUiTest<ComponentActivity>.snapshot(name: String) {
        // Best effort: PixelCopy-based captureToImage() is unsupported under Robolectric.
        runCatching {
            val view = activity!!.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/screenshots/$name").apply { parentFile?.mkdirs() }.outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
