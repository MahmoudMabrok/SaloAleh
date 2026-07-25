package tools.mo3ta.salo.data.update

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import tools.mo3ta.salo.domain.AppUpdateConfig
import tools.mo3ta.salo.domain.FakeMohamedLoversFirebaseApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {

    private val currentVersion = "3.9.1"

    private fun checker(
        api: FakeMohamedLoversFirebaseApi,
        store: UpdatePromptStore = UpdatePromptStore(MapSettings()),
    ) = UpdateChecker(api, store)

    private fun apiWith(
        latest: String? = null,
        minSupported: String? = null,
    ): FakeMohamedLoversFirebaseApi =
        FakeMohamedLoversFirebaseApi().apply {
            appConfigResult = Result.success(
                if (latest == null && minSupported == null) null
                else AppUpdateConfig(latestVersion = latest ?: "", minSupportedVersion = minSupported),
            )
        }

    private fun apiWithLatest(version: String?): FakeMohamedLoversFirebaseApi =
        apiWith(latest = version)

    @Test
    fun prompts_when_remote_version_is_newer() = runTest {
        val prompt = checker(apiWithLatest("3.9.2")).check(currentVersion)
        assertEquals("3.9.2", prompt?.version)
        assertFalse(prompt!!.forced)
    }

    @Test
    fun no_prompt_when_remote_matches_current() = runTest {
        assertNull(checker(apiWithLatest("3.9.1")).check(currentVersion))
    }

    @Test
    fun no_prompt_when_remote_is_older() = runTest {
        assertNull(checker(apiWithLatest("3.9.0")).check(currentVersion))
    }

    @Test
    fun no_prompt_when_config_absent() = runTest {
        assertNull(checker(apiWithLatest(null)).check(currentVersion))
    }

    @Test
    fun no_prompt_when_remote_version_blank() = runTest {
        assertNull(checker(apiWithLatest("   ")).check(currentVersion))
    }

    @Test
    fun no_prompt_when_config_read_fails() = runTest {
        val api = FakeMohamedLoversFirebaseApi().apply {
            appConfigResult = Result.failure(RuntimeException("offline"))
        }
        assertNull(checker(api).check(currentVersion))
    }

    @Test
    fun no_prompt_after_version_dismissed() = runTest {
        val store = UpdatePromptStore(MapSettings())
        store.markDismissed("3.9.2")
        assertNull(checker(apiWithLatest("3.9.2"), store).check(currentVersion))
    }

    @Test
    fun prompts_for_newer_version_even_after_older_one_dismissed() = runTest {
        val store = UpdatePromptStore(MapSettings())
        store.markDismissed("3.9.2")
        val prompt = checker(apiWithLatest("4.0.0"), store).check(currentVersion)
        assertEquals("4.0.0", prompt?.version)
        assertFalse(prompt!!.forced)
    }

    @Test
    fun markDismissed_suppresses_subsequent_check() = runTest {
        val store = UpdatePromptStore(MapSettings())
        val checker = checker(apiWithLatest("3.9.2"), store)
        assertEquals("3.9.2", checker.check(currentVersion)?.version)
        checker.markDismissed("3.9.2")
        assertNull(checker.check(currentVersion))
    }

    @Test
    fun forces_update_when_min_supported_is_newer() = runTest {
        val prompt = checker(apiWith(minSupported = "3.9.2")).check(currentVersion)
        assertEquals("3.9.2", prompt?.version)
        assertTrue(prompt!!.forced)
    }

    @Test
    fun no_force_when_min_supported_equals_current() = runTest {
        assertNull(checker(apiWith(minSupported = "3.9.1")).check(currentVersion))
    }

    @Test
    fun no_force_when_min_supported_is_older() = runTest {
        assertNull(checker(apiWith(minSupported = "3.9.0")).check(currentVersion))
    }

    @Test
    fun no_force_when_min_supported_blank() = runTest {
        assertNull(checker(apiWith(minSupported = "   ")).check(currentVersion))
    }

    @Test
    fun forced_update_ignores_prior_dismissal() = runTest {
        val store = UpdatePromptStore(MapSettings())
        store.markDismissed("4.0.0")
        val prompt = checker(apiWith(minSupported = "4.0.0"), store).check(currentVersion)
        assertEquals("4.0.0", prompt?.version)
        assertTrue(prompt!!.forced)
    }

    @Test
    fun forced_update_wins_over_soft_update() = runTest {
        // minSupported forces even when latestVersion would only prompt softly.
        val prompt = checker(apiWith(latest = "4.1.0", minSupported = "4.0.0")).check(currentVersion)
        assertEquals("4.0.0", prompt?.version)
        assertTrue(prompt!!.forced)
    }

    @Test
    fun soft_prompt_when_below_latest_but_at_or_above_min_supported() = runTest {
        val prompt = checker(apiWith(latest = "4.1.0", minSupported = "3.9.0")).check(currentVersion)
        assertEquals("4.1.0", prompt?.version)
        assertFalse(prompt!!.forced)
    }
}
