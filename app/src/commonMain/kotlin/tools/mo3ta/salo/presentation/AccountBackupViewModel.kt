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
    /** The id derived from [backupCode]; shown so the user can confirm the two belong together. */
    val publicId: String = "",
    val codeInput: String = "",
    val restoring: Boolean = false,
    /** Set once a restore attempt finishes; drives the result dialog. */
    val result: AccountRestoreResult? = null,
)

class AccountBackupViewModel(
    private val restoreManager: AccountRestoreManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AccountBackupUiState(
            backupCode = restoreManager.currentBackupCode(),
            publicId = restoreManager.currentPublicId(),
        ),
    )
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
                    // A restore rewrites the device's identity, so the displayed code and id
                    // have to follow it; a failed attempt leaves the old pair in place.
                    backupCode = restoreManager.currentBackupCode(),
                    publicId = restoreManager.currentPublicId(),
                    codeInput = if (result is AccountRestoreResult.Restored) "" else current.codeInput,
                )
            }
        }
    }

    fun dismissResult() {
        _state.update { it.copy(result = null) }
    }
}
