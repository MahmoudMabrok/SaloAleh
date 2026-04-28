package com.elsharif.dailyseventy.presentation.mohamedlovers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elsharif.dailyseventy.domain.mohamedlovers.MOHAMED_LOVERS_FRIDAY_MULTIPLIER
import com.elsharif.dailyseventy.domain.mohamedlovers.MohamedLoversCompetitionWindow
import com.elsharif.dailyseventy.domain.mohamedlovers.MohamedLoversPlayer
import com.elsharif.dailyseventy.domain.mohamedlovers.MohamedLoversRepository
import com.elsharif.dailyseventy.domain.mohamedlovers.buildMohamedLoversDisplayTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MohamedLoversLeaderboardEntry(
    val rank: Int,
    val displayTag: String,
    val totalCount: Int,
    val isCurrentUser: Boolean,
)

enum class MohamedLoversStatus {
    WaitingNetwork,
    FirebaseOff,
    Open,
}

sealed interface MohamedLoversError {
    data object Connection : MohamedLoversError
    data class Raw(val message: String) : MohamedLoversError
}

data class MohamedLoversUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSavingSession: Boolean = false,
    val countryCode: String = "",
    val selfDisplayTag: String = "",
    val status: MohamedLoversStatus = MohamedLoversStatus.WaitingNetwork,
    val firebaseConfigured: Boolean = true,
    val isFridayBonus: Boolean = false,
    val roundKey: String? = null,
    val roundEndLabel: String = "",
    val networkTimeLabel: String = "",
    val canCount: Boolean = false,
    val syncedTotal: Int = 0,
    val sessionClicks: Int = 0,
    val isWinner: Boolean = false,
    val winnerCode: String = "",
    val selfEntry: MohamedLoversLeaderboardEntry? = null,
    val selfInTop: Boolean = false,
    val topPlayers: List<MohamedLoversLeaderboardEntry> = emptyList(),
    val error: MohamedLoversError? = null,
)

@HiltViewModel
class MohamedLoversViewModel @Inject constructor(
    private val repository: MohamedLoversRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MohamedLoversUiState())
    val state: StateFlow<MohamedLoversUiState> = _state.asStateFlow()

    private val flushMutex = Mutex()
    private var topJob: Job? = null
    private var selfJob: Job? = null
    private var remoteTopPlayers: List<MohamedLoversPlayer> = emptyList()
    private var remoteSelfPlayer: MohamedLoversPlayer? = null
    private var authUid: String? = null
    private var currentWindow: MohamedLoversCompetitionWindow = MohamedLoversCompetitionWindow()

    init {
        refresh()
    }

    fun refresh() {
        repository.refreshNetworkTime()
        topJob?.cancel()
        selfJob?.cancel()
        remoteTopPlayers = emptyList()
        remoteSelfPlayer = null
        authUid = null

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = true,
                    error = null,
                    topPlayers = emptyList(),
                    selfEntry = null,
                    selfInTop = false,
                    winnerCode = "",
                    syncedTotal = 0,
                )
            }

            val bootstrap = repository.bootstrap()
            currentWindow = bootstrap.competitionWindow

            val displayFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd - hh:mm a")
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    countryCode = bootstrap.countryCode,
                    firebaseConfigured = bootstrap.firebaseConfigured,
                    isFridayBonus = bootstrap.competitionWindow.isFridayBonus,
                    roundKey = bootstrap.competitionWindow.roundKey,
                    roundEndLabel = bootstrap.competitionWindow.roundEnd
                        ?.format(displayFormatter)
                        .orEmpty(),
                    networkTimeLabel = bootstrap.competitionWindow.networkNow
                        ?.format(displayFormatter)
                        .orEmpty(),
                    status = resolveStatus(
                        firebaseConfigured = bootstrap.firebaseConfigured,
                        competitionWindow = bootstrap.competitionWindow,
                    ),
                    canCount = bootstrap.competitionWindow.networkNow != null,
                    sessionClicks = bootstrap.pendingSession.clickCount,
                    error = null,
                )
            }

            flushPendingSession()
            connectToLeaderboardIfPossible()
        }
    }

    fun onCountClick() {
        val current = state.value
        val roundKey = current.roundKey ?: return
        if (!current.canCount) {
            return
        }

        val delta = if (current.isFridayBonus) MOHAMED_LOVERS_FRIDAY_MULTIPLIER else 1
        val pendingSession = repository.registerLocalTap(roundKey, delta)
        _state.update {
            it.copy(
                sessionClicks = pendingSession.clickCount,
                error = null,
            )
        }
        applyLeaderboard()
    }

    fun flushPendingSession() {
        viewModelScope.launch {
            flushMutex.withLock {
                val pending = repository.getPendingSession()
                val roundKey = state.value.roundKey
                Log.d(
                    "TestTest",
                    "flushPendingSession1: pending=$pending roundKey=$roundKey firebase=${state.value.firebaseConfigured}",
                )

                if (!state.value.firebaseConfigured) {
                    Log.w("TestTest", "flushPendingSession: firebase not configured, skip")
                    _state.update { it.copy(isSavingSession = false) }
                    applyLeaderboard()
                    return@withLock
                }

                if (roundKey.isNullOrBlank()) {
                    Log.w(
                        "TestTest",
                        "flushPendingSession: roundKey null/blank — NTP not synced, skip flush. pendingClicks=${pending.clickCount}",
                    )
                    _state.update { it.copy(isSavingSession = false) }
                    applyLeaderboard()
                    return@withLock
                }

                Log.d("TestTest", "flushPendingSession3: before flush (always upsert)")

                _state.update { it.copy(isSavingSession = true, error = null) }

                val result = repository.flushPendingSession(
                    countryCode = state.value.countryCode,
                    fallbackRoundKey = roundKey,
                )
                val latestPending = repository.getPendingSession()

                Log.d(
                    "TestTest",
                    "flushPendingSession4: $result countryCode=${state.value.countryCode} latestPending=$latestPending",
                )

                _state.update {
                    it.copy(
                        isSavingSession = false,
                        sessionClicks = latestPending.clickCount,
                        error = result.exceptionOrNull()?.message
                            ?.takeIf { msg -> msg.isNotBlank() }
                            ?.let(MohamedLoversError::Raw),
                    )
                }

                Log.d("TestTest", "flushPendingSession5: $result")

                applyLeaderboard()
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun connectToLeaderboardIfPossible() {
        val roundKey = state.value.roundKey
        if (!state.value.firebaseConfigured || roundKey.isNullOrBlank()) {
            topJob?.cancel()
            selfJob?.cancel()
            remoteTopPlayers = emptyList()
            remoteSelfPlayer = null
            applyLeaderboard()
            return
        }

        viewModelScope.launch {
            val uidResult = repository.ensureAnonymousUser()
            val uid = uidResult.getOrNull()
            if (uid == null) {
                _state.update {
                    it.copy(
                        error = uidResult.exceptionOrNull()?.message
                            ?.takeIf { msg -> msg.isNotBlank() }
                            ?.let(MohamedLoversError::Raw)
                            ?: MohamedLoversError.Connection,
                    )
                }
                applyLeaderboard()
                return@launch
            }

            authUid = uid
            _state.update {
                it.copy(selfDisplayTag = buildMohamedLoversDisplayTag(uid, it.countryCode))
            }

            topJob?.cancel()
            topJob = launch {
                repository.observeTopPlayers(roundKey).collectLatest { result ->
                    result.onSuccess { players ->
                        remoteTopPlayers = players
                        applyLeaderboard()
                    }.onFailure { throwable ->
                        _state.update {
                            it.copy(
                                error = throwable.message
                                    ?.takeIf { msg -> msg.isNotBlank() }
                                    ?.let(MohamedLoversError::Raw)
                                    ?: MohamedLoversError.Connection,
                            )
                        }
                    }
                }
            }

            selfJob?.cancel()
            selfJob = launch {
                repository.observeSelfPlayer(roundKey, uid).collectLatest { result ->
                    result.onSuccess { player ->
                        remoteSelfPlayer = player
                        applyLeaderboard()
                    }.onFailure { throwable ->
                        _state.update {
                            it.copy(
                                error = throwable.message
                                    ?.takeIf { msg -> msg.isNotBlank() }
                                    ?.let(MohamedLoversError::Raw)
                                    ?: MohamedLoversError.Connection,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyLeaderboard() {
        val currentState = state.value
        val uid = authUid
        val selfRemoteTotal = remoteSelfPlayer?.totalCount ?: 0
        val selfProjectedTotal = selfRemoteTotal + currentState.sessionClicks

        Log.d("TestTest", "applyLeaderboard: ${uid} $selfRemoteTotal", )

        val sortedTop = remoteTopPlayers
            .sortedWith(
                compareByDescending<MohamedLoversPlayer> { it.totalCount }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.uid },
            )

        val topEntries = sortedTop.mapIndexed { index, player ->
            val isCurrentUser = player.uid == uid
            val totalForDisplay = if (isCurrentUser) selfProjectedTotal else player.totalCount
            MohamedLoversLeaderboardEntry(
                rank = index + 1,
                displayTag = buildMohamedLoversDisplayTag(player.uid, player.countryCode),
                totalCount = totalForDisplay,
                isCurrentUser = isCurrentUser,
            )
        }

        val selfInTop = uid != null && topEntries.any { it.isCurrentUser }

        val selfEntry = when {
            uid == null -> null
            selfProjectedTotal <= 0 -> null
            else -> MohamedLoversLeaderboardEntry(
                rank = 0,
                displayTag = buildMohamedLoversDisplayTag(
                    uid,
                    remoteSelfPlayer?.countryCode?.ifBlank { currentState.countryCode }
                        ?: currentState.countryCode,
                ),
                totalCount = selfProjectedTotal,
                isCurrentUser = true,
            )
        }

        _state.update {
            it.copy(
                syncedTotal = selfRemoteTotal,
                isWinner = remoteSelfPlayer?.isWinner == true,
                winnerCode = remoteSelfPlayer?.winnerCode.orEmpty(),
                topPlayers = topEntries,
                selfEntry = selfEntry,
                selfInTop = selfInTop,
            )
        }
    }

    private fun resolveStatus(
        firebaseConfigured: Boolean,
        competitionWindow: MohamedLoversCompetitionWindow,
    ): MohamedLoversStatus {
        return when {
            competitionWindow.networkNow == null -> MohamedLoversStatus.WaitingNetwork
            !firebaseConfigured -> MohamedLoversStatus.FirebaseOff
            else -> MohamedLoversStatus.Open
        }
    }
}
