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
import tools.mo3ta.salo.domain.MohamedLoversMedals
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.MohamedLoversRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MohamedLoversViewModelSelfMedalsTest {

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
        val repo = MohamedLoversRepository(fake, networkTime, sessionStore, countryCode)
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
    fun self_entry_outside_top_carries_fetched_medals() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        // Medals live on the user node and are only attached to server top-N entries.
        // A user outside the top must still see their own medals on the self row.
        fake.selfMedalsResult = Result.success(MohamedLoversMedals(gold = 2, silver = 1, bronze = 3))

        val vm = buildViewModel(fake)

        // Self has a score but no server leaderboard entries → rendered as the synthetic self row.
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 50)))

        val self = vm.state.value.selfEntry
        assertEquals(2, self?.goldMedals)
        assertEquals(1, self?.silverMedals)
        assertEquals(3, self?.bronzeMedals)
    }

    @Test
    fun self_entry_omits_zero_medals() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        fake.selfMedalsResult = Result.success(MohamedLoversMedals(gold = 0, silver = 0, bronze = 0))

        val vm = buildViewModel(fake)
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 50)))

        val self = vm.state.value.selfEntry
        assertEquals(null, self?.goldMedals)
        assertEquals(null, self?.silverMedals)
        assertEquals(null, self?.bronzeMedals)
    }
}
