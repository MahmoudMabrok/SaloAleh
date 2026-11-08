package tools.mo3ta.salo.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Runs the app's real flush ([MohamedLoversFirebaseClient.incrementSession], gitlive on Android)
 * against the Realtime Database emulator with the repo's database.rules.json.
 *
 * The workflow `flush-instrumented-test.yml` starts the emulators on the host, seeds
 * [SEEDED_UID] with server-owned fields (rank, yesterdayTotalScore), and after this run
 * verifies the written nodes over the emulator's REST API. Reads from the Android SDK
 * never return against the emulator in CI, so the device side only checks that the
 * flush itself succeeds. The emulator does not enforce App Check, so nothing here
 * touches production.
 */
@RunWith(AndroidJUnit4::class)
class MohamedLoversFlushEmulatorTest {

    private val client: MohamedLoversFirebaseClient
        get() = GlobalContext.get().get()

    @Test
    fun flushOnSeededNodeSucceeds() = runBlocking {
        val result = client.incrementSession(
            roundKey = ROUND,
            uid = SEEDED_UID,
            delta = 5,
            countryCode = "EG",
            todayCount = 45,
        )
        assertTrue("flush failed: ${result.exceptionOrNull()}", result.isSuccess)
    }

    @Test
    fun firstFlushCreatesTheNode() = runBlocking {
        val result = client.incrementSession(
            roundKey = ROUND,
            uid = NEW_UID,
            delta = 3,
            countryCode = "EG",
            todayCount = 3,
        )
        assertTrue("flush failed: ${result.exceptionOrNull()}", result.isSuccess)
    }

    companion object {
        // Host loopback as seen from the Android emulator.
        private const val HOST = "10.0.2.2"
        const val ROUND = "2099-01-02"
        const val SEEDED_UID = "instrumented-seeded"
        const val NEW_UID = "instrumented-new"

        @BeforeClass
        @JvmStatic
        fun pointAtEmulators() {
            // Must run before anything else touches these instances in the process.
            FirebaseDatabase.getInstance().useEmulator(HOST, 9000)
            FirebaseFirestore.getInstance().useEmulator(HOST, 8080)
        }
    }
}
