package tools.mo3ta.salo.data.community

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import tools.mo3ta.salo.domain.CommunityExperience
import tools.mo3ta.salo.domain.ExperienceCategory
import tools.mo3ta.salo.domain.ExperienceDraft
import tools.mo3ta.salo.domain.ExperienceLimits
import tools.mo3ta.salo.domain.ExperiencesException
import tools.mo3ta.salo.domain.ExperiencesFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperiencesRepositoryTest {

    @Test
    fun endpoint_joinsOriginAndRejectsBlank() {
        assertNull(experiencesEndpoint("   "))
        assertNull(experiencesEndpoint("https://community.deno.dev/extra"))
        assertEquals(
            "https://community.deno.dev/experiences",
            experiencesEndpoint("https://community.deno.dev/"),
        )
    }

    @Test
    fun missingOrigin_doesNotCallRemote() = runTest {
        val remote = FakeRemote()
        val repository = repository(remote, origin = "")
        val loaded = repository.loadPublished()
        val submitted = repository.submit(draft("تجربة"))
        assertEquals(ExperiencesFailure.NotConfigured, loaded.failure())
        assertEquals(ExperiencesFailure.NotConfigured, submitted.failure())
        assertEquals(0, remote.fetchCount)
        assertTrue(remote.submitted.isEmpty())
    }

    @Test
    fun originOutage_doesNotCallRemote() = runTest {
        val remote = FakeRemote()
        val repository = ExperiencesRepository(
            remote,
            FakeOrigin(error = ExperiencesException(ExperiencesFailure.Unavailable)),
        )
        assertEquals(ExperiencesFailure.Unavailable, repository.loadPublished().failure())
        assertEquals(0, remote.fetchCount)
    }

    @Test
    fun invalidDraft_isRejectedBeforeTheNetwork() = runTest {
        val remote = FakeRemote()
        val repository = repository(remote)
        val result = repository.submit(draft("  <b>لا</b>  "))
        assertEquals(ExperiencesFailure.Rejected, result.failure())
        assertTrue(remote.submitted.isEmpty())
    }

    @Test
    fun submit_sendsTheTrimmedDraftWithFallbackName() = runTest {
        val remote = FakeRemote()
        val repository = repository(remote)
        val result = repository.submit(ExperienceDraft("  ", "  تجربة قصيرة  ", ExperienceCategory.DHIKR))
        assertTrue(result.isSuccess)
        assertEquals(
            ExperienceDraft(ExperienceLimits.ANONYMOUS_DISPLAY_NAME, "تجربة قصيرة", ExperienceCategory.DHIKR),
            remote.submitted.single(),
        )
        assertEquals(listOf("https://community.deno.dev"), remote.requested)
    }

    @Test
    fun load_returnsPublishedRowsAndMapsOutages() = runTest {
        val remote = FakeRemote()
        val row = CommunityExperience("1", "هدى", "نص", ExperienceCategory.APP, "2026-06-15T10:00:00Z")
        remote.published = listOf(row)
        val repository = repository(remote)
        assertEquals(listOf(row), repository.loadPublished().getOrNull())

        remote.fetchError = IllegalStateException("down")
        assertEquals(ExperiencesFailure.Unavailable, repository.loadPublished().failure())

        remote.fetchError = ExperiencesException(ExperiencesFailure.RateLimited)
        assertEquals(ExperiencesFailure.RateLimited, repository.loadPublished().failure())
    }

    @Test
    fun cancellation_isRethrown() = runTest {
        val remote = FakeRemote()
        remote.fetchError = CancellationException("stop")
        assertFailsWith<CancellationException> {
            repository(remote).loadPublished()
        }
    }

    private fun draft(body: String) = ExperienceDraft("هدى", body, ExperienceCategory.APP)

    private fun repository(remote: FakeRemote, origin: String = "https://community.deno.dev") =
        ExperiencesRepository(remote, FakeOrigin(origin))

    private fun Result<*>.failure(): ExperiencesFailure {
        val error = exceptionOrNull()
        assertIs<ExperiencesException>(error)
        return error.failure
    }

    private class FakeOrigin(
        private val value: String = "",
        private val error: Throwable? = null,
    ) : ExperiencesApiOrigin {
        override suspend fun current(): String {
            error?.let { throw it }
            return value
        }
    }

    private class FakeRemote : ExperiencesRemote {
        var published: List<CommunityExperience> = emptyList()
        var fetchError: Throwable? = null
        var fetchCount = 0
        val submitted = mutableListOf<ExperienceDraft>()
        val requested = mutableListOf<String>()

        override suspend fun fetchPublished(baseUrl: String): List<CommunityExperience> {
            fetchCount++
            requested += baseUrl
            fetchError?.let { throw it }
            return published
        }

        override suspend fun submit(baseUrl: String, draft: ExperienceDraft) {
            requested += baseUrl
            submitted += draft
        }
    }
}
