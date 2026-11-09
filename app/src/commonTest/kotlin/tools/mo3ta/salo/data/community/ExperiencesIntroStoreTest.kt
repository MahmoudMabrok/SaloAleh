package tools.mo3ta.salo.data.community

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperiencesIntroStoreTest {

    @Test
    fun freshInstall_hasNotSeenTheIntro() {
        assertFalse(ExperiencesIntroStore(MapSettings()).isShown())
    }

    @Test
    fun markShown_persistsAcrossStoreInstances() {
        val settings = MapSettings()
        ExperiencesIntroStore(settings).markShown()
        assertTrue(ExperiencesIntroStore(settings).isShown())
    }

    @Test
    fun markShown_isIdempotent() {
        val store = ExperiencesIntroStore(MapSettings())
        store.markShown()
        store.markShown()
        assertTrue(store.isShown())
    }
}

class ExperiencesIntroPresentationTest {

    @Test
    fun presents_onceTheScreenIsClear() {
        assertTrue(
            shouldPresentExperiencesIntro(
                enabled = true,
                alreadyShown = false,
                onboardingVisible = false,
                nicknamePromptVisible = false,
                experiencesScreenVisible = false,
                streakAnnouncementVisible = false,
            ),
        )
    }

    @Test
    fun staysHidden_afterItHasBeenShown() {
        assertFalse(clear().copy(alreadyShown = true).present())
    }

    @Test
    fun staysHidden_whileDisabled() {
        assertFalse(clear().copy(enabled = false).present())
    }

    @Test
    fun staysHidden_duringOnboarding_andTheNicknamePrompt() {
        assertFalse(clear().copy(onboardingVisible = true).present())
        assertFalse(clear().copy(nicknamePromptVisible = true).present())
    }

    @Test
    fun staysHidden_whenExperiencesIsAlreadyOpen() {
        assertFalse(clear().copy(experiencesScreenVisible = true).present())
    }

    @Test
    fun yields_toTheStreakAnnouncement_soTheTwoPromosDoNotStack() {
        assertFalse(clear().copy(streakAnnouncementVisible = true).present())
    }

    private fun clear() = IntroGate()

    private data class IntroGate(
        val enabled: Boolean = true,
        val alreadyShown: Boolean = false,
        val onboardingVisible: Boolean = false,
        val nicknamePromptVisible: Boolean = false,
        val experiencesScreenVisible: Boolean = false,
        val streakAnnouncementVisible: Boolean = false,
    )

    private fun IntroGate.present() = shouldPresentExperiencesIntro(
        enabled = enabled,
        alreadyShown = alreadyShown,
        onboardingVisible = onboardingVisible,
        nicknamePromptVisible = nicknamePromptVisible,
        experiencesScreenVisible = experiencesScreenVisible,
        streakAnnouncementVisible = streakAnnouncementVisible,
    )
}
