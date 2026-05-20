# عشر ذي الحجة (Ten Days) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a time-limited "Ten Days of Dhul Hijjah" screen with daily worship tracking (adhkar counters, fasting/charity toggles, takbeer counter), points system, and a dedicated Firebase leaderboard.

**Architecture:** New vertical slice (store → viewmodel → screen) following the existing MohamedLovers pattern. Local persistence via `russhwolf/settings`, Firebase sync for total score only, separate RTDB node for leaderboard. Entry via new icon button in MohamedLoversScreen top bar.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin DI, Firebase RTDB (dev.gitlive.firebase), russhwolf/settings, kotlinx-datetime

---

## File Structure

| File | Responsibility |
|------|---------------|
| `data/tendays/TenDaysStore.kt` | Local persistence of per-day counters/toggles and auto-play pref |
| `data/tendays/TenDaysFirebaseClient.kt` | Firebase RTDB read/write for ten_days_dhul_hijjah node |
| `presentation/TenDaysUiState.kt` | UI state data class |
| `presentation/TenDaysViewModel.kt` | Business logic, score computation, Firebase sync |
| `ui/tendays/TenDaysScreen.kt` | Screen composable with all UI sections |
| `ui/tendays/TenDaysPalette.kt` | Color constants for the enlightening theme |
| `App.kt` | Add `showTenDays` navigation state |
| `ui/MohamedLoversScreen.kt` | Add ten days icon button before settings |
| `di/AppModule.kt` | Register TenDaysStore, TenDaysFirebaseClient, TenDaysViewModel |

All new files under: `app/src/commonMain/kotlin/tools/mo3ta/salo/`

---

### Task 1: TenDaysStore — Local Persistence

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/tendays/TenDaysStore.kt`

- [ ] **Step 1: Create TenDaysStore**

```kotlin
package tools.mo3ta.salo.data.tendays

import com.russhwolf.settings.Settings

class TenDaysStore(private val settings: Settings) {

    fun getDhikrCount(day: Int, dhikr: DhikrType): Int =
        settings.getInt(key(day, dhikr.key), 0)

    fun incrementDhikr(day: Int, dhikr: DhikrType): Int {
        val k = key(day, dhikr.key)
        val updated = settings.getInt(k, 0) + 1
        settings.putInt(k, updated)
        return updated
    }

    fun getTakbeerCount(day: Int): Int =
        settings.getInt(key(day, "takbeer"), 0)

    fun incrementTakbeer(day: Int): Int {
        val k = key(day, "takbeer")
        val updated = settings.getInt(k, 0) + 1
        settings.putInt(k, updated)
        return updated
    }

    fun isFasting(day: Int): Boolean =
        settings.getBoolean(key(day, "fasting"), false)

    fun setFasting(day: Int, value: Boolean) =
        settings.putBoolean(key(day, "fasting"), value)

    fun isSadaqah(day: Int): Boolean =
        settings.getBoolean(key(day, "sadaqah"), false)

    fun setSadaqah(day: Int, value: Boolean) =
        settings.putBoolean(key(day, "sadaqah"), value)

    fun isAutoPlayTakbeer(): Boolean =
        settings.getBoolean(KEY_AUTO_PLAY, false)

    fun setAutoPlayTakbeer(enabled: Boolean) =
        settings.putBoolean(KEY_AUTO_PLAY, enabled)

    private fun key(day: Int, suffix: String) = "tenDays_day${day}_$suffix"

    private companion object {
        const val KEY_AUTO_PLAY = "tenDays_autoPlayTakbeer"
    }
}

enum class DhikrType(val key: String, val label: String) {
    SubhanAllah("subhanallah", "سبحان الله"),
    Alhamdulillah("alhamdulillah", "الحمد لله"),
    AllahuAkbar("allahuakbar", "الله أكبر"),
    LaIlahaIllallah("lailaha", "لا إله إلا الله"),
    LaHawla("lahawla", "لا حول ولا قوة إلا بالله"),
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/tendays/TenDaysStore.kt
git commit -m "feat(ten-days): add TenDaysStore for local persistence"
```

---

### Task 2: TenDaysFirebaseClient — Firebase Sync

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/tendays/TenDaysFirebaseClient.kt`

- [ ] **Step 1: Create TenDaysFirebaseClient**

```kotlin
package tools.mo3ta.salo.data.tendays

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore

data class TenDaysLeaderboardEntry(
    val uid: String,
    val totalScore: Int,
    val countryCode: String,
)

class TenDaysFirebaseClient(private val sessionStore: MohamedLoversSessionStore) {

    private val log = Logger.withTag("TenDaysFirebase")

    fun observeLeaderboard(periodKey: String): Flow<Result<List<TenDaysLeaderboardEntry>>> =
        Firebase.database.reference("$ROOT/$periodKey/leaderboard")
            .valueEvents
            .map { snapshot ->
                runCatching {
                    snapshot.children.mapNotNull { child ->
                        val uid = child.child("uid").value<String?>() ?: return@mapNotNull null
                        val score = child.child("totalScore").value<Int?>() ?: 0
                        val country = child.child("countryCode").value<String?>() ?: ""
                        TenDaysLeaderboardEntry(uid, score, country)
                    }
                }
            }
            .catch { e ->
                log.e(e) { "observeLeaderboard error" }
                emit(Result.failure(e))
            }

    suspend fun syncScore(
        periodKey: String,
        uid: String,
        totalScore: Int,
        countryCode: String,
    ): Result<Unit> = runCatching {
        val playerRef = Firebase.database.reference("$ROOT/$periodKey/players/$uid")
        playerRef.setValue(
            mapOf(
                "uid" to uid,
                "totalScore" to totalScore,
                "updatedAt" to ServerValue.TIMESTAMP,
                "countryCode" to countryCode,
            )
        )
        log.d { "syncScore[$periodKey/$uid]: $totalScore" }
    }

    suspend fun fetchSelfScore(periodKey: String, uid: String): Result<Int> = runCatching {
        val snap = Firebase.database.reference("$ROOT/$periodKey/players/$uid/totalScore")
            .valueEvents
            .map { it.value<Int?>() ?: 0 }
        snap.toString().toIntOrNull() ?: 0
    }

    private companion object {
        const val ROOT = "ten_days_dhul_hijjah"
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/tendays/TenDaysFirebaseClient.kt
git commit -m "feat(ten-days): add TenDaysFirebaseClient for score sync and leaderboard"
```

---

### Task 3: TenDaysUiState — State Data Class

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/TenDaysUiState.kt`

- [ ] **Step 1: Create TenDaysUiState**

```kotlin
package tools.mo3ta.salo.presentation

import tools.mo3ta.salo.data.tendays.DhikrType
import tools.mo3ta.salo.data.tendays.TenDaysLeaderboardEntry

data class TenDaysDayState(
    val day: Int,
    val dhikrCounts: Map<DhikrType, Int> = DhikrType.entries.associateWith { 0 },
    val takbeerCount: Int = 0,
    val isFasting: Boolean = false,
    val isSadaqah: Boolean = false,
) {
    val dayScore: Int
        get() = dhikrCounts.values.sum() +
                (if (isFasting) 100 else 0) +
                (takbeerCount * 5) +
                (if (isSadaqah) 150 else 0)
}

data class TenDaysUiState(
    val currentDay: Int = 1,
    val totalDays: Int = 9,
    val days: List<TenDaysDayState> = (1..9).map { TenDaysDayState(day = it) },
    val totalScore: Int = 0,
    val selfRank: Int = 0,
    val leaderboard: List<TenDaysLeaderboardEntry> = emptyList(),
    val autoPlayTakbeer: Boolean = false,
    val isActive: Boolean = true,
    val periodKey: String = "",
)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/TenDaysUiState.kt
git commit -m "feat(ten-days): add TenDaysUiState and TenDaysDayState"
```

---

### Task 4: TenDaysViewModel — Business Logic

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/TenDaysViewModel.kt`

- [ ] **Step 1: Create TenDaysViewModel**

```kotlin
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
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

    fun onFastingToggle() {
        val day = _state.value.currentDay
        if (!store.isFasting(day)) {
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

    fun onDaySelected(day: Int) {
        _state.update { it.copy(currentDay = day.coerceIn(1, 9)) }
    }

    fun onAutoPlayToggle() {
        val newValue = !_state.value.autoPlayTakbeer
        store.setAutoPlayTakbeer(newValue)
        _state.update { it.copy(autoPlayTakbeer = newValue) }
    }

    private fun loadState() {
        val periodKey = computePeriodKey()
        val today = Clock.System.todayIn(cairoTz)
        val startDate = kotlinx.datetime.LocalDate.parse(periodKey)
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
            val countryCode = "" // will be filled by platform
            firebaseClient.syncScore(
                periodKey = _state.value.periodKey,
                uid = uid,
                totalScore = _state.value.totalScore,
                countryCode = countryCode,
            )
        }
    }

    private fun observeLeaderboard() {
        viewModelScope.launch {
            val periodKey = _state.value.periodKey
            if (periodKey.isBlank()) return@launch
            firebaseClient.observeLeaderboard(periodKey).collectLatest { result ->
                result.onSuccess { entries ->
                    val uid = sessionStore.getOrCreateUid()
                    val selfRank = entries.indexOfFirst { it.uid == uid } + 1
                    _state.update { it.copy(leaderboard = entries.take(10), selfRank = selfRank) }
                }
            }
        }
    }

    private fun computePeriodKey(): String {
        // Hardcoded start date for Dhul Hijjah 2026
        // TODO: Update yearly or make configurable via Remote Config
        return "2026-06-06"
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/TenDaysViewModel.kt
git commit -m "feat(ten-days): add TenDaysViewModel with score computation and Firebase sync"
```

---

### Task 5: TenDaysPalette — Theme Colors

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/tendays/TenDaysPalette.kt`

- [ ] **Step 1: Create TenDaysPalette**

```kotlin
package tools.mo3ta.salo.ui.tendays

import androidx.compose.ui.graphics.Color

object TenDaysPalette {
    val BackgroundDark = Color(0xFF0A0A1F)
    val BackgroundMid = Color(0xFF1A1040)
    val BackgroundLight = Color(0xFF2A1A50)
    val Gold = Color(0xFFFBBF24)
    val GoldDim = Color(0xFFF59E0B)
    val Green = Color(0xFF22C55E)
    val CardBackground = Color(0xFF1E293B)
    val CardBackgroundAlpha = Color(0xCC1E293B)
    val SurfaceDark = Color(0xFF0F172A)
    val TextPrimary = Color(0xFFEEEEEE)
    val TextSecondary = Color(0xFF94A3B8)
    val GrayBorder = Color(0xFF475569)
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/tendays/TenDaysPalette.kt
git commit -m "feat(ten-days): add TenDaysPalette color constants"
```

---

### Task 6: TenDaysScreen — Main UI

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/tendays/TenDaysScreen.kt`

- [ ] **Step 1: Create TenDaysScreen composable**

```kotlin
package tools.mo3ta.salo.ui.tendays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.data.tendays.DhikrType
import tools.mo3ta.salo.presentation.TenDaysDayState
import tools.mo3ta.salo.presentation.TenDaysUiState
import tools.mo3ta.salo.presentation.TenDaysViewModel

@Composable
fun TenDaysScreen(
    onBack: () -> Unit = {},
    viewModel: TenDaysViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TenDaysPalette.BackgroundDark,
                        TenDaysPalette.BackgroundMid,
                        TenDaysPalette.BackgroundLight,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // Back button
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = TenDaysPalette.TextPrimary,
                )
            }

            // Header
            TenDaysHeader(state)

            Spacer(Modifier.height(12.dp))

            // Score banner
            ScoreBanner(state.totalScore)

            Spacer(Modifier.height(12.dp))

            // Day selector
            DaySelector(
                currentDay = state.currentDay,
                days = state.days,
                onDaySelected = viewModel::onDaySelected,
            )

            Spacer(Modifier.height(14.dp))

            // Actions for current day
            val currentDayState = state.days.getOrNull(state.currentDay - 1)
                ?: TenDaysDayState(day = state.currentDay)

            BaqiyatSection(
                dayState = currentDayState,
                onDhikrTap = viewModel::onDhikrTap,
            )

            Spacer(Modifier.height(10.dp))

            FastingRow(
                isFasting = currentDayState.isFasting,
                onToggle = viewModel::onFastingToggle,
            )

            Spacer(Modifier.height(10.dp))

            TakbeerRow(
                count = currentDayState.takbeerCount,
                onTap = viewModel::onTakbeerTap,
                autoPlay = state.autoPlayTakbeer,
                onAutoPlayToggle = viewModel::onAutoPlayToggle,
            )

            Spacer(Modifier.height(10.dp))

            SadaqahRow(
                isSadaqah = currentDayState.isSadaqah,
                onToggle = viewModel::onSadaqahToggle,
            )

            Spacer(Modifier.height(14.dp))

            // Leaderboard
            MiniLeaderboard(state)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TenDaysHeader(state: TenDaysUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "عشر ذي الحجة",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TenDaysPalette.Gold,
        )
        Text(
            text = "اليوم ${state.currentDay} من ${state.totalDays}",
            fontSize = 14.sp,
            color = TenDaysPalette.TextSecondary,
        )
    }
}

@Composable
private fun ScoreBanner(totalScore: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(TenDaysPalette.Gold, TenDaysPalette.GoldDim)
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "مجموع نقاطك",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Text(
                text = "$totalScore",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
    }
}

@Composable
private fun DaySelector(
    currentDay: Int,
    days: List<TenDaysDayState>,
    onDaySelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        days.forEach { dayState ->
            val isCurrent = dayState.day == currentDay
            val isCompleted = dayState.dayScore > 0 && dayState.day < currentDay
            val bgColor = when {
                isCurrent -> TenDaysPalette.Gold
                isCompleted -> TenDaysPalette.Green
                else -> TenDaysPalette.SurfaceDark
            }
            val textColor = when {
                isCurrent -> Color.Black
                isCompleted -> Color.White
                else -> TenDaysPalette.GrayBorder
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable { onDaySelected(dayState.day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${dayState.day}",
                    fontSize = 12.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun BaqiyatSection(
    dayState: TenDaysDayState,
    onDhikrTap: (DhikrType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "الباقيات الصالحات",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TenDaysPalette.TextPrimary,
            )
            Text(
                text = "+١ لكل ذكر",
                fontSize = 11.sp,
                color = TenDaysPalette.Gold,
            )
        }

        Spacer(Modifier.height(10.dp))

        // 2x2 grid
        val topRow = listOf(DhikrType.SubhanAllah, DhikrType.Alhamdulillah)
        val midRow = listOf(DhikrType.AllahuAkbar, DhikrType.LaIlahaIllallah)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            topRow.forEach { dhikr ->
                DhikrCell(
                    dhikr = dhikr,
                    count = dayState.dhikrCounts[dhikr] ?: 0,
                    onClick = { onDhikrTap(dhikr) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            midRow.forEach { dhikr ->
                DhikrCell(
                    dhikr = dhikr,
                    count = dayState.dhikrCounts[dhikr] ?: 0,
                    onClick = { onDhikrTap(dhikr) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Centered last item
        DhikrCell(
            dhikr = DhikrType.LaHawla,
            count = dayState.dhikrCounts[DhikrType.LaHawla] ?: 0,
            onClick = { onDhikrTap(DhikrType.LaHawla) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DhikrCell(
    dhikr: DhikrType,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TenDaysPalette.SurfaceDark.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dhikr.label,
            fontSize = 12.sp,
            color = TenDaysPalette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$count",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) TenDaysPalette.Green else TenDaysPalette.GrayBorder,
        )
    }
}

@Composable
private fun FastingRow(isFasting: Boolean, onToggle: () -> Unit) {
    ActionRow(
        title = "الصيام",
        pointsLabel = "+١٠٠ نقطة",
    ) {
        Switch(
            checked = isFasting,
            onCheckedChange = { if (!isFasting) onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = TenDaysPalette.Green,
                uncheckedTrackColor = TenDaysPalette.GrayBorder,
            ),
        )
    }
}

@Composable
private fun TakbeerRow(
    count: Int,
    onTap: () -> Unit,
    autoPlay: Boolean,
    onAutoPlayToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "التكبير",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TenDaysPalette.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "+٥ نقاط",
                    fontSize = 11.sp,
                    color = TenDaysPalette.Gold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$count",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TenDaysPalette.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onTap,
                    colors = ButtonDefaults.buttonColors(containerColor = TenDaysPalette.Gold),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("كبّر", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Auto-play toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "تشغيل تلقائي كل ١٠ دقائق",
                fontSize = 12.sp,
                color = TenDaysPalette.TextSecondary,
            )
            Switch(
                checked = autoPlay,
                onCheckedChange = { onAutoPlayToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = TenDaysPalette.Gold,
                    uncheckedTrackColor = TenDaysPalette.GrayBorder,
                ),
            )
        }
    }
}

@Composable
private fun SadaqahRow(isSadaqah: Boolean, onToggle: () -> Unit) {
    ActionRow(
        title = "الصدقة",
        pointsLabel = "+١٥٠ نقطة",
    ) {
        Switch(
            checked = isSadaqah,
            onCheckedChange = { if (!isSadaqah) onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = TenDaysPalette.Green,
                uncheckedTrackColor = TenDaysPalette.GrayBorder,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    pointsLabel: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TenDaysPalette.TextPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = pointsLabel,
                fontSize = 11.sp,
                color = TenDaysPalette.Gold,
            )
        }
        trailing()
    }
}

@Composable
private fun MiniLeaderboard(state: TenDaysUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "المتصدرين",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TenDaysPalette.TextPrimary,
            )
            if (state.selfRank > 0) {
                Text(
                    text = "ترتيبك: #${state.selfRank}",
                    fontSize = 11.sp,
                    color = TenDaysPalette.Gold,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.leaderboard.isEmpty()) {
            Text(
                text = "لا يوجد بيانات بعد",
                fontSize = 12.sp,
                color = TenDaysPalette.TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            state.leaderboard.take(3).forEachIndexed { index, entry ->
                val medal = when (index) {
                    0 -> "🥇"
                    1 -> "🥈"
                    2 -> "🥉"
                    else -> "${index + 1}"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$medal ${entry.uid.take(8)}",
                        fontSize = 12.sp,
                        color = TenDaysPalette.TextPrimary,
                    )
                    Text(
                        text = "${entry.totalScore}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TenDaysPalette.Gold,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/tendays/TenDaysScreen.kt
git commit -m "feat(ten-days): add TenDaysScreen composable with all UI sections"
```

---

### Task 7: DI Registration

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt`

- [ ] **Step 1: Add imports and bindings to AppModule**

Add these imports at the top:
```kotlin
import tools.mo3ta.salo.data.tendays.TenDaysStore
import tools.mo3ta.salo.data.tendays.TenDaysFirebaseClient
import tools.mo3ta.salo.presentation.TenDaysViewModel
```

Add these bindings inside the `module { }` block, after the existing `viewModel` declarations:
```kotlin
    single { TenDaysStore(get()) }
    single { TenDaysFirebaseClient(get()) }
    viewModel { TenDaysViewModel(get(), get(), get()) }
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt
git commit -m "feat(ten-days): register TenDays DI bindings in AppModule"
```

---

### Task 8: Navigation Wiring — App.kt + MohamedLoversScreen

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt`

- [ ] **Step 1: Add navigation state to App.kt**

Add import at top:
```kotlin
import tools.mo3ta.salo.ui.tendays.TenDaysScreen
```

Add state variable after `var showExtensionQr`:
```kotlin
        var showTenDays by remember { mutableStateOf(false) }
```

Update the `PlatformBackHandler` enabled condition to include `showTenDays`:
```kotlin
        PlatformBackHandler(enabled = showTenDays || showHadithList || showAchievements || showSettings || showOnboarding || showExtensionQr) {
            when {
                showExtensionQr -> showExtensionQr = false
                showTenDays -> showTenDays = false
                showHadithList -> showHadithList = false
                showAchievements -> showAchievements = false
                showOnboarding -> showOnboarding = false
                showSettings -> showSettings = false
            }
        }
```

Add `showTenDays` case in the `when` block, before the `else`:
```kotlin
            showTenDays -> TenDaysScreen(onBack = { showTenDays = false })
```

Update `MohamedLoversScreen` call to include `onOpenTenDays`:
```kotlin
            else -> MohamedLoversScreen(
                onOpenAchievements = { showAchievements = true },
                onOpenSettings = { showSettings = true },
                onOpenHadithList = { showHadithList = true },
                onOpenTenDays = { showTenDays = true },
            )
```

- [ ] **Step 2: Add `onOpenTenDays` parameter and icon to MohamedLoversScreen**

Add parameter to the function signature (after `onOpenHadithList`):
```kotlin
    onOpenTenDays: () -> Unit = {},
```

Add a new `IconButton` in the top-start `Row` block, just before the settings `IconButton`:
```kotlin
                    IconButton(onClick = onOpenTenDays) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "عشر ذي الحجة",
                            tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
                        )
                    }
```

Add the import at the top of MohamedLoversScreen.kt:
```kotlin
import androidx.compose.material.icons.filled.WbSunny
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt
git commit -m "feat(ten-days): wire TenDaysScreen navigation with icon in top bar"
```

---

### Task 9: Full Build Verification

- [ ] **Step 1: Run full compile check**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run Android compile**

Run: `./gradlew :app:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Fix any compilation errors**

Address import issues, missing types, or API mismatches.

- [ ] **Step 4: Final commit if fixes needed**

```bash
git add -A
git commit -m "fix(ten-days): resolve compilation issues"
```

---

## Notes for Implementation

- **Period key**: Hardcoded as `"2026-06-06"` — update when actual Dhul Hijjah 1447 dates are confirmed.
- **Auto-play audio**: The toggle is wired in the UI and persisted. Actual background audio playback requires platform-specific implementation (Android `AlarmManager` + `MediaPlayer`, iOS `UNNotificationRequest`). This is left as a follow-up since it needs `expect`/`actual` declarations.
- **Country code in sync**: The ViewModel passes empty string. To fill it, inject `CountryCodeProvider` (already exists as expect/actual in the project) into `TenDaysViewModel`.
- **Leaderboard server script**: Needs a new `populate-ten-days-leaderboard.js` script mirroring `populate-leaderboard.js` but reading from `ten_days_dhul_hijjah/{periodKey}/players` and writing to `ten_days_dhul_hijjah/{periodKey}/leaderboard`. This is a server-side task outside KMP scope.
