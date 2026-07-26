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
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.heart.HeartStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.engagement.RoundStreakStore
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MohamedLoversViewModelDailyBadgeTest {

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
    fun badge_is_not_published_before_a_milestone_is_reached() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 0)))

        // Below the first (100-tap) milestone: no badge write and no forced flush from tapping.
        repeat(99) { vm.onCountClick() }

        assertTrue(fake.writeDailyBadgeCalls.isEmpty(), "badge must not be published before a milestone")
    }

    @Test
    fun crossing_a_milestone_flushes_the_score_then_publishes_the_badge() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 0)))

        repeat(100) { vm.onCountClick() }

        // Score reached the server (flush ran) AND the badge was published after it.
        assertTrue(fake.incrementCalls.isNotEmpty(), "score must be flushed on milestone crossing")
        assertEquals(1, fake.writeDailyBadgeCalls.size)
        assertEquals("sprout", fake.writeDailyBadgeCalls.first().badgeKey)
    }

    @Test
    fun failed_badge_publish_is_retried_on_the_next_flush() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val vm = buildViewModel(fake)
        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 0)))

        // First milestone crossing publishes, but the write fails so the guard must not advance.
        fake.writeDailyBadgeResult = Result.failure(RuntimeException("offline"))
        repeat(100) { vm.onCountClick() }
        assertEquals(1, fake.writeDailyBadgeCalls.size)

        // Next flush retries the still-unpublished badge; this time it succeeds.
        fake.writeDailyBadgeResult = Result.success(Unit)
        vm.flushPendingSession()
        assertEquals(2, fake.writeDailyBadgeCalls.size)
        assertEquals("sprout", fake.writeDailyBadgeCalls.last().badgeKey)

        // Once published, a further flush does not re-write the same badge level.
        vm.flushPendingSession()
        assertEquals(2, fake.writeDailyBadgeCalls.size)
    }
}
