package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.mo3ta.salo.audio.TakbeerSoundPlayer

/**
 * Drives a group takbeer session: the highlight (and audio) cycles around the
 * ring of participants one by one. When it reaches the user the audio stops and
 * the session waits for the user to finish their own takbeer.
 */
class TakbeerSessionViewModel(
    private val soundPlayer: TakbeerSoundPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(TakbeerSessionUiState())
    val state: StateFlow<TakbeerSessionUiState> = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var userTurnSignal: CompletableDeferred<Unit>? = null

    fun onPeopleInputChange(text: String) {
        if (_state.value.phase != TakbeerPhase.SETUP) return
        val digits = text.filter(Char::isDigit).take(2)
        _state.update { it.copy(peopleCountInput = digits) }
    }

    fun onStep(delta: Int) {
        if (_state.value.phase != TakbeerPhase.SETUP) return
        val current = _state.value.peopleCountInput.toIntOrNull()
            ?: TakbeerSessionUiState.DEFAULT_PEOPLE
        val next = (current + delta).coerceIn(
            TakbeerSessionUiState.MIN_PEOPLE,
            TakbeerSessionUiState.MAX_PEOPLE,
        )
        _state.update { it.copy(peopleCountInput = next.toString()) }
    }

    fun startSession() {
        if (_state.value.phase == TakbeerPhase.RUNNING) return
        val count = _state.value.validCount ?: return
        _state.update {
            it.copy(
                phase = TakbeerPhase.RUNNING,
                peopleCount = count,
                currentTurn = 0,
                roundsCompleted = 0,
                awaitingUser = false,
            )
        }
        runLoop(count)
    }

    fun onUserDone() {
        userTurnSignal?.complete(Unit)
    }

    fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        userTurnSignal = null
        soundPlayer.stop()
        _state.update {
            it.copy(
                phase = TakbeerPhase.SETUP,
                currentTurn = -1,
                awaitingUser = false,
            )
        }
    }

    private fun runLoop(count: Int) {
        sessionJob?.cancel()
        val userIndex = count - 1
        sessionJob = viewModelScope.launch {
            var turn = 0
            while (true) {
                _state.update { it.copy(currentTurn = turn, awaitingUser = turn == userIndex) }
                if (turn == userIndex) {
                    soundPlayer.stop()
                    val signal = CompletableDeferred<Unit>()
                    userTurnSignal = signal
                    signal.await()
                    userTurnSignal = null
                    _state.update { it.copy(awaitingUser = false) }
                } else {
                    soundPlayer.play()
                    delay(TURN_GAP_MS)
                }
                turn = if (turn >= count - 1) {
                    _state.update { it.copy(roundsCompleted = it.roundsCompleted + 1) }
                    0
                } else {
                    turn + 1
                }
            }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        soundPlayer.stop()
        super.onCleared()
    }

    private companion object {
        const val TURN_GAP_MS = 450L
    }
}
