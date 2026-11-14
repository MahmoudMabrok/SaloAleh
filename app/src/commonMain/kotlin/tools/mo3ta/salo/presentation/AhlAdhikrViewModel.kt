package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.mo3ta.salo.data.ahladhikr.AhlAdhikrRepository
import tools.mo3ta.salo.data.ahladhikr.AhlAdhikrResult
import tools.mo3ta.salo.domain.AhlAdhikrChallenge
import tools.mo3ta.salo.domain.AhlAdhikrMilestone
import tools.mo3ta.salo.domain.AhlAdhikrSnapshot
import tools.mo3ta.salo.domain.AhlAdhikrSource

/** Everything the أهل الذكر screen renders. */
data class AhlAdhikrUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val snapshotDate: String = "",
    val source: AhlAdhikrSource = AhlAdhikrSource.EMPTY,
    val selectedChallenge: AhlAdhikrChallenge = AhlAdhikrChallenge.SALAWAT,
    val selectedMilestone: AhlAdhikrMilestone = AhlAdhikrMilestone.D30,
    val snapshot: AhlAdhikrSnapshot? = null,
) {
    /** Whether the visible lists came from cache because the live fetch failed. */
    val showingStale: Boolean get() = source == AhlAdhikrSource.CACHE
}

class AhlAdhikrViewModel(
    private val repository: AhlAdhikrRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AhlAdhikrUiState())
    val uiState: StateFlow<AhlAdhikrUiState> = _uiState.asStateFlow()

    init {
        load(initial = true)
    }

    fun load(initial: Boolean = false) {
        _uiState.update {
            it.copy(isLoading = initial, isRefreshing = !initial)
        }
        viewModelScope.launch {
            val result: AhlAdhikrResult = try {
                repository.load()
            } catch (_: Exception) {
                AhlAdhikrResult(null, AhlAdhikrSource.EMPTY)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    snapshotDate = result.snapshot?.date ?: "",
                    source = result.source,
                    snapshot = result.snapshot,
                )
            }
        }
    }

    fun selectChallenge(challenge: AhlAdhikrChallenge) {
        _uiState.update { it.copy(selectedChallenge = challenge) }
    }

    fun selectMilestone(milestone: AhlAdhikrMilestone) {
        _uiState.update { it.copy(selectedMilestone = milestone) }
    }
}
