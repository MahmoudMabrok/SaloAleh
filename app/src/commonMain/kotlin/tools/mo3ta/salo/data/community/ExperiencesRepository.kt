package tools.mo3ta.salo.data.community

import kotlinx.coroutines.CancellationException
import tools.mo3ta.salo.domain.CommunityExperience
import tools.mo3ta.salo.domain.ExperienceDraft
import tools.mo3ta.salo.domain.ExperiencesException
import tools.mo3ta.salo.domain.ExperiencesFailure
import tools.mo3ta.salo.domain.normalizeExperienceDraft
import tools.mo3ta.salo.domain.validateExperienceDraft

interface ExperiencesRemote {
    suspend fun fetchPublished(baseUrl: String): List<CommunityExperience>
    suspend fun submit(baseUrl: String, draft: ExperienceDraft)
}

interface CommunityExperiences {
    suspend fun loadPublished(): Result<List<CommunityExperience>>
    suspend fun submit(draft: ExperienceDraft): Result<Unit>
}

class ExperiencesRepository(
    private val remote: ExperiencesRemote,
    private val origin: ExperiencesApiOrigin,
) : CommunityExperiences {

    override suspend fun loadPublished(): Result<List<CommunityExperience>> = guard {
        remote.fetchPublished(requireOrigin())
    }

    override suspend fun submit(draft: ExperienceDraft): Result<Unit> {
        if (validateExperienceDraft(draft) != null) {
            return Result.failure(ExperiencesException(ExperiencesFailure.Rejected))
        }
        val normalized = normalizeExperienceDraft(draft)
        return guard { remote.submit(requireOrigin(), normalized) }
    }

    private suspend fun requireOrigin(): String {
        val resolved = sanitizeOrigin(origin.current())
        if (resolved.isEmpty()) throw ExperiencesException(ExperiencesFailure.NotConfigured)
        return resolved
    }

    private suspend fun <T> guard(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ExperiencesException) {
        Result.failure(e)
    } catch (_: Throwable) {
        Result.failure(ExperiencesException(ExperiencesFailure.Unavailable))
    }
}
