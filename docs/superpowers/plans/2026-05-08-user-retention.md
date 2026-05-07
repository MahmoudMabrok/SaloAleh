# User Retention Enhancement — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve daily opens and round completion via mid-week engagement loop (daily goals, streak grace, round recap) and smart FCM notification script.

**Architecture:** Phase 1 is pure client-side — new `DailyGoalStore`, streak grace in `EngagementStore`, and `RoundRecapSheet` composable wired into `MohamedLoversViewModel`. Phase 2 adds RTDB user metadata writes from client and a Node.js notification script (mirrors `populate-leaderboard.js`) run every 6h via GitHub Actions.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform, kotlinx-datetime, com.russhwolf.settings, dev.gitlive.firebase.database, Node.js + firebase-admin, GitHub Actions.

---

## File Map

### Phase 1 — Mid-Week Engagement Loop

| Action | File |
|--------|------|
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt` |
| Modify | `app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/EngagementStoreTest.kt` |
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/DailyGoalStore.kt` |
| Create | `app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/DailyGoalStoreTest.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt` |
| Modify | `app/src/commonTest/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStoreTest.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt` |
| Create | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/RoundRecapSheet.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt` |
| Modify | `app/src/commonMain/composeResources/values/strings.xml` |

### Phase 2 — Smart Notifications

| Action | File |
|--------|------|
| Modify | `database.rules.json` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt` |
| Modify | `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/SaloFirebaseMessagingService.kt` |
| Modify | `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt` |
| Create | `scripts/notify-users.js` |
| Create | `.github/workflows/notify-users.yml` |

---

## Task 1: EngagementStore — Streak Grace Period

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt`
- Modify: `app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/EngagementStoreTest.kt`

- [ ] **Step 1.1: Write failing tests for grace period**

Add to `EngagementStoreTest.kt`:

```kotlin
@Test
fun missOneDay_graceAvailable_streakPreserved() {
    val s = MapSettings()
    val store = store(s)
    // Build streak of 5
    for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
    // Skip May 1 (miss exactly 1 day), open May 2
    val data = store.recordOpen(today = LocalDate(2026, 5, 2))
    assertEquals(6, data.currentStreak)
    assertTrue(data.graceConsumedNow)
}

@Test
fun missOneDay_graceAlreadyUsed_streakBreaks() {
    val s = MapSettings()
    val store = store(s)
    for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
    // First miss — grace consumed
    store.recordOpen(today = LocalDate(2026, 5, 2))
    // Build streak again within same 7-day window
    store.recordOpen(today = LocalDate(2026, 5, 3))
    store.recordOpen(today = LocalDate(2026, 5, 4))
    // Second miss within same 7-day window — no grace
    val data = store.recordOpen(today = LocalDate(2026, 5, 6))
    assertEquals(1, data.currentStreak)
    assertFalse(data.graceConsumedNow)
}

@Test
fun missOneDay_graceResets_after7Days() {
    val s = MapSettings()
    val store = store(s)
    for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
    // Grace consumed May 2
    store.recordOpen(today = LocalDate(2026, 5, 2))
    // 8 days later — new 7-day window; miss 1 day
    store.recordOpen(today = LocalDate(2026, 5, 9))
    store.recordOpen(today = LocalDate(2026, 5, 10))
    val data = store.recordOpen(today = LocalDate(2026, 5, 12))
    assertTrue(data.graceConsumedNow)
}

@Test
fun missTwoDays_noGrace_streakBreaks() {
    val s = MapSettings()
    val store = store(s)
    for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
    // Miss 2 days — grace only covers 1 missed day
    val data = store.recordOpen(today = LocalDate(2026, 5, 3))
    assertEquals(1, data.currentStreak)
    assertFalse(data.graceConsumedNow)
}

@Test
fun wasGraceConsumedToday_trueAfterGrace() {
    val s = MapSettings()
    val store = store(s)
    for (day in 26..30) store.recordOpen(today = LocalDate(2026, 4, day))
    store.recordOpen(today = LocalDate(2026, 5, 2))
    assertTrue(store.wasGraceConsumedToday(LocalDate(2026, 5, 2)))
    assertFalse(store.wasGraceConsumedToday(LocalDate(2026, 5, 3)))
}
```

- [ ] **Step 1.2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.salo.data.engagement.EngagementStoreTest" 2>&1 | tail -20
```

Expected: compilation error — `graceConsumedNow` does not exist on `EngagementData`.

- [ ] **Step 1.3: Add `graceConsumedNow` to `EngagementData`**

In `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt`, add the field:

```kotlin
data class EngagementData(
    val openCount: Int,
    val currentStreak: Int,
    val newlyEarnedBadge: BadgeType?,
    val shouldRequestNotifPermission: Boolean,
    val graceConsumedNow: Boolean = false,
)
```

- [ ] **Step 1.4: Implement grace period in `EngagementStore`**

In `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt`, add three private helpers and modify `recordOpen`:

```kotlin
fun recordOpen(today: LocalDate): EngagementData {
    val openCount = settings.getInt(KEY_OPEN_COUNT, 0) + 1
    settings.putInt(KEY_OPEN_COUNT, openCount)

    val lastDateStr = settings.getStringOrNull(KEY_LAST_OPEN_DATE)
    val lastDate = lastDateStr?.let { LocalDate.parse(it) }

    var graceConsumedNow = false
    val streak = when {
        lastDate == null -> 1
        lastDate == today -> settings.getInt(KEY_STREAK, 1)
        lastDate == today.minusDays(1) -> settings.getInt(KEY_STREAK, 1) + 1
        lastDate == today.minusDays(2) -> {
            if (isGraceAvailable(today)) {
                consumeGrace(today)
                graceConsumedNow = true
                settings.getInt(KEY_STREAK, 1) + 1
            } else {
                1
            }
        }
        else -> 1
    }

    if (lastDate != today) {
        settings.putString(KEY_LAST_OPEN_DATE, today.toString())
        settings.putInt(KEY_STREAK, streak)
    }

    // ... rest of method unchanged (badge logic, notif permission) ...

    return EngagementData(
        openCount = openCount,
        currentStreak = streak,
        newlyEarnedBadge = newBadge,
        shouldRequestNotifPermission = shouldAskNotif,
        graceConsumedNow = graceConsumedNow,
    )
}

fun wasGraceConsumedToday(today: LocalDate): Boolean {
    val date = settings.getStringOrNull(KEY_GRACE_DATE) ?: return false
    return LocalDate.parse(date) == today
}

private fun isGraceAvailable(today: LocalDate): Boolean {
    val used = settings.getBoolean(KEY_GRACE_USED, false)
    if (!used) return true
    val lastGrace = settings.getStringOrNull(KEY_GRACE_DATE) ?: return true
    return today.toEpochDays() - LocalDate.parse(lastGrace).toEpochDays() >= 7
}

private fun consumeGrace(today: LocalDate) {
    settings.putBoolean(KEY_GRACE_USED, true)
    settings.putString(KEY_GRACE_DATE, today.toString())
}
```

Add keys to companion object:
```kotlin
const val KEY_GRACE_USED = "eng_grace_used"
const val KEY_GRACE_DATE = "eng_grace_date"
```

- [ ] **Step 1.5: Run tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.salo.data.engagement.EngagementStoreTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests pass.

- [ ] **Step 1.6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/EngagementModels.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/EngagementStore.kt \
        app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/EngagementStoreTest.kt
git commit -m "feat: add streak grace period — one skip per 7-day window preserves streak"
```

---

## Task 2: DailyGoalStore

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/DailyGoalStore.kt`
- Create: `app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/DailyGoalStoreTest.kt`

- [ ] **Step 2.1: Write failing tests**

Create `app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/DailyGoalStoreTest.kt`:

```kotlin
package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.MapSettings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyGoalStoreTest {

    private fun store(s: MapSettings = MapSettings()) = DailyGoalStore(s)

    @Test
    fun mondayTarget_is33() {
        val store = store()
        // 2026-04-27 is a Monday
        assertEquals(33, store.todayTarget(LocalDate(2026, 4, 27)))
    }

    @Test
    fun fridayTarget_is200() {
        val store = store()
        // 2026-05-01 is a Friday
        assertEquals(200, store.todayTarget(LocalDate(2026, 5, 1)))
    }

    @Test
    fun progressStartsAtZero() {
        val store = store()
        assertEquals(0, store.todayProgress(LocalDate(2026, 4, 27)))
    }

    @Test
    fun recordTap_accumulatesProgress() {
        val store = store()
        val today = LocalDate(2026, 4, 27)
        store.recordTap(today, 1)
        store.recordTap(today, 2)
        assertEquals(3, store.todayProgress(today))
    }

    @Test
    fun progressResetsOnNewDay() {
        val s = MapSettings()
        val store = store(s)
        store.recordTap(LocalDate(2026, 4, 27), 50)
        assertEquals(0, store.todayProgress(LocalDate(2026, 4, 28)))
    }

    @Test
    fun goalNotComplete_belowTarget() {
        val store = store()
        val today = LocalDate(2026, 4, 27) // Monday, target=33
        store.recordTap(today, 32)
        assertFalse(store.isGoalComplete(today))
    }

    @Test
    fun goalComplete_atTarget() {
        val store = store()
        val today = LocalDate(2026, 4, 27) // Monday, target=33
        store.recordTap(today, 33)
        assertTrue(store.isGoalComplete(today))
    }

    @Test
    fun recordTap_newDayClearsProgress() {
        val s = MapSettings()
        val store = store(s)
        store.recordTap(LocalDate(2026, 4, 27), 100)
        store.recordTap(LocalDate(2026, 4, 28), 5) // Tuesday
        assertEquals(5, store.todayProgress(LocalDate(2026, 4, 28)))
    }
}
```

- [ ] **Step 2.2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.salo.data.engagement.DailyGoalStoreTest" 2>&1 | tail -10
```

Expected: compilation error — `DailyGoalStore` does not exist.

- [ ] **Step 2.3: Implement `DailyGoalStore`**

Create `app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/DailyGoalStore.kt`:

```kotlin
package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class DailyGoalStore(private val settings: Settings) {

    private val targets = mapOf(
        DayOfWeek.MONDAY to 33,
        DayOfWeek.TUESDAY to 66,
        DayOfWeek.WEDNESDAY to 100,
        DayOfWeek.THURSDAY to 133,
        DayOfWeek.FRIDAY to 200,
        DayOfWeek.SATURDAY to 33,
        DayOfWeek.SUNDAY to 33,
    )

    fun todayTarget(today: LocalDate): Int = targets[today.dayOfWeek] ?: 33

    fun recordTap(today: LocalDate, delta: Int) {
        val storedDate = settings.getStringOrNull(KEY_DATE)
        if (storedDate != today.toString()) {
            settings.putString(KEY_DATE, today.toString())
            settings.putInt(KEY_PROGRESS, 0)
        }
        settings.putInt(KEY_PROGRESS, settings.getInt(KEY_PROGRESS, 0) + delta)
    }

    fun todayProgress(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_PROGRESS, 0)
    }

    fun isGoalComplete(today: LocalDate): Boolean = todayProgress(today) >= todayTarget(today)

    private companion object {
        const val KEY_DATE = "daily_goal_date"
        const val KEY_PROGRESS = "daily_goal_progress"
    }
}
```

- [ ] **Step 2.4: Run tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.salo.data.engagement.DailyGoalStoreTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2.5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/engagement/DailyGoalStore.kt \
        app/src/commonTest/kotlin/tools/mo3ta/salo/data/engagement/DailyGoalStoreTest.kt
git commit -m "feat: add DailyGoalStore — per-day tap targets Mon 33 → Fri 200"
```

---

## Task 3: SessionStore — Recap & Personal Best Keys

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt`
- Modify: `app/src/commonTest/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStoreTest.kt`

- [ ] **Step 3.1: Write failing tests**

Add to `MohamedLoversSessionStoreTest.kt`:

```kotlin
@Test
fun getOrSetInstallDate_firstCall_storesAndReturns() {
    val s = MapSettings()
    val store = MohamedLoversSessionStore(s)
    val date = LocalDate(2026, 5, 8)
    val result = store.getOrSetInstallDate(date)
    assertEquals("2026-05-08", result)
    // Second call with different date returns the original
    val result2 = store.getOrSetInstallDate(LocalDate(2026, 5, 9))
    assertEquals("2026-05-08", result2)
}

@Test
fun markRecapShown_getRecapShownRound_roundTrip() {
    val s = MapSettings()
    val store = MohamedLoversSessionStore(s)
    assertNull(store.getRecapShownRound())
    store.markRecapShown("2026-05-09")
    assertEquals("2026-05-09", store.getRecapShownRound())
}

@Test
fun personalBestRank_defaultIsMaxInt() {
    val store = MohamedLoversSessionStore(MapSettings())
    assertEquals(Int.MAX_VALUE, store.getPersonalBestRank())
}

@Test
fun updatePersonalBestRank_onlyImproves() {
    val s = MapSettings()
    val store = MohamedLoversSessionStore(s)
    store.updatePersonalBestRank(5)
    assertEquals(5, store.getPersonalBestRank())
    store.updatePersonalBestRank(8) // worse rank — not saved
    assertEquals(5, store.getPersonalBestRank())
    store.updatePersonalBestRank(2) // better rank — saved
    assertEquals(2, store.getPersonalBestRank())
}

@Test
fun lastRoundTaps_defaultZero_roundTrip() {
    val s = MapSettings()
    val store = MohamedLoversSessionStore(s)
    assertEquals(0, store.getLastRoundTaps())
    store.saveLastRoundTaps(420)
    assertEquals(420, store.getLastRoundTaps())
}
```

Note: these tests require `LocalDate` import — add `import kotlinx.datetime.LocalDate` and `import kotlin.test.assertNull`.

- [ ] **Step 3.2: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.salo.data.session.MohamedLoversSessionStoreTest" 2>&1 | tail -10
```

Expected: compilation errors — methods do not exist yet.

- [ ] **Step 3.3: Add methods to `MohamedLoversSessionStore`**

Add the following methods and keys to `MohamedLoversSessionStore.kt`:

```kotlin
import kotlinx.datetime.LocalDate

// Add after clearPendingSession():

fun getOrSetInstallDate(today: LocalDate): String {
    val stored = settings.getStringOrNull(KEY_INSTALL_DATE)
    if (stored != null) return stored
    val s = today.toString()
    settings.putString(KEY_INSTALL_DATE, s)
    return s
}

fun markRecapShown(roundKey: String) = settings.putString(KEY_RECAP_SHOWN_ROUND, roundKey)
fun getRecapShownRound(): String? = settings.getStringOrNull(KEY_RECAP_SHOWN_ROUND)

fun getPersonalBestRank(): Int = settings.getInt(KEY_PERSONAL_BEST_RANK, Int.MAX_VALUE)
fun updatePersonalBestRank(rank: Int) {
    if (rank < getPersonalBestRank()) settings.putInt(KEY_PERSONAL_BEST_RANK, rank)
}

fun getLastRoundTaps(): Int = settings.getInt(KEY_LAST_ROUND_TAPS, 0)
fun saveLastRoundTaps(taps: Int) = settings.putInt(KEY_LAST_ROUND_TAPS, taps)
```

Add to companion object:
```kotlin
const val KEY_INSTALL_DATE = "install_date"
const val KEY_RECAP_SHOWN_ROUND = "recap_shown_round"
const val KEY_PERSONAL_BEST_RANK = "personal_best_rank"
const val KEY_LAST_ROUND_TAPS = "last_round_taps"
```

- [ ] **Step 3.4: Run tests to confirm they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "tools.mo3ta.salo.data.session.MohamedLoversSessionStoreTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3.5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt \
        app/src/commonTest/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStoreTest.kt
git commit -m "feat: add recap/personal best/install date keys to SessionStore"
```

---

## Task 4: Repository — Recap & User Activity Delegations

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt`

- [ ] **Step 4.1: Add delegation methods**

Add to `MohamedLoversRepository.kt` (after `refreshNetworkTime()`):

```kotlin
import kotlinx.datetime.LocalDate

// Recap
fun markRecapShown(roundKey: String) = sessionStore.markRecapShown(roundKey)
fun getRecapShownRound(): String? = sessionStore.getRecapShownRound()
fun getPersonalBestRank(): Int = sessionStore.getPersonalBestRank()
fun updatePersonalBestRank(rank: Int) = sessionStore.updatePersonalBestRank(rank)
fun getLastRoundTaps(): Int = sessionStore.getLastRoundTaps()
fun saveLastRoundTaps(taps: Int) = sessionStore.saveLastRoundTaps(taps)

// User activity (Phase 2 prep — RTDB write)
fun getOrSetInstallDate(today: LocalDate): String = sessionStore.getOrSetInstallDate(today)
```

- [ ] **Step 4.2: Run all tests to confirm no regressions**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4.3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt
git commit -m "feat: delegate recap and user activity methods from Repository to stores"
```

---

## Task 5: UiState — Recap and Grace Fields

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt`

- [ ] **Step 5.1: Add fields to `MohamedLoversUiState`**

Add these fields to `MohamedLoversUiState` data class (after `showHadithDialog`):

```kotlin
// Round recap (shown once per completed round)
val showRoundRecap: Boolean = false,
val recapRank: Int = 0,
val recapTotalPlayers: Int = 0,
val recapIsPersonalBest: Boolean = false,
val recapTapsDelta: Int = 0,

// Grace warning banner
val showGraceWarning: Boolean = false,

// Daily goal
val dailyGoalTarget: Int = 0,
val dailyGoalProgress: Int = 0,
val dailyGoalJustCompleted: Boolean = false,
```

- [ ] **Step 5.2: Run all tests to confirm no regressions**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5.3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt
git commit -m "feat: add recap, grace warning, and daily goal fields to UiState"
```

---

## Task 6: String Resources

**Files:**
- Modify: `app/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 6.1: Add new strings**

Add before `</resources>`:

```xml
<!-- Round Recap Sheet -->
<string name="recap_title">انتهت الجولة!</string>
<string name="recap_rank_label">جئت في المرتبة #%1$d من %2$d مشاركاً</string>
<string name="recap_personal_best">🏅 أفضل مرتبة لك!</string>
<string name="recap_taps_up">+%1$d صلاة عن الأسبوع الماضي</string>
<string name="recap_taps_down">%1$d صلاة عن الأسبوع الماضي</string>
<string name="recap_cta">ابدأ الجولة الجديدة</string>

<!-- Grace Warning Banner -->
<string name="grace_warning">استُخدِم الحذف الواحد هذا الأسبوع — حافظ على سلسلتك!</string>

<!-- Daily Goal -->
<string name="daily_goal_completed">أحسنت! هدف اليوم اكتمل 🎉</string>
<string name="daily_goal_progress">%1$d / %2$d صلاة</string>
```

- [ ] **Step 6.2: Commit**

```bash
git add app/src/commonMain/composeResources/values/strings.xml
git commit -m "feat: add strings for round recap, grace warning, and daily goal"
```

---

## Task 7: RoundRecapSheet Composable

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/RoundRecapSheet.kt`

- [ ] **Step 7.1: Create the composable**

Create `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/RoundRecapSheet.kt`:

```kotlin
package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.recap_cta
import tools.mo3ta.salo.generated.resources.recap_personal_best
import tools.mo3ta.salo.generated.resources.recap_rank_label
import tools.mo3ta.salo.generated.resources.recap_taps_down
import tools.mo3ta.salo.generated.resources.recap_taps_up
import tools.mo3ta.salo.generated.resources.recap_title
import tools.mo3ta.salo.ui.MohamedLoversPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoundRecapSheet(
    rank: Int,
    totalPlayers: Int,
    isPersonalBest: Boolean,
    tapsDelta: Int,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MohamedLoversPalette.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.recap_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MohamedLoversPalette.GoldHighlight,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.recap_rank_label, rank, totalPlayers),
                fontSize = 18.sp,
                color = MohamedLoversPalette.TextPrimary,
                textAlign = TextAlign.Center,
            )
            if (isPersonalBest) {
                Text(
                    text = stringResource(Res.string.recap_personal_best),
                    fontSize = 15.sp,
                    color = MohamedLoversPalette.GoldHighlight,
                    textAlign = TextAlign.Center,
                )
            }
            if (tapsDelta != 0) {
                val deltaStr = if (tapsDelta > 0)
                    stringResource(Res.string.recap_taps_up, tapsDelta)
                else
                    stringResource(Res.string.recap_taps_down, tapsDelta)
                Text(
                    text = deltaStr,
                    fontSize = 14.sp,
                    color = MohamedLoversPalette.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldHighlight,
                ),
            ) {
                Text(stringResource(Res.string.recap_cta), fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

> **Note:** Check `MohamedLoversPalette` for the exact color names by reading `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversPalette.kt` (or search for `object MohamedLoversPalette`). Replace `TextPrimary`, `TextSecondary`, `Surface` with the actual names used in that file.

- [ ] **Step 7.2: Build to confirm it compiles**

```bash
./gradlew :app:compileCommonMainKotlinMetadata 2>&1 | grep -E "error:|warning:" | head -20
```

Expected: no errors.

- [ ] **Step 7.3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/RoundRecapSheet.kt
git commit -m "feat: add RoundRecapSheet bottom sheet composable"
```

---

## Task 8: ViewModel — Wire Recap, Grace, and Daily Goals

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt`

- [ ] **Step 8.1: Add `DailyGoalStore` to ViewModel constructor**

Change the constructor signature:

```kotlin
class MohamedLoversViewModel(
    private val repository: MohamedLoversRepository,
    private val engagementStore: EngagementStore,
    private val hadithStore: DailyHadithStore,
    private val dailyGoalStore: DailyGoalStore,
) : ViewModel()
```

- [ ] **Step 8.2: Wire grace warning in `init`**

Add to the `init` block (after the hadith dialog and `refresh()` calls):

```kotlin
init {
    _state.update { it.copy(showHadithDialog = hadithStore.showOnStartup) }
    refresh()
    // Grace warning — check if grace was consumed today
    val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
    if (engagementStore.wasGraceConsumedToday(today)) {
        _state.update { it.copy(showGraceWarning = true) }
    }
    // Daily goal
    _state.update {
        it.copy(
            dailyGoalTarget = dailyGoalStore.todayTarget(today),
            dailyGoalProgress = dailyGoalStore.todayProgress(today),
        )
    }
    viewModelScope.launch {
        delay(90_000L)
        refresh()
    }
}
```

- [ ] **Step 8.3: Update `onCountClick()` to track goal progress**

In `onCountClick()`, after `val pending = repository.registerLocalTap(...)`, add:

```kotlin
val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
val wasComplete = dailyGoalStore.isGoalComplete(today)
dailyGoalStore.recordTap(today, delta)
val isNowComplete = dailyGoalStore.isGoalComplete(today)
_state.update {
    it.copy(
        sessionClicks = pending.clickCount,
        error = null,
        dailyGoalProgress = dailyGoalStore.todayProgress(today),
        dailyGoalJustCompleted = !wasComplete && isNowComplete,
    )
}
```

Replace the existing `_state.update { it.copy(sessionClicks = pending.clickCount, error = null) }` line.

- [ ] **Step 8.4: Wire round recap in `connectToLeaderboardIfPossible()`**

In the `leaderboardJob` coroutine, inside the `leaderboard.isFinal` block (after the existing rank achievement check), add:

```kotlin
if (leaderboard.isFinal) {
    val match = leaderboard.entries.firstOrNull { it.uid == uid }
    if (match != null) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val achievement = engagementStore.checkAndSaveRankAchievement(roundKey, match.rank, today)
        if (achievement != null) {
            _state.update { it.copy(newlyEarnedRankAchievement = achievement) }
        }
    }
    // Round recap
    val recapRound = repository.getRecapShownRound()
    if (recapRound != roundKey) {
        val rank = remoteSelfPlayer?.rank ?: 0
        val syncedTaps = state.value.syncedTotal
        val lastTaps = repository.getLastRoundTaps()
        val prevBest = repository.getPersonalBestRank()
        val isPersonalBest = rank in 1..10 && rank < prevBest
        if (rank > 0) {
            repository.updatePersonalBestRank(rank)
            repository.saveLastRoundTaps(syncedTaps)
            repository.markRecapShown(roundKey)
            _state.update {
                it.copy(
                    showRoundRecap = true,
                    recapRank = rank,
                    recapTotalPlayers = it.roundPlayerCount,
                    recapIsPersonalBest = isPersonalBest,
                    recapTapsDelta = syncedTaps - lastTaps,
                )
            }
        }
    }
}
```

- [ ] **Step 8.5: Add `dismissRoundRecap()` and `dismissGraceWarning()` methods**

```kotlin
fun dismissRoundRecap() = _state.update { it.copy(showRoundRecap = false) }
fun dismissGraceWarning() = _state.update { it.copy(showGraceWarning = false) }
fun dismissDailyGoalCompleted() = _state.update { it.copy(dailyGoalJustCompleted = false) }
```

- [ ] **Step 8.6: Build to confirm it compiles**

```bash
./gradlew :app:compileCommonMainKotlinMetadata 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 8.7: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt
git commit -m "feat: wire round recap, grace warning, and daily goal into ViewModel"
```

---

## Task 9: AppModule and Screen Wiring

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt`

- [ ] **Step 9.1: Add `DailyGoalStore` to AppModule**

In `AppModule.kt`, add:

```kotlin
import tools.mo3ta.salo.data.engagement.DailyGoalStore

// Add alongside other singles:
single { DailyGoalStore(get()) }
```

Update the ViewModel binding:

```kotlin
viewModel { MohamedLoversViewModel(get(), get(), get(), get()) }
```

(The 4th `get()` resolves `DailyGoalStore`.)

- [ ] **Step 9.2: Add `RoundRecapSheet` and grace banner to `MohamedLoversScreen`**

In `MohamedLoversScreen.kt`, after the `MohamedLoversInfoSheet(...)` call, add:

```kotlin
import tools.mo3ta.salo.ui.components.RoundRecapSheet

// After MohamedLoversInfoSheet:
if (state.showRoundRecap) {
    RoundRecapSheet(
        rank = state.recapRank,
        totalPlayers = state.recapTotalPlayers,
        isPersonalBest = state.recapIsPersonalBest,
        tapsDelta = state.recapTapsDelta,
        onDismiss = { viewModel.dismissRoundRecap() },
    )
}
```

For the grace warning banner, find the main `Box` or `Column` at the top of the screen and add a banner. Locate where the screen content starts (after `Scaffold` or top-level `Box`) and add inside the box, below the top bar:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import tools.mo3ta.salo.generated.resources.grace_warning

// Inside the main screen Box, after the top bar:
AnimatedVisibility(visible = state.showGraceWarning) {
    androidx.compose.material3.Surface(
        color = MohamedLoversPalette.GoldHighlight.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.grace_warning),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { viewModel.dismissGraceWarning() },
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MohamedLoversPalette.GoldHighlight,
        )
    }
}
```

> **Note:** Read `MohamedLoversScreen.kt` to find the exact location to insert this banner (after the top-bar row, before the counter section). Use the file's existing layout structure.

- [ ] **Step 9.3: Build to confirm it compiles**

```bash
./gradlew :app:compileCommonMainKotlinMetadata 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 9.4: Run full test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9.5: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt
git commit -m "feat: wire DailyGoalStore into DI, show RoundRecapSheet and grace banner in screen"
```

---

## Task 10: RTDB Security Rules — Users Node

**Files:**
- Modify: `database.rules.json`

- [ ] **Step 10.1: Add `users` node rules**

In `database.rules.json`, inside `"mohamed_lovers"`, add after the `"$round"` block:

```json
"users": {
  "$uid": {
    ".read": false,
    ".write": "auth == null || auth.uid != null",
    "fcmToken": {
      ".validate": "newData.isString() && newData.val().length > 0"
    },
    "installDate": {
      ".validate": "newData.isString() && newData.val().length == 10"
    },
    "lastOpenDate": {
      ".validate": "newData.isString() && newData.val().length == 10"
    },
    "lastRivalNotifDate": {
      ".validate": "newData.isString() && newData.val().length == 10"
    },
    "$other": { ".validate": false }
  }
}
```

> The app uses anonymous auth (no `auth.uid` constraint needed for writes since the uid is the hashed local UID, not Firebase auth UID). The `.write` rule allows unauthenticated writes — this matches the existing players node which allows writes based on `newData.child('uid').val() === $uid`. If the app enforces Auth UID matching, update this rule accordingly.

- [ ] **Step 10.2: Deploy rules**

```bash
cd /Users/appleworld/Documents/SaloAleh && npx firebase-tools deploy --only database
```

If `firebase-tools` is not installed locally, deploy via Firebase Console or add it: `npm install -g firebase-tools`.

- [ ] **Step 10.3: Commit**

```bash
git add database.rules.json
git commit -m "feat: add RTDB security rules for mohamed_lovers/users/ node"
```

---

## Task 11: FirebaseClient — User Activity Write Methods

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt`

- [ ] **Step 11.1: Add `writeUserActivity` and `writeFcmToken`**

Add to `MohamedLoversFirebaseClient.kt` (after `incrementSession`):

```kotlin
suspend fun writeUserActivity(uid: String, installDate: String, lastOpenDate: String): Result<Unit> {
    log.d { "writeUserActivity[$uid]" }
    return runCatching {
        Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid").updateChildren(
            mapOf(
                "installDate" to installDate,
                "lastOpenDate" to lastOpenDate,
            )
        )
    }.also { result ->
        result.fold(
            onSuccess = { log.d { "writeUserActivity[$uid] ok" } },
            onFailure = { log.e(it) { "writeUserActivity[$uid] failed" } },
        )
    }
}

suspend fun writeFcmToken(uid: String, token: String): Result<Unit> {
    log.d { "writeFcmToken[$uid]" }
    return runCatching {
        Firebase.database.reference("$ROOT_PATH/$USERS_PATH/$uid").updateChildren(
            mapOf("fcmToken" to token)
        )
    }.also { result ->
        result.fold(
            onSuccess = { log.d { "writeFcmToken[$uid] ok" } },
            onFailure = { log.e(it) { "writeFcmToken[$uid] failed" } },
        )
    }
}
```

Add to companion object:
```kotlin
const val USERS_PATH = "users"
```

- [ ] **Step 11.2: Build to confirm it compiles**

```bash
./gradlew :app:compileCommonMainKotlinMetadata 2>&1 | grep -E "error:" | head -10
```

Expected: no errors.

- [ ] **Step 11.3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt
git commit -m "feat: add writeUserActivity and writeFcmToken to FirebaseClient"
```

---

## Task 12: Repository + ViewModel — Write User Activity

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt`

- [ ] **Step 12.1: Add `writeUserActivity` to Repository**

Add to `MohamedLoversRepository.kt`:

```kotlin
suspend fun writeUserActivity(uid: String, today: kotlinx.datetime.LocalDate): Result<Unit> {
    val installDate = sessionStore.getOrSetInstallDate(today)
    val lastOpenDate = today.toString()
    return firebaseClient.writeUserActivity(uid, installDate, lastOpenDate)
}
```

- [ ] **Step 12.2: Call `writeUserActivity` from ViewModel after auth**

In `MohamedLoversViewModel.connectToLeaderboardIfPossible()`, after `authUid = uid`, add:

```kotlin
authUid = uid
_state.update { it.copy(selfDisplayTag = buildMohamedLoversDisplayTag(uid, it.countryCode)) }

// Write user activity to RTDB (fire-and-forget)
val today = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))
launch { repository.writeUserActivity(uid, today) }
```

- [ ] **Step 12.3: Build to confirm it compiles**

```bash
./gradlew :app:compileCommonMainKotlinMetadata 2>&1 | grep -E "error:" | head -10
```

Expected: no errors.

- [ ] **Step 12.4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt \
        app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt
git commit -m "feat: write installDate and lastOpenDate to RTDB on each app open"
```

---

## Task 13: FCM Token Write on New Token

**Files:**
- Modify: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/SaloFirebaseMessagingService.kt`

- [ ] **Step 13.1: Write FCM token to RTDB in `onNewToken`**

Replace the `onNewToken` method:

```kotlin
import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore

override fun onNewToken(token: String) {
    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        val settings = SharedPreferencesSettings(
            getSharedPreferences("ml_session", Context.MODE_PRIVATE)
        )
        val uid = MohamedLoversSessionStore(settings).getOrCreateUid()
        MohamedLoversFirebaseClient(MohamedLoversSessionStore(settings)).writeFcmToken(uid, token)
    }
}
```

- [ ] **Step 13.2: Build to confirm it compiles**

```bash
./gradlew :app:assembleDebug 2>&1 | grep -E "error:" | head -10
```

Expected: no errors.

- [ ] **Step 13.3: Commit**

```bash
git add app/src/androidMain/kotlin/tools/mo3ta/salo/notification/SaloFirebaseMessagingService.kt
git commit -m "feat: write FCM token to RTDB on token refresh"
```

---

## Task 14: notify-users.js Script

**Files:**
- Create: `scripts/notify-users.js`

- [ ] **Step 14.1: Create the notification script**

Create `scripts/notify-users.js`:

```javascript
// Reads user activity from RTDB, evaluates notification segments,
// sends FCM messages for at-risk users. Runs every 6h via GitHub Actions.
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Mirrors CompetitionWindowUtils.kt — next Friday 18:00 Cairo
function cairoRoundKey() {
  const now = new Date();
  const zone = 'Africa/Cairo';
  const weekdayStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(now);
  const dayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const jsDow = dayMap[weekdayStr];
  const hourStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(now);
  const cairoHour = parseInt(hourStr, 10);
  let daysToFriday = (5 - jsDow + 7) % 7;
  if (daysToFriday === 0 && cairoHour >= 18) daysToFriday = 7;
  const fridayDate = new Date(now.getTime() + daysToFriday * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
}

function cairoToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

function isRoundFinal(roundKey) {
  const now = new Date();
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hour12: false,
  });
  const parts = Object.fromEntries(fmt.formatToParts(now).map(p => [p.type, p.value]));
  const cairoDate = `${parts.year}-${parts.month}-${parts.day}`;
  const cairoHour = parseInt(parts.hour, 10);
  if (cairoDate > roundKey) return true;
  if (cairoDate === roundKey && cairoHour >= 18) return true;
  return false;
}

function daysBetween(dateStr1, dateStr2) {
  const d1 = new Date(dateStr1);
  const d2 = new Date(dateStr2);
  return Math.round((d2 - d1) / 86400000);
}

async function main() {
  const db = admin.database();
  const roundKey = cairoRoundKey();
  const today = cairoToday();
  const isFinal = isRoundFinal(roundKey);

  console.log(`Round: ${roundKey} | isFinal: ${isFinal} | Today: ${today}`);

  // Read Remote Config for thresholds (with defaults)
  const rivalThreshold = parseInt(process.env.NOTIF_RIVAL_THRESHOLD || '200', 10);
  const rivalEnabled = process.env.NOTIF_RIVAL_ENABLED !== 'false';
  const midweekEnabled = process.env.NOTIF_MIDWEEK_ENABLED !== 'false';

  // Read all users
  const usersSnap = await db.ref('mohamed_lovers/users').get();
  if (!usersSnap.exists()) { console.log('No users found.'); process.exit(0); }

  // Read leaderboard for 10th-place score (needed for rival alert)
  let tenthPlaceScore = null;
  if (rivalEnabled && !isFinal) {
    const lbSnap = await db.ref(`mohamed_lovers/${roundKey}/leaderboard/10`).get();
    if (lbSnap.exists()) tenthPlaceScore = lbSnap.val()?.score ?? null;
  }

  const yesterday = new Date(new Date().getTime() - 86400000);
  const yesterdayStr = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(yesterday);

  const sendPromises = [];
  const updates = {};

  usersSnap.forEach(userSnap => {
    const uid = userSnap.key;
    const user = userSnap.val();
    const { fcmToken, installDate, lastOpenDate, lastRivalNotifDate } = user || {};

    if (!fcmToken) return; // no token — can't send

    const daysInactive = lastOpenDate ? daysBetween(lastOpenDate, today) : null;
    const daysInstalled = installDate ? daysBetween(installDate, today) : null;

    // Segment 1: Day-1 lapsed
    if (daysInstalled === 1 && daysInactive >= 1) {
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'السلام عليكم', body: 'لم تبدأ بعد — الجمعة القادمة فرصتك' },
        }).catch(e => console.error(`day1_lapsed ${uid}: ${e.message}`))
      );
      return; // only one notification per user per run
    }

    // Segment 2: Mid-week inactive (3+ days, round active)
    if (midweekEnabled && !isFinal && daysInactive >= 3) {
      const daysToFriday = Math.max(0, daysBetween(today, roundKey));
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'نفتقدك 🤍', body: `مضاعفة الجمعة بعد ${daysToFriday} أيام — أين أنت؟` },
        }).catch(e => console.error(`midweek_inactive ${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 3: Round-end recap (isFinal, user not notified yet)
    if (isFinal && lastOpenDate && lastOpenDate < today) {
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'انتهت الجولة! 🏆', body: 'افتح التطبيق لتعرف ترتيبك النهائي' },
        }).catch(e => console.error(`round_end ${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 4: Streak at risk — opened yesterday but not today (approximation; true streak is device-local)
    if (!isFinal && lastOpenDate === yesterdayStr && daysInactive === 1) {
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'لا تنقطع سلسلتك! 🔥', body: 'سلسلتك على المحك — افتح التطبيق الآن' },
        }).catch(e => console.error(`streak_at_risk ${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 5: Rival alert (out of top 10, close to entering)
    if (rivalEnabled && !isFinal && tenthPlaceScore !== null && lastRivalNotifDate !== today) {
      // Read user's score from player node
      sendPromises.push(
        db.ref(`mohamed_lovers/${roundKey}/players/${uid}/totalCount`).get().then(snap => {
          const userScore = snap.val() ?? 0;
          const gap = tenthPlaceScore - userScore;
          if (gap > 0 && gap <= rivalThreshold) {
            updates[`mohamed_lovers/users/${uid}/lastRivalNotifDate`] = today;
            return admin.messaging().send({
              token: fcmToken,
              notification: { title: 'قريب من الصدارة! 🔥', body: `أنت على بُعد ${gap} صلاة من دخول قائمة الأوائل!` },
            });
          }
        }).catch(e => console.error(`rival_alert ${uid}: ${e.message}`))
      );
    }
  });

  await Promise.all(sendPromises);

  if (Object.keys(updates).length > 0) {
    await db.ref('/').update(updates);
    console.log(`Updated ${Object.keys(updates).length} rival notif debounce flags.`);
  }

  console.log(`Processed ${sendPromises.length} notification sends.`);
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
```

- [ ] **Step 14.2: Test the script locally (dry run)**

```bash
cd /Users/appleworld/Documents/SaloAleh/scripts && \
FIREBASE_SERVICE_ACCOUNT='{}' FIREBASE_DATABASE_URL='https://example.firebaseio.com' \
node -e "require('./notify-users.js')" 2>&1 | head -5
```

Expected: script starts, fails with auth error (not a valid credential) — confirms it loads without syntax errors.

- [ ] **Step 14.3: Commit**

```bash
git add scripts/notify-users.js
git commit -m "feat: add notify-users.js — FCM notification script for at-risk user segments"
```

---

## Task 15: GitHub Actions Workflow — notify-users.yml

**Files:**
- Create: `.github/workflows/notify-users.yml`

- [ ] **Step 15.1: Create the workflow**

Create `.github/workflows/notify-users.yml`:

```yaml
name: Send User Retention Notifications

on:
  schedule:
    - cron: '0 */6 * * *'   # every 6 hours
  workflow_dispatch:
    inputs:
      rival_threshold:
        description: 'Tap gap to trigger rival alert (default 200)'
        required: false
        default: '200'

jobs:
  notify:
    name: Evaluate segments and send FCM notifications
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install firebase-admin
        run: npm install firebase-admin
        working-directory: scripts

      - name: Send notifications
        run: node scripts/notify-users.js
        env:
          FIREBASE_SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
          FIREBASE_DATABASE_URL: ${{ secrets.FIREBASE_DATABASE_URL }}
          NOTIF_RIVAL_THRESHOLD: ${{ github.event.inputs.rival_threshold || '200' }}
          NOTIF_RIVAL_ENABLED: 'true'
          NOTIF_MIDWEEK_ENABLED: 'true'
```

- [ ] **Step 15.2: Commit and verify workflow appears in GitHub**

```bash
git add .github/workflows/notify-users.yml
git commit -m "feat: add notify-users.yml — 6h cron for FCM retention notifications"
git push origin main
```

Then check: `gh workflow list` — confirm `notify-users.yml` appears.

- [ ] **Step 15.3: Trigger a manual test run**

```bash
gh workflow run notify-users.yml --ref main
```

Wait ~60s then check:

```bash
gh run list --workflow=notify-users.yml --limit 3
```

Expected: most recent run shows `completed` with `success` or `failure` (failure is OK if RTDB has no users yet — script exits cleanly on empty users node).

---

## Done — Verification Checklist

- [ ] All unit tests pass: `./gradlew :app:testDebugUnitTest`
- [ ] Debug APK builds: `./gradlew :app:assembleDebug`
- [ ] `notify-users.yml` workflow shows in `gh workflow list`
- [ ] RTDB rules deployed — check Firebase Console > Database > Rules
- [ ] `mohamed_lovers/users/{uid}` node appears in RTDB after opening the app once post-deploy
