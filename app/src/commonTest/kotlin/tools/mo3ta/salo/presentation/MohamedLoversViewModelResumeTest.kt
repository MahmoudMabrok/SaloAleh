package tools.mo3ta.salo.presentation

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import tools.mo3ta.salo.domain.MohamedLoversRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the round-transition-on-resume fix: a device backgrounded before a round
 * boundary must pick up the new round key as soon as the app is resumed, instead of
 * waiting for the (possibly-suspended) background final-minutes timer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MohamedLoversViewModelResumeTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MutableNetworkTimeProvider(
        var window: MohamedLoversCompetitionWindow,
    ) : NetworkTimeProvider {
        var callCount = 0
        override fun prime() {}
        override fun getCompetitionWindow(): MohamedLoversCompetitionWindow {
            callCount++
            return window
        }
    }

    private fun buildViewModel(provider: MutableNetworkTimeProvider): MohamedLoversViewModel {
        val sessionStore = MohamedLoversSessionStore(MapSettings())
        val countryCode = object : CountryCodeProvider {
            override fun get() = "EG"
        }
        val premiumStore = PremiumStore(MapSettings())
        val repo = MohamedLoversRepository(
            FakeMohamedLoversFirebaseApi(),
            provider,
            sessionStore,
            countryCode,
            premiumStore,
        )
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
    fun onAppResumed_after_round_end_switches_to_new_round() = runTest {
        val oldRoundEnd = Instant.parse("2000-01-01T00:00:00Z") // long past
        val provider = MutableNetworkTimeProvider(
            MohamedLoversCompetitionWindow(
                networkNow = Instant.parse("1999-12-25T00:00:00Z"),
                roundKey = "1999-12-31",
                roundEnd = oldRoundEnd,
            )
        )
        val vm = buildViewModel(provider)
        assertEquals("1999-12-31", vm.state.value.roundKey)
        assertEquals(1, provider.callCount)

        // Server/network time now reports the round that follows the (already past) boundary.
        provider.window = MohamedLoversCompetitionWindow(
            networkNow = Instant.parse("2000-01-02T00:00:00Z"),
            roundKey = "2000-01-07",
            roundEnd = Instant.parse("2099-01-01T00:00:00Z"),
        )

        vm.onAppResumed()

        assertEquals("2000-01-07", vm.state.value.roundKey, "onAppResumed must pick up the new round key")
        assertEquals(2, provider.callCount, "a stale round must trigger a full refresh(), not just a session-click re-read")
        assertEquals(0, vm.state.value.sessionClicks, "new round starts counting from zero")
    }

    @Test
    fun onAppResumed_before_round_end_only_refreshes_session_clicks() = runTest {
        val futureRoundEnd = Instant.parse("2099-01-01T00:00:00Z")
        val provider = MutableNetworkTimeProvider(
            MohamedLoversCompetitionWindow(
                networkNow = Instant.parse("2026-05-12T00:00:00Z"),
                roundKey = "2026-05-15",
                roundEnd = futureRoundEnd,
            )
        )
        val vm = buildViewModel(provider)
        assertEquals(1, provider.callCount)

        vm.onCountClick()
        val clicksBeforeResume = vm.state.value.sessionClicks

        vm.onAppResumed()

        assertEquals(1, provider.callCount, "an active round must not trigger a full refresh()")
        assertEquals("2026-05-15", vm.state.value.roundKey)
        assertEquals(clicksBeforeResume, vm.state.value.sessionClicks)
    }
}
