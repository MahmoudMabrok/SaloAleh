# SaloAleh

Weekly salawat tap competition — Kotlin Multiplatform (Android + iOS) with Compose Multiplatform UI.

## Quick reference

| Item | Value |
|------|-------|
| Package | `tools.mo3ta.salo` |
| Firebase project | `kamapp-3b3ac` |
| RTDB URL | `https://kamapp-3b3ac-default-rtdb.firebaseio.com` |
| GitHub repo | `MahmoudMabrok/SaloAleh` |
| Min SDK | Android 24 / iOS 15+ |
| JDK | 17 |
| Kotlin | 2.1.20 |
| Compose Multiplatform | 1.7.3 |

## Build and test commands

```bash
# Android
./gradlew assembleDebug                              # debug APK
./gradlew installDebug                                # build + install on device/emulator
make android                                          # build + install + launch

# iOS (simulator)
./gradlew :app:linkDebugFrameworkIosSimulatorArm64    # must run before Xcode build
make ios                                              # framework + xcodebuild + simctl

# Tests
./gradlew allTests                                    # all targets (commonTest + iosX64 + iosSimArm64 + JVM)
./gradlew testDebugUnitTest                           # Android JVM unit tests only
./gradlew iosSimulatorArm64Test                       # iOS native tests only

# Compile checks (no device needed)
./gradlew :app:compileCommonMainKotlinMetadata        # common code
./gradlew :app:compileDebugKotlinAndroid              # Android
```

## KMP source sets

| Source set | Path | Purpose |
|------------|------|---------|
| `commonMain` | `app/src/commonMain/kotlin/tools/mo3ta/salo/` | All shared logic, UI, domain, data |
| `androidMain` | `app/src/androidMain/kotlin/tools/mo3ta/salo/` | Android platform impls: Kronos time, country code, FCM, floating bubble, analytics |
| `iosMain` | `app/src/iosMain/kotlin/tools/mo3ta/salo/` | iOS platform impls: NTP time, country code, MainViewController |
| `commonTest` | `app/src/commonTest/kotlin/tools/mo3ta/salo/` | Shared tests with fakes |

`expect`/`actual` declarations: `Sha256`, `HttpClientFactory`, `CountryCodeProvider`, `NetworkTimeProvider`, `NotificationScheduler`, `PlatformActions`.

## Architecture

Strict unidirectional layers: **UI → Presentation → Domain → Data**. No UseCase layer — Repository is the orchestrator.

DI via Koin: `appModule` (commonMain) + `androidModule`/`iosModule` (platform). All wiring in `di/AppModule.kt`.

## Firebase RTDB structure

```
mohamed_lovers/
├── allTimeTotal                          # aggregate across all rounds (read-only to client)
├── users/{uid}/                          # per-device user data
│   ├── fcmToken, installDate, lastOpenDate, lastRivalNotifDate
│   └── achievements/{roundKey}/          # rank, score, date
└── {roundKey}/                           # e.g. "2026-05-16" (next Friday Cairo date)
    ├── roundTotal, roundPlayerCount      # server-computed aggregates
    ├── leaderboard/                      # server-populated top-N
    └── players/{uid}/                    # client-writable: uid, totalCount, updatedAt, countryCode
```

**Round key convention:** `YYYY-MM-DD` of the _next_ Friday in Cairo timezone (`Africa/Cairo`). Round resets at 19:00 Cairo time (16:00 UTC) on Friday.

Security rules live in `database.rules.json`. Deploy with: `firebase deploy --only database`

## Server-side scripts

Two Node.js runtimes use `firebase-admin` v12; a third (Deno) only triggers a workflow:

| Directory | Runtime | Purpose |
|-----------|---------|---------|
| `scripts/` | GitHub Actions cron | Admin scripts: notifications, leaderboard, stats |
| `functions/` | Cloud Functions (Node 20) | Firebase-triggered functions |
| `deno-scheduler/` | Deno Deploy cron | Dispatches `leaderboard-populate.yml` every 30 min + `aggregate-all-time.yml` Fridays after 19:00 Cairo via the GitHub REST API (Deno Cron is precise; GitHub `schedule:` cron is best-effort). Needs `GITHUB_TOKEN` env var. |

### GitHub Actions workflows

| Workflow | Schedule | Script |
|----------|----------|--------|
| `build.yml` | PR to main | Android + iOS CI build |
| `deploy.yml` | Manual dispatch | Google Play release (iOS commented out) |
| `leaderboard-populate.yml` | Deno Deploy cron, every 30 min (workflow_dispatch only) | `scripts/populate-leaderboard.js` |
| `notify-users.yml` | Cairo-aware schedule, Friday hourly | `scripts/notify-users.js` |
| `update-stats.yml` | Daily 23:45 Cairo | `scripts/generate-stats.js` |
| `aggregate-all-time.yml` | Deno Deploy cron, Fridays after 19:00 Cairo (workflow_dispatch only) | `scripts/aggregate-all-time.js` |

All workflows use secrets: `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_DATABASE_URL`.

## Versioning

- Android: `versionCode` / `versionName` in `app/build.gradle.kts`
- `deploy.yml` auto-increments `versionCode` and sets `versionName` from input, commits bump after deploy
- iOS: `CURRENT_PROJECT_VERSION` / `MARKETING_VERSION` in Xcode project (iOS deploy currently disabled)

## Chrome extension

`chrome-extension/` — floating window dhikr counter that syncs via phone UUID. Not part of KMP build.

## Conventions

- All UI in Arabic, RTL layout
- Timezone-sensitive logic uses `Africa/Cairo` — never UTC or device-local
- Device identity = SHA-256 of persisted UUID (no Firebase Auth)
- `google-services.json` and `GoogleService-Info.plist` are gitignored — injected from secrets in CI
- Never `git push` without explicit user request

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`MahmoudMabrok/SaloAleh`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical label strings — no overrides. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context repo — one `CONTEXT.md` + `docs/adr/` at root. See `docs/agents/domain.md`.
