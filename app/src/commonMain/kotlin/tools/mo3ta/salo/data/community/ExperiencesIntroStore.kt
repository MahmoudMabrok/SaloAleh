package tools.mo3ta.salo.data.community

import com.russhwolf.settings.Settings

/**
 * One-shot gate for the experiences intro overlay.
 *
 * Local-only persistence via `multiplatform-settings` key [KEY_SHOWN]. The flag is set when
 * the user opens Experiences or dismisses the overlay, and it is never cleared, so the prompt
 * does not return on later launches.
 */
class ExperiencesIntroStore(private val settings: Settings) {

    fun isShown(): Boolean = settings.getBoolean(KEY_SHOWN, false)

    fun markShown() {
        settings.putBoolean(KEY_SHOWN, true)
    }

    companion object {
        const val KEY_SHOWN = "experiences_intro_shown"
    }
}

/**
 * Whether the intro may cover the current screen.
 *
 * A dismissed install stays quiet. Onboarding, the nickname prompt, and an already-open
 * Experiences screen keep the surface. The streak-badge announcement is yielded to, so two
 * feature promos never stack; this one appears once that dialog is gone.
 */
internal fun shouldPresentExperiencesIntro(
    enabled: Boolean,
    alreadyShown: Boolean,
    onboardingVisible: Boolean,
    nicknamePromptVisible: Boolean,
    experiencesScreenVisible: Boolean,
    streakAnnouncementVisible: Boolean,
): Boolean =
    enabled &&
        !alreadyShown &&
        !onboardingVisible &&
        !nicknamePromptVisible &&
        !experiencesScreenVisible &&
        !streakAnnouncementVisible
