package tools.mo3ta.salo.appcheck

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.debug.internal.StorageHelper
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The debug secret DebugAppCheckProvider exchanges lives in the SDK's own
 * preferences file, keyed by the Firebase app's persistence key. A fixed token
 * only works if it lands exactly there — otherwise every install mints a fresh
 * random secret that has to be allow-listed again in the Firebase Console.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FixedDebugAppCheckSecretTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        FirebaseApp.clearInstancesForTest()
    }

    @Test
    fun seed_writesTheTokenWhereTheDebugProviderReadsIt() {
        val app = newApp("seed-token")

        assertTrue(seedFixedDebugSecret(app, "  11111111-2222-3333-4444-555555555555  "))

        val stored = StorageHelper(context, app.persistenceKey).retrieveDebugSecret()
        assertEquals("11111111-2222-3333-4444-555555555555", stored)
    }

    @Test
    fun seed_withNoToken_keepsTheExistingSecret() {
        val app = newApp("keep-secret")
        assertTrue(seedFixedDebugSecret(app, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))

        assertFalse(seedFixedDebugSecret(app, ""))
        assertFalse(seedFixedDebugSecret(app, "   "))

        assertEquals(
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            StorageHelper(context, app.persistenceKey).retrieveDebugSecret(),
        )
    }

    private fun newApp(name: String): FirebaseApp =
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("1:100000000000:android:0000000000000000")
                .setApiKey("AIzaSyA-test-key-test-key-test-key-testke")
                .setProjectId("salo-aleh-test")
                .build(),
            name,
        )
}
