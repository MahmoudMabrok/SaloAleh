package tools.mo3ta.salo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class, qualifiers = "ar-w411dp-h1400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class MohamedLoversLeaderboardCardTest {

    private val top10 = (1..10).map { rank ->
        MohamedLoversLeaderboardEntry(
            rank = rank,
            displayTag = "EG • لاعب $rank",
            totalCount = 20_000 - rank * 1_000,
            isCurrentUser = false,
            uid = "uid$rank",
        )
    }
    private val self = MohamedLoversLeaderboardEntry(
        rank = 37,
        displayTag = "EG • أنا",
        totalCount = 1_234,
        isCurrentUser = true,
        uid = "me",
    )

    @Test
    fun selfOutsideTop10_showsOwnRowWithoutSeparator() = runAndroidComposeUiTest<ComponentActivity> {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(
                    Modifier
                        .width(411.dp)
                        .background(MohamedLoversPalette.SkyTop)
                        .padding(12.dp),
                ) {
                    LeaderboardCard(
                        topPlayers = top10,
                        selfEntry = self,
                        selfInTop = false,
                        isDaily = false,
                        isSwitching = false,
                        onToggleLeaderboardType = {},
                        isPremium = true,
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("أنا", substring = true).assertIsDisplayed()
        onNodeWithText("• • •").assertDoesNotExist()

        // Snapshot for PR review: app/build/screenshots/mohamed_lovers_leaderboard.png
        // Drawn straight from the view tree: PixelCopy-based captureToImage() is not supported
        // under Robolectric. Best effort only, so it can never fail the assertions above.
        runCatching {
            val view = activity!!.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/screenshots/mohamed_lovers_leaderboard.png")
                .apply { parentFile?.mkdirs() }
                .outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
