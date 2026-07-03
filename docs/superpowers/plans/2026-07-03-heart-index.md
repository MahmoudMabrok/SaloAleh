# Heart Index Feature — Implementation Plan

## Context

The app is a salawat tap counter. This feature adds an emotional "heart index" gauge: every salawat tap fills the heart (+10 points), and the heart decays by 1 point every 10 seconds — including time spent with the app closed. When the score drops to zero or below, the UI nudges the user to "refill his heart" by sending salawat. The goal is a gentle, continuous pull back to the core tap action.

**Confirmed product decisions (via user Q&A):**
- Lives on the main screen (`MohamedLoversScreen`) as a widget near the counter, with a nudge banner.
- Score is **unbounded** in both directions within a week — no max cap, no negative floor.
- Instead of a floor, the heart **resets to 0 every Friday at 22:00 Africa/Cairo** (a fresh start each week, aligned with the app's weekly-round rhythm; note the competition round itself resets 19:00 Friday — the heart reset is deliberately 22:00 per user).
- **Local-only** persistence (multiplatform-settings). No Firebase, no rules changes.

**Mechanics:**
- +10 per tap (`onCountClick`), −1 per 10 s elapsed.
- Two decay phases, one equation: (a) live 10 s ticker while the app is open; (b) on cold start / ON_RESUME, compute elapsed time since the persisted anchor timestamp and apply the same pure function.
- Remainder-safe: 25 s elapsed → decay 2, anchor advances by exactly 20 000 ms so the 5 s remainder is never lost or double-counted.
- Weekly reset semantics: if the persisted anchor predates the most recent Friday-22:00-Cairo boundary, score resets to 0 and the anchor jumps to `now` (fresh start — no retroactive decay piling up from the boundary). This also fires naturally on the next tick if the app is open when the boundary passes.
- Fresh install: score 0, anchor 0 → no decay and no nudge until the first tap (first tap starts the clock).
- Nudge shown when the clock has started and score ≤ 0 (`HEART_LOW_THRESHOLD = 0`, a named constant — easy to tune).

## Files

### New: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/heart/HeartIndexMath.kt`
Pure logic, modeled on `data/time/FinalMinutesTick.kt`:

```kotlin
internal const val HEART_TAP_BONUS = 10
internal const val HEART_DECAY_INTERVAL_MS = 10_000L
internal const val HEART_DECAY_PER_INTERVAL = 1
internal const val HEART_LOW_THRESHOLD = 0
internal val HEART_RESET_DAY = DayOfWeek.FRIDAY   // reset at 22:00 Africa/Cairo
internal const val HEART_RESET_HOUR = 22

internal data class HeartSettleResult(val score: Int, val anchorTs: Long, val didReset: Boolean)

// Most recent Friday 22:00 Cairo boundary <= now (epoch ms). Pure given `now`.
internal fun lastHeartResetBoundary(nowTs: Long, zone: TimeZone = TimeZone.of("Africa/Cairo")): Long

internal fun settleHeart(storedScore: Int, anchorTs: Long, nowTs: Long, resetBoundaryTs: Long): HeartSettleResult {
    if (anchorTs <= 0L) return HeartSettleResult(storedScore, anchorTs, false)  // never started
    if (anchorTs < resetBoundaryTs) return HeartSettleResult(0, nowTs, true)    // weekly reset, fresh start
    if (nowTs <= anchorTs) return HeartSettleResult(storedScore, anchorTs, false)
    val intervals = (nowTs - anchorTs) / HEART_DECAY_INTERVAL_MS                // Long math, overflow-safe
    if (intervals == 0L) return HeartSettleResult(storedScore, anchorTs, false)
    return HeartSettleResult(
        score = (storedScore - intervals * HEART_DECAY_PER_INTERVAL).toInt(),   // unbounded, can go negative
        anchorTs = anchorTs + intervals * HEART_DECAY_INTERVAL_MS,              // remainder carried
        didReset = false,
    )
}
```

Boundary math mirrors `data/time/CompetitionWindowUtils.kt` (which already does next-Friday-19:00-Cairo) — write the "most recent Friday 22:00 ≤ now" variant with `kotlinx.datetime`.

### New: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/heart/HeartStore.kt`
Same shape as `EngagementStore`/`BaqiyatStore` (class takes `com.russhwolf.settings.Settings`, companion-object keys):
- `getScore(): Int` (default 0, negatives round-trip fine), `getAnchorTs(): Long` (default 0), `save(score, anchorTs)`.
- Keys: `"heart_score"`, `"heart_anchor_ts"`.

### Modified: `di/AppModule.kt`
- `single { HeartStore(get()) }` next to the other stores.
- `MohamedLoversViewModel` factory: 7 → 8 `get()`s.

### Modified: `presentation/MohamedLoversUiState.kt`
Append: `val heartScore: Int = 0`, `val showHeartRefillNudge: Boolean = false`.

### Modified: `presentation/MohamedLoversViewModel.kt`
1. Add `heartStore: HeartStore` constructor param (8th).
2. Private `settleHeartDecay(nowTs = Clock.System.now().toEpochMilliseconds())` — the single choke point: compute boundary via `lastHeartResetBoundary(nowTs)`, call `settleHeart(...)`, persist to store only if changed, `_state.update` with `heartScore` and `showHeartRefillNudge = anchorTs > 0 && score <= HEART_LOW_THRESHOLD`.
3. `init`: new coroutine next to the existing 60 s idle ticker (~lines 91–100): `while (isActive) { settleHeartDecay(); delay(HEART_DECAY_INTERVAL_MS) }`. First iteration performs the cold-start offline catch-up (phase b) for free.
4. `onCountClick()` (~line 174, after the existing `saveLastSalawatTimestamp` call): settle-then-add with one shared `now` —
   ```kotlin
   val settled = settleHeart(heartStore.getScore(), heartStore.getAnchorTs(), nowMs, lastHeartResetBoundary(nowMs))
   val heartScore = settled.score + HEART_TAP_BONUS
   val heartAnchor = if (settled.anchorTs <= 0L) nowMs else settled.anchorTs  // first tap starts the clock
   heartStore.save(heartScore, heartAnchor)
   ```
   Keep `settled.anchorTs` (do NOT reset to `now`) so a tap can't erase an owed decay remainder. Fold the two fields into the existing `_state.update` block.
5. `refreshSessionClicks()` (~line 250, already invoked from the screen's ON_RESUME DisposableEffect): add `settleHeartDecay()` as first line — immediate catch-up on warm resume.
6. `submitManualSalawat()` (~line 273): apply the same settle-then-+10 (one salawat act = flat +10).
7. ON_STOP: no change needed — every mutation (tick/tap) persists score + anchor synchronously.

### Modified: strings — all 4 locale files in one pass
`app/src/commonMain/composeResources/values/strings.xml` (after the `idle_*` block ~line 555) + `values-en/`, `values-ur/`, `values-zh/`:

| key | ar (default) | en | ur | zh |
|---|---|---|---|---|
| `heart_index_label` | مؤشر القلب | Heart index | دل کا اشاریہ | 心之指数 |
| `heart_refill_nudge` | املأ قلبك بالصلاة على النبي ﷺ | Refill your heart — send salawat upon the Prophet ﷺ | اپنے دل کو نبی ﷺ پر درود بھیج کر بھر دیں | 为先知 ﷺ 祝祷，重新填满你的心 |

Check for duplicate keys before inserting (CLAUDE.md convention).

### Modified: `ui/MohamedLoversScreen.kt`
Both composables screen-private, mirroring `IdleBanner` (line ~643):
1. `HeartIndexIndicator(score, onClick)` — `Row` with `Icons.Filled.Favorite` (tint red when score > 0, grey when ≤ 0) + score text; placed in the bottom column region near the IdleBanner conditional (~333–343). `contentDescription = stringResource(Res.string.heart_index_label)`.
2. `HeartRefillBanner(onClick)` — clone of `IdleBanner`'s Surface styling with a warm red accent, text `Res.string.heart_refill_nudge`, shown when `state.showHeartRefillNudge`; click → `if (tapsEnabled) viewModel.onCountClick()` (same wiring as IdleBanner). If both banners qualify simultaneously, show only the heart banner.

RTL already handled at the screen root — no extra work.

## Tests (commonTest, existing patterns)

1. **New** `data/heart/HeartIndexMathTest.kt` (pattern: `FinalMinutesTickTest.kt`):
   - anchor = 0 → unchanged; now == anchor → unchanged; 9 999 ms → unchanged
   - 10 000 ms → −1, anchor +10 000; 25 000 ms → −2, anchor +20 000 (remainder carry)
   - composition property: settle at +25 s then +10 s ≡ single settle at +35 s
   - decay through zero into negatives (unbounded); 30-day gap → no overflow
   - weekly reset: anchor before boundary → score 0, anchor = now, `didReset` true; anchor exactly at boundary → normal decay, no reset
   - `lastHeartResetBoundary`: a Thursday `now` → previous Friday 22:00; a Friday 21:59 Cairo → previous week; Friday 22:00/22:01 → same-day boundary
2. **New** `data/heart/HeartStoreTest.kt` (pattern: `EngagementStoreTest.kt`, `MapSettings()`): defaults, roundtrip, negative-score roundtrip.
3. **New** `presentation/MohamedLoversViewModelHeartTest.kt` — extend the `buildViewModel` helper copied from `MohamedLoversViewModelProjectionTest.kt:44-70` with a `HeartStore(MapSettings())` param:
   - tap → `heartScore == 10`, persisted, clock started
   - pre-seed `save(50, now − 35_000)` → after init settle: `heartScore == 47`, stored anchor advanced by exactly 30 000
   - pre-seed low/negative → `showHeartRefillNudge == true`; high score → false
   - pre-seed anchor older than last Friday 22:00 → score reset to 0

## Sequencing

1. `HeartIndexMath.kt` + `HeartIndexMathTest.kt` (pure logic green first)
2. `HeartStore.kt` + test
3. DI + UiState + ViewModel wiring + VM test
4. Strings (4 locales) + screen UI
5. Verification

## Verification

```bash
./gradlew :app:compileCommonMainKotlinMetadata   # common code compiles
./gradlew :app:testDebugUnitTest                 # JVM unit tests (or ./gradlew allTests)
./gradlew assembleDebug                          # Android build
make android                                     # optional: install + launch, tap and watch heart fill, wait 10s ticks, background/foreground to see catch-up decay
```

Manual check on device: tap → +10 per tap; leave idle → −1 every 10 s; kill app, reopen after ~2 min → score dropped by ~12; drive score negative → red-accent refill banner appears; tapping the banner registers a salawat and refills.
