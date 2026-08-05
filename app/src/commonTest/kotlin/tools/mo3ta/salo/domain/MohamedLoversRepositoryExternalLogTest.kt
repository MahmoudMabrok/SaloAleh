package tools.mo3ta.salo.domain

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MohamedLoversRepositoryExternalLogTest {

    private fun buildRepo(): Pair<MohamedLoversRepository, FakeMohamedLoversFirebaseApi> {
        val firebaseApi = FakeMohamedLoversFirebaseApi()
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
            PremiumStore(MapSettings()),
        )
        return repo to firebaseApi
    }

    private val at = LocalDateTime(2026, 8, 2, 12, 5).toInstant(TimeZone.of("Africa/Cairo"))

    @Test
    fun batch_is_appended_under_its_cairo_minute() = runTest {
        val (repo, fake) = buildRepo()

        val result = repo.appendExternalLog("R1", 5_000, at)

        assertTrue(result.isSuccess)
        assertEquals(1, fake.externalLogCalls.size)
        assertEquals("R1", fake.externalLogCalls[0].roundKey)
        assertEquals("2026-08-02 12;05", fake.externalLogCalls[0].timeKey)
        assertEquals(5_000, fake.externalLogCalls[0].count)
    }

    @Test
    fun every_push_is_logged_regardless_of_size() = runTest {
        val (repo, fake) = buildRepo()

        assertTrue(repo.appendExternalLog("R1", 2_000, at).isSuccess)
        assertTrue(repo.appendExternalLog("R1", 1, at).isSuccess)

        assertEquals(listOf(2_000, 1), fake.externalLogCalls.map { it.count })
    }

    @Test
    fun zero_push_is_a_no_op() = runTest {
        val (repo, fake) = buildRepo()

        assertTrue(repo.appendExternalLog("R1", 0, at).isSuccess)

        assertTrue(fake.externalLogCalls.isEmpty())
    }

    @Test
    fun only_cap_consuming_pushes_carry_the_daily_ledger_key() = runTest {
        val (repo, fake) = buildRepo()

        repo.appendExternalLog("R1", 5_000, at, countsTowardDailyCap = true)
        repo.appendExternalLog("R1", 700, at)

        assertEquals("2026-08-02", fake.externalLogCalls[0].dayKey)
        assertNull(fake.externalLogCalls[1].dayKey)
    }

    @Test
    fun correction_is_logged_as_a_negative_ledger_entry() = runTest {
        val (repo, fake) = buildRepo()

        repo.appendExternalLog("R1", -3_000, at, countsTowardDailyCap = true)

        assertEquals(-3_000, fake.externalLogCalls[0].count)
        assertEquals("2026-08-02", fake.externalLogCalls[0].dayKey)
    }

    @Test
    fun used_today_reads_the_ledger_for_the_cairo_day() = runTest {
        val (repo, fake) = buildRepo()
        fake.externalDailyUsed["2026-08-02"] = 6_500

        assertEquals(6_500, repo.fetchExternalUsedToday("R1", at).getOrNull())
    }

    @Test
    fun used_today_is_zero_when_the_ledger_is_absent() = runTest {
        val (repo, _) = buildRepo()

        assertEquals(0, repo.fetchExternalUsedToday("R1", at).getOrNull())
    }

    @Test
    fun auth_failure_surfaces_and_writes_nothing() = runTest {
        val (repo, fake) = buildRepo()
        fake.signInResult = Result.failure(RuntimeException("auth failed"))

        val result = repo.appendExternalLog("R1", 5_000, at)

        assertTrue(result.isFailure)
        assertTrue(fake.externalLogCalls.isEmpty())
    }
}
