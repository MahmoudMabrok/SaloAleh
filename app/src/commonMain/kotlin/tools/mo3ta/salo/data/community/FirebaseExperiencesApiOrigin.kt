package tools.mo3ta.salo.data.community

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import tools.mo3ta.salo.domain.ExperiencesException
import tools.mo3ta.salo.domain.ExperiencesFailure

/** Resolves the Deno Deploy origin. A blank result means the screen should stay offline. */
interface ExperiencesApiOrigin {
    suspend fun current(): String
}

/** Reads one Remote Config string. Implementations must not log the value if it could hold a secret. */
internal fun interface ExperiencesRemoteConfigReader {
    suspend fun getString(key: String): String
}

/**
 * Production source of the experiences API origin.
 * A non-empty [debugOverride] (Android debug builds only) skips Remote Config.
 */
internal class FirebaseExperiencesApiOrigin(
    private val remoteConfig: ExperiencesRemoteConfigReader,
    private val debugOverride: String = "",
) : ExperiencesApiOrigin {

    override suspend fun current(): String {
        sanitizeOrigin(debugOverride).takeIf { it.isNotEmpty() }?.let { return it }
        val raw = try {
            remoteConfig.getString(EXPERIENCES_API_BASE_URL_KEY)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExperiencesException) {
            throw e
        } catch (_: Throwable) {
            throw ExperiencesException(ExperiencesFailure.Unavailable)
        }
        return sanitizeOrigin(raw)
    }
}

/**
 * GitLive Remote Config on the existing Firebase app. Does not read Realtime Database
 * or Firestore. A failed fetch keeps the last activated value; with nothing cached it fails.
 */
internal class GitLiveExperiencesRemoteConfigReader(
    private val fetchImmediately: Boolean,
) : ExperiencesRemoteConfigReader {

    override suspend fun getString(key: String): String {
        val config = Firebase.remoteConfig
        config.settings {
            minimumFetchInterval = if (fetchImmediately) 0.seconds else 1.hours
        }
        config.setDefaults(key to "")
        val fetched = runCatching { config.fetchAndActivate() }.isSuccess
        return originAfterFetch(fetched, config.getValue(key).asString())
    }
}
