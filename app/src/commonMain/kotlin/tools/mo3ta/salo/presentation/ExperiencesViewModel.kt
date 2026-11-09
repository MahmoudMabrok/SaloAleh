package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.mo3ta.salo.data.community.CommunityExperiences
import tools.mo3ta.salo.domain.CommunityExperience
import tools.mo3ta.salo.domain.ExperienceCategory
import tools.mo3ta.salo.domain.ExperienceDraft
import tools.mo3ta.salo.domain.ExperienceDraftError
import tools.mo3ta.salo.domain.ExperienceLimits
import tools.mo3ta.salo.domain.ExperiencesException
import tools.mo3ta.salo.domain.ExperiencesFailure
import tools.mo3ta.salo.domain.validateExperienceDraft

data class ExperiencesUiState(
    val loading: Boolean = true,
    val experiences: List<CommunityExperience> = emptyList(),
    val loadError: ExperiencesFailure? = null,
    val displayName: String = "",
    val body: String = "",
    val category: ExperienceCategory = ExperienceCategory.APP,
    val draftError: ExperienceDraftError? = null,
    val submitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitFailure: ExperiencesFailure? = null,
)

class ExperiencesViewModel(
    private val experiences: CommunityExperiences,
) : ViewModel() {

    private val _state = MutableStateFlow(ExperiencesUiState())
    val state: StateFlow<ExperiencesUiState> = _state.asStateFlow()
    private var loadGeneration = 0

    fun refresh() {
        val generation = ++loadGeneration
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            experiences.loadPublished()
                .onSuccess { items ->
                    if (generation != loadGeneration) return@onSuccess
                    _state.update { it.copy(loading = false, experiences = items, loadError = null) }
                }
                .onFailure { throwable ->
                    if (generation != loadGeneration) return@onFailure
                    _state.update {
                        it.copy(loading = false, loadError = throwable.toFailure())
                    }
                }
        }
    }

    fun onNameChange(value: String) {
        _state.update {
            it.copy(
                displayName = value.take(ExperienceLimits.NAME_MAX),
                draftError = null,
            )
        }
    }

    fun onBodyChange(value: String) {
        _state.update {
            it.copy(
                body = value.take(ExperienceLimits.BODY_MAX),
                draftError = null,
            )
        }
    }

    fun onCategoryChange(category: ExperienceCategory) {
        _state.update { it.copy(category = category) }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val draft = ExperienceDraft(current.displayName, current.body, current.category)
        val error = validateExperienceDraft(draft)
        if (error != null) {
            _state.update {
                it.copy(draftError = error, submitSuccess = false, submitFailure = null)
            }
            return
        }
        _state.update {
            it.copy(submitting = true, draftError = null, submitSuccess = false, submitFailure = null)
        }
        viewModelScope.launch {
            experiences.submit(draft)
                .onSuccess {
                    _state.update {
                        it.copy(
                            submitting = false,
                            displayName = "",
                            body = "",
                            submitSuccess = true,
                            submitFailure = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(submitting = false, submitFailure = throwable.toFailure())
                    }
                }
        }
    }

    fun dismissSubmitNotice() {
        _state.update { it.copy(submitSuccess = false, submitFailure = null) }
    }
}

private fun Throwable.toFailure(): ExperiencesFailure =
    (this as? ExperiencesException)?.failure ?: ExperiencesFailure.Unavailable
