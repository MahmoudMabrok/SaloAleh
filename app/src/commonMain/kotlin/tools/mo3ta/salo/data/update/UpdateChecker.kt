package tools.mo3ta.salo.data.update

import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.domain.isNewerVersion

/**
 * Decides whether the app should prompt the user to update at startup.
 *
 * Reads the latest published version from remote config (RTDB `mohamed_lovers/app_config`),
 * compares it to the running app version, and suppresses the prompt once the user has
 * dismissed it for that specific version (see [UpdatePromptStore]).
 */
class UpdateChecker(
    private val firebaseApi: MohamedLoversFirebaseApi,
    private val store: UpdatePromptStore,
) {

    /**
     * Returns the newer version string to prompt for, or `null` when the app is already
     * up to date, remote config is missing/unreadable, or the user already dismissed the
     * prompt for that version. [currentVersion] is the running app's version name.
     */
    suspend fun check(currentVersion: String): String? {
        val latest = firebaseApi.fetchAppConfig().getOrNull()
            ?.latestVersion
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (!isNewerVersion(latest, currentVersion)) return null
        if (store.isDismissedFor(latest)) return null
        return latest
    }

    /** Records that the user dismissed the update prompt for [version]. */
    fun markDismissed(version: String) = store.markDismissed(version)
}
