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
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.engagement.RoundStreakStore
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.heart.HeartStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
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
class MohamedLoversViewModelSubtractTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(fake: FakeMohamedLoversFirebaseApi): MohamedLoversViewModel {
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
        val sessionStore = MohamedLoversSessionStore(MapSettings())
        val premiumStore = PremiumStore(MapSettings())
        val repo = MohamedLoversRepository(fake, networkTime, sessionStore, countryCode, premiumStore)
        return MohamedLoversViewModel(
            repository = repo,
            engagementStore = EngagementStore(MapSettings()),
            hadithStore = DailyHadithStore(MapSettings()),
            dailyGoalStore = DailyGoalStore(MapSettings()),
            roundStreakStore = RoundStreakStore(MapSettings()),
            settingsStore = NotificationSettingsStore(MapSettings()),
            sessionStore = sessionStore,
            premiumStore = premiumStore,
            heartStore = HeartStore(MapSettings()),
            startTimers = false,
        )
    }

    @Test
    fun subtract_reduces_local_pending_before_touching_the_server() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)

        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 100)))
        repeat(5) { vm.onCountClick() }
        assertEquals(5, vm.state.value.sessionClicks)

        // Subtracting within the un-flushed pending only touches local state, no server write.
        vm.subtractManualSalawat(3)
        assertEquals(2, vm.state.value.sessionClicks)
        assertEquals(0, fake.decrementScoreCalls.size)
    }

    @Test
    fun subtract_spills_over_to_server_after_exhausting_pending() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)

        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 100)))
        repeat(2) { vm.onCountClick() }
        assertEquals(2, vm.state.value.sessionClicks)

        // 2 pending + 48 from the saved server score = 50 removed.
        vm.subtractManualSalawat(50)
        assertEquals(0, vm.state.value.sessionClicks)
        assertEquals(1, fake.decrementScoreCalls.size)
        assertEquals(48, fake.decrementScoreCalls.first().amount)
    }

    @Test
    fun subtract_is_capped_to_the_current_score_so_it_never_goes_negative() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)

        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 30)))
        repeat(4) { vm.onCountClick() }
        assertEquals(4, vm.state.value.sessionClicks)

        // Current score is 34; asking to remove far more clamps to 34 (4 pending + 30 server).
        vm.subtractManualSalawat(1000)
        assertEquals(0, vm.state.value.sessionClicks)
        assertEquals(1, fake.decrementScoreCalls.size)
        assertEquals(30, fake.decrementScoreCalls.first().amount)
    }
}
