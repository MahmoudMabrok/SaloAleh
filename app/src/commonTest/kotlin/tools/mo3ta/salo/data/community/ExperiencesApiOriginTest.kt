package tools.mo3ta.salo.data.community

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import tools.mo3ta.salo.domain.ExperiencesException
import tools.mo3ta.salo.domain.ExperiencesFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExperiencesApiOriginTest {

    @Test
    fun sanitize_acceptsAnOriginAndDropsAPath() {
        assertEquals("https://salo.deno.dev", sanitizeOrigin("  https://salo.deno.dev/  "))
        assertEquals("http://10.0.2.2:8000", sanitizeOrigin("http://10.0.2.2:8000"))
        assertEquals("", sanitizeOrigin(""))
        assertEquals("", sanitizeOrigin("https://salo.deno.dev/experiences"))
        assertEquals("", sanitizeOrigin("javascript:alert(1)"))
    }

    @Test
    fun debugOverride_skipsRemoteConfig() = runTest {
        val reader = FakeReader("https://from-firebase.deno.dev")
        val origin = FirebaseExperiencesApiOrigin(
            remoteConfig = reader,
            debugOverride = "https://local.deno.dev/",
        )
        assertEquals("https://local.deno.dev", origin.current())
        assertEquals(0, reader.calls)
    }

    @Test
    fun remoteValue_isUsedWhenThereIsNoOverride() = runTest {
        val reader = FakeReader("https://from-firebase.deno.dev/")
        val origin = FirebaseExperiencesApiOrigin(reader)
        assertEquals("https://from-firebase.deno.dev", origin.current())
        assertEquals(listOf(EXPERIENCES_API_BASE_URL_KEY), reader.keys)
    }

    @Test
    fun blankRemoteValue_staysBlank() = runTest {
        val origin = FirebaseExperiencesApiOrigin(FakeReader("   "))
        assertEquals("", origin.current())
    }

    @Test
    fun remoteFailure_isAnOutage() = runTest {
        val origin = FirebaseExperiencesApiOrigin(FakeReader(error = IllegalStateException("offline")))
        val error = assertFailsWith<ExperiencesException> { origin.current() }
        assertEquals(ExperiencesFailure.Unavailable, error.failure)
    }

    @Test
    fun cancellation_isRethrown() = runTest {
        val origin = FirebaseExperiencesApiOrigin(FakeReader(error = CancellationException("stop")))
        assertFailsWith<CancellationException> { origin.current() }
    }

    @Test
    fun failedFetchWithNoCache_isAnOutage_andACachedOriginSurvives() {
        assertFailsWith<ExperiencesException> {
            originAfterFetch(fetchSucceeded = false, rawValue = "")
        }
        assertEquals(
            "https://cached.deno.dev",
            originAfterFetch(fetchSucceeded = false, rawValue = "https://cached.deno.dev"),
        )
        assertEquals("", originAfterFetch(fetchSucceeded = true, rawValue = "not a url"))
        assertTrue(EXPERIENCES_API_BASE_URL_KEY == "experiences_api_base_url")
    }

    private class FakeReader(
        private val value: String = "",
        private val error: Throwable? = null,
    ) : ExperiencesRemoteConfigReader {
        var calls = 0
        val keys = mutableListOf<String>()
        override suspend fun getString(key: String): String {
            calls++
            keys += key
            error?.let { throw it }
            return value
        }
    }
}
