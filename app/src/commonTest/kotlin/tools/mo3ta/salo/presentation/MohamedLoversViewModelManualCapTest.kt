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
import tools.mo3ta.salo.domain.SalawatManualCap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fresh installs seed the install date to "today", so the gradual new-user cap resolves to its day-1
 * value of 1,000 for every test here — enough to prove the clamp, ledger, and refund behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MohamedLoversViewModelManualCapTest {

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
    fun freshInstall_startsWithDayOneAllowance() = runTest {
        val vm = buildViewModel(FakeMohamedLoversFirebaseApi())
        assertEquals(SalawatManualCap.RAMP_STEP, vm.state.value.manualRemaining)
    }

    @Test
    fun submit_beyondCap_isClampedAndExhaustsAllowance() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 0)))

        // Day-1 cap is 1,000; asking for 5,000 only records 1,000 and empties the allowance.
        vm.submitManualSalawat(5_000)
        assertEquals(0, vm.state.value.manualRemaining)
    }

    @Test
    fun submit_accumulatesAgainstTheDailyCap() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 0)))

        vm.submitManualSalawat(300)
        assertEquals(700, vm.state.value.manualRemaining)
        vm.submitManualSalawat(300)
        assertEquals(400, vm.state.value.manualRemaining)
        // Further entries beyond the cap add nothing more.
        vm.submitManualSalawat(9_999)
        assertEquals(0, vm.state.value.manualRemaining)
        vm.submitManualSalawat(50)
        assertEquals(0, vm.state.value.manualRemaining)
    }

    @Test
    fun subtract_refundsTheManualAllowance() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 0)))

        vm.submitManualSalawat(1_000)
        assertEquals(0, vm.state.value.manualRemaining)
        // The manual batch is flushed to the server; reflect the new total back like the live flow would.
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 1_000)))
        // Correcting the mistaken batch frees the allowance again.
        vm.subtractManualSalawat(1_000)
        assertEquals(SalawatManualCap.RAMP_STEP, vm.state.value.manualRemaining)
    }
}
