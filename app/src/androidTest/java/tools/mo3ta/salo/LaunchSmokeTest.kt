package tools.mo3ta.salo

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the real [MainActivity]. A startup crash (a ContentProvider whose class
 * is missing, or an exception in [SaloApplication]) fails this test.
 * The pipeline installs the base APK only, so the on-demand Mushaf module is absent.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @Test
    fun mainActivityStaysOpen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
            Thread.sleep(3_000)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
