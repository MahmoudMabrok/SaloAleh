package tools.mo3ta.salo.presentation

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider
import tools.mo3ta.salo.domain.FakeMohamedLoversFirebaseApi
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.MohamedLoversRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MohamedLoversViewModelProjectionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        fake: FakeMohamedLoversFirebaseApi,
        sessionStore: MohamedLoversSessionStore = MohamedLoversSessionStore(MapSettings()),
    ): MohamedLoversViewModel {
        val window = MohamedLoversCompetitionWindow(
            networkNow = Instant.parse("2026-05-12T00:00:00Z"),
            roundKey = "2026-05-15",
        )
        val networkTime = object : NetworkTimeProvider {
            override fun prime() {}
            override fun getCompetitionWindow() = window
        }
        val countryCode = object : CountryCodeProvider {
            override fun get() = "EG"
        }
        val repo = MohamedLoversRepository(fake, networkTime, sessionStore, countryCode)
        return MohamedLoversViewModel(
            repository = repo,
            engagementStore = EngagementStore(MapSettings()),
            hadithStore = DailyHadithStore(MapSettings()),
            dailyGoalStore = DailyGoalStore(MapSettings()),
        )
    }

    @Test
    fun projection_does_not_double_count_when_observe_fires_during_flush() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow

        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fake.incrementGate = gate

        val sessionStore = MohamedLoversSessionStore(MapSettings())
        val vm = buildViewModel(fake, sessionStore)

        // Tap 5 times
        repeat(5) { vm.onCountClick() }
        assertEquals(5, vm.state.value.sessionClicks)

        // Start flush — will suspend on incrementGate
        vm.flushPendingSession()

        // Simulate Firebase observeSelfPlayer firing with the flushed total
        // This happens when updateChildren applies to local cache before server ack
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 5)))

        // The projected total must be 5, NOT 10
        val selfTotal = vm.state.value.topPlayers.find { it.isCurrentUser }?.totalCount
            ?: vm.state.value.selfEntry?.totalCount
            ?: 0
        assertEquals(5, selfTotal, "Projection doubled: remote=5 + sessionClicks=5 should not be 10")

        // Release the network gate
        gate.complete(Unit)
    }
}
