package tools.mo3ta.salo.data.community

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import tools.mo3ta.salo.domain.CommunityExperience
import tools.mo3ta.salo.domain.ExperienceDraft
import tools.mo3ta.salo.domain.ExperiencesException
import tools.mo3ta.salo.domain.ExperiencesFailure
import tools.mo3ta.salo.domain.communityExperienceOrNull

/**
 * Talks only to the community API (Deno Deploy). This client has no GitHub token
 * and does not call api.github.com or raw.githubusercontent.com.
 */
class ExperiencesRemoteDataSource(
    private val client: HttpClient,
) : ExperiencesRemote {

    override suspend fun fetchPublished(baseUrl: String): List<CommunityExperience> {
        val response = client.get(endpoint(baseUrl))
        ensureAccepted(response)
        return response.body<ExperiencesListDto>().experiences.mapNotNull { it.toDomain() }
    }

    override suspend fun submit(baseUrl: String, draft: ExperienceDraft) {
        val response = client.post(endpoint(baseUrl)) {
            contentType(ContentType.Application.Json)
            setBody(
                ExperienceSubmitDto(
                    displayName = draft.displayName,
                    body = draft.body,
                    category = draft.category.wire,
                ),
            )
        }
        ensureAccepted(response)
        response.bodyAsText()
    }

    private fun endpoint(baseUrl: String): String =
        experiencesEndpoint(baseUrl) ?: throw ExperiencesException(ExperiencesFailure.NotConfigured)

    private suspend fun ensureAccepted(response: HttpResponse) {
        when (response.status.value) {
            in 200..299 -> return
            429 -> {
                response.bodyAsText()
                throw ExperiencesException(ExperiencesFailure.RateLimited)
            }
            400, 413, 422 -> {
                response.bodyAsText()
                throw ExperiencesException(ExperiencesFailure.Rejected)
            }
            else -> {
                response.bodyAsText()
                throw ExperiencesException(ExperiencesFailure.Unavailable)
            }
        }
    }
}

@Serializable
internal data class ExperiencesListDto(
    val experiences: List<ExperienceDto> = emptyList(),
)

@Serializable
internal data class ExperienceDto(
    val id: String = "",
    val displayName: String = "",
    val body: String = "",
    val category: String = "",
    val createdAt: String = "",
) {
    fun toDomain(): CommunityExperience? =
        communityExperienceOrNull(id, displayName, body, category, createdAt)
}

@Serializable
internal data class ExperienceSubmitDto(
    val displayName: String,
    val body: String,
    val category: String,
)
