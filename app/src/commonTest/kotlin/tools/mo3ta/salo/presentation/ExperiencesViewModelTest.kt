package tools.mo3ta.salo.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import tools.mo3ta.salo.data.community.CommunityExperiences
import tools.mo3ta.salo.domain.CommunityExperience
import tools.mo3ta.salo.domain.ExperienceCategory
import tools.mo3ta.salo.domain.ExperienceDraft
import tools.mo3ta.salo.domain.ExperienceDraftError
import tools.mo3ta.salo.domain.ExperienceLimits
import tools.mo3ta.salo.domain.ExperiencesException
import tools.mo3ta.salo.domain.ExperiencesFailure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExperiencesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_showsPublishedRows_andKeepsThemWhenALaterLoadFails() {
        val community = FakeCommunity()
        val row = CommunityExperience("1", "هدى", "نص منشور", ExperienceCategory.APP, "2026-06-15T10:00:00Z")
        community.published = Result.success(listOf(row))
        val viewModel = ExperiencesViewModel(community)

        viewModel.refresh()
        assertEquals(listOf(row), viewModel.state.value.experiences)
        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.loadError)

        community.published = Result.failure(ExperiencesException(ExperiencesFailure.Unavailable))
        viewModel.refresh()
        assertEquals(listOf(row), viewModel.state.value.experiences)
        assertEquals(ExperiencesFailure.Unavailable, viewModel.state.value.loadError)
    }

    @Test
    fun emptyPublishedList_isAnEmptySuccess() {
        val viewModel = ExperiencesViewModel(FakeCommunity())
        viewModel.refresh()
        assertTrue(viewModel.state.value.experiences.isEmpty())
        assertNull(viewModel.state.value.loadError)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun submit_doesNotInsertTheStoryIntoTheList() {
        val community = FakeCommunity()
        val viewModel = ExperiencesViewModel(community)
        viewModel.refresh()
        viewModel.onNameChange("  ")
        viewModel.onBodyChange("  تجربتي مع الذكر  ")
        viewModel.onCategoryChange(ExperienceCategory.DHIKR)

        viewModel.submit()

        assertTrue(viewModel.state.value.submitSuccess)
        assertEquals("", viewModel.state.value.body)
        assertEquals("", viewModel.state.value.displayName)
        assertTrue(viewModel.state.value.experiences.isEmpty())
        assertEquals(
            ExperienceDraft("  ", "  تجربتي مع الذكر  ", ExperienceCategory.DHIKR),
            community.submitted.single(),
        )
    }

    @Test
    fun unsafeText_isNotSubmitted() {
        val community = FakeCommunity()
        val viewModel = ExperiencesViewModel(community)
        viewModel.onBodyChange("<script>no</script>")
        viewModel.submit()
        assertEquals(ExperienceDraftError.UnsafeContent, viewModel.state.value.draftError)
        assertTrue(community.submitted.isEmpty())
        assertFalse(viewModel.state.value.submitSuccess)
    }

    @Test
    fun nameAndBody_areCappedAtTheLimits() {
        val viewModel = ExperiencesViewModel(FakeCommunity())
        viewModel.onNameChange("ا".repeat(80))
        viewModel.onBodyChange("ب".repeat(ExperienceLimits.BODY_MAX + 50))
        assertEquals(ExperienceLimits.NAME_MAX, viewModel.state.value.displayName.length)
        assertEquals(ExperienceLimits.BODY_MAX, viewModel.state.value.body.length)
    }

    @Test
    fun submitFailure_isSurfacedAndTheListStaysEmpty() {
        val community = FakeCommunity()
        community.submitResult = Result.failure(ExperiencesException(ExperiencesFailure.RateLimited))
        val viewModel = ExperiencesViewModel(community)
        viewModel.refresh()
        viewModel.onBodyChange("تجربة")
        viewModel.submit()
        assertEquals(ExperiencesFailure.RateLimited, viewModel.state.value.submitFailure)
        assertFalse(viewModel.state.value.submitSuccess)
        assertTrue(viewModel.state.value.experiences.isEmpty())
        assertEquals("تجربة", viewModel.state.value.body)
    }

    private class FakeCommunity : CommunityExperiences {
        var published: Result<List<CommunityExperience>> = Result.success(emptyList())
        var submitResult: Result<Unit> = Result.success(Unit)
        val submitted = mutableListOf<ExperienceDraft>()

        override suspend fun loadPublished(): Result<List<CommunityExperience>> = published

        override suspend fun submit(draft: ExperienceDraft): Result<Unit> {
            submitted += draft
            return submitResult
        }
    }
}
