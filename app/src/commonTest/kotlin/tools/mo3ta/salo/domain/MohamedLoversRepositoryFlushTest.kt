package tools.mo3ta.salo.domain

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MohamedLoversRepositoryFlushTest {

    private fun buildRepo(
        sessionStore: MohamedLoversSessionStore = MohamedLoversSessionStore(MapSettings()),
        firebaseApi: FakeMohamedLoversFirebaseApi = FakeMohamedLoversFirebaseApi(),
    ): Pair<MohamedLoversRepository, FakeMohamedLoversFirebaseApi> {
        val networkTime = object : NetworkTimeProvider {
            override fun prime() {}
            override fun getCompetitionWindow() = MohamedLoversCompetitionWindow()
        }
        val countryCode = object : CountryCodeProvider {
            override fun get() = "EG"
        }
        val premiumStore = PremiumStore(MapSettings())
        return MohamedLoversRepository(firebaseApi, networkTime, sessionStore, countryCode, premiumStore) to firebaseApi
    }

    @Test
    fun flush_sends_pending_count_to_firebase() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 5)
        val (repo, fake) = buildRepo(sessionStore = store)

        val result = repo.flushPendingSession("EG")

        assertTrue(result.isSuccess)
        assertEquals(1, fake.incrementCalls.size)
        assertEquals(5, fake.incrementCalls[0].delta)
        assertEquals("R1", fake.incrementCalls[0].roundKey)
    }

    @Test
    fun flush_clears_pending_on_success() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 5)
        val (repo, _) = buildRepo(sessionStore = store)

        repo.flushPendingSession("EG")

        assertEquals(0, store.getPendingSession("R1").clickCount)
        assertTrue(store.getAllPendingRounds().isEmpty())
    }

    @Test
    fun flush_preserves_pending_on_failure() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 5)
        val fake = FakeMohamedLoversFirebaseApi()
        fake.incrementResult = Result.failure(RuntimeException("network error"))
        val (repo, _) = buildRepo(sessionStore = store, firebaseApi = fake)

        val result = repo.flushPendingSession("EG")

        assertTrue(result.isFailure)
        assertEquals(5, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun flush_preserves_taps_added_during_network_call() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 5)
        val fake = FakeMohamedLoversFirebaseApi()
        val gate = CompletableDeferred<Unit>()
        fake.incrementGate = gate
        val (repo, _) = buildRepo(sessionStore = store, firebaseApi = fake)

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val flushJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.flushPendingSession("EG")
        }

        // User taps 3 more while flush is suspended on network
        store.incrementPendingClick("R1", 3)

        // Release the network call
        gate.complete(Unit)
        flushJob.join()

        // The 3 new taps must survive — only the snapshotted 5 should be decremented
        assertEquals(3, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun flush_multiple_rounds_handles_partial_failure() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 5)
        store.incrementPendingClick("R2", 10)

        var callCount = 0
        val fake = FakeMohamedLoversFirebaseApi()
        val originalIncrement = fake.incrementResult

        val fakeWithPartialFailure = object : FakeMohamedLoversFirebaseApi() {
            override suspend fun incrementSession(roundKey: String, uid: String, delta: Int, countryCode: String, todayCount: Int): Result<Unit> {
                callCount++
                incrementCalls.add(IncrementCall(roundKey, uid, delta, countryCode, todayCount))
                return if (roundKey == "R1") Result.success(Unit)
                else Result.failure(RuntimeException("fail R2"))
            }
        }

        val (repo, _) = buildRepo(sessionStore = store, firebaseApi = fakeWithPartialFailure)

        val result = repo.flushPendingSession("EG")

        assertTrue(result.isFailure)
        assertEquals(0, store.getPendingSession("R1").clickCount)
        assertEquals(10, store.getPendingSession("R2").clickCount)
    }

    @Test
    fun flush_empty_pending_returns_success() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        val (repo, fake) = buildRepo(sessionStore = store)

        val result = repo.flushPendingSession("EG")

        assertTrue(result.isSuccess)
        assertTrue(fake.incrementCalls.isEmpty())
    }

    @Test
    fun flush_auth_failure_does_not_clear_pending() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 5)
        val fake = FakeMohamedLoversFirebaseApi()
        fake.signInResult = Result.failure(RuntimeException("auth failed"))
        val (repo, _) = buildRepo(sessionStore = store, firebaseApi = fake)

        val result = repo.flushPendingSession("EG")

        assertTrue(result.isFailure)
        assertEquals(5, store.getPendingSession("R1").clickCount)
    }
}
