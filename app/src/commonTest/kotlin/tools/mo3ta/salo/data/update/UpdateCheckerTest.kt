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
    private val currentVersionCode = 125

    private fun checker(
        api: FakeMohamedLoversFirebaseApi,
        store: UpdatePromptStore = UpdatePromptStore(MapSettings()),
    ) = UpdateChecker(api, store)

    private fun apiWith(
        latest: String? = null,
        minSupportedCode: Int? = null,
    ): FakeMohamedLoversFirebaseApi =
        FakeMohamedLoversFirebaseApi().apply {
            appConfigResult = Result.success(
                if (latest == null && minSupportedCode == null) null
                else AppUpdateConfig(latestVersion = latest ?: "", minSupportedVersionCode = minSupportedCode),
            )
        }

    private fun apiWithLatest(version: String?): FakeMohamedLoversFirebaseApi =
        apiWith(latest = version)

    @Test
    fun prompts_when_remote_version_is_newer() = runTest {
        val prompt = checker(apiWithLatest("3.9.2")).check(currentVersion, currentVersionCode)
        assertEquals("3.9.2", prompt?.version)
        assertFalse(prompt!!.forced)
    }

    @Test
    fun no_prompt_when_remote_matches_current() = runTest {
        assertNull(checker(apiWithLatest("3.9.1")).check(currentVersion, currentVersionCode))
    }

    @Test
    fun no_prompt_when_remote_is_older() = runTest {
        assertNull(checker(apiWithLatest("3.9.0")).check(currentVersion, currentVersionCode))
    }

    @Test
    fun no_prompt_when_config_absent() = runTest {
        assertNull(checker(apiWithLatest(null)).check(currentVersion, currentVersionCode))
    }

    @Test
    fun no_prompt_when_remote_version_blank() = runTest {
        assertNull(checker(apiWithLatest("   ")).check(currentVersion, currentVersionCode))
    }

    @Test
    fun no_prompt_when_config_read_fails() = runTest {
        val api = FakeMohamedLoversFirebaseApi().apply {
            appConfigResult = Result.failure(RuntimeException("offline"))
        }
        assertNull(checker(api).check(currentVersion, currentVersionCode))
    }

    @Test
    fun no_prompt_after_version_dismissed() = runTest {
        val store = UpdatePromptStore(MapSettings())
        store.markDismissed("3.9.2")
        assertNull(checker(apiWithLatest("3.9.2"), store).check(currentVersion, currentVersionCode))
    }

    @Test
    fun prompts_for_newer_version_even_after_older_one_dismissed() = runTest {
        val store = UpdatePromptStore(MapSettings())
        store.markDismissed("3.9.2")
        val prompt = checker(apiWithLatest("4.0.0"), store).check(currentVersion, currentVersionCode)
        assertEquals("4.0.0", prompt?.version)
        assertFalse(prompt!!.forced)
    }

    @Test
    fun markDismissed_suppresses_subsequent_check() = runTest {
        val store = UpdatePromptStore(MapSettings())
        val checker = checker(apiWithLatest("3.9.2"), store)
        assertEquals("3.9.2", checker.check(currentVersion, currentVersionCode)?.version)
        checker.markDismissed("3.9.2")
        assertNull(checker.check(currentVersion, currentVersionCode))
    }

    @Test
    fun forces_update_when_min_supported_code_is_higher() = runTest {
        // Shows the newest available versionName while forcing on the code comparison.
        val prompt = checker(apiWith(latest = "3.9.2", minSupportedCode = 126)).check(currentVersion, currentVersionCode)
        assertTrue(prompt!!.forced)
        assertEquals("3.9.2", prompt.version)
    }

    @Test
    fun no_force_when_min_supported_code_equals_current() = runTest {
        assertNull(checker(apiWith(minSupportedCode = 125)).check(currentVersion, currentVersionCode))
    }

    @Test
    fun no_force_when_min_supported_code_is_lower() = runTest {
        assertNull(checker(apiWith(minSupportedCode = 124)).check(currentVersion, currentVersionCode))
    }

    @Test
    fun no_force_when_min_supported_code_absent_or_zero() = runTest {
        assertNull(checker(apiWith(minSupportedCode = 0)).check(currentVersion, currentVersionCode))
    }

    @Test
    fun forced_update_ignores_prior_dismissal() = runTest {
        val store = UpdatePromptStore(MapSettings())
        store.markDismissed("4.0.0")
        val prompt = checker(apiWith(latest = "4.0.0", minSupportedCode = 126), store).check(currentVersion, currentVersionCode)
        assertTrue(prompt!!.forced)
    }

    @Test
    fun forced_update_wins_over_soft_update() = runTest {
        // A higher minSupportedVersionCode forces even when latestVersion would only prompt softly.
        val prompt = checker(apiWith(latest = "4.1.0", minSupportedCode = 126)).check(currentVersion, currentVersionCode)
        assertTrue(prompt!!.forced)
        assertEquals("4.1.0", prompt.version)
    }

    @Test
    fun soft_prompt_when_below_latest_but_at_min_supported_code() = runTest {
        val prompt = checker(apiWith(latest = "4.1.0", minSupportedCode = 125)).check(currentVersion, currentVersionCode)
        assertEquals("4.1.0", prompt?.version)
        assertFalse(prompt!!.forced)
    }
}
