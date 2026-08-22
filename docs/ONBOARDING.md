# SaloAleh — New Joiner Onboarding

Welcome! This guide gets you from clone to first meaningful PR. Read it top-to-bottom on day one; use the cookbook later as a reference.

**Companion docs (read all three this week):**

| Doc | What it gives you |
|-----|-------------------|
| `README.md` | Feature list, C1–C3 architecture diagrams, ADR summaries, run instructions |
| `CLAUDE.md` | Quick-reference table, build/test commands, deep feature notes, Firebase structure, **conventions** |
| `CONTEXT.md` | Glossary, full file map, data flows, ViewModel state contracts |

---

## 1. What is SaloAleh?

A weekly **salawat tap competition** — users tap a button to count salawat (blessings on the Prophet), compete on a global leaderboard, and earn badges/medals/streaks. One shared codebase ships **Android and iOS** via Kotlin Multiplatform (KMP) + Compose Multiplatform.

| Item | Value |
|------|-------|
| Package | `tools.mo3ta.salo` |
| Repo | `MahmoudMabrok/SaloAleh` |
| Firebase project | `kamapp-3b3ac` |
| Min SDK | Android 24 / iOS 15+ |
| JDK / Kotlin / Compose MP | 17 / 2.1.20 / 1.7.3 |
| UI language | Arabic default (RTL), with en/ur/zh locales |
| Timezone for all logic | `Africa/Cairo` — never UTC, never device-local |

---

## 2. The 30-second architecture

```
┌──────────────────────────────────────────────────────────┐
│  Android app (MainActivity)     iOS app (MainViewController)│
│         └──────────── both embed the KMP module ────────────┤
│  :app  →  app/src/commonMain   (ALL logic + UI lives here)  │
│           UI → Presentation → Domain → Data                │
│           Koin DI · GitLive Firebase SDK · mplat-settings   │
│         androidMain / iosMain = thin platform impls only    │
└──────────────┬───────────────────────────┬───────────────-─┘
               │                           │
      Firebase RTDB (source of truth)   Firestore (Phase-1 mirror)
               ▲
        scripts/ (GitHub Actions cron, firebase-admin)
        populate leaderboards · daily stats · round close · notifications
```

Rules that define the shape of the code:

1. **Strict unidirectional layers**: `UI → Presentation → Domain → Data`. Nothing points backwards.
2. **No UseCase layer** (ADR-0004) — the Repository *is* the orchestrator.
3. **DI is Koin**, wired in `commonMain/di/AppModule.kt` plus `androidModule`/`iosModule` (ADR-0002).
4. **No Firebase Auth**: device identity = SHA-256 of a locally persisted UUID (`data/crypto/Sha256.kt`). Write integrity comes from **Firebase App Check** (Play Integrity in release), not auth (ADR-0003).
5. **Firebase access is the GitLive Kotlin SDK** so calls are written once in commonMain (ADR-0005).
6. **Persistence is `multiplatform-settings`** (SharedPreferences / NSUserDefaults under the hood) (ADR-0006).

---

## 3. Repo map — what lives where

```
SaloAleh/
├── app/                        # THE KMP module (:app) — where you'll spend 95% of your time
│   └── src/
│       ├── commonMain/kotlin/tools/mo3ta/salo/
│       │   ├── App.kt              # Compose root + navigation + feature flags
│       │   ├── ui/                 # Compose screens (MohamedLoversScreen, per-challenge dirs, components/)
│       │   ├── presentation/       # ViewModels + UiState (one pair per screen)
│       │   ├── domain/             # Models + MohamedLoversRepository + caps/logs logic
│       │   ├── data/               # Firebase clients, Settings-backed stores, time, crypto…
│       │   ├── di/AppModule.kt     # Koin wiring (common)
│       │   └── analytics/, audio/, notification/
│       ├── androidMain/            # Kronos NTP, country code, FCM, billing, floating bubble,
│       │   └── MainActivity.kt     #   auto-click guard (dispatchTouchEvent), SaloApplication (App Check)
│       ├── iosMain/                # NTP, country code, MainViewController, platform actions
│       └── commonTest/             # Shared tests mirroring the above layout
├── dhikr-model/                # Gradle module (:dhikr-model) — Android runtime for TFLite voice-dhikr models
├── iosApp/                     # Swift shell (AppDelegate, CocoaPods workspace)
├── scripts/                    # Node.js firebase-admin cron jobs run by GitHub Actions (leaderboards, stats, medals, notifications)
├── deno-scheduler/             # Deno Deploy cron that triggers GitHub workflows precisely (every 30 min / Friday 19:10 Cairo)
├── DhikrSpeech/                # Python ML training pipeline (Colab) for voice dhikr — see its README + docs/LEARNING_GUIDE.md
├── SpeechCollector/            # Web tool volunteers use to record training audio
├── database.rules.json         # RTDB security rules  (deploy: firebase deploy --only database)
├── firestore.rules             # Firestore rules       (deploy: firebase deploy --only firestore:rules)
└── .github/workflows/          # build.yml, deploy.yml, update-stats.yml, leaderboard-populate.yml, aggregate-all-time.yml, …
```

Not part of the app build: `DhikrSpeech/`, `SpeechCollector/`, `deno-scheduler/`, `chrome-extension/`. Don't worry about them until a task touches them.

---

## 4. The four layers in practice

A feature flows like this (example: the main tap counter):

| Layer | File(s) | Responsibility |
|-------|---------|----------------|
| **UI** | `ui/MohamedLoversScreen.kt` | Compose-only. Renders `UiState`, emits user intents to the ViewModel. No business logic. |
| **Presentation** | `presentation/MohamedLoversViewModel.kt` + `MohamedLoversUiState.kt` | Holds a single immutable-ish `UiState` exposed as state flows. Calls repository, maps results to UI state. |
| **Domain** | `domain/MohamedLoversRepository.kt`, `domain/*Models.kt` | Repository orchestrates data sources (bootstrap merges network-time + Firebase + local store). Models are plain Kotlin. Caps/log rules (`SalawatDailyCap.kt`) live here too. |
| **Data** | `data/firebase/MohamedLoversFirebaseClient.kt`, `data/session/MohamedLoversSessionStore.kt`, `data/time/*` | Firebase clients (one per competition), Settings-backed local stores, time providers. Platform specifics enter here via injected interfaces. |

Per-challenge features repeat the same pattern — e.g. ghars: `domain/GharsChallengeModels.kt` → `data/ghars/GharsChallengeStore.kt` + `GharsChallengeFirebaseClient.kt` → `presentation/GharsChallengeViewModel.kt` → `ui/ghars/`. Copy an existing challenge end-to-end when adding a new one.

**Expect/actual pairs** (where platform code enters): `Sha256`, `HttpClientFactory`, `CountryCodeProvider`, `NetworkTimeProvider`, `NotificationScheduler`, `PlatformActions`. If you need platform capability, add an expect/actual + a Koin binding in both platform modules — never an Android import inside commonMain.

---

## 5. Core concepts (the vocabulary)

Full glossary in `CONTEXT.md`. The ones you need before your first conversation:

- **Round** — the weekly competition cycle. Ends **Friday 19:00 Cairo**. Identified by `roundKey` = `"YYYY-MM-DD"` of that Friday; it shards everything in Firebase (`mohamed_lovers/{roundKey}/…`).
- **Tap** — one salawat invocation. Buffered locally as a **pending session** (`sessionClicks`) so the counter feels instant, then flushed to RTDB (~90 s or on resume).
- **UID** — SHA-256 of a persisted random UUID. Not a credential; App Check is the real gate.
- **Leaderboard** — server-computed top-10 at `{roundKey}/leaderboard`; clients never compute rank. There's also a **daily** variant ranked on today's taps only.
- **Daily badge** — icon tier by today's tap count (Spark 10 → Star 10 000). Cleared nightly by cron.
- **Streaks & achievements** — open-streak badges (7/30-day), "perfect week" streak published to the leaderboard, per-challenge win badges, gold/silver/bronze medals (server-owned, written only by admin scripts).
- **Challenges** — daily standalone competitions beyond the main round (dhikr, baqiyat, istighfar, zabad, ghars, quran, al-baqara, alf-hasana, kalimat, hawqala). Each has its own Store/FirebaseClient/ViewModel/screen directory.
- **Push cap** — client-enforced hard ceiling of **25 000/day** pushed to Firebase across all sources; excess is discarded at flush time (`domain/SalawatDailyCap.kt`).

---

## 6. The flow you must understand first

**Tap → persist → sync** (trace these files once):

1. Tap → `MohamedLoversViewModel.incrementSessionClick()` → `repository.registerLocalTap(roundKey)`
2. `sessionStore.incrementPendingClick()` (Settings write) → UI updates instantly
3. Background flush every ~90 s / on resume → `firebaseClient.incrementSession(...)` → RTDB `ServerValue.increment()` + stamps `todayCount`, `schemaVersion`
4. Success → local pending cleared. Server crons later build `leaderboard/` from players.

Other flows worth tracing in `CONTEXT.md § Key Data Flows`: round-end detection & recap, daily badge milestones, score masking (premium), engagement streaks.

---

## 7. Backend & ops overview

### Firebase RTDB (source of truth)

```
mohamed_lovers/
├── app_config/{latestVersion, minSupportedVersionCode}   # remote config for update prompts
├── users/{uid}/            # profile, achievements history, server-only scoreHistory/paceFlags
└── {roundKey}/             # e.g. "2026-05-16" (next Friday, Cairo)
    ├── roundTotal, roundPlayerCount, leaderboard/, dailyLeaderboard/
    └── players/{uid}/      # CLIENT-writable node: totalCount, todayCount, schemaVersion, externalLog/…
```

Key integrity facts (details in `CONTEXT.md § Write Integrity`):

- Client may write **only its own player node** (`uid` match rule), must include `schemaVersion >= 1` (obsolete builds denied), unknown keys rejected (`$other: false`).
- **App Check** enforces genuine Play-installed builds; enforcement is toggled in the Firebase console, not in code.
- Abuse handling is **record-not-block**: `abnormal_users/`, `paceFlags/`, `badgeAdjustments/` are audit streams reviewed after the fact; the rate-limit rule was deliberately removed (see `docs/adr/score-reliability-fixes.md` context).
- Medals & `yesterdayTotalScore` baselines are **server-only** (Admin SDK bypasses rules).

### Server jobs (all in `scripts/`, run by GitHub Actions)

| Job | When | Script |
|-----|------|--------|
| Populate leaderboards | every 30 min (Deno cron → workflow) | `populate-leaderboard.js` |
| Daily close: score snapshots, badge reset, abnormal flags, notifications | 23:45 Cairo | `generate-stats.js` |
| Round close: aggregate all-time, award medals, seed new round | Friday 19:10 Cairo | `aggregate-all-time.js` |
| Prune inactive users | daily ~05:00 Cairo | `delete-inactive-users.js` |
| User notifications | Friday hourly | `notify-users.js` |
| Release | manual dispatch | `.github/workflows/deploy.yml` (auto-bumps `versionCode`) |

Firestore is currently a **dual-write mirror** (Phase 1); RTDB remains read-source-of-truth. See `CLAUDE.md § Firestore`.

---

## 8. Getting set up

Requirements: JDK 17+, Android SDK API 24+, Xcode 15+ & CocoaPods (iOS work only).

```bash
# Secrets (gitignored, injected from CI secrets otherwise)
#   Android: app/google-services.json
#   iOS:     iosApp/GoogleService-Info.plist
# Get them from an existing team member / Firebase console for kamapp-3b3ac.

# Build & run Android
./gradlew installDebug          # or: make android (build + install + launch)

# Build & run iOS simulator (two steps — framework MUST link before Xcode)
./gradlew :app:linkDebugFrameworkIosSimulatorArm64
make ios                         # xcodebuild + simctl install + launch

# Fast compile checks (no device needed)
./gradlew :app:compileCommonMainKotlinMetadata
./gradlew :app:compileDebugKotlinAndroid
```

Debug builds use the debug App Check provider, so you can develop against the real Firebase project without Play Integrity.

---

## 9. Testing

Tests live in `app/src/commonTest/kotlin/tools/mo3ta/salo/` mirroring the main layout (`data/…`, `domain/…`, `presentation/…`, fakes provided for Firebase/settings).

```bash
./gradlew allTests                 # every target
./gradlew testDebugUnitTest        # JVM only (fastest loop)
./gradlew iosSimulatorArm64Test    # iOS native only
```

Script-side tests exist too (`scripts/*.test.js` — leaderboard math, medal awards, abnormal-user flags). When changing scoring/cap/badge logic there is almost always a test file already covering it — extend it rather than starting fresh.

---

## 10. Conventions & gotchas (violating these breaks review)

1. **No hardcoded strings.** Always `stringResource(Res.string.…)`; add keys to all **four** locale files in one pass: `values/` (Arabic default), `values-en/`, `values-zh/`, `values-ur/`.
2. **All timezone math uses `Africa/Cairo`.** Round boundaries, daily resets, streaks, caps. Never device-local, never UTC.
3. **Challenge tap counters must not re-render the whole screen** (regression fixed in #139): no full-screen ripple (`indication = null` + own `MutableInteractionSource`), no count-keyed background animations, isolate the counter in its own StateFlow/leaf composable.
4. **New tappable count challenge ⇒ floating bubble support:** add `FloatingBubbleService.BubbleType` and update every exhaustive `when`; reading-only challenges (al-baqara) are exempt.
5. **Never push to git** without explicit request.
6. **Feature flags live in `App.kt`** (e.g. `UPDATE_PROMPT_ENABLED`); check whether your feature needs one.
7. **RTDB rule changes ship in order:** new build first → force-update floor set → *then* `firebase deploy --only database` (rules denying old clients immediately would lock out users).
8. Identity writes go through the repository's fire-and-forget paths; failures must never block or revert user-visible actions.

---

## 11. Cookbook — common first tasks

| Task | Touch points |
|------|--------------|
| Add a user-visible string | 4 locale files under `app/src/androidMain/res/` (+ Compose resources if used) → reference via `stringResource` |
| Add/extend a challenge screen | Copy sibling challenge end-to-end: domain models → data/store + firebase client → ViewModel+UiState → ui dir → wire route in `App.kt` → strings ×4 → bubble type → rules validators if new fields |
| Change scoring/sync logic | `domain/MohamedLoversRepository.kt` + cap checks in `domain/SalawatDailyCap.kt`; update `database.rules.json` validators; extend matching commonTest + `scripts/*.test.js` |
| Add a notification | See `docs/notification-formats.md`; server senders in `scripts/notify-users.js` / `generate-stats.js`; client opt-in flags in `NotificationSettingsStore` |
| Ship a release | `.github/workflows/deploy.yml` manual dispatch (auto-bumps versionCode); consider setting force-update floor via `Set Min Supported Version` workflow |
| Deploy DB rules | `firebase deploy --only database` (RTDB) / `--only firestore:rules` — mind gotcha #7 |

---

## 12. Suggested reading order

**Day 1**
1. This file.
2. Run the app (`make android` or `make ios`) and tap around: main screen, a couple of challenges, settings.
3. Trace the tap flow (§6 above) in the actual files.

**Day 2–3**
4. `CONTEXT.md` fully — glossary, file map, data flows, ViewModel contracts.
5. `README.md` ADRs (why KMP/Koin/no-auth/no-UseCase/GitLive/multiplatform-settings).
6. Skim `CLAUDE.md § Firebase RTDB structure` next to `database.rules.json`.

**Week 1**
7. Pick one challenge (e.g. dhikr) and read its full vertical slice: models → store → client → ViewModel → screen → sheet.
8. Read the two ADRs in `docs/adr/` and skim recent design specs in `docs/superpowers/specs/` to learn how features get specified before being built.
9. Find a `good first issue` in GitHub Issues (`MahmoudMabrok/SaloAleh`) and ship it.

---

*Found something wrong or missing here? Fix it — this file is part of the docs and should stay true.*
