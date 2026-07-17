package tools.mo3ta.salo.data.update

import com.russhwolf.settings.Settings

/**
 * Remembers which released version the user has already dismissed the update prompt for,
 * so the soft-update dialog appears at most once per version. Dismissing a prompt for
 * version X suppresses it forever for X, but a later, higher version prompts again.
 *
 * Local-only persistence via `multiplatform-settings` key `update_prompt_dismissed_version`.
 */
class UpdatePromptStore(private val settings: Settings) {

    fun isDismissedFor(version: String): Boolean =
        settings.getStringOrNull(KEY_DISMISSED_VERSION) == version

    fun markDismissed(version: String) {
        settings.putString(KEY_DISMISSED_VERSION, version)
    }

    private companion object {
        const val KEY_DISMISSED_VERSION = "update_prompt_dismissed_version"
    }
}
