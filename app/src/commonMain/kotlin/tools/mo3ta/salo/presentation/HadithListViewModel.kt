package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.mo3ta.salo.data.hadith.HadithItem
import tools.mo3ta.salo.data.hadith.HadithListRepository
import tools.mo3ta.salo.data.media.MediaItem

data class HadithListUiState(
    val isLoadingTexts: Boolean = false,
    val isLoadingMedia: Boolean = false,
    val texts: List<HadithItem> = emptyList(),
    val media: List<MediaItem> = emptyList(),
    val textsLoaded: Boolean = false,
    val mediaLoaded: Boolean = false,
)

class HadithListViewModel(
    private val repository: HadithListRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HadithListUiState())
    val state: StateFlow<HadithListUiState> = _state.asStateFlow()

    init {
        loadTexts()
        loadMedia()
    }

    private fun loadTexts() {
        _state.update { it.copy(isLoadingTexts = true) }
        viewModelScope.launch {
            val result = repository.loadHadiths()
            _state.update {
                it.copy(
                    isLoadingTexts = false,
                    textsLoaded = true,
                    texts = result.getOrDefault(emptyList()),
                )
            }
        }
    }

    private fun loadMedia() {
        _state.update { it.copy(isLoadingMedia = true) }
        viewModelScope.launch {
            val result = repository.loadMedia()
            _state.update {
                it.copy(
                    isLoadingMedia = false,
                    mediaLoaded = true,
                    media = result.getOrDefault(emptyList()),
                )
            }
        }
    }
}
