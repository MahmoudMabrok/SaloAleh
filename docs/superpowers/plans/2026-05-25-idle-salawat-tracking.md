# Idle Salawat Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show users how long since their last salawat (client banner) and send FCM push to players idle >8 hours (server segment in populate-leaderboard.js).

**Architecture:** Client stores `lastSalawatTimestamp` in multiplatform Settings, ViewModel ticks every 60s to compute elapsed time, banner below tap button shows formatted Arabic duration. Server reads existing `updatedAt` per player, sends FCM if idle >8h, debounced once per Cairo calendar day via `lastIdleNotifDate` on user node.

**Tech Stack:** Kotlin Multiplatform (Compose Multiplatform UI), multiplatform-settings, Node.js firebase-admin (server script)

---

### Task 1: Add `lastSalawatTimestamp` Storage to SessionStore

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt`

- [ ] **Step 1: Add constant and getter/setter**

Add to the companion object (after `KEY_LAST_KNOWN_RANK` on line ~163):

```kotlin
const val KEY_LAST_SALAWAT_TS = "last_salawat_ts"
```

Add methods (after `saveLastKnownRank` on line ~125):

```kotlin
fun getLastSalawatTimestamp(): Long = settings.getLong(KEY_LAST_SALAWAT_TS, 0L)
fun saveLastSalawatTimestamp(ts: Long) = settings.putLong(KEY_LAST_SALAWAT_TS, ts)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/session/MohamedLoversSessionStore.kt
git commit -m "feat: add lastSalawatTimestamp storage to SessionStore"
```

---

### Task 2: Add String Resources for Idle Banner (All 4 Locales)

**Files:**
- Modify: `app/src/commonMain/composeResources/values/strings.xml`
- Modify: `app/src/commonMain/composeResources/values-en/strings.xml`
- Modify: `app/src/commonMain/composeResources/values-ur/strings.xml`
- Modify: `app/src/commonMain/composeResources/values-zh/strings.xml`

- [ ] **Step 1: Add Arabic strings (values/strings.xml)**

Insert before `</resources>`:

```xml
    <!-- Idle salawat banner -->
    <string name="idle_banner_prefix">لم تصلِّ منذ</string>
    <string name="idle_minutes_one">دقيقة</string>
    <string name="idle_minutes_two">دقيقتين</string>
    <string name="idle_minutes_plural">%1$d دقائق</string>
    <string name="idle_hours_one">ساعة</string>
    <string name="idle_hours_two">ساعتين</string>
    <string name="idle_hours_plural">%1$d ساعات</string>
    <string name="idle_days_one">يوم</string>
    <string name="idle_days_two">يومين</string>
    <string name="idle_days_plural">%1$d أيام</string>
```

- [ ] **Step 2: Add English strings (values-en/strings.xml)**

Insert before `</resources>`:

```xml
    <!-- Idle salawat banner -->
    <string name="idle_banner_prefix">You haven\'t sent salawat for</string>
    <string name="idle_minutes_one">1 minute</string>
    <string name="idle_minutes_two">2 minutes</string>
    <string name="idle_minutes_plural">%1$d minutes</string>
    <string name="idle_hours_one">1 hour</string>
    <string name="idle_hours_two">2 hours</string>
    <string name="idle_hours_plural">%1$d hours</string>
    <string name="idle_days_one">1 day</string>
    <string name="idle_days_two">2 days</string>
    <string name="idle_days_plural">%1$d days</string>
```

- [ ] **Step 3: Add Urdu strings (values-ur/strings.xml)**

Insert before `</resources>`:

```xml
    <!-- Idle salawat banner -->
    <string name="idle_banner_prefix">آپ نے درود نہیں بھیجا</string>
    <string name="idle_minutes_one">ایک منٹ سے</string>
    <string name="idle_minutes_two">دو منٹ سے</string>
    <string name="idle_minutes_plural">%1$d منٹ سے</string>
    <string name="idle_hours_one">ایک گھنٹے سے</string>
    <string name="idle_hours_two">دو گھنٹے سے</string>
    <string name="idle_hours_plural">%1$d گھنٹے سے</string>
    <string name="idle_days_one">ایک دن سے</string>
    <string name="idle_days_two">دو دن سے</string>
    <string name="idle_days_plural">%1$d دن سے</string>
```

- [ ] **Step 4: Add Chinese strings (values-zh/strings.xml)**

Insert before `</resources>`:

```xml
    <!-- Idle salawat banner -->
    <string name="idle_banner_prefix">距上次赞圣已过</string>
    <string name="idle_minutes_one">1 分钟</string>
    <string name="idle_minutes_two">2 分钟</string>
    <string name="idle_minutes_plural">%1$d 分钟</string>
    <string name="idle_hours_one">1 小时</string>
    <string name="idle_hours_two">2 小时</string>
    <string name="idle_hours_plural">%1$d 小时</string>
    <string name="idle_days_one">1 天</string>
    <string name="idle_days_two">2 天</string>
    <string name="idle_days_plural">%1$d 天</string>
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/composeResources/values/strings.xml \
       app/src/commonMain/composeResources/values-en/strings.xml \
       app/src/commonMain/composeResources/values-ur/strings.xml \
       app/src/commonMain/composeResources/values-zh/strings.xml
git commit -m "feat: add idle salawat banner string resources for 4 locales"
```

---

### Task 3: Add Idle Elapsed State and Ticker to ViewModel

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt`

- [ ] **Step 1: Add `lastSalawatElapsedMinutes` to UiState**

In `MohamedLoversUiState` data class, add before the closing parenthesis (after `rankMovementNewRank: Int = 0,` on line ~92):

```kotlin
    // Idle salawat tracking
    val lastSalawatElapsedMinutes: Long? = null,
```

- [ ] **Step 2: Add timestamp write in `onCountClick()`**

In `MohamedLoversViewModel.onCountClick()`, add as the first line inside the function body (after `if (!current.canCount) return` on line ~160):

```kotlin
        sessionStore.saveLastSalawatTimestamp(Clock.System.now().toEpochMilliseconds())
```

Also add the same line at the end of `submitManualSalawat()` (after the `_state.update` block around line ~273, before the closing brace):

```kotlin
        sessionStore.saveLastSalawatTimestamp(Clock.System.now().toEpochMilliseconds())
```

- [ ] **Step 3: Add ticker coroutine in init block**

In `MohamedLoversViewModel` init block, add after the existing `viewModelScope.launch { delay(90_000L); refresh() }` block (after line ~86):

```kotlin
        viewModelScope.launch {
            while (isActive) {
                val ts = sessionStore.getLastSalawatTimestamp()
                val elapsed = if (ts > 0L) {
                    (Clock.System.now().toEpochMilliseconds() - ts) / 60_000L
                } else null
                _state.update { it.copy(lastSalawatElapsedMinutes = elapsed) }
                delay(60_000L)
            }
        }
```

- [ ] **Step 4: Reset elapsed immediately on tap**

In `onCountClick()`, inside the existing `_state.update` block (around line ~182), add to the `copy()` call:

```kotlin
                lastSalawatElapsedMinutes = 0L,
```

Do the same in `submitManualSalawat()` `_state.update` block.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt \
       app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt
git commit -m "feat: add idle salawat elapsed tracking in ViewModel with 60s ticker"
```

---

### Task 4: Add IdleBanner Composable to MohamedLoversScreen

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt`

- [ ] **Step 1: Add duration formatting helper**

Add a private composable function at the bottom of the file (before the closing, after `TopBarTooltip` ends around line ~777):

```kotlin
@Composable
private fun formatIdleDuration(totalMinutes: Long): String {
    val prefix = stringResource(Res.string.idle_banner_prefix)
    val unit = when {
        totalMinutes < 60 -> {
            val m = totalMinutes.toInt()
            when (m) {
                1 -> stringResource(Res.string.idle_minutes_one)
                2 -> stringResource(Res.string.idle_minutes_two)
                else -> stringResource(Res.string.idle_minutes_plural, m)
            }
        }
        totalMinutes < 1440 -> {
            val h = (totalMinutes / 60).toInt()
            when (h) {
                1 -> stringResource(Res.string.idle_hours_one)
                2 -> stringResource(Res.string.idle_hours_two)
                else -> stringResource(Res.string.idle_hours_plural, h)
            }
        }
        else -> {
            val d = (totalMinutes / 1440).toInt()
            when (d) {
                1 -> stringResource(Res.string.idle_days_one)
                2 -> stringResource(Res.string.idle_days_two)
                else -> stringResource(Res.string.idle_days_plural, d)
            }
        }
    }
    return "$prefix $unit"
}
```

- [ ] **Step 2: Add IdleBanner composable**

Add after the `formatIdleDuration` function:

```kotlin
@Composable
private fun IdleBanner(elapsedMinutes: Long, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MohamedLoversPalette.GoldBase.copy(alpha = 0.25f)),
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        Text(
            text = formatIdleDuration(elapsedMinutes),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontFamily = MohamedLoversFonts.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
```

- [ ] **Step 3: Place banner below counter in the screen**

In `MohamedLoversScreen`, locate the `Column` aligned to `Alignment.BottomCenter` (around line ~375). After the manual salawat button `Surface` block (around line ~396), add:

```kotlin
                val elapsedMinutes = state.lastSalawatElapsedMinutes
                if (elapsedMinutes != null && elapsedMinutes >= 1) {
                    Spacer(Modifier.height(8.dp))
                    IdleBanner(
                        elapsedMinutes = elapsedMinutes,
                        onClick = {
                            if (tapsEnabled) viewModel.onCountClick()
                        },
                    )
                }
```

- [ ] **Step 4: Add missing imports if needed**

`TextAlign` is already imported (line 68). `BorderStroke` is used inline as `androidx.compose.foundation.BorderStroke` in this file (line 380) — follow the same pattern in `IdleBanner`, or add the import and use the short form. No new imports strictly required.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt
git commit -m "feat: add idle salawat banner below counter on main screen"
```

---

### Task 5: Add Idle >8h FCM Segment to populate-leaderboard.js

**Files:**
- Modify: `scripts/populate-leaderboard.js`

- [ ] **Step 1: Add helper to get Cairo today string**

Add after the `isRoundFinal()` function (around line ~56):

```javascript
function cairoToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}
```

- [ ] **Step 2: Add idle notification segment**

After the dropped-out users notification block (after `console.log('Wrote ... lastDropOutNotifRound flag(s)');` around line ~268, before the `// --- Ten Days of Dhul Hijjah leaderboard ---` comment), add:

```javascript
  // --- Idle >8h notification segment ---
  const IDLE_THRESHOLD_MS = 8 * 60 * 60 * 1000; // 8 hours
  const nowMs = Date.now();
  const todayStr = cairoToday();
  const isFinal = isRoundFinal(roundKey);

  if (!isFinal) {
    const idleCandidates = allPlayers.filter(p =>
      p.updatedAt && p.score > 0 && (nowMs - p.updatedAt) > IDLE_THRESHOLD_MS
    );
    console.log(`\nIdle >8h check: ${idleCandidates.length} candidate(s) of ${allPlayers.length} total`);

    if (idleCandidates.length > 0) {
      const idleUpdates = {};
      const idlePromises = idleCandidates.map(async p => {
        const userSnap = await db.ref(`mohamed_lovers/users/${p.uid}`).get();
        const user = userSnap.val();
        if (!user?.fcmToken) { console.log(`  idle uid=${p.uid}: no FCM token — skip`); return; }
        if (user.lastIdleNotifDate === todayStr) { console.log(`  idle uid=${p.uid}: already notified today — skip`); return; }
        idleUpdates[`mohamed_lovers/users/${p.uid}/lastIdleNotifDate`] = todayStr;
        return admin.messaging().send({
          token: user.fcmToken,
          notification: {
            title: 'أين صلاتك على النبي ﷺ؟',
            body: 'لم نرك منذ فترة — عُد وأحيِ ذكر الحبيب ﷺ',
          },
          data: {
            title: 'أين صلاتك على النبي ﷺ؟',
            body: 'لم نرك منذ فترة — عُد وأحيِ ذكر الحبيب ﷺ',
          },
        })
          .then(msgId => console.log(`  idle uid=${p.uid}: sent msgId=${msgId}`))
          .catch(e => console.error(`  idle uid=${p.uid}: send failed: ${e.message}`));
      });
      await Promise.all(idlePromises);
      if (Object.keys(idleUpdates).length > 0) {
        await db.ref('/').update(idleUpdates);
        console.log(`Wrote ${Object.keys(idleUpdates).length} lastIdleNotifDate flag(s)`);
      }
    }
  } else {
    console.log('\nRound is final — skipping idle notifications');
  }
```

- [ ] **Step 3: Verify `sortedPlayers` has `updatedAt` field**

The existing code builds an `allPlayers` array sorted by `score` desc, tiebroken by `updatedAt` desc (line ~112). Each entry has `.uid`, `.score`, `.updatedAt`. The idle filter above uses these fields directly.

- [ ] **Step 4: Test locally (dry run)**

```bash
cd scripts && FIREBASE_SERVICE_ACCOUNT="$(cat path/to/sa.json)" FIREBASE_DATABASE_URL="https://kamapp-3b3ac-default-rtdb.firebaseio.com" node populate-leaderboard.js
```

Expected: Console shows `Idle >8h check: N candidate(s)` log line. FCM sends should appear for qualifying players.

- [ ] **Step 5: Commit**

```bash
git add scripts/populate-leaderboard.js
git commit -m "feat: add idle >8h FCM notification segment to populate-leaderboard"
```

---

### Task 6: Verify Android Compilation End-to-End

**Files:** None (verification only)

- [ ] **Step 1: Full Android compile check**

Run: `./gradlew :app:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run common metadata compile**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Fix any compilation errors**

If errors occur, fix them. Common issues:
- Missing import for `Clock`, `TimeZone` — add `import kotlinx.datetime.Clock` and `import kotlinx.datetime.TimeZone`
- Missing `isActive` import — add `import kotlinx.coroutines.isActive`
- `BorderStroke` not found — verify import in MohamedLoversScreen.kt

- [ ] **Step 4: Commit any fixes**

```bash
git add -u
git commit -m "fix: resolve compilation issues in idle salawat tracking"
```
