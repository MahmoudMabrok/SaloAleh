package tools.mo3ta.salo.domain

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import tools.mo3ta.salo.data.country.CountryCodeProvider
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.data.time.NetworkTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MohamedLoversRepositoryUserActivityTest {

    private fun buildRepo(
        settings: MapSettings = MapSettings(),
    ): Pair<MohamedLoversRepository, FakeMohamedLoversFirebaseApi> {
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
            MohamedLoversSessionStore(settings),
            countryCode,
        )
        return repo to firebaseApi
    }

    private val today = LocalDate(2026, 8, 3)

    @Test
    fun startup_write_carries_the_running_version_and_code() = runTest {
        val (repo, fake) = buildRepo()

        val result = repo.writeUserActivity("uid-1", today, "3.9.2", 125)

        assertTrue(result.isSuccess)
        assertEquals(1, fake.userActivityCalls.size)
        val call = fake.userActivityCalls[0]
        assertEquals("uid-1", call.uid)
        assertEquals("2026-08-03", call.lastOpenDate)
        assertEquals("3.9.2", call.appVersion)
        assertEquals(125, call.appVersionCode)
    }

    @Test
    fun install_date_is_kept_while_version_is_refreshed() = runTest {
        val settings = MapSettings()
        val (first, _) = buildRepo(settings)
        first.writeUserActivity("uid-1", LocalDate(2026, 7, 1), "3.9.0", 120)

        // Same device, later launch on a newer build.
        val (second, fake) = buildRepo(settings)
        second.writeUserActivity("uid-1", today, "3.9.2", 125)

        val call = fake.userActivityCalls[0]
        assertEquals("2026-07-01", call.installDate)
        assertEquals("2026-08-03", call.lastOpenDate)
        assertEquals("3.9.2", call.appVersion)
        assertEquals(125, call.appVersionCode)
    }
}
