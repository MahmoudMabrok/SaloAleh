# Ghars challenge — implementation plan

New daily challenge: **«سبحان الله العظيم وبحمده»**, with a palm-grove planting simulation.

> «مَنْ قَالَ: سُبْحَانَ اللَّهِ الْعَظِيمِ وَبِحَمْدِهِ، غُرِسَتْ لَهُ نَخْلَةٌ فِي الْجَنَّةِ» — رواه الترمذي وحسّنه

Internal name: `ghars` (غَرْس = planting). Accent `#1F5C40`.
Visual reference: `ghars-grove.html` (interactive mockup, approved direction).

## Relationship to the Zabad challenge — read this first

`zabad` already exists and is **also** a tasbeeh challenge: «سبحان الله وبحمده», daily goal 100,
procedural rising-foam visual. Ghars is a **different hadith** (`العظيم`) with a **different reward**
(a palm planted, not sins washed), so it is a legitimately distinct dhikr — but the two will sit
adjacent in `ChallengesScreen` as two goal-100 tap counters with a procedural visual.

This is a **product decision to confirm before building**, not a technical blocker. If both ship,
their cards must read as clearly different at a glance (different accent, different reward copy).

The upside: **`zabad` is the template.** It is the freshest complete vertical slice and has exactly
the shape ghars needs, down to a pure-math file plus its test. Phases 1, 3 and 4 below are close to a
mechanical rename of the zabad slice. The only genuinely new code is the renderer (phase 2).

## The mechanic

One tap = one tasbeeh = **one palm planted**. Daily goal **100 palms**, resetting at Cairo midnight
like every other challenge. Score is unbounded — a user may plant well past 100.

What makes this screen different from the other five challenges: **there is no progress ring. The
counter is the grove.**

| Element | Driven by | Behaviour |
|---|---|---|
| Palms on screen | `count` | One palm per tasbeeh, planted in the front row at the next free slot. **Capped at 25** — see the wrap, below. |
| Row depth | Number of palms | Front row holds 7; when it fills, the grove steps back a row (8, then 10). Rows are staggered into a quincunx, like a real date grove. |
| The wrap | Every 25th palm | The 25th palm **bears dates** and completes the grove. The next tasbeeh sends the whole grove **receding to the horizon**, where it joins a hazed tree-line, and breaks fresh soil in front. |
| Tree-line | Groves completed today | A single procedural canopy path on the horizon; density grows with `completedGroves`. Resets with the day. |
| Sprout | A tap | The new palm rises out of the soil over 620 ms, fronds unfurling, with a soil-ring burst. |
| Sheet | `count` | Today's count, the daily goal, four grove pips, a progress bar to 100. |

### The wrap is why this is cheap

```kotlin
fun shownPalms(count: Int)      = if (count <= 0) 0 else ((count - 1) % GROVE_SIZE) + 1  // ≤ 25
fun groveStartIndex(count: Int) = if (count <= 0) 0 else ((count - 1) / GROVE_SIZE) * GROVE_SIZE
fun completedGroves(count: Int) = if (count <= 0) 0 else count / GROVE_SIZE
fun completesGrove(count: Int)  = count > 1 && (count - 1) % GROVE_SIZE == 0
```

**The canvas never draws a 26th palm.** Render cost is a constant, independent of score — no
`ImageBitmap` caching, no LOD, nothing to profile on a low-end device. A hard clear at 25 would read
as losing your work, so the finished grove *recedes* rather than being erased: the day's total stays
legible as distance.

Tuning constants (all in `GroveMath.kt` so they can be adjusted without touching draw code):

```kotlin
const val GHARS_CHALLENGE_DAILY_GOAL = 100     // four complete groves
const val GROVE_SIZE = 25                      // palms per grove; hard drawing ceiling
val ROW_CAPACITY = intArrayOf(7, 8, 10)        // palms per depth row, nearest first; sums to 25
const val ROW_DEPTH_FALLOFF = 0.72f            // scale multiplier per row back
const val HAZE_MAX = 0.62f                     // atmospheric haze cap on the furthest row
```

Animation must be **delta-time driven** (`withInfiniteAnimationFrameNanos`, clamp `dt` to ~50 ms),
not per-frame constants — the same trap the zabad mockup hit.

## The renderer — the only thing without precedent

Every palm is **deterministic**: height, lean, frond count, trunk bow, x-jitter and sway phase all
come from a `mulberry32` PRNG seeded on the palm's absolute index. Palm #12 is identical on every
device, forever.

```kotlin
data class PalmParams(
    val heightScale: Float, val lean: Float, val frondCount: Int,
    val trunkBow: Float, val jitter: Float, val swayPhase: Float,
    val bearsDates: Boolean,   // (index + 1) % GROVE_SIZE == 0
)
fun palmParams(index: Int): PalmParams
```

**Draw primitives are constrained on purpose.** Only `Path` (quadratic curves), `drawArc`, `drawOval`
and linear/radial `Brush`. **No blur anywhere** — Compose `Canvas` has no cheap blur, so haze and glow
are radial gradients plus alpha. This is why the HTML mockup ports across essentially 1:1; do not
introduce a shadow/blur effect during the port or the cost model breaks.

A palm is: a tapered trunk (quadratic spine, seeded bow, scale rings), then 12–16 thin arching fronds
with heavy droop on the outer ones, then — on a grove-completing palm — three amber date clusters and
a gold radial halo.

Three animations, all skipped when the OS reports reduced motion:

| Animation | Duration | Trigger |
|---|---|---|
| Sprout (height ease-out + frond unfurl `spread` 0.22 → 1) | 620 ms | every tap |
| Soil burst (expanding ring + light bloom) | 700 ms | every tap |
| Grove recede (slides toward horizon, shrinks, hazes to 0 alpha) | 1200 ms | `completesGrove(count)` |

## Files

### Phase 1 — Data & domain (no UI, mechanical clone of `zabad`)

| File | Note |
|---|---|
| `domain/GharsChallengeModels.kt` | `GHARS_CHALLENGE_DAILY_GOAL = 100`, `GharsChallengeDayStats`, `GharsLeaderboardEntry` |
| `domain/ChallengeBadgeModels.kt` | add `GHARS("ghars", GHARS_CHALLENGE_DAILY_GOAL)` — the achievement badge then falls out for free |
| `data/ghars/GharsChallengeStore.kt` | remote-baseline + pending-delta, keyed on the Cairo date. Keys `ghars_challenge_{date,count,pending}` |
| `data/ghars/GharsChallengeFirebaseClient.kt` | RTDB read/write + leaderboard parse |
| `data/firebase/FirestoreMirror.kt` | add `mirrorGharsUserDay(...)` + `GHARS_COLLECTION` |
| `commonTest/data/ghars/GharsChallengeStoreTest.kt` | pending flush, Cairo day rollover, previous-day back-flush |

**No schema surprises**: one new RTDB subtree, one new Firestore collection, no lifetime total, no new
rule *shape*.

```
ghars_challenge/{dateKey}/
  users/{uid}/count          <- client-writable
  users/{uid}/data/{uid,date,countryCode,nickname,goal,completed,updatedAt}
  users/{uid}/rank           <- server-computed, read-only to client
  participantCount           <- server-computed
  totalTodayGhars            <- server-computed
  leaderboard                <- server-computed top-10
```

Firestore mirror: `ghars_challenge/{dateKey}/users/{uid}`.

### Phase 2 — The grove renderer

| File | Note |
|---|---|
| `ui/ghars/GroveMath.kt` | **pure Kotlin, no Compose.** `mulberry32`, `palmParams`, `shownPalms`, `rowOccupancy`, `rowScale`, `rowHaze`, `palmXFraction` |
| `ui/ghars/PalmGroveCanvas.kt` | the Compose `Canvas` — sky, sun, tree-line, rows, recede layer, burst, foreground vignette |
| `commonTest/ui/ghars/GroveMathTest.kt` | determinism (same index → same palm), wrap arithmetic, row occupancy sums to 25, `shownPalms` never exceeds 25 |

### Phase 3 — Screen and wiring

| File | Note |
|---|---|
| `presentation/GharsChallengeUiState.kt` / `GharsChallengeViewModel.kt` | `onScreenEntered` / `onScreenLeft` flush behind a `Mutex`, as zabad does |
| `ui/GharsScreen.kt` | the grove **is** the tap target (whole hero `clickable`, `Role.Button`); sand sheet below |
| `ui/ghars/GharsDesignSystem.kt` | palette as an `internal object`, matching the other five |
| `ui/ghars/GharsLeaderboardSheet.kt`, `ui/ghars/ManualGharsSheet.kt` | clones of the zabad pair |
| `di/AppModule.kt` | two `single`s + one `viewModel` |
| `App.kt` | flag, `PlatformBackHandler`, `nicknamePromptBlocked`, `when` branch, callback — the same 5 edits every challenge needs |
| `composeResources/values{,-en,-ur,-zh}/strings.xml` | all four locales **in one pass** |

Adds the app's **first custom fonts** — `Aref Ruqaa` (the tasbeeh, the title, the tier name; nothing
else) and `IBM Plex Sans Arabic` (body and tabular numerals). Deliberately not Cairo/Tajawal. Scope
them to this screen; do not retrofit the other challenges in this PR.

### Phase 4 — Server & entry point

| File | Note |
|---|---|
| `scripts/generate-stats.js` | ghars block parallel to zabad: `totalTodayGhars`, `participantCount`, top-10 `leaderboard` (rank + rankChange), per-user `rank`, rank-1 FCM |
| `ui/ChallengesScreen.kt` | the `ChallengeItem` card (accent `#1F5C40` — the only deep green in the row) |
| `presentation/ChallengesViewModel.kt` | `ChallengesTotals.ghars` + `readTotal(db, "ghars_challenge/$dateKey/totalTodayGhars")` |
| `database.rules.json`, `firestore.rules` | mirror the `zabad_challenge` blocks; aggregates read-only to the client |
| `analytics/` | `GHARS_SCREEN_VIEW`, `GHARS_TAP`, `OPEN_GHARS_CHALLENGE` |

## Palette

| Token | Hex | Use |
|---|---|---|
| `NightIndigo` | `#14103A` | top of sky — inherits the app's `DeepBlue` world |
| `DawnRose` | `#8C4A54` | mid sky, the pre-sunrise flush |
| `HorizonApricot` | `#E09A62` | the lit band the palms stand against |
| `PalmDeep` | `#0B2A22` | trunks and fronds in the foreground |
| `FrondLit` | `#3E8F63` | sunlit frond edges only |
| `DateAmber` | `#C4762A` | **the one accent** — milestone fruit, nothing else |
| `SandPale` | `#EFE2CB` | the bottom sheet |

Tasbeeh is the dhikr of «حين تُصبحون وحين تُمسون», so the grove is lit at first light: foreground palms
are near-silhouette against a lit horizon and read as shape, not colour.

## Deliberate omissions

- **No confetti.** The other challenges fire it at milestones. Here the milestone is *in the soil* —
  the grove-completing palm bears fruit, and the grove joins the horizon. That is the celebration.
- **No water channel.** A still قناة was drawn and cut; finicky code for a 40 px strip the grove
  didn't need.
- **No tending sim.** "Agriculture" could mean watering / pruning / trees dying from neglect. A
  **reward** tree does not die because you skipped a day. Slow maturation (sapling → mature palm over
  real days) is available as a later layer if wanted, and keeps the theology intact.
- **No tier ladder claiming الجنة.** The hadith is quoted; no rank a user *achieves* is named
  "paradise" — that is not ours to award.

## Copy

Register is humble and **passive**: the palm is planted *for* you, it is not something you built.

| Key | Arabic |
|---|---|
| `challenge_ghars_title` | اغرس نخلة |
| `challenge_ghars_body` | مَن قال: سبحان الله العظيم وبحمده، غُرست له نخلة في الجنة |
| `ghars_title` | الغَرْس |
| `ghars_phrase` | سُبْحَانَ اللهِ الْعَظِيمِ وَبِحَمْدِهِ |
| `ghars_tap_hint` | اضغط في أيّ مكان لتُغرَس لك نخلة |
| `ghars_empty_hint` | الأرض مُهيّأة. سبِّح لتُغرَس أوّل نخلة |
| `ghars_today_label` | غُرِسَ لك اليوم |
| `ghars_groves_label` | بساتين مكتملة |
| `ghars_grove_toast` | اكتمل بستانك رقم %1$d |

## Order of work

Phases 1 and 2 are **independent** — the renderer needs no backend, the store needs no UI — so they
can run in parallel. Phase 1 is near-mechanical against the zabad template; **budget the real time
for phase 2.**
