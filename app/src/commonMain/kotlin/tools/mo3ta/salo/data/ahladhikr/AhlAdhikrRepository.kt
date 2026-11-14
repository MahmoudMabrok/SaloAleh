package tools.mo3ta.salo.data.ahladhikr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import tools.mo3ta.salo.domain.AhlAdhikrSnapshot
import tools.mo3ta.salo.domain.AhlAdhikrSource

/** What [AhlAdhikrRepository.load] produced and where it came from. */
data class AhlAdhikrResult(
    val snapshot: AhlAdhikrSnapshot?,
    val source: AhlAdhikrSource,
)

/**
 * Remote-first reader for the published أهل الذكر snapshot:
 *
 * 1. Fresh fetch (bounded by [FETCH_TIMEOUT_MS]) → parse → persist to cache → REMOTE.
 * 2. Fetch or parse failed → last cached snapshot → CACHE (the UI shows a stale notice).
 * 3. Nothing cached (first run, no network) → EMPTY (safe empty state).
 *
 * The repository never throws for ordinary failures — the ViewModel renders from the
 * result's source; only cancellation propagates.
 */
class AhlAdhikrRepository(
    private val remote: AhlAdhikrRemote,
    private val store: AhlAdhikrStore,
) {

    suspend fun load(): AhlAdhikrResult {
        val fresh = try {
            val snapshot = withTimeout(FETCH_TIMEOUT_MS) { remote.fetchSnapshot() }
            store.cachedJson = AHL_ADHIKR_JSON.encodeToString(AhlAdhikrSnapshotDto.serializer(), toDto(snapshot))
            AhlAdhikrResult(snapshot, AhlAdhikrSource.REMOTE)
        } catch (e: TimeoutCancellationException) {
            // withTimeout's expiry — an ordinary failure, not scope cancellation.
            cachedResult() ?: AhlAdhikrResult(null, AhlAdhikrSource.EMPTY)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cachedResult() ?: AhlAdhikrResult(null, AhlAdhikrSource.EMPTY)
        }
        return fresh
    }

    /** Parses the cached JSON; a cache written by an older schema fails here and clears itself. */
    private fun cachedResult(): AhlAdhikrResult? {
        val cached = store.cachedJson ?: return null
        return try {
            val snapshot = AHL_ADHIKR_JSON.decodeFromString<AhlAdhikrSnapshotDto>(cached).toDomain()
            AhlAdhikrResult(snapshot, AhlAdhikrSource.CACHE)
        } catch (_: Exception) {
            store.clear()
            null
        }
    }

    private fun toDto(snapshot: AhlAdhikrSnapshot): AhlAdhikrSnapshotDto = AhlAdhikrSnapshotDto(
        generatedAt = snapshot.generatedAt,
        date = snapshot.date,
        challenges = snapshot.challenges.mapValues { (_, buckets) ->
            buckets.mapValues { (_, entries) ->
                entries.map { AhlAdhikrEntryDto(it.id, it.name, it.streak) }
            }
        },
    )

    companion object {
        const val FETCH_TIMEOUT_MS = 15_000L
    }
}
