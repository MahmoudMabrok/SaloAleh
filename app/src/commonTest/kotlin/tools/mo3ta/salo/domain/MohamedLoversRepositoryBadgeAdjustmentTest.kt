package tools.mo3ta.salo.domain

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MohamedLoversRepositoryBadgeAdjustmentTest {

    private fun buildRepo(
        firebaseApi: FakeMohamedLoversFirebaseApi = FakeMohamedLoversFirebaseApi(),
    ): Pair<MohamedLoversRepository, FakeMohamedLoversFirebaseApi> {
        val networkTime = object : NetworkTimeProvider {
            override fun prime() {}
            override fun getCompetitionWindow() = MohamedLoversCompetitionWindow()
        }
        val countryCode = object : CountryCodeProvider {
            override fun get() = "EG"
        }
        val repo = MohamedLoversRepository(
            firebaseApi,
            networkTime,
            MohamedLoversSessionStore(MapSettings()),
            countryCode,
        )
        return repo to firebaseApi
    }

    // 2026-05-12T09:07:00Z is 12:07 in Cairo (UTC+3 in May).
    private val at = Instant.parse("2026-05-12T09:07:00Z")

    @Test
    fun the_adjustment_is_recorded_with_its_case_time_progress_and_badge() = runTest {
        val (repo, fake) = buildRepo()

        val result = repo.logDailyBadgeAdjustment(
            case = DailyBadgeAdjustmentLog.CASE_BADGE_ABOVE_PROGRESS,
            at = at,
            progress = 120,
            badge = DailyBadge.CROWN,
        )

        assertTrue(result.isSuccess)
        val call = fake.badgeAdjustmentCalls.single()
        assertEquals("fake-uid", call.uid)
        assertEquals("badge_above_progress", call.adjustment.case)
        assertEquals(at.toEpochMilliseconds(), call.adjustment.atMs)
        // The short count as it was found, before it was raised.
        assertEquals(120, call.adjustment.progress)
        assertEquals("crown", call.adjustment.badgeKey)
        assertEquals(5_000, call.adjustment.badgeValue)
    }

    @Test
    fun the_entry_is_keyed_by_the_cairo_minute() = runTest {
        val (repo, fake) = buildRepo()

        repo.logDailyBadgeAdjustment(
            case = DailyBadgeAdjustmentLog.CASE_BADGE_ABOVE_PROGRESS,
            at = at,
            progress = 0,
            badge = DailyBadge.SPROUT,
        )

        assertEquals("2026-05-12 12;07", fake.badgeAdjustmentCalls.single().timeKey)
    }

    @Test
    fun an_auth_failure_surfaces_without_writing() = runTest {
        val fake = FakeMohamedLoversFirebaseApi()
        fake.signInResult = Result.failure(RuntimeException("auth failed"))
        val (repo, _) = buildRepo(firebaseApi = fake)

        val result = repo.logDailyBadgeAdjustment(
            case = DailyBadgeAdjustmentLog.CASE_BADGE_ABOVE_PROGRESS,
            at = at,
            progress = 10,
            badge = DailyBadge.DOME,
        )

        assertTrue(result.isFailure)
        assertTrue(fake.badgeAdjustmentCalls.isEmpty())
    }

    @Test
    fun a_failed_write_is_reported_rather_than_thrown() = runTest {
        val fake = FakeMohamedLoversFirebaseApi()
        fake.badgeAdjustmentResult = Result.failure(RuntimeException("offline"))
        val (repo, _) = buildRepo(firebaseApi = fake)

        val result = repo.logDailyBadgeAdjustment(
            case = DailyBadgeAdjustmentLog.CASE_BADGE_ABOVE_PROGRESS,
            at = at,
            progress = 10,
            badge = DailyBadge.DOME,
        )

        assertTrue(result.isFailure)
    }
}
