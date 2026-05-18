package tools.mo3ta.salo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.tendays.DhikrType
import tools.mo3ta.salo.data.tendays.TenDaysFirebaseClient
import tools.mo3ta.salo.data.tendays.TenDaysStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore

class TenDaysViewModel(
    private val store: TenDaysStore,
    private val firebaseClient: TenDaysFirebaseClient,
    private val sessionStore: MohamedLoversSessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TenDaysUiState())
    val state: StateFlow<TenDaysUiState> = _state.asStateFlow()

    private var syncJob: Job? = null
    private val cairoTz = TimeZone.of("Africa/Cairo")

    init {
        loadState()
        observeLeaderboard()
    }

    fun onDhikrTap(dhikr: DhikrType) {
        val day = _state.value.currentDay
        store.incrementDhikr(day, dhikr)
        refreshDay(day)
        debouncedSync()
    }

    fun onTakbeerTap() {
        val day = _state.value.currentDay
        store.incrementTakbeer(day)
        refreshDay(day)
        debouncedSync()
    }

    fun canToggleFasting(): Boolean {
        val now = Clock.System.now().toLocalDateTime(cairoTz)
        return now.hour >= 20
    }

    fun onFastingToggle() {
        val day = _state.value.currentDay
        if (!store.isFasting(day) && canToggleFasting()) {
            store.setFasting(day, true)
            refreshDay(day)
            debouncedSync()
        }
    }

    fun onSadaqahToggle() {
        val day = _state.value.currentDay
        if (!store.isSadaqah(day)) {
            store.setSadaqah(day, true)
            refreshDay(day)
            debouncedSync()
        }
    }

    fun onDaySelected(@Suppress("UNUSED_PARAMETER") day: Int) {
    }

    fun onAutoPlayToggle() {
        val newValue = !_state.value.autoPlayTakbeer
        store.setAutoPlayTakbeer(newValue)
        _state.update { it.copy(autoPlayTakbeer = newValue) }
    }

    fun onTakbeerIntervalChanged(minutes: Int) {
        store.setTakbeerIntervalMinutes(minutes)
        _state.update { it.copy(takbeerIntervalMinutes = minutes) }
    }

    fun onTakbeerRepeatChanged(count: Int) {
        store.setTakbeerRepeatCount(count)
        _state.update { it.copy(takbeerRepeatCount = count) }
    }

    private fun loadState() {
        val periodKey = computePeriodKey()
        val today = Clock.System.todayIn(cairoTz)
        val startDate = LocalDate.parse(periodKey)
        val currentDay = (startDate.daysUntil(today) + 1).coerceIn(1, 9)

        val days = (1..9).map { day ->
            TenDaysDayState(
                day = day,
                dhikrCounts = DhikrType.entries.associateWith { store.getDhikrCount(day, it) },
                takbeerCount = store.getTakbeerCount(day),
                isFasting = store.isFasting(day),
                isSadaqah = store.isSadaqah(day),
            )
        }

        val totalScore = days.sumOf { it.dayScore }

        _state.value = TenDaysUiState(
            currentDay = currentDay,
            days = days,
            totalScore = totalScore,
            autoPlayTakbeer = store.isAutoPlayTakbeer(),
            takbeerIntervalMinutes = store.getTakbeerIntervalMinutes(),
            takbeerRepeatCount = store.getTakbeerRepeatCount(),
            canFast = canToggleFasting(),
            periodKey = periodKey,
        )
    }

    private fun refreshDay(day: Int) {
        val dayState = TenDaysDayState(
            day = day,
            dhikrCounts = DhikrType.entries.associateWith { store.getDhikrCount(day, it) },
            takbeerCount = store.getTakbeerCount(day),
            isFasting = store.isFasting(day),
            isSadaqah = store.isSadaqah(day),
        )
        _state.update { current ->
            val updatedDays = current.days.toMutableList()
            updatedDays[day - 1] = dayState
            val totalScore = updatedDays.sumOf { it.dayScore }
            current.copy(days = updatedDays, totalScore = totalScore)
        }
    }

    private fun debouncedSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(1000)
            val uid = sessionStore.getOrCreateUid()
            firebaseClient.syncScore(
                periodKey = _state.value.periodKey,
                uid = uid,
                totalScore = _state.value.totalScore,
                countryCode = "",
            )
        }
    }

    private fun observeLeaderboard() {
        viewModelScope.launch {
            val periodKey = _state.value.periodKey
            if (periodKey.isBlank()) return@launch
            val uid = sessionStore.getOrCreateUid()
            _state.update { it.copy(currentUid = uid) }
            firebaseClient.observeLeaderboard(periodKey).collectLatest { result ->
                result.onSuccess { entries ->
                    val selfRank = entries.indexOfFirst { it.uid == uid } + 1
                    _state.update { it.copy(leaderboard = entries.take(10), selfRank = selfRank) }
                }
            }
        }
    }

    private fun computePeriodKey(): String {
        return "2026-05-18"
    }
}
