package tools.mo3ta.salo.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Runs the app's real flush ([MohamedLoversFirebaseClient.incrementSession], gitlive on Android)
 * against the Realtime Database emulator with the repo's database.rules.json.
 *
 * The workflow `flush-instrumented-test.yml` starts the emulators on the host and seeds
 * [SEEDED_PATH] with server-owned fields (rank, yesterdayTotalScore) before the run.
 * The emulator does not enforce App Check, so nothing here touches production.
 */
@RunWith(AndroidJUnit4::class)
class MohamedLoversFlushEmulatorTest {

    private val client: MohamedLoversFirebaseClient
        get() = GlobalContext.get().get()

    @Test
    fun flushIncrementsTotalAndKeepsServerOwnedFields() = runBlocking {
        val before = read(SEEDED_PATH)
        val beforeTotal = before.long("totalCount")

        val result = client.incrementSession(
            roundKey = ROUND,
            uid = SEEDED_UID,
            delta = 5,
            countryCode = "EG",
            todayCount = 45,
        )
        assertTrue("flush failed: ${result.exceptionOrNull()}", result.isSuccess)

        val after = read(SEEDED_PATH)
        assertEquals(beforeTotal + 5, after.long("totalCount"))
        assertEquals(45L, after.long("todayCount"))
        assertEquals(7L, after.long("rank"))
        assertEquals(60L, after.long("yesterdayTotalScore"))
        assertServerTimestamp(after)
    }

    @Test
    fun firstFlushCreatesTheNode() = runBlocking {
        val uid = "instrumented-new-${System.currentTimeMillis()}"
        val result = client.incrementSession(
            roundKey = ROUND,
            uid = uid,
            delta = 3,
            countryCode = "EG",
            todayCount = 3,
        )
        assertTrue("flush failed: ${result.exceptionOrNull()}", result.isSuccess)

        val node = read("$PLAYERS/$uid")
        assertEquals(3L, node.long("totalCount"))
        assertServerTimestamp(node)
    }

    private fun read(path: String): DataSnapshot =
        Tasks.await(FirebaseDatabase.getInstance().getReference(path).get(), 30, TimeUnit.SECONDS)

    private fun DataSnapshot.long(key: String): Long =
        (child(key).value as? Number)?.toLong() ?: error("$key missing in ${value}")

    private fun assertServerTimestamp(node: DataSnapshot) {
        val updatedAt = node.long("updatedAt")
        assertTrue("updatedAt $updatedAt is not a recent server timestamp", abs(System.currentTimeMillis() - updatedAt) < 10 * 60_000)
    }

    companion object {
        // Host loopback as seen from the Android emulator.
        private const val HOST = "10.0.2.2"
        const val ROUND = "2099-01-02"
        const val SEEDED_UID = "instrumented-seeded"
        private const val PLAYERS = "mohamed_lovers/$ROUND/players"
        const val SEEDED_PATH = "$PLAYERS/$SEEDED_UID"

        @BeforeClass
        @JvmStatic
        fun pointAtEmulators() {
            // Must run before anything else touches these instances in the process.
            FirebaseDatabase.getInstance().useEmulator(HOST, 9000)
            FirebaseFirestore.getInstance().useEmulator(HOST, 8080)
        }
    }
}
