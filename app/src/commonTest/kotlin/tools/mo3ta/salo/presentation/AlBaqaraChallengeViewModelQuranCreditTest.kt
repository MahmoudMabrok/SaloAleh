package tools.mo3ta.salo.presentation

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.data.albaqara.AlBaqaraChallengeFirebaseClient
import tools.mo3ta.salo.data.albaqara.AlBaqaraChallengeStore
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.data.firebase.FirestoreMirror
import tools.mo3ta.salo.data.quran.QuranChallengeFirebaseClient
import tools.mo3ta.salo.data.quran.QuranChallengeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.ChallengeType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AlBaqaraChallengeViewModelQuranCreditTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val cairoZone = TimeZone.of("Africa/Cairo")
    private val today: LocalDate get() = Clock.System.todayIn(cairoZone)

    private val quranStore = QuranChallengeStore(MapSettings())
    private val albaqaraStore = AlBaqaraChallengeStore(MapSettings())
    private val challengeBadgeStore = ChallengeBadgeStore(MapSettings())

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val analytics = object : AnalyticsManager {
        override fun setUserId(uid: String) {}
        override fun logAction(name: String, params: Map<String, String>) {}
        override fun logView(name: String, params: Map<String, String>) {}
    }

    private val countryCode = object : CountryCodeProvider {
        override fun get() = "EG"
    }

    /** Fake Quran client: configurable baseline fetch, records every write's count. */
    private class FakeQuranClient(
        mirror: FirestoreMirror,
        analytics: AnalyticsManager,
        private val fetchResult: Result<Int?>,
    ) : QuranChallengeFirebaseClient(mirror, analytics) {
        val writtenCounts = mutableListOf<Int>()

        override fun isConfigured(): Boolean = true

        override suspend fun fetchUserCount(dateKey: String, uid: String): Result<Int?> = fetchResult

        override suspend fun writeUserDay(
            dateKey: String,
            uid: String,
            count: Int,
            countryCode: String,
            nickname: String,
            streak: Int,
        ): Result<Unit> {
            writtenCounts += count
            return Result.success(Unit)
        }
    }

    private fun buildViewModel(quranClient: QuranChallengeFirebaseClient): AlBaqaraChallengeViewModel {
        val mirror = FirestoreMirror()
        return AlBaqaraChallengeViewModel(
            store = albaqaraStore,
            firebaseClient = AlBaqaraChallengeFirebaseClient(mirror, analytics),
            sessionStore = MohamedLoversSessionStore(MapSettings()),
            countryCodeProvider = countryCode,
            quranStore = quranStore,
            quranFirebaseClient = quranClient,
            challengeBadgeStore = challengeBadgeStore,
        )
    }

    private fun fakeQuran(fetchResult: Result<Int?>) =
        FakeQuranClient(FirestoreMirror(), analytics, fetchResult)

    // The discriminating case: a credit must fetch the remote baseline and add to *it*,
    // never overwrite a higher remote total with a stale local one.
    @Test
    fun credit_fetchesRemoteBaselineBeforeWriting() = runTest {
        val client = fakeQuran(Result.success(30))
        val vm = buildViewModel(client)

        vm.creditQuranPages()

        assertEquals(listOf(78), client.writtenCounts)
        assertEquals(78, quranStore.todayCount(today))
    }

    // The second data-loss case: a failed baseline fetch must issue no write at all.
    @Test
    fun credit_failedBaselineFetch_doesNotWrite() = runTest {
        val client = fakeQuran(Result.failure(RuntimeException("network down")))
        val vm = buildViewModel(client)

        vm.creditQuranPages()

        assertTrue(client.writtenCounts.isEmpty())
        // The 48 pages still land locally to reconcile on the next Quran-screen visit.
        assertEquals(48, quranStore.todayCount(today))
    }

    @Test
    fun credit_addsExactly48Pages() = runTest {
        val client = fakeQuran(Result.success(null))
        val vm = buildViewModel(client)

        vm.creditQuranPages()

        assertEquals(48, quranStore.todayCount(today))
        assertEquals(listOf(48), client.writtenCounts)
    }

    @Test
    fun credit_recordsWinAndKeepsStreakAlive() = runTest {
        val client = fakeQuran(Result.success(null))
        val vm = buildViewModel(client)

        vm.creditQuranPages()

        assertEquals(1, challengeBadgeStore.getWinCount(ChallengeType.QURAN))
        assertEquals(1, challengeBadgeStore.getCurrentStreak(ChallengeType.QURAN, today))
    }

    @Test
    fun dismiss_leavesQuranCountUntouched() = runTest {
        val client = fakeQuran(Result.success(null))
        val vm = buildViewModel(client)

        vm.onReadTap()
        vm.onQuranCreditDismissed()

        assertEquals(0, quranStore.todayCount(today))
        assertTrue(client.writtenCounts.isEmpty())
        assertTrue(!vm.state.value.showQuranCreditDialog)
    }

    @Test
    fun undoAfterCredit_reducesBaqaraNotQuran() = runTest {
        val client = fakeQuran(Result.success(null))
        val vm = buildViewModel(client)

        vm.onReadTap()          // Baqara -> 1
        vm.creditQuranPages()   // Quran -> 48
        vm.onUndoTap()          // Baqara -> 0

        assertEquals(0, albaqaraStore.todayCount(today))
        assertEquals(48, quranStore.todayCount(today))
    }

    @Test
    fun twoReadingsCredited_yield96AndRefreshCountBetweenDialogs() = runTest {
        val client = fakeQuran(Result.success(null))
        val vm = buildViewModel(client)

        vm.onReadTap()
        assertEquals(0, vm.state.value.quranTodayCount) // first dialog opens against 0
        vm.creditQuranPages()
        assertEquals(48, vm.state.value.quranTodayCount) // refreshed after the first credit

        vm.onReadTap()
        assertEquals(48, vm.state.value.quranTodayCount) // second dialog opens against 48, not 0
        vm.creditQuranPages()

        assertEquals(96, quranStore.todayCount(today))
        assertEquals(listOf(48, 96), client.writtenCounts)
    }

    @Test
    fun twoCreditsOneDay_awardOnlyOneBadge() = runTest {
        val client = fakeQuran(Result.success(null))
        val vm = buildViewModel(client)

        vm.creditQuranPages()
        vm.creditQuranPages()

        assertEquals(1, challengeBadgeStore.getWinCount(ChallengeType.QURAN))
    }
}
