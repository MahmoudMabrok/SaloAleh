package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.mo3ta.salo.domain.AccountRestoreManager
import tools.mo3ta.salo.domain.AccountRestoreResult

data class AccountBackupUiState(
    /** This device's backup code, shown for the user to copy and keep. */
    val backupCode: String = "",
    val codeInput: String = "",
    val restoring: Boolean = false,
    /** Set once a restore attempt finishes; drives the result dialog. */
    val result: AccountRestoreResult? = null,
)

class AccountBackupViewModel(
    private val restoreManager: AccountRestoreManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountBackupUiState(backupCode = restoreManager.currentBackupCode()))
    val state: StateFlow<AccountBackupUiState> = _state.asStateFlow()

    fun onCodeInputChange(value: String) {
        _state.update { it.copy(codeInput = value) }
    }

    fun restore() {
        if (_state.value.restoring) return
        _state.update { it.copy(restoring = true, result = null) }
        viewModelScope.launch {
            val result = restoreManager.restore(_state.value.codeInput)
            _state.update { current ->
                current.copy(
                    restoring = false,
                    result = result,
                    // A restore rewrites the device's identity, so the displayed code has to
                    // follow it; a failed attempt leaves the old code in place.
                    backupCode = restoreManager.currentBackupCode(),
                    codeInput = if (result is AccountRestoreResult.Restored) "" else current.codeInput,
                )
            }
        }
    }

    fun dismissResult() {
        _state.update { it.copy(result = null) }
    }
}
