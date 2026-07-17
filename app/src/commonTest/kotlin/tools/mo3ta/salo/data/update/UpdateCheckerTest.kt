package tools.mo3ta.salo.data.update

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import tools.mo3ta.salo.domain.AppUpdateConfig
import tools.mo3ta.salo.domain.FakeMohamedLoversFirebaseApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateCheckerTest {

    private val currentVersion = "3.9.1"

    private fun checker(
        api: FakeMohamedLoversFirebaseApi,
        store: UpdatePromptStore = UpdatePromptStore(MapSettings()),
    ) = UpdateChecker(api, store)

    private fun apiWithLatest(version: String?): FakeMohamedLoversFirebaseApi =
        FakeMohamedLoversFirebaseApi().apply {
            appConfigResult = Result.success(version?.let { AppUpdateConfig(it) })
        }

    @Test
    fun prompts_when_remote_version_is_newer() = runTest {
        val version = checker(apiWithLatest("3.9.2")).check(currentVersion)
        assertEquals("3.9.2", version)
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
        val version = checker(apiWithLatest("4.0.0"), store).check(currentVersion)
        assertEquals("4.0.0", version)
    }

    @Test
    fun markDismissed_suppresses_subsequent_check() = runTest {
        val store = UpdatePromptStore(MapSettings())
        val checker = checker(apiWithLatest("3.9.2"), store)
        assertEquals("3.9.2", checker.check(currentVersion))
        checker.markDismissed("3.9.2")
        assertNull(checker.check(currentVersion))
    }
}
