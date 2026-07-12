# Zabad challenge — implementation plan

New daily challenge: **«سبحان الله وبحمده» ١٠٠ مرة**, with a sin-washing sea simulation.

> «مَنْ قَالَ: سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، فِي يَوْمٍ مِائَةَ مَرَّةٍ، حُطَّتْ خَطَايَاهُ وَإِنْ كَانَتْ مِثْلَ زَبَدِ الْبَحْرِ»

Internal name: `zabad` (زَبَد = sea foam). Accent `#2ED3C4`.
Visual reference: `zabad-sea.html` (interactive mockup, approved direction).

## The mechanic

A round is **100 tasbeehat**. Reaching 100 plays the wash, then the counter returns to 0 and a new
round begins — unlimited rounds per day.

What makes this screen different from the other four challenges: **the sea does not track your
counter — it tracks time.**

| Element | Driven by | Behavior |
|---|---|---|
| Water level | Time since last wash | Rises from a low floor to a ceiling over 12h, then holds. Drains back to the floor after a wash. |
| Foam clumps (الخطايا) | Time since last wash | Count and drift speed both grow with elapsed time — the longer the silence, the more sins may have gathered. |
| Water murk | Time since last wash | Suspended silt thickens; stars and moon dim. |
| Counter (0→100) | Taps | The only progress indicator: a number plus a slim line. **No progress ring**, unlike dhikr/istighfar. |
| The wash | Reaching 100 | A wave sweeps right→left (RTL), carries all foam out of frame and dissolves it into light; water clears to turquoise, then recedes. Elapsed-time clock resets to zero. |

Tuning constants (from the mockup, all in one object so they can be adjusted without touching draw code):

```kotlin
const val ZABAD_ROUND_TARGET = 100
val ZABAD_SEA_CAP = 12.hours     // elapsed time at which the sea reaches its ceiling
const val SEA_FLOOR = 0.74f      // waterline as a fraction of canvas height, freshly washed
const val SEA_CEIL  = 0.44f      // waterline at the cap
const val FOAM_MIN  = 2          // clumps right after a wash
const val FOAM_MAX  = 24         // clumps at the cap
```

Animation must be **delta-time driven** (`withInfiniteAnimationFrameNanos`, clamp `dt` to ~50 ms), not
per-frame constants — the mockup originally counted frames and the timing broke off 60 fps.

## New state (the only thing without precedent in the codebase)

Everything else mirrors the istighfar challenge. The sea clock does not:

| Key (`multiplatform-settings`) | Meaning |
|---|---|
| `zabad_last_wash_ts` | Epoch millis of the last completed round. Sea level/foam/murk are all derived from `now - lastWashTs`. |
| `zabad_rounds_today` | Rounds completed this Cairo day (display + badge). |

On cold start, derive the sea state from the persisted timestamp — so a user who returns after 9
hours opens to a high, foamy sea. **Fresh install:** `lastWashTs = 0`; treat as "no accumulation
yet" and start at the floor rather than showing a maxed-out sea to a first-time user.

Timezone-sensitive rollovers use `Africa/Cairo`, per project convention.

## Files

Mirror the istighfar challenge exactly unless noted.

| File | Note |
|---|---|
| `domain/ZabadChallengeModels.kt` | `ZABAD_CHALLENGE_DAILY_GOAL = 100`, leaderboard entry, day stats |
| `domain/ChallengeBadgeModels.kt` | add `ZABAD("zabad", ZABAD_CHALLENGE_DAILY_GOAL)` to `ChallengeType` |
| `data/zabad/ZabadChallengeStore.kt` | remote-baseline + pending-taps pattern, **plus the two sea-clock keys above** |
| `data/zabad/ZabadChallengeFirebaseClient.kt` | copy of the istighfar client |
| `presentation/ZabadChallengeUiState.kt` / `ZabadChallengeViewModel.kt` | adds `elapsedSinceWash`, `roundsToday`, `isWashing` |
| `ui/ZabadScreen.kt` | single full-bleed `Canvas` sea + HUD overlay |
| `ui/zabad/ZabadDesignSystem.kt` | palette below |
| `ui/zabad/ZabadLeaderboardSheet.kt`, `ManualZabadSheet.kt` | copies |
| `di/AppModule.kt` | `single { ZabadChallengeStore(get()) }`, `single { ZabadChallengeFirebaseClient(get(), get()) }`, `viewModel { ZabadChallengeViewModel(...) }` |
| `App.kt` | `showZabadChallenge -> ZabadScreen(...)` branch |
| `ui/ChallengesScreen.kt` | new `ChallengeItem`, accent `#2ED3C4`, icon `Icons.Default.Waves` |
| `presentation/ChallengesViewModel.kt` | add `zabad` to `ChallengesTotals` + read `zabad_challenge/$dateKey/totalTodayZabad` |

Palette — deliberately outside the four reserved challenge accents (dhikr `#7DD3A8`, baqiyat
`#B68CE0`, istighfar `#C08A3E`, quran `#1F7A5C`):

```
Abyss #04121C   Murk #123B49   Tide #1FA5A0   Clear #2ED3C4
Silt  #5C6B70   Foam #EAF6F4   Moon #E9C46A  (Moon = MohamedLoversPalette.GoldHighlight tie-in)
```

## Backend

RTDB (source of truth), matching the other challenges:

```
zabad_challenge/{dateKey}/
├── totalTodayZabad
├── participantCount
├── leaderboard/
└── players/{uid}/   # uid, count, countryCode, nickname, updatedAt
```

- Firestore mirror: `zabad_challenge/{dateKey}` via `FirestoreMirror.kt` (dual-write, Phase 1).
- `database.rules.json` + `firestore.rules`: copy the istighfar block. Deploy with
  `firebase deploy --only database` and `--only firestore:rules`.
- `scripts/generate-stats.js`: add zabad to the daily aggregate + rank-1 notification, alongside
  dhikr/baqiyat.
- The leaderboard count is **total tasbeehat** (`rounds × 100 + current`), not rounds — keeps it
  comparable to the other challenges.

## Strings

All user-visible text via `stringResource`. Add every key to **all four** locale files in one pass:
`values/` (Arabic, default), `values-en/`, `values-ur/`, `values-zh/`.

Copy from the approved mockup:

| Key | Arabic |
|---|---|
| `zabad_phrase` | سُبْحَانَ اللهِ وَبِحَمْدِهِ |
| `zabad_tap_hint` | اضغط لتُسبِّح — كلَّما طال صمتك، تراكم الزَّبَد وعلا البحر |
| `zabad_progress` | من ١٠٠ |
| `zabad_accumulated` | زَبَدٌ تراكم منذ %1$s س %2$s د |
| `zabad_verdict_title` | حُطَّتْ خَطَايَاهُ |
| `zabad_verdict_sub` | وإن كانت مثل زَبَدِ البحر |
| `challenge_zabad_title` | تسبيح المئة |
| `challenge_zabad_body` | سبحان الله وبحمده ١٠٠ مرة — تُحَطُّ الخطايا ولو كانت مثل زبد البحر |

## Badge

`ChallengeBadgeStore` awards at most one win per Cairo day per challenge. So: **first completed round
of the day earns the badge**; later rounds still add to the leaderboard total but not the badge count.
No change to `ChallengeBadgeStore` itself — just the new `ChallengeType` entry.

## Tests

- `commonTest/data/zabad/ZabadChallengeStoreTest.kt` — pending/remote baseline, day rollover.
- Sea-clock math as a pure function (`elapsed → waterLevel, foamCount, murk`) so it is testable
  without a canvas: floor at 0, ceiling at 12h, monotonic in between, reset on wash.
- `ChallengeBadgeStoreTest` — one win per day even across multiple rounds.

## Build order

1. Domain + store + sea-clock math (+ tests) — no UI.
2. Firebase client, DI, RTDB/Firestore rules.
3. `ZabadScreen` canvas + HUD; wire nav from `ChallengesScreen`.
4. Strings in four locales; leaderboard sheet + manual entry sheet.
5. `generate-stats.js` + `ChallengesViewModel` totals.

Verify with `./gradlew :app:compileCommonMainKotlinMetadata` and `./gradlew allTests`; drive the
real screen for the wash → reset handoff, which is the part unit tests cannot see.

## Open questions

1. **Sea ceiling.** 12h is a guess. Should the sea max out over a day (24h) instead, so the ceiling
   coincides with the daily rollover?
2. **Day rollover vs. wash.** If a user never completes a round, does the sea keep accumulating past
   midnight Cairo, or reset with the day? Currently: keeps accumulating (only a wash clears it).
3. **Rounds display.** Rounds/day are persisted but the approved mockup shows no round tally on the
   screen. Keep it hidden, or surface it in the leaderboard sheet?
