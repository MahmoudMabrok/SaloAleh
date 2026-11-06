package tools.mo3ta.salo.domain

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.firebase.applySessionFlush
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
            override suspend fun incrementSession(roundKey: String, uid: String, delta: Int, countryCode: String, todayCount: Int, flushToken: String): Result<Unit> {
                callCount++
                incrementCalls.add(IncrementCall(roundKey, uid, delta, countryCode, todayCount, flushToken))
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
    fun flush_pushes_only_the_allowance_and_discards_the_rest() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 30_000)
        val (repo, fake) = buildRepo(sessionStore = store)

        val result = repo.flushPendingSession("EG", allowance = MOHAMED_LOVERS_DAILY_PUSH_CAP)

        assertEquals(1, fake.incrementCalls.size)
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, fake.incrementCalls[0].delta)
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, result.getOrThrow().pushed)
        assertEquals(5_000, result.getOrThrow().discarded)
        // The pending is cleared in full — the excess is dropped, not carried to tomorrow.
        assertEquals(0, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun flush_with_no_allowance_left_pushes_nothing_and_drops_pending() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 400)
        val (repo, fake) = buildRepo(sessionStore = store)

        val result = repo.flushPendingSession("EG", allowance = 0)

        assertTrue(fake.incrementCalls.isEmpty(), "nothing may be pushed once the cap is spent")
        assertEquals(0, result.getOrThrow().pushed)
        assertEquals(400, result.getOrThrow().discarded)
        assertEquals(0, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun flush_allowance_is_shared_across_pending_rounds() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 700)
        store.incrementPendingClick("R2", 700)
        val (repo, fake) = buildRepo(sessionStore = store)

        val result = repo.flushPendingSession("EG", allowance = 1_000)

        assertEquals(1_000, result.getOrThrow().pushed)
        assertEquals(400, result.getOrThrow().discarded)
        assertEquals(listOf(700, 300), fake.incrementCalls.map { it.delta })
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
    @Test
    fun retry_after_lost_ack_reuses_token_and_does_not_double_count_even_after_restart() = runTest {
        val settings = MapSettings()
        val store = MohamedLoversSessionStore(settings)
        val processed = mutableSetOf<String>()
        var remoteTotal = 0
        var loseAck = true
        val fake = object : FakeMohamedLoversFirebaseApi() {
            override suspend fun incrementSession(
                roundKey: String, uid: String, delta: Int, countryCode: String,
                todayCount: Int, flushToken: String,
            ): Result<Unit> {
                incrementCalls.add(IncrementCall(roundKey, uid, delta, countryCode, todayCount, flushToken))
                if (processed.add(flushToken)) remoteTotal += delta
                if (loseAck) {
                    loseAck = false
                    return Result.failure(RuntimeException("server committed, response lost"))
                }
                return Result.success(Unit)
            }
        }
        store.incrementPendingClick("R1", 5)
        val (firstRepo, _) = buildRepo(store, fake)
        assertTrue(firstRepo.flushPendingSession("EG", todayCount = 5).isFailure)
        assertEquals(5, remoteTotal)
        assertEquals(5, store.getPendingSession("R1").clickCount)

        // A process restart plus additional taps must not change the original retry payload.
        val restartedStore = MohamedLoversSessionStore(settings)
        restartedStore.incrementPendingClick("R1", 3)
        val (restartedRepo, _) = buildRepo(restartedStore, fake)
        assertTrue(restartedRepo.flushPendingSession("US", todayCount = 8, allowance = 2).isSuccess)
        assertEquals(fake.incrementCalls[0], fake.incrementCalls[1])
        assertTrue(fake.incrementCalls[0].flushToken.isNotBlank())
        assertEquals(5, remoteTotal)
        assertEquals(3, restartedStore.getPendingSession("R1").clickCount)

        assertTrue(restartedRepo.flushPendingSession("EG", todayCount = 8).isSuccess)
        assertTrue(fake.incrementCalls[2].flushToken != fake.incrementCalls[0].flushToken)
        assertEquals(8, remoteTotal)
        assertEquals(0, restartedStore.getPendingSession("R1").clickCount)
    }

    @Test
    fun concurrent_flushes_do_not_send_the_same_pending_twice() = runTest {
        val store = MohamedLoversSessionStore(MapSettings())
        store.incrementPendingClick("R1", 5)
        val gate = CompletableDeferred<Unit>()
        val fake = FakeMohamedLoversFirebaseApi().apply { incrementGate = gate }
        val (repo, _) = buildRepo(store, fake)
        val first = launch { repo.flushPendingSession("EG") }
        val second = launch { repo.flushPendingSession("EG") }
        testScheduler.runCurrent()
        gate.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, fake.incrementCalls.size)
        assertEquals(0, store.getPendingSession("R1").clickCount)
    }

    @Test
    fun persisted_pending_counts_from_previous_version_are_preserved() = runTest {
        val settings = MapSettings().apply {
            putString("pending_rounds_index", "R1")
            putInt("pending_count_R1", 5)
        }
        val store = MohamedLoversSessionStore(settings)
        val (repo, fake) = buildRepo(store)
        assertTrue(repo.flushPendingSession("EG").isSuccess)
        assertEquals(5, fake.incrementCalls.single().delta)
        assertEquals(0, store.getPendingSession("R1").clickCount)
        assertTrue(store.getAllPendingRounds().isEmpty())
    }

    @Test
    fun transaction_update_skips_old_tokens_and_preserves_other_player_fields() {
        val original = mapOf<String, Any?>("totalCount" to 10L, "rank" to 7, "isWinner" to true)
        val (first, applied) = applySessionFlush(original, "token-a", 5, mapOf("todayCount" to 5))
        assertTrue(applied)
        assertEquals(15L, first["totalCount"])
        assertEquals(7, first["rank"])
        assertEquals(true, first["isWinner"])
        val (second, _) = applySessionFlush(first, "token-b", 3, mapOf("todayCount" to 8))
        val (retry, appliedAgain) = applySessionFlush(second, "token-a", 5, mapOf("todayCount" to 5))
        assertEquals(false, appliedAgain)
        assertEquals(second, retry)
        assertEquals(18L, retry["totalCount"])
        assertEquals(8, retry["todayCount"])
    }

}
