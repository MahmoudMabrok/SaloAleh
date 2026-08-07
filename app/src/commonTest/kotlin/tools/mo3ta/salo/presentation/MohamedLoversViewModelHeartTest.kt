package tools.mo3ta.salo.presentation

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.engagement.EngagementStore
import tools.mo3ta.salo.data.engagement.RoundStreakStore
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.heart.HEART_DECAY_INTERVAL_MS
import tools.mo3ta.salo.data.heart.HeartStore
import tools.mo3ta.salo.data.heart.lastHeartResetBoundary
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MohamedLoversViewModelHeartTest {

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
        override fun prime() {}
        override fun getCompetitionWindow() = window
    }

    private fun buildViewModel(
        heartStore: HeartStore = HeartStore(MapSettings()),
        sessionStore: MohamedLoversSessionStore = MohamedLoversSessionStore(MapSettings()),
        provider: MutableNetworkTimeProvider? = null,
    ): MohamedLoversViewModel {
        val defaultWindow = MohamedLoversCompetitionWindow(
            networkNow = Instant.parse("2026-05-12T00:00:00Z"),
            roundKey = "2026-05-15",
        )
        val networkTime = provider ?: MutableNetworkTimeProvider(defaultWindow)
        val countryCode = object : CountryCodeProvider {
            override fun get() = "EG"
        }
        val premiumStore = PremiumStore(MapSettings())
        val repo = MohamedLoversRepository(
            FakeMohamedLoversFirebaseApi(),
            networkTime,
            sessionStore,
            countryCode,
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
            heartStore = heartStore,
            startTimers = false,
        )
    }

    @Test
    fun tap_increments_heart_and_starts_clock() = runTest {
        val heartStore = HeartStore(MapSettings())
        val vm = buildViewModel(heartStore)

        vm.onCountClick()

        assertEquals(10, vm.state.value.heartScore)
        assertEquals(10, heartStore.getScore())
        assertTrue(heartStore.getAnchorTs() > 0L)
    }

    @Test
    fun manual_salawat_scales_heart_by_count() = runTest {
        val heartStore = HeartStore(MapSettings())
        val vm = buildViewModel(heartStore)

        vm.submitManualSalawat(5)

        assertEquals(50, vm.state.value.heartScore)
        assertEquals(50, heartStore.getScore())
        assertTrue(heartStore.getAnchorTs() > 0L)
    }

    @Test
    fun extension_score_credits_heart_by_count() = runTest {
        val heartStore = HeartStore(MapSettings())
        val vm = buildViewModel(heartStore)

        vm.applyExtensionScore("2026-05-15", 3)

        assertEquals(30, vm.state.value.heartScore)
        assertEquals(30, heartStore.getScore())
        assertTrue(heartStore.getAnchorTs() > 0L)
    }

    @Test
    fun init_settles_offline_decay_and_carries_remainder() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val anchor = now - 35_000L
        val heartStore = HeartStore(MapSettings()).apply { save(50, anchor) }

        val vm = buildViewModel(heartStore)

        assertEquals(47, vm.state.value.heartScore)
        assertEquals(47, heartStore.getScore())
        assertEquals(anchor + 30_000L, heartStore.getAnchorTs())
    }

    @Test
    fun low_started_score_shows_refill_nudge() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val heartStore = HeartStore(MapSettings()).apply { save(0, now) }

        val vm = buildViewModel(heartStore)

        assertTrue(vm.state.value.showHeartRefillNudge)
    }

    @Test
    fun high_score_hides_refill_nudge() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val heartStore = HeartStore(MapSettings()).apply { save(12, now) }

        val vm = buildViewModel(heartStore)

        assertFalse(vm.state.value.showHeartRefillNudge)
    }

    @Test
    fun init_resets_anchor_older_than_last_friday_22() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val oldAnchor = lastHeartResetBoundary(now) - 1_000L
        val heartStore = HeartStore(MapSettings()).apply { save(50, oldAnchor) }

        val vm = buildViewModel(heartStore)

        assertEquals(0, vm.state.value.heartScore)
        assertEquals(0, heartStore.getScore())
        assertTrue(heartStore.getAnchorTs() > oldAnchor)
    }

    @Test
    fun onAppResumed_settles_heart_decay_even_when_round_ended() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val anchor = now - 60 * HEART_DECAY_INTERVAL_MS // 60 intervals ago
        val heartStore = HeartStore(MapSettings()).apply { save(100, anchor) }

        val oldRoundEnd = Instant.parse("2000-01-01T00:00:00Z")
        val provider = MutableNetworkTimeProvider(
            MohamedLoversCompetitionWindow(
                networkNow = Instant.parse("1999-12-25T00:00:00Z"),
                roundKey = "1999-12-31",
                roundEnd = oldRoundEnd,
            )
        )
        val vm = buildViewModel(heartStore, provider = provider)

        // Simulate round ending while user was away
        provider.window = MohamedLoversCompetitionWindow(
            networkNow = Instant.parse("2000-01-02T00:00:00Z"),
            roundKey = "2000-01-07",
            roundEnd = Instant.parse("2099-01-01T00:00:00Z"),
        )

        vm.onAppResumed()

        assertTrue(
            vm.state.value.heartScore <= 40,
            "heart should have decayed by at least 60 intervals, was ${vm.state.value.heartScore}"
        )
    }
}
