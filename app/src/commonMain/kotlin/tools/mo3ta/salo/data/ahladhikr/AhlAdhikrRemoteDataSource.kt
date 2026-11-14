package tools.mo3ta.salo.data.ahladhikr

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tools.mo3ta.salo.domain.AhlAdhikrEntry
import tools.mo3ta.salo.domain.AhlAdhikrSnapshot

/** Published snapshot URL — GitHub Pages, committed by the "Ahl Adhikr Snapshot" workflow. */
const val AHL_ADHIKR_URL = "https://mahmoudmabrok.github.io/SaloAleh/ahl-adhikr/latest.json"

/** Lenient decode shared by the data source and the repository's cache path: unknown keys are dropped. */
internal val AHL_ADHIKR_JSON = Json { ignoreUnknownKeys = true }

/** The network boundary of the أهل الذكر reader, so tests can substitute it. */
interface AhlAdhikrRemote {
    suspend fun fetchSnapshot(): AhlAdhikrSnapshot
}

@Serializable
internal data class AhlAdhikrEntryDto(
    val id: String = "",
    val name: String = "",
    val streak: Int = 0,
) {
    fun toDomain(): AhlAdhikrEntry? =
        if (id.isBlank() || streak <= 0) null else AhlAdhikrEntry(id, name.ifBlank { id }, streak)
}

@Serializable
internal data class AhlAdhikrSnapshotDto(
    val generatedAt: String = "",
    val date: String = "",
    // Published shape: challenge key → milestone key ("30_days"…) → entries.
    val challenges: Map<String, Map<String, List<AhlAdhikrEntryDto>>> = emptyMap(),
) {
    fun toDomain(): AhlAdhikrSnapshot = AhlAdhikrSnapshot(
        generatedAt = generatedAt,
        date = date,
        challenges = challenges.mapValues { (_, buckets) ->
            buckets.mapValues { (_, entries) -> entries.mapNotNull { it.toDomain() } }
        },
    )
}

/**
 * Fetches and decodes the published snapshot. Malformed JSON, a missing file (404
 * page served as HTML) or any network problem surfaces as an exception — the
 * repository decides between cache and empty-state.
 */
class AhlAdhikrRemoteDataSource(private val client: HttpClient) : AhlAdhikrRemote {

    override suspend fun fetchSnapshot(): AhlAdhikrSnapshot {
        val response = client.get(AHL_ADHIKR_URL)
        if (response.status.value !in 200..299) {
            throw AhlAdhikrException("snapshot fetch returned HTTP ${response.status.value}")
        }
        val text = response.bodyAsText()
        // A stale Pages deploy or a redirect can serve HTML — decodeFromString then
        // fails with a SerializationException, which the repository treats as unavailable.
        return AHL_ADHIKR_JSON.decodeFromString<AhlAdhikrSnapshotDto>(text).toDomain()
    }
}

/** Any fetch/decode failure of the published snapshot. */
class AhlAdhikrException(message: String) : Exception(message)
