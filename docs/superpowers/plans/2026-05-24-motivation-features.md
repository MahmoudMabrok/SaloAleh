# Motivation Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three in-session motivation features — overtake alerts, daily salawat milestone badges, and rank movement summary — to make tapping more rewarding.

**Architecture:** All three features use local projection from existing leaderboard data (no new Firebase listeners). Daily badges write a single `dailyBadge` string field to the player node in Firebase, which `populate-leaderboard.js` copies to leaderboard entries and `generate-stats.js` clears nightly. New UI composables render as overlays on the main tap screen.

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform), Firebase RTDB (gitlive-firebase), Node.js scripts, SVG drawable resources.

**Spec:** `docs/superpowers/specs/2026-05-24-motivation-features-design.md`

---

### Task 1: Add Daily Badge Model and Constants

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/DailyBadgeModels.kt`

- [ ] **Step 1: Create DailyBadgeModels.kt**

```kotlin
package tools.mo3ta.salo.domain

enum class DailyBadge(val key: String, val threshold: Int) {
    SPROUT("sprout", 100),
    HEART("heart", 200),
    TASBIH("tasbih", 500),
    DOME("dome", 1000),
    CRESCENT("crescent", 2000),
    CROWN("crown", 5000);

    companion object {
        fun fromTapCount(count: Int): DailyBadge? =
            entries.lastOrNull { count >= it.threshold }

        fun fromKey(key: String): DailyBadge? =
            entries.firstOrNull { it.key == key }

        val VALID_KEYS = entries.map { it.key }.toSet()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/DailyBadgeModels.kt
git commit -m "feat: add DailyBadge enum with thresholds and key mapping"
```

---

### Task 2: Unit Tests for DailyBadge

**Files:**
- Create: `app/src/commonTest/kotlin/tools/mo3ta/salo/domain/DailyBadgeModelsTest.kt`

- [ ] **Step 1: Write tests for DailyBadge.fromTapCount()**

```kotlin
package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyBadgeModelsTest {

    @Test
    fun fromTapCount_belowFirst_returnsNull() {
        assertNull(DailyBadge.fromTapCount(0))
        assertNull(DailyBadge.fromTapCount(99))
    }

    @Test
    fun fromTapCount_exactThresholds() {
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromTapCount(100))
        assertEquals(DailyBadge.HEART, DailyBadge.fromTapCount(200))
        assertEquals(DailyBadge.TASBIH, DailyBadge.fromTapCount(500))
        assertEquals(DailyBadge.DOME, DailyBadge.fromTapCount(1000))
        assertEquals(DailyBadge.CRESCENT, DailyBadge.fromTapCount(2000))
        assertEquals(DailyBadge.CROWN, DailyBadge.fromTapCount(5000))
    }

    @Test
    fun fromTapCount_betweenThresholds_returnsLowerTier() {
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromTapCount(199))
        assertEquals(DailyBadge.HEART, DailyBadge.fromTapCount(499))
        assertEquals(DailyBadge.TASBIH, DailyBadge.fromTapCount(999))
        assertEquals(DailyBadge.DOME, DailyBadge.fromTapCount(1999))
        assertEquals(DailyBadge.CRESCENT, DailyBadge.fromTapCount(4999))
    }

    @Test
    fun fromTapCount_aboveMax_returnsCrown() {
        assertEquals(DailyBadge.CROWN, DailyBadge.fromTapCount(10000))
    }

    @Test
    fun fromKey_validKeys() {
        assertEquals(DailyBadge.SPROUT, DailyBadge.fromKey("sprout"))
        assertEquals(DailyBadge.CROWN, DailyBadge.fromKey("crown"))
    }

    @Test
    fun fromKey_invalidKey_returnsNull() {
        assertNull(DailyBadge.fromKey("unknown"))
        assertNull(DailyBadge.fromKey(""))
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew testDebugUnitTest --tests "tools.mo3ta.salo.domain.DailyBadgeModelsTest"`
Expected: All 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/commonTest/kotlin/tools/mo3ta/salo/domain/DailyBadgeModelsTest.kt
git commit -m "test: add unit tests for DailyBadge.fromTapCount and fromKey"
```

---

### Task 3: Add Badge SVG Resources and Strings

**Files:**
- Create: `app/src/commonMain/composeResources/drawable/badge_sprout.svg`
- Create: `app/src/commonMain/composeResources/drawable/badge_heart.svg`
- Create: `app/src/commonMain/composeResources/drawable/badge_tasbih.svg`
- Create: `app/src/commonMain/composeResources/drawable/badge_dome.svg`
- Create: `app/src/commonMain/composeResources/drawable/badge_crescent.svg`
- Create: `app/src/commonMain/composeResources/drawable/badge_crown.svg`
- Modify: `app/src/commonMain/composeResources/values/strings.xml`
- Modify: `app/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Copy badge SVGs from downloads to drawable resources**

```bash
cp /Users/appleworld/Downloads/salou_alayh_leaderboard_badges_svg/rank_100_sprout.svg app/src/commonMain/composeResources/drawable/badge_sprout.svg
cp /Users/appleworld/Downloads/salou_alayh_leaderboard_badges_svg/rank_200_heart.svg app/src/commonMain/composeResources/drawable/badge_heart.svg
cp /Users/appleworld/Downloads/salou_alayh_leaderboard_badges_svg/rank_500_tasbih.svg app/src/commonMain/composeResources/drawable/badge_tasbih.svg
cp /Users/appleworld/Downloads/salou_alayh_leaderboard_badges_svg/rank_1000_dome.svg app/src/commonMain/composeResources/drawable/badge_dome.svg
cp /Users/appleworld/Downloads/salou_alayh_leaderboard_badges_svg/rank_2000_crescent.svg app/src/commonMain/composeResources/drawable/badge_crescent.svg
cp /Users/appleworld/Downloads/salou_alayh_leaderboard_badges_svg/rank_5000_crown.svg app/src/commonMain/composeResources/drawable/badge_crown.svg
```

- [ ] **Step 2: Add Arabic strings to `values/strings.xml`**

Add before the closing `</resources>` tag:

```xml
    <!-- Motivation: Overtake alerts -->
    <string name="overtake_alert">⬆ تجاوزت المحب #%d!</string>

    <!-- Motivation: Daily milestones -->
    <string name="milestone_today">%d صلاة اليوم!</string>
    <string name="badge_sprout_title">مبتدئ الذكر</string>
    <string name="badge_heart_title">محب النبي ﷺ</string>
    <string name="badge_tasbih_title">كثير الصلاة</string>
    <string name="badge_dome_title">رفيق الذكر</string>
    <string name="badge_crescent_title">من المكثرين</string>
    <string name="badge_crown_title">فارس الصلاة</string>

    <!-- Motivation: Rank movement -->
    <string name="rank_climbed">⬆ صعدت %d مراكز</string>
    <string name="rank_dropped">⬇ نزلت %d مراكز</string>
    <string name="since_last_visit">منذ آخر زيارة</string>
```

- [ ] **Step 3: Add English strings to `values-en/strings.xml`**

Add before the closing `</resources>` tag:

```xml
    <!-- Motivation: Overtake alerts -->
    <string name="overtake_alert">⬆ You passed lover #%d!</string>

    <!-- Motivation: Daily milestones -->
    <string name="milestone_today">%d salawat today!</string>
    <string name="badge_sprout_title">Beginner</string>
    <string name="badge_heart_title">Lover of the Prophet ﷺ</string>
    <string name="badge_tasbih_title">Frequent in Salawat</string>
    <string name="badge_dome_title">Companion of Dhikr</string>
    <string name="badge_crescent_title">Among the Devoted</string>
    <string name="badge_crown_title">Champion of Salawat</string>

    <!-- Motivation: Rank movement -->
    <string name="rank_climbed">⬆ You climbed %d ranks</string>
    <string name="rank_dropped">⬇ You dropped %d ranks</string>
    <string name="since_last_visit">Since last visit</string>
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/composeResources/drawable/badge_*.svg
git add app/src/commonMain/composeResources/values/strings.xml
git add app/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat: add daily badge SVG resources and motivation strings"
```

---

### Task 4: Add `dailyBadge` to Firebase Data Layer

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseApi.kt` (L8-24)
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt` (L238-251 pattern, L272-283)
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversModels.kt` — `FirebaseLeaderboardEntry`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt`

- [ ] **Step 1: Add `writeDailyBadge` to `MohamedLoversFirebaseApi`**

In `MohamedLoversFirebaseApi.kt`, add after `setSupporter` (L23):

```kotlin
    suspend fun writeDailyBadge(roundKey: String, uid: String, badgeKey: String?): Result<Unit>
```

- [ ] **Step 2: Add `dailyBadge` field to `FirebaseLeaderboardEntry`**

In `MohamedLoversModels.kt`, add to `FirebaseLeaderboardEntry` data class after `isSupporter`:

```kotlin
    val dailyBadge: String? = null,
```

- [ ] **Step 3: Implement `writeDailyBadge` in `MohamedLoversFirebaseClient`**

Add after `setSupporter` method (around L265), following the same pattern as `setScoreMasked`:

```kotlin
    override suspend fun writeDailyBadge(roundKey: String, uid: String, badgeKey: String?): Result<Unit> {
        log.d { "writeDailyBadge[$roundKey/$uid] badge=$badgeKey" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                mapOf(DAILY_BADGE_KEY to badgeKey)
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "writeDailyBadge[$roundKey/$uid] ok" } },
                onFailure = { log.e(it) { "writeDailyBadge[$roundKey/$uid] failed" } },
            )
        }
    }
```

Add constant to the companion object:

```kotlin
private const val DAILY_BADGE_KEY = "dailyBadge"
```

- [ ] **Step 4: Parse `dailyBadge` in `toLeaderboardEntry`**

In `MohamedLoversFirebaseClient.kt`, modify `toLeaderboardEntry()` (L272-283). Add after `isSupporter` parsing:

```kotlin
        val dailyBadge = map[DAILY_BADGE_KEY] as? String
```

And add `dailyBadge = dailyBadge` to the `FirebaseLeaderboardEntry(...)` constructor call.

- [ ] **Step 5: Add `writeDailyBadge` pass-through in `MohamedLoversRepository`**

In `MohamedLoversRepository.kt`, add after `setSupporter` method:

```kotlin
    suspend fun writeDailyBadge(roundKey: String, badgeKey: String?): Result<Unit> {
        val uid = ensureAnonymousUser().getOrElse { return Result.failure(it) }
        return firebaseClient.writeDailyBadge(roundKey, uid, badgeKey)
    }
```

- [ ] **Step 6: Verify compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseApi.kt
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversModels.kt
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversRepository.kt
git commit -m "feat: add dailyBadge to Firebase data layer"
```

---

### Task 5: Add `dailyBadge` to Firebase Security Rules

**Files:**
- Modify: `database.rules.json`

- [ ] **Step 1: Add `dailyBadge` validation to player node rules**

In `database.rules.json`, inside the `$uid` player node validation (where `scoreMasked`, `totalExternal`, etc. are validated), add:

```json
"dailyBadge": {
  ".validate": "newData.isString() && (newData.val() === 'sprout' || newData.val() === 'heart' || newData.val() === 'tasbih' || newData.val() === 'dome' || newData.val() === 'crescent' || newData.val() === 'crown')"
}
```

Also update the top-level `".validate"` for the `$uid` node — the current rule requires `newData.hasChildren(['uid','totalCount','updatedAt','countryCode'])`. The `dailyBadge` field is optional, so the existing validation already allows it (it only requires specific children exist, doesn't reject additional ones). The new `dailyBadge` sub-rule validates the value when present.

- [ ] **Step 2: Commit**

```bash
git add database.rules.json
git commit -m "feat: add dailyBadge validation to Firebase security rules"
```

**Note:** Deploy rules with `firebase deploy --only database` after merging — do NOT deploy now.

---

### Task 6: Update UiState with Motivation Event Fields

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt` (L5-16, L24-78)

- [ ] **Step 1: Add `dailyBadge` to `MohamedLoversLeaderboardEntry`**

In `MohamedLoversUiState.kt`, add to `MohamedLoversLeaderboardEntry` data class (L5-16) after `isSupporter`:

```kotlin
    val dailyBadge: String? = null,
```

- [ ] **Step 2: Add motivation event fields to `MohamedLoversUiState`**

Add before the closing `)` of `MohamedLoversUiState` (before L78):

```kotlin
    // Motivation: overtake alerts
    val overtakeRank: Int? = null,

    // Motivation: daily milestone celebration
    val milestoneThreshold: Int? = null,
    val milestoneBadgeKey: String? = null,
    val currentDailyBadge: String? = null,

    // Motivation: rank movement summary
    val rankMovementDelta: Int? = null,
    val rankMovementOldRank: Int = 0,
    val rankMovementNewRank: Int = 0,
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt
git commit -m "feat: add motivation event fields to UiState and LeaderboardEntry"
```

---

### Task 7: Add Milestone Tracking to Session Store

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt`

- [ ] **Step 1: Add milestone and rank tracking keys and methods**

Add to the companion object:

```kotlin
const val KEY_LAST_MILESTONE_DATE = "last_milestone_date"
const val KEY_LAST_MILESTONE_LEVEL = "last_milestone_level"
const val KEY_LAST_KNOWN_RANK = "last_known_rank"
```

Add these methods to `MohamedLoversSessionStore`:

```kotlin
    fun getLastMilestoneLevel(today: String): Int {
        if (settings.getStringOrNull(KEY_LAST_MILESTONE_DATE) != today) return 0
        return settings.getInt(KEY_LAST_MILESTONE_LEVEL, 0)
    }

    fun saveLastMilestoneLevel(today: String, threshold: Int) {
        settings.putString(KEY_LAST_MILESTONE_DATE, today)
        settings.putInt(KEY_LAST_MILESTONE_LEVEL, threshold)
    }

    fun getLastKnownRank(): Int = settings.getInt(KEY_LAST_KNOWN_RANK, 0)

    fun saveLastKnownRank(rank: Int) = settings.putInt(KEY_LAST_KNOWN_RANK, rank)
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt
git commit -m "feat: add milestone and rank tracking to session store"
```

---

### Task 8: Add Overtake Detection and Milestone Logic to ViewModel

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt`

- [ ] **Step 1: Add tracking fields to ViewModel class body**

Add after existing private fields (around L45, near `private var remoteSelfPlayer`, `private var remoteLeaderboard`):

```kotlin
    private var lastProjectedRank: Int = 0
    private var overtakeCooldownUntil: Long = 0L
    private var rankMovementShown: Boolean = false
```

- [ ] **Step 2: Add overtake detection to `applyLeaderboard()`**

In `applyLeaderboard()` (L439-487), after the `_state.update` block, add overtake detection. Replace the entire `_state.update` block at the end of `applyLeaderboard()` with:

```kotlin
        val currentRank = when {
            selfInTop -> topEntries.firstOrNull { it.isCurrentUser }?.rank ?: 0
            else -> remoteSelfPlayer?.rank ?: 0
        }

        // Overtake detection
        var overtakeRank: Int? = null
        if (lastProjectedRank > 0 && currentRank in 1 until lastProjectedRank) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now >= overtakeCooldownUntil) {
                overtakeRank = lastProjectedRank
                overtakeCooldownUntil = now + 5_000L
            }
        }
        if (currentRank > 0) lastProjectedRank = currentRank

        // Rank movement summary (once per app session)
        var rankDelta: Int? = null
        var oldRank = 0
        var newRank = 0
        if (!rankMovementShown && currentRank > 0) {
            val storedRank = sessionStore.getLastKnownRank()
            if (storedRank > 0 && storedRank != currentRank) {
                rankDelta = storedRank - currentRank
                oldRank = storedRank
                newRank = currentRank
                rankMovementShown = true
            }
        }
        if (currentRank > 0) sessionStore.saveLastKnownRank(currentRank)

        _state.update {
            it.copy(
                syncedTotal = selfRemoteTotal,
                isWinner = remoteSelfPlayer?.isWinner == true,
                winnerCode = remoteSelfPlayer?.winnerCode.orEmpty(),
                topPlayers = topEntries,
                selfEntry = selfEntry,
                selfInTop = selfInTop,
                overtakeRank = overtakeRank ?: it.overtakeRank,
                rankMovementDelta = rankDelta ?: it.rankMovementDelta,
                rankMovementOldRank = if (rankDelta != null) oldRank else it.rankMovementOldRank,
                rankMovementNewRank = if (rankDelta != null) newRank else it.rankMovementNewRank,
            )
        }
```

- [ ] **Step 3: Add `dailyBadge` mapping in `applyLeaderboard()`**

In the `topEntries` mapping (where `MohamedLoversLeaderboardEntry` is constructed from `remoteLeaderboard.entries`), add `dailyBadge`:

```kotlin
                dailyBadge = entry.dailyBadge,
```

Also add `dailyBadge` when constructing `selfEntry` for users outside top 10 — but this requires reading from state. For now, use `state.value.currentDailyBadge`:

```kotlin
                dailyBadge = state.value.currentDailyBadge,
```

- [ ] **Step 4: Add milestone detection to `onCountClick()`**

In `onCountClick()` (L150-170), after the `dailyGoalStore.recordTap(today, delta)` call and before `_state.update`, add:

```kotlin
        val todayStr = today.toString()
        val rawTaps = dailyGoalStore.todayProgress(today)
        val badge = DailyBadge.fromTapCount(rawTaps)
        val lastMilestone = sessionStore.getLastMilestoneLevel(todayStr)
        var milestoneThreshold: Int? = null
        var milestoneBadgeKey: String? = null
        if (badge != null && badge.threshold > lastMilestone) {
            milestoneThreshold = badge.threshold
            milestoneBadgeKey = badge.key
            sessionStore.saveLastMilestoneLevel(todayStr, badge.threshold)
            val rk = current.roundKey
            if (rk != null) {
                viewModelScope.launch { repository.writeDailyBadge(rk, badge.key) }
            }
        }
```

Then update the `_state.update` block to include:

```kotlin
                milestoneThreshold = milestoneThreshold ?: it.milestoneThreshold,
                milestoneBadgeKey = milestoneBadgeKey ?: it.milestoneBadgeKey,
                currentDailyBadge = badge?.key ?: it.currentDailyBadge,
```

- [ ] **Step 5: Add dismiss methods for motivation events**

Add after `dismissDailyGoalCompleted` (L278):

```kotlin
    fun dismissOvertake() = _state.update { it.copy(overtakeRank = null) }
    fun dismissMilestone() = _state.update { it.copy(milestoneThreshold = null, milestoneBadgeKey = null) }
    fun dismissRankMovement() = _state.update { it.copy(rankMovementDelta = null) }
```

- [ ] **Step 6: Add `DailyBadge` import**

Add to imports:

```kotlin
import tools.mo3ta.salo.domain.DailyBadge
```

- [ ] **Step 7: Verify compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt
git commit -m "feat: add overtake detection, milestone tracking, and rank movement to ViewModel"
```

---

### Task 9: Create Overlay UI Composables

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/OvertakeOverlay.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MilestoneCelebration.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/RankMovementBanner.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/DailyBadgeIcon.kt`

- [ ] **Step 1: Create OvertakeOverlay.kt**

```kotlin
package tools.mo3ta.salo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.overtake_alert

@Composable
fun OvertakeOverlay(
    overtakeRank: Int?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(overtakeRank) {
        if (overtakeRank != null) {
            visible = true
            delay(2000)
            visible = false
            delay(300)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(
                    color = Color(0xFF2A5E2A).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.overtake_alert, overtakeRank ?: 0),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

- [ ] **Step 2: Create MilestoneCelebration.kt**

```kotlin
package tools.mo3ta.salo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.milestone_today
import tools.mo3ta.salo.domain.DailyBadge

@Composable
fun MilestoneCelebration(
    threshold: Int?,
    badgeKey: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(threshold) {
        if (threshold != null) {
            visible = true
            delay(3000)
            visible = false
            delay(300)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    visible = false
                    onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(
                        color = Color(0xF01A1A2E),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                val badge = badgeKey?.let { DailyBadge.fromKey(it) }
                if (badge != null) {
                    val iconRes = badgeDrawableResource(badge)
                    if (iconRes != null) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Text(
                    text = stringResource(Res.string.milestone_today, threshold ?: 0),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4A853),
                    textAlign = TextAlign.Center,
                )

                if (badge != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = badgeTitleString(badge),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF999999),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create DailyBadgeIcon.kt**

```kotlin
package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.badge_crown
import tools.mo3ta.salo.generated.resources.badge_crescent
import tools.mo3ta.salo.generated.resources.badge_dome
import tools.mo3ta.salo.generated.resources.badge_heart
import tools.mo3ta.salo.generated.resources.badge_sprout
import tools.mo3ta.salo.generated.resources.badge_tasbih
import tools.mo3ta.salo.generated.resources.badge_sprout_title
import tools.mo3ta.salo.generated.resources.badge_heart_title
import tools.mo3ta.salo.generated.resources.badge_tasbih_title
import tools.mo3ta.salo.generated.resources.badge_dome_title
import tools.mo3ta.salo.generated.resources.badge_crescent_title
import tools.mo3ta.salo.generated.resources.badge_crown_title
import tools.mo3ta.salo.domain.DailyBadge

fun badgeDrawableResource(badge: DailyBadge): DrawableResource? = when (badge) {
    DailyBadge.SPROUT -> Res.drawable.badge_sprout
    DailyBadge.HEART -> Res.drawable.badge_heart
    DailyBadge.TASBIH -> Res.drawable.badge_tasbih
    DailyBadge.DOME -> Res.drawable.badge_dome
    DailyBadge.CRESCENT -> Res.drawable.badge_crescent
    DailyBadge.CROWN -> Res.drawable.badge_crown
}

@Composable
fun badgeTitleString(badge: DailyBadge): String = when (badge) {
    DailyBadge.SPROUT -> stringResource(Res.string.badge_sprout_title)
    DailyBadge.HEART -> stringResource(Res.string.badge_heart_title)
    DailyBadge.TASBIH -> stringResource(Res.string.badge_tasbih_title)
    DailyBadge.DOME -> stringResource(Res.string.badge_dome_title)
    DailyBadge.CRESCENT -> stringResource(Res.string.badge_crescent_title)
    DailyBadge.CROWN -> stringResource(Res.string.badge_crown_title)
}

@Composable
fun DailyBadgeIcon(
    badgeKey: String?,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    val badge = badgeKey?.let { DailyBadge.fromKey(it) } ?: return
    val res = badgeDrawableResource(badge) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = badgeTitleString(badge),
        modifier = modifier.size(size),
    )
}
```

- [ ] **Step 4: Create RankMovementBanner.kt**

```kotlin
package tools.mo3ta.salo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.rank_climbed
import tools.mo3ta.salo.generated.resources.rank_dropped
import tools.mo3ta.salo.generated.resources.since_last_visit

@Composable
fun RankMovementBanner(
    delta: Int?,
    oldRank: Int,
    newRank: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(delta) {
        if (delta != null) {
            visible = true
            delay(4000)
            visible = false
            delay(300)
            onDismiss()
        }
    }

    val isClimb = (delta ?: 0) > 0
    val bgColor = if (isClimb) Color(0xFF1A2A1A) else Color(0xFF2A1A1A)
    val borderColor = if (isClimb) Color(0xFF4A8A4A) else Color(0xFF8A4A4A)
    val textColor = if (isClimb) Color(0xFF4AEE4A) else Color(0xFFEE4A4A)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(
                    color = bgColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    visible = false
                    onDismiss()
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(Res.string.since_last_visit),
                style = MaterialTheme.typography.bodySmall,
                color = borderColor,
            )
            Text(
                text = if (isClimb) {
                    stringResource(Res.string.rank_climbed, delta ?: 0)
                } else {
                    stringResource(Res.string.rank_dropped, -(delta ?: 0))
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            Text(
                text = "#$oldRank → #$newRank",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
            )
        }
    }
}
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/OvertakeOverlay.kt
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MilestoneCelebration.kt
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/DailyBadgeIcon.kt
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/RankMovementBanner.kt
git commit -m "feat: add motivation overlay UI composables"
```

---

### Task 10: Wire Overlays into MohamedLoversScreen

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt` (L515-547 area)

- [ ] **Step 1: Add overlay composables to screen**

In `MohamedLoversScreen.kt`, after the existing overlay block (around L542, after `NewRoundCountdownOverlay`), add:

```kotlin
        // Overtake alert
        OvertakeOverlay(
            overtakeRank = state.overtakeRank,
            onDismiss = viewModel::dismissOvertake,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
        )

        // Daily milestone celebration
        MilestoneCelebration(
            threshold = state.milestoneThreshold,
            badgeKey = state.milestoneBadgeKey,
            onDismiss = viewModel::dismissMilestone,
        )

        // Rank movement summary
        RankMovementBanner(
            delta = state.rankMovementDelta,
            oldRank = state.rankMovementOldRank,
            newRank = state.rankMovementNewRank,
            onDismiss = viewModel::dismissRankMovement,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
        )
```

- [ ] **Step 2: Add imports**

Add to the imports section:

```kotlin
import tools.mo3ta.salo.ui.components.OvertakeOverlay
import tools.mo3ta.salo.ui.components.MilestoneCelebration
import tools.mo3ta.salo.ui.components.RankMovementBanner
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt
git commit -m "feat: wire motivation overlays into main screen"
```

---

### Task 11: Show Daily Badge in Leaderboard

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversInfoSheet.kt` (LeaderboardRow area, ~L610-692)

- [ ] **Step 1: Add DailyBadgeIcon to LeaderboardRow**

In the `LeaderboardRow` composable, after the `displayTag` `Text` composable and before the score display, add:

```kotlin
                if (entry.dailyBadge != null) {
                    DailyBadgeIcon(
                        badgeKey = entry.dailyBadge,
                        size = 22.dp,
                    )
                }
```

- [ ] **Step 2: Add import**

```kotlin
import tools.mo3ta.salo.ui.components.DailyBadgeIcon
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversInfoSheet.kt
git commit -m "feat: show daily badge icon in leaderboard rows"
```

---

### Task 12: Update populate-leaderboard.js to Copy dailyBadge

**Files:**
- Modify: `scripts/populate-leaderboard.js`

- [ ] **Step 1: Add dailyBadge to player data collection**

In `populate-leaderboard.js`, at L98-107, where `allPlayers.push({...})` builds the player list from snapshot data, add `dailyBadge` after the `isSupporter` line (L105):

```javascript
        // existing lines for context:
        scoreMasked: data.scoreMasked === true,
        isSupporter: data.isSupporter === true,
        dailyBadge: typeof data.dailyBadge === 'string' ? data.dailyBadge : null,
      });
```

- [ ] **Step 2: Add dailyBadge to weekly leaderboard entry construction**

At L124-132, where the weekly `leaderboard` entries are built, add `dailyBadge` after the `isSupporter` conditional (L131):

```javascript
    // existing lines for context:
    if (player.scoreMasked) entry.scoreMasked = true;
    if (player.isSupporter) entry.isSupporter = true;
    if (player.dailyBadge) entry.dailyBadge = player.dailyBadge;
    leaderboard[String(i + 1)] = entry;
```

- [ ] **Step 3: Add dailyBadge to daily leaderboard entry construction**

At L143-153, where `dailyLeaderboard` entries are built, add after the `isSupporter` conditional (L151):

```javascript
    // existing lines for context:
    if (player.scoreMasked) entry.scoreMasked = true;
    if (player.isSupporter) entry.isSupporter = true;
    if (player.dailyBadge) entry.dailyBadge = player.dailyBadge;
    dailyLeaderboard[String(i + 1)] = entry;
```

- [ ] **Step 4: Commit**

```bash
git add scripts/populate-leaderboard.js
git commit -m "feat: copy dailyBadge from player node to leaderboard entries"
```

---

### Task 13: Add dailyBadge Reset to generate-stats.js

**Files:**
- Modify: `scripts/generate-stats.js`

- [ ] **Step 1: Add dailyBadge clear logic**

In `generate-stats.js`, at L103-106, after the `yesterdayTotalScore` multi-update (`await db.ref('/').update(yesterdayTotalScoreUpdates)`) and before the stats dir creation (L108), add:

```javascript
  // existing line for context (L106):
  // console.log(`Updated yesterdayTotalScore for ${Object.keys(yesterdayTotalScoreUpdates).length} player(s).`);

  // Clear dailyBadge for all players (midnight reset)
  const badgeUpdates = {};
  if (playersSnap.exists()) {
    playersSnap.forEach((child) => {
      if (child.val().dailyBadge) {
        badgeUpdates[`mohamed_lovers/${roundKey}/players/${child.key}/dailyBadge`] = null;
      }
    });
  }
  // Also clear from leaderboard entries
  if (leaderboardSnap.exists()) {
    leaderboardSnap.forEach((child) => {
      if (child.val().dailyBadge) {
        badgeUpdates[`mohamed_lovers/${roundKey}/leaderboard/${child.key}/dailyBadge`] = null;
      }
    });
  }
  if (Object.keys(badgeUpdates).length > 0) {
    await db.ref('/').update(badgeUpdates);
    console.log(`Cleared ${Object.keys(badgeUpdates).length} dailyBadge fields`);
  }

  // existing line for context (L108):
  // if (!fs.existsSync(statsDir)) fs.mkdirSync(statsDir);
```

Note: `playersSnap` and `leaderboardSnap` are already fetched at L35-40 in the `Promise.all` call, so no additional reads needed.

- [ ] **Step 2: Commit**

```bash
git add scripts/generate-stats.js
git commit -m "feat: clear dailyBadge fields in nightly stats run"
```

---

### Task 14: Compile and Test

**Files:** None new — verification only.

- [ ] **Step 1: Full compile check**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Android compile check**

Run: `./gradlew :app:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run existing tests**

Run: `./gradlew testDebugUnitTest`
Expected: All existing tests PASS

- [ ] **Step 4: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: compilation fixes for motivation features"
```
