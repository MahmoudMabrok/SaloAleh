package tools.mo3ta.salo.presentation

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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
import tools.mo3ta.salo.domain.MOHAMED_LOVERS_DAILY_PUSH_CAP
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow
import tools.mo3ta.salo.domain.MohamedLoversPlayer
import tools.mo3ta.salo.domain.MohamedLoversRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the per-Cairo-day competition push cap and the daily-badge reconciliation that runs on
 * every self-player snapshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MohamedLoversViewModelDailyCapTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val cairo = TimeZone.of("Africa/Cairo")
    private val today get() = Clock.System.todayIn(cairo)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val vm: MohamedLoversViewModel,
        val fake: FakeMohamedLoversFirebaseApi,
        val sessionStore: MohamedLoversSessionStore,
        val dailyGoalStore: DailyGoalStore,
    )

    private fun build(
        fake: FakeMohamedLoversFirebaseApi = FakeMohamedLoversFirebaseApi(),
        sessionStore: MohamedLoversSessionStore = MohamedLoversSessionStore(MapSettings()),
        dailyGoalStore: DailyGoalStore = DailyGoalStore(MapSettings()),
    ): Fixture {
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
        val premiumStore = PremiumStore(MapSettings())
        val repo = MohamedLoversRepository(fake, networkTime, sessionStore, countryCode, premiumStore)
        val vm = MohamedLoversViewModel(
            repository = repo,
            engagementStore = EngagementStore(MapSettings()),
            hadithStore = DailyHadithStore(MapSettings()),
            dailyGoalStore = dailyGoalStore,
            roundStreakStore = RoundStreakStore(MapSettings()),
            settingsStore = NotificationSettingsStore(MapSettings()),
            sessionStore = sessionStore,
            premiumStore = premiumStore,
            heartStore = HeartStore(MapSettings()),
            startTimers = false,
        )
        return Fixture(vm, fake, sessionStore, dailyGoalStore)
    }

    // --- Push cap ---

    @Test
    fun a_first_push_larger_than_the_cap_is_clamped_to_the_cap() = runTest {
        val f = build()
        // A single oversized external batch, e.g. an extension sync on a fresh install.
        f.vm.applyExtensionScore("2026-05-15", 40_000)

        assertEquals(1, f.fake.incrementCalls.size)
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, f.fake.incrementCalls.single().delta)
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, f.sessionStore.dailyPushUsed(today))
        assertEquals(0, f.sessionStore.dailyPushRemaining(today))
    }

    @Test
    fun the_local_score_is_reset_to_match_the_remote_one_after_a_capped_push() = runTest {
        val f = build()
        f.vm.applyExtensionScore("2026-05-15", 40_000)

        // Nothing is left queued: the excess is discarded, not retried tomorrow.
        assertEquals(0, f.vm.state.value.sessionClicks)
        // Today's local count is pulled back to the cap so it agrees with what was published.
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, f.dailyGoalStore.todayProgress(today))
        assertEquals(MOHAMED_LOVERS_DAILY_PUSH_CAP, f.vm.state.value.todayCount)
        assertEquals(15_000, f.vm.state.value.dailyCapDiscarded)
    }

    @Test
    fun a_second_push_after_the_cap_is_spent_reaches_firebase_as_nothing() = runTest {
        val f = build()
        f.vm.applyExtensionScore("2026-05-15", MOHAMED_LOVERS_DAILY_PUSH_CAP)
        val callsAfterFirst = f.fake.incrementCalls.size

        f.vm.onCountClick()
        f.vm.flushPendingSession()

        assertEquals(callsAfterFirst, f.fake.incrementCalls.size, "no write once the day's cap is spent")
        assertEquals(0, f.vm.state.value.sessionClicks)
    }

    @Test
    fun the_published_today_count_never_exceeds_the_cap() = runTest {
        val f = build()
        f.vm.applyExtensionScore("2026-05-15", 40_000)

        assertTrue(f.fake.incrementCalls.all { it.todayCount <= MOHAMED_LOVERS_DAILY_PUSH_CAP })
    }

    @Test
    fun the_manual_allowance_shrinks_to_what_the_push_cap_still_permits() = runTest {
        val f = build()
        // Burn all but 400 of the day's push allowance through the extension path.
        f.vm.applyExtensionScore("2026-05-15", MOHAMED_LOVERS_DAILY_PUSH_CAP - 400)
        f.vm.showManualSalawatSheet()

        assertEquals(400, f.vm.state.value.manualRemaining)
    }

    @Test
    fun a_server_snapshot_teaches_the_ledger_what_the_day_already_holds() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val f = build(fake = fake)

        // Fresh install: the local ledger is empty, but the server already recorded 24k for today
        // (round total 30k against last night's 6k baseline).
        selfPlayerFlow.emit(
            Result.success(
                MohamedLoversPlayer(uid = "fake-uid", totalCount = 30_000, yesterdayTotalScore = 6_000)
            )
        )

        assertEquals(24_000, f.sessionStore.dailyPushUsed(today))
        assertEquals(1_000, f.sessionStore.dailyPushRemaining(today))
        // The baseline is persisted with its fetch time so it survives the app being closed.
        assertEquals(6_000, f.sessionStore.dailyBaseline(today))
        assertTrue(f.sessionStore.dailyBaselineFetchedAt(today) > 0L)

        // Only the remaining 1k of a 5k batch can now be pushed.
        f.vm.applyExtensionScore("2026-05-15", 5_000)
        assertEquals(1_000, fake.incrementCalls.single().delta)
    }

    // --- Daily badge reconciliation ---

    @Test
    fun a_badge_above_the_local_count_raises_it_and_warns() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val f = build(fake = fake)

        // Local storage lost today's progress; the server still holds the 5,000-tap "crown" badge.
        selfPlayerFlow.emit(
            Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 5_000, dailyBadge = "crown"))
        )

        assertEquals(5_000, f.dailyGoalStore.todayProgress(today))
        assertEquals(5_000, f.vm.state.value.todayCount)
        assertEquals(5_000, f.vm.state.value.badgeAdjustedTo)
        assertEquals("crown", f.vm.state.value.currentDailyBadge)
    }

    @Test
    fun a_badge_at_or_below_the_local_count_changes_nothing() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val goalStore = DailyGoalStore(MapSettings())
        goalStore.recordTap(today, 6_000)
        val f = build(fake = fake, dailyGoalStore = goalStore)

        selfPlayerFlow.emit(
            Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 6_000, dailyBadge = "crown"))
        )

        assertEquals(6_000, f.dailyGoalStore.todayProgress(today))
        assertNull(f.vm.state.value.badgeAdjustedTo)
    }

    @Test
    fun no_badge_on_the_player_node_is_a_no_op() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val f = build(fake = fake)

        selfPlayerFlow.emit(Result.success(MohamedLoversPlayer(uid = "fake-uid", totalCount = 900)))

        assertEquals(0, f.dailyGoalStore.todayProgress(today))
        assertNull(f.vm.state.value.badgeAdjustedTo)
    }

    @Test
    fun the_badge_warning_fires_only_once() = runTest {
        val selfPlayerFlow = MutableSharedFlow<Result<MohamedLoversPlayer?>>()
        val fake = FakeMohamedLoversFirebaseApi()
        fake.selfPlayerFlow = selfPlayerFlow
        val f = build(fake = fake)
        val player = MohamedLoversPlayer(uid = "fake-uid", totalCount = 5_000, dailyBadge = "crown")

        selfPlayerFlow.emit(Result.success(player))
        f.vm.dismissBadgeAdjustment()
        // A second snapshot of the same state must not re-raise the warning.
        selfPlayerFlow.emit(Result.success(player))

        assertNull(f.vm.state.value.badgeAdjustedTo)
    }
}
