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
    fun large_batch_is_appended_under_its_cairo_minute() = runTest {
        val (repo, fake) = buildRepo()

        val result = repo.appendExternalLog("R1", 5_000, at)

        assertTrue(result.isSuccess)
        assertEquals(1, fake.externalLogCalls.size)
        assertEquals("R1", fake.externalLogCalls[0].roundKey)
        assertEquals("2026-08-02 12;05", fake.externalLogCalls[0].timeKey)
        assertEquals(5_000, fake.externalLogCalls[0].count)
    }

    @Test
    fun batch_at_or_below_threshold_is_not_logged() = runTest {
        val (repo, fake) = buildRepo()

        assertTrue(repo.appendExternalLog("R1", 2_000, at).isSuccess)
        assertTrue(repo.appendExternalLog("R1", 1, at).isSuccess)

        assertTrue(fake.externalLogCalls.isEmpty())
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
