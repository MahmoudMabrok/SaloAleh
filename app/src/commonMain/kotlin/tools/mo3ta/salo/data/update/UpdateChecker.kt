package tools.mo3ta.salo.data.update

import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.domain.isNewerVersion

/**
 * Outcome of an update check.
 *
 * @param version the version name to point the user at.
 * @param forced when `true` the running build is below the minimum supported version:
 *   the prompt blocks the app and cannot be dismissed. When `false` it is the soft,
 *   dismissable "new version available" nudge.
 */
data class UpdatePrompt(
    val version: String,
    val forced: Boolean,
)

/**
 * Decides whether the app should prompt the user to update at startup.
 *
 * Reads remote config (RTDB `mohamed_lovers/app_config`) and compares it to the running
 * app version:
 * - `minSupportedVersion` newer than the running build ⇒ a **forced** update (blocks the
 *   app, never dismissable, ignores any per-version dismissal). Takes precedence.
 * - otherwise `latestVersion` newer than the running build ⇒ a **soft** update, suppressed
 *   once the user has dismissed it for that specific version (see [UpdatePromptStore]).
 */
class UpdateChecker(
    private val firebaseApi: MohamedLoversFirebaseApi,
    private val store: UpdatePromptStore,
) {

    /**
     * Returns the update to prompt for, or `null` when the app is already up to date,
     * remote config is missing/unreadable, or the user already dismissed the soft prompt
     * for that version. [currentVersion] is the running app's version name.
     */
    suspend fun check(currentVersion: String): UpdatePrompt? {
        val config = firebaseApi.fetchAppConfig().getOrNull() ?: return null

        // Forced update wins over the soft prompt and ignores per-version dismissal —
        // an obsolete build must not be usable regardless of what the user tapped before.
        config.minSupportedVersion
            ?.takeIf { it.isNotBlank() }
            ?.let { min ->
                if (isNewerVersion(min, currentVersion)) return UpdatePrompt(min, forced = true)
            }

        val latest = config.latestVersion.takeIf { it.isNotBlank() } ?: return null
        if (!isNewerVersion(latest, currentVersion)) return null
        if (store.isDismissedFor(latest)) return null
        return UpdatePrompt(latest, forced = false)
    }

    /** Records that the user dismissed the soft update prompt for [version]. */
    fun markDismissed(version: String) = store.markDismissed(version)
}
