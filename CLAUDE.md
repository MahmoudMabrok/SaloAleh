# SaloAleh

Weekly salawat tap competition — Kotlin Multiplatform (Android + iOS) with Compose Multiplatform UI.

## Quick reference

| Item | Value |
|------|-------|
| Package | `tools.mo3ta.salo` |
| Firebase project | `kamapp-3b3ac` |
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

## Feature notes

### Heart index

Main-screen emotional gauge for salawat momentum.

- Core files: `data/heart/HeartIndexMath.kt`, `data/heart/HeartStore.kt`, `presentation/MohamedLoversViewModel.kt`, `ui/MohamedLoversScreen.kt`.
- Local-only persistence through `multiplatform-settings` keys `heart_score` and `heart_anchor_ts`; no Firebase schema/rules changes.
- Mechanics: every tap adds `+10`; live/offline decay subtracts `1` per `10_000ms` elapsed. Decay is remainder-safe: advance the anchor only by full decay intervals so partial seconds are carried.
- External salawat (manual entry sheet, Chrome extension sync via `applyExtensionScore`) credit the heart index too, scaled by count (`+10 * count`), via the same `addHeartTap` path as regular taps.
- Score is unbounded above and below. Do not clamp to a max or floor negative values.
- Heart reset runs every 2 days at 22:00 `Africa/Cairo`, on a fixed two-day grid anchored to epoch day 1 (1970-01-02); intentionally different from the competition round reset at Friday 19:00 Cairo. If the stored anchor predates the latest 22:00 reset boundary, reset score to `0` and anchor to `now` with no retroactive decay.
- Fresh install uses `score=0`, `anchorTs=0`; do not show the refill nudge until the clock has started.
- Nudge condition: `anchorTs > 0 && score <= HEART_LOW_THRESHOLD`.
- UI: heart widget lives top-left on `MohamedLoversScreen`, includes a short tooltip, and displays a red fill level that reaches full at `1000` points. The visual fill cap does not cap the stored score.
- Tests: heart math/store tests live under `commonTest/data/heart`; ViewModel coverage is in `MohamedLoversViewModelHeartTest`.

### Round streak badge ("perfect week")

Continuous daily-activity streak earned by sending salawat every day without missing a day.

- Core files: `data/engagement/RoundStreakStore.kt`, `domain/EngagementModels.kt` (`Achievement.RoundStreakBadge`, `ROUND_STREAK_TARGET = 7`), `presentation/MohamedLoversViewModel.kt`, `ui/AchievementsScreen.kt`, `ui/StreakBadgeAnnouncementDialog.kt`.
- Local persistence via `multiplatform-settings` keys `round_streak_last_active`, `round_streak_count`, `round_streak_badges`. The streak is **continuous across rounds** — it is never reset on a round boundary and only a missed day restarts it at 1. The `roundKey` passed to `recordActivity` is used solely to award one repeatable badge per round at 7 consecutive active days. (Legacy installs may still carry an orphaned `round_streak_round_key` key; it is no longer read.)
- Every salawat path (tap, manual sheet, extension sync) records activity and **publishes the current streak to `players/{uid}/roundStreak`** (client, fire-and-forget, only on change) so it shows next to the name on the leaderboard. Hidden when `0`/absent.
- Leaderboard plumbing mirrors `dailyBadge`: `leaderboard-utils.js` carries `roundStreak` into `leaderboard`/`dailyLeaderboard` entries; the client parses it in `toLeaderboardEntry`; the UI renders a 🔥+count pill in `MohamedLoversInfoSheet`.
- The daily cron `generate-stats.js` **breaks streaks for users inactive that day** — if `totalCount <= yesterdayTotalScore` (no salawat since the last run) it clears `roundStreak` in `players` and `leaderboard`, so a stale badge disappears even if the client never reopens.
- Announcement gated by its own `STREAK_BADGE_ANNOUNCEMENT_ENABLED` flag in `App.kt` (independent of the globally-suppressed `APP_ANNOUNCEMENTS_ENABLED`).
- Tests: `commonTest/data/engagement/RoundStreakStoreTest.kt`.

### Challenge badges

Per-challenge achievement badges: each daily challenge (dhikr, baqiyat, istighfar) has one badge whose count grows by 1 for every "win" — a day where the user reaches that challenge's daily goal (dhikr 100, baqiyat 10 cycles, istighfar 70).

- Core files: `domain/ChallengeBadgeModels.kt` (`ChallengeType`), `data/engagement/ChallengeBadgeStore.kt`, the three challenge ViewModels, `ui/AchievementsScreen.kt`.
- Local-only persistence via `multiplatform-settings` keys `challenge_badge_last_win_{id}`, `challenge_badge_count_{id}`. No Firebase schema/rules changes.
- At most one win per Cairo day per challenge (`recordWin` is idempotent within a day). Wins are checked on every count change — taps, manual entry sheets, and remote-baseline sync on screen enter.
- UI: "challenge badges" section on `AchievementsScreen` with a gold count pill per badge; counts also feed the badges stat card.
- Each challenge also tracks a consecutive-day **streak** in `ChallengeBadgeStore` (`recordActivity`/`getCurrentStreak`, keys `challenge_streak_last_active_{id}`, `challenge_streak_count_{id}`, `challenge_streak_best_{id}`). The streak is **activity-based, not goal-gated**: any count for the day (a single tap/cycle) keeps it alive via `recordActivity` — decoupled from the goal-gated `recordWin` that drives the badge count. Each ViewModel's `maybeRecordWin` calls `recordActivity` when `total > 0` and `recordWin` when `total >= goal`. The live streak is published to that challenge's own player node (`{challenge}/{dateKey}/users|players/{uid}/streak`, top-level next to `count`) on every sync via each challenge's `FirebaseClient.writeUserDay` + `FirestoreMirror`. `scripts/populate-leaderboard.js` carries `streak` into each challenge's server-computed `leaderboard` entries, the client parses it in each `to…LeaderboardEntry`, and each `*LeaderboardSheet` renders a 🔥+count pill next to the name (each board shows only its own challenge's streak). Requires a `streak` validator per challenge in `database.rules.json`. No stale-clearing cron needed — challenge leaderboards are rebuilt per Cairo day from that day's active participants.
- RTDB rules for each challenge's per-user node validate only the essentials — write-auth (`uid`/`data.uid` === `$uid`), `count` (`>= 0` within its bound), `streak` (`>= 0`), and server-owned `rank`. The `goal`/`completed`/`date`/`countryCode`/`nickname`/`updatedAt` fields and the `hasChildren`/`$other` schema locks were removed, so the client still writes those fields but they are no longer validated.
- Tests: `commonTest/data/engagement/ChallengeBadgeStoreTest.kt`.

### Winner medal badge

Cumulative podium-finish medals shown next to a player's name on the weekly competition leaderboard. Gold = #rank-1 finishes, silver = #rank-2, bronze = #rank-3. The app renders all three (🥇/🥈/🥉+count pills); tapping any pill opens `MedalInfoDialog` explaining the medal tiers.

- Core files (server): `scripts/aggregate-all-time.js` (increments winners' medals on round close), `scripts/leaderboard-utils.js` (`populateMohamedLoversRound` copies each participant's gold/silver/bronze counts onto leaderboard entries), `scripts/backfill-medal-counts.js` (one-off backfill). Core files (client): `data/firebase/MohamedLoversFirebaseClient.kt` (`GOLD_MEDALS_KEY`/`SILVER_MEDALS_KEY`/`BRONZE_MEDALS_KEY`, `toLeaderboardEntry`), `domain/MohamedLoversModels.kt` (`FirebaseLeaderboardEntry.{gold,silver,bronze}Medals`), `presentation/MohamedLoversUiState.kt` (`MohamedLoversLeaderboardEntry.{gold,silver,bronze}Medals`), `ui/components/MohamedLoversInfoSheet.kt` (medal pills), `ui/components/MedalInfoDialog.kt` (tap explanation).
- Counts are stored server-authoritatively at RTDB `mohamed_lovers/users/{uid}/medals` = `{ gold, silver, bronze }` (mirrored to Firestore `mohamed_lovers_users/{uid}.medals`). Written only by admin scripts (they bypass rules); the client never writes medals. `database.rules.json` marks the node `.read: true` and enforces server-only writes with `.validate: false` on the node and its `$medalType` children — a `.write: false` would be useless here because the blanket write grant on `users/$uid` cascades and can't be revoked deeper, whereas `.validate: false` rejects every client write while the admin SDK bypasses validation. (Without this, `medals` would just be another whitelisted client-writable field — the surrounding `$other: false` only blocks *unknown* keys.)
- Continuity: `aggregate-all-time.js` increments the rank 1/2/3 winners' medal counts (separate write map from `writes` so the achievements Firestore mirror never mistakes a medal path for an achievement entry) every Friday round close, right after it writes the `achievements` history node that medals are derived from.
- Rendering: unlike client-owned `roundStreak`, medals live on the user node (not the player node), so `populateMohamedLoversRound` fetches the whole `users/{uid}/medals` node per-uid for the top-10 weekly + daily set and attaches `goldMedals`/`silverMedals`/`bronzeMedals` to the entry; the client parses them (positive-only) in `toLeaderboardEntry` and shows them for every entry including the current user (no local medal state).
- One-off backfill: `node scripts/backfill-medal-counts.js` recomputes all users' `{gold,silver,bronze}` from their `achievements` history and writes the `medals` node (RTDB + Firestore). Run it via the `Backfill Medal Counts` GitHub Actions workflow (`.github/workflows/backfill-medal-counts.yml`, manual `workflow_dispatch`, no inputs) so it has the Firebase secrets. Safe to re-run — it overwrites with recomputed absolute counts.

### Challenge winner medals

The same podium-medal concept as the weekly Winner medal badge, but awarded **daily** per challenge. Each of the seven challenges (dhikr `100_challenge`, baqiyat `baqiyat_saliha`, istighfar `istighfar_challenge`, zabad `zabad_challenge`, ghars `ghars_challenge`, quran `quran_challenge`, albaqara `albaqara_challenge`) grants gold/silver/bronze to that day's rank-1/2/3 finishers, cumulative over time, shown as 🥇/🥈/🥉+count pills next to the name on that challenge's leaderboard sheet. Each board shows only its own challenge's medals.

- Core files (server): `scripts/generate-stats.js` (`awardAllChallengeMedals` → `awardChallengeMedals`, at the daily 23:45 Cairo close, right after `persistHeroes` and **before** the aggregate-and-clean steps delete the per-day nodes), `scripts/leaderboard-utils.js` (`awardChallengeMedals` awards a day's top-3; `attachChallengeMedals` copies each entry's counts onto the leaderboard entries in `populate-leaderboard.js`). Core files (client): the seven `data/{challenge}/*FirebaseClient.kt` (`GOLD_MEDALS_KEY`/`SILVER_MEDALS_KEY`/`BRONZE_MEDALS_KEY`, `to…LeaderboardEntry` positive-only parse), the seven `domain/*Models.kt` (`*LeaderboardEntry.{gold,silver,bronze}Medals`), the seven `ui/{challenge}/*LeaderboardSheet.kt`, and the shared `ui/components/ChallengeMedalPills.kt` (renders the pills + its own tap-to-explain dialog).
- Counts are stored server-authoritatively at RTDB `{challengeRoot}/users/{uid}/medals` = `{ gold, silver, bronze }` — a **persistent** node, sibling to the deleted-daily `{dateKey}` nodes (not under a date). Written only by admin scripts (they bypass rules); the client never writes medals. `database.rules.json` gives each `{challengeRoot}/users/{uid}/medals` node `.read: true` with `.validate: false` (server-only); a client write is denied because no write grant reaches this root-level `users` node in the first place (unlike salawat, there is no cascading `users/$uid` write grant here). Ranking recomputes from the live end-of-day counts via `readChallengeRankedUsers`, so it matches the winner-notification and heroes logic.
- Idempotency: `awardChallengeMedals` writes a `{challengeRoot}/{dateKey}/medalsAwarded` marker on the day node. Because the marker lives on the day node it is deleted with it — a normal re-run after cleanup finds no participants and awards nothing, while a re-run after a crash between award and cleanup sees the marker and skips. Failures are per-challenge isolated so one never blocks the others or the cleanup.
- No backfill (unlike salawat): challenge day nodes are deleted daily and there is no per-round `achievements` history to derive medals from, so counts only accumulate going forward.
- Firestore mirror is intentionally skipped — the app-side/script mirror is off behind the `MIRROR_ENABLED = false` kill-switch (`scripts/firestore-utils.js`), so a challenge-medal mirror would be a no-op.
- Strings: reuses `leaderboard_medals_info_{gold,silver,bronze}`, adds `challenge_medals_info_{title,desc}` (all four locales).
- Tests: `commonTest/data/dhikr/DhikrChallengeFirebaseClientTest.kt` (medal parse), `scripts/challenge-medals.test.js` (award + attach).

### Challenge lifetime total (total over time)

Per-challenge cumulative "total over time" — the overall count a device has ever logged for a challenge (like ghars's "total palms"), extended to **all count challenges** and published to the persistent DB user node.

- Applies to the 8 count challenges: dhikr (`100_challenge`), baqiyat (`baqiyat_saliha`), istighfar (`istighfar_challenge`), zabad (`zabad_challenge`), quran (`quran_challenge`), albaqara (`albaqara_challenge`), alfhasana (`alf_hasana_challenge`), ghars (`ghars_challenge`).
- Core files: each `data/{challenge}/*Store.kt` (`KEY_LIFETIME = "{prefix}_lifetime"`, `lifetimeCount()`, private `addLifetime()`), each `data/{challenge}/*FirebaseClient.kt` (`TOTAL_COUNT_KEY = "totalCount"`, `writeUserTotal(uid, total)`), each challenge ViewModel (`publishLifetimeTotal()`), `database.rules.json`.
- **Local accumulator:** `addLifetime` is called from `incrementToday` (`+1` per tap) and `addToday` (the *applied* manual amount, after cap-clamping — never the requested amount). It **survives day rollover** (never reset by `ensureToday`), is **never advanced by a remote baseline fetch** (`updateRemoteBaseline` only moves the daily total), and is **not walked back by `subtractToday`** (a mistaken-entry correction lowers today's count but not the lifetime tally) — same semantics as ghars.
- **DB publish:** `writeUserTotal` writes the absolute lifetime to the **persistent** node `{challengeRoot}/users/{uid}/totalCount` (sibling of the server-only `medals` node, not under a `{dateKey}`). The ViewModel's `publishLifetimeTotal()` is fire-and-forget and **batched on screen enter/leave** (`onScreenEntered`/`onScreenLeft`) rather than per-tap so it never spams the network; a publish failure never affects the daily-count sync.
- RTDB rules: each `{challengeRoot}/users/{uid}/totalCount` is `.read: true`, `.write: true`, `.validate: isNumber() && >= 0` (absolute, client-writable). This is the *only* client write grant reaching the root-level `users` node, so `medals` stays server-only (its `.validate: false` still rejects client writes; the new grant is scoped to the `totalCount` child). The main competition (`mohamed_lovers`) is unchanged — it already publishes its own `totalCount`/`todayCount` on the player node.
- No Firestore mirror (RTDB is the source of truth for these, matching challenge medals). No server script reads it yet — it accumulates going forward for later use.
- Tests: `commonTest/data/ghars/GharsChallengeStoreTest.kt` and `commonTest/data/zabad/ZabadChallengeStoreTest.kt` cover the accumulator semantics.

### Daily today-count, score history & abnormal-user tracking

Client-published per-Cairo-day salawat total that drives the daily leaderboard directly, plus a server-side daily audit trail, a fixed-threshold abnormal-usage flag, and a day-of-round pace flag.

- Core files (client): `data/engagement/DailyGoalStore.kt` (`recordTap`/`todayProgress`, key `daily_goal_progress` — the single source of truth for today's competition count), `presentation/MohamedLoversViewModel.kt`, `data/firebase/MohamedLoversFirebaseClient.kt` (`TODAY_COUNT_KEY`, stamped in `incrementSession`), `domain/MohamedLoversModels.kt` (`MohamedLoversPlayer.todayCount`). Core files (server): `scripts/leaderboard-utils.js` (`computeTodayScore`, `buildDailyScoreSnapshots`, `ABNORMAL_DAILY_THRESHOLD = 12000`, `PACE_DAILY_INCREMENT = 11000`, `roundDayNumber`), `scripts/generate-stats.js`.
- **Today count:** the daily leaderboard uses the **daily-goal tap progress** (`DailyGoalStore.todayProgress`) as the day's competition count — the same value that drives the home rank strip and the daily badge, so badge and board can never disagree. Every salawat path records it (`recordTap`): taps, manual entry, and extension sync all call it; a competition-total correction does **not** walk it back (it records activity, like the heart index/streak). The ViewModel publishes it as an **absolute** `todayCount` on the player node on every flush (via `incrementSession`). The daily leaderboard (`populateMohamedLoversRound`) ranks on `computeTodayScore` — the client `todayCount` when present, falling back to the `totalCount - yesterdayTotalScore` diff for un-updated clients. The self-row daily score uses the same local progress (which already includes not-yet-flushed taps). `yesterdayTotalScore` is still written by `generate-stats.js` and still gates the `totalCount` write rule, so it is kept. (The earlier standalone `ml_today_count`/`ml_today_date` session-store ledger was removed — it double-tracked the day count and drifted from the badge whenever a client updated mid-day.)
- **Daily close (`generate-stats.js`, 23:45 Cairo):** `buildDailyScoreSnapshots` writes, per active player, `users/{uid}/scoreHistory/{dateKey}` = that day's total, flags anyone whose day total exceeds `ABNORMAL_DAILY_THRESHOLD` into `abnormal_users/{dateKey}/{uid}` = `{count,totalCount,countryCode}` (record-only; no automatic penalty), and resets each client-pushed `todayCount` to 0 so the next Cairo day starts fresh.
- **Pace flag (day-of-round tracking):** the same daily close also flags anyone whose **cumulative round `totalCount`** outruns the day-of-round ceiling `dayOfRound × PACE_DAILY_INCREMENT` into `users/{uid}/paceFlags/{dateKey}` = `{totalCount, dayOfRound, threshold, countryCode}` (record-only, per-user history for later analysis — no penalty). `dayOfRound` is `roundDayNumber(roundKey, dateKey)`: Saturday (first day after the Friday 19:00 reset) = 1 → ceiling 11k, Sunday = 2 → 22k, … end-Friday = 7, clamped to `[1,7]` so the post-reset Friday-night close (a fresh round) reads as day 1. Distinct from `abnormal_users`: that is a fixed per-day total threshold; this is a per-round accumulation-rate check keyed to how many days the round has run. Gated by passing `roundDay` to `buildDailyScoreSnapshots` (omit ⇒ no pace flags).
- RTDB rules: `players/{uid}/todayCount` validates `isNumber() && >= 0` (client-writable, absolute). `users/{uid}/scoreHistory`, `users/{uid}/paceFlags`, and top-level `mohamed_lovers/abnormal_users` are server-only — `scoreHistory`/`paceFlags` use the `.validate: false` pattern (the cascading `users/$uid` write grant reaches them, so `.write:false` would be useless); `abnormal_users` is an explicit named node (`.read:false`, `.write:false`) so it isn't shadowed by the `$round` wildcard, and the Admin SDK bypasses both. No Firestore mirror (RTDB is the source of truth for these).
- Tests: `commonTest/data/engagement/DailyGoalStoreTest.kt` (daily progress), `scripts/abnormal-users.test.js` (`computeTodayScore` + `buildDailyScoreSnapshots` incl. pace flags + `roundDayNumber`).

### Large external-entry log

Audit trail for oversized externally-recorded salawat batches: any manual ("record external") entry or Chrome-extension sync **strictly greater than 2,000** is stamped onto the player node so a big claim leaves a timestamped record next to the score it produced. Record-only — nothing about the score, cap, heart index, streak or daily count changes because of it.

- Core files: `domain/ExternalSalawatLog.kt` (`MIN_LOGGED_COUNT = 2_000`, `shouldLog`, `entryKey`), `domain/MohamedLoversRepository.kt` (`appendExternalLog`), `data/firebase/MohamedLoversFirebaseClient.kt` (`EXTERNAL_LOG_PATH`, `appendExternalLog`), `presentation/MohamedLoversViewModel.kt` (`submitManualSalawat`, `applyExtensionScore`), `database.rules.json`.
- Written to RTDB `mohamed_lovers/{roundKey}/players/{uid}/externalLog/{timeKey}` = count, where `timeKey` is the Cairo wall clock as `yyyy-MM-dd HH;mm` (minute precision; `;` instead of `:`, and no `.`/`$`/`#`/`[`/`]`/`/`, so it is a legal RTDB key). Values are written with `ServerValue.increment`, so two batches landing in the same minute add up instead of overwriting each other.
- Regular in-app taps are **never** logged — only bulk amounts a user claims. The manual path logs the **applied** (cap-clamped) amount, i.e. what actually scored; the extension path logs the synced count.
- The write goes through `playerPatch` as a deep path (`externalLog/{timeKey}`) so the patch still carries `uid` + `schemaVersion` for the write rule and the required-field gate. It is fire-and-forget and runs after the manual sheet's submitting state clears, so a failure never blocks or reverts the entry.
- RTDB rules: `externalLog/$entryKey` validates `isNumber() && >= 0` under the existing player `.write` grant (the player node's `$other: false` means the key had to be whitelisted). Mirrored to Firestore as a nested `externalLog` map field on the player doc.
- Tests: `commonTest/domain/ExternalSalawatLogTest.kt`, `commonTest/domain/MohamedLoversRepositoryExternalLogTest.kt`.

### Remote update prompt

Startup update dialog driven by remote-config values (RTDB, not Firebase Remote Config — the latter is unused). Two modes: a **soft** "new version available" nudge (`latestVersion`, a versionName string) and a **forced**, non-dismissable "update required" block (`minSupportedVersionCode`, an integer version code).

- Core files: `domain/AppUpdateConfig.kt` (`AppUpdateConfig`, `isNewerVersion`), `data/update/UpdatePromptStore.kt`, `data/update/UpdateChecker.kt` (`UpdatePrompt(version, forced)`), `data/firebase/MohamedLoversFirebaseClient.kt` (`fetchAppConfig`), `App.kt`, `ui/VersionUpdateDialog.kt`, `ui/PlatformActions.kt` (`getAppVersion`/`getAppVersionCode`).
- Config lives at RTDB `mohamed_lovers/app_config/{latestVersion (versionName string like `3.9.2`), minSupportedVersionCode (integer version code like `125`)}`; `.read: true` in `database.rules.json`, client-read-only (admin scripts bypass rules). Absent/unreadable node ⇒ no prompt. No `database.rules.json` change is needed — the existing `app_config` grant already reads the whole subtree and denies client writes.
- **Force update:** when `minSupportedVersionCode` is **greater than** the running build's version code (`getAppVersionCode()` — Android `versionCode` / iOS `CFBundleVersion`), `UpdateChecker.check` returns `UpdatePrompt(forced=true)` and `VersionUpdateDialog` renders in blocking mode — `DialogProperties(dismissOnBackPress=false, dismissOnClickOutside=false)`, no "Later", "Update now" opens the store but keeps the dialog up. The prompt displays the newest available versionName (`latestVersion`) since the code itself is not user-meaningful. It **ignores the per-version dismissal**, shows **even during onboarding**, and **takes precedence over** both the soft `latestVersion` prompt and the FCM notification path. A **version code** (not a versionName) is used so internal builds sharing a name are still gated precisely and the check is an unambiguous integer compare. Set `minSupportedVersionCode` by hand (console / `Set Min Supported Version` workflow → `scripts/set-min-supported-version.js` → `publishMinSupportedVersionCode`) to retire old builds — it is not auto-published like `latestVersion`. Note: only builds shipping this force-update code can be blocked; older installs still only have the soft prompt. The floor is a single global value compared against each platform's code, so it is Android-centric (iOS deploy is currently disabled and iOS build numbers are not kept in sync with Android's `versionCode`).
- **`latestVersion` is published automatically, never by hand.** It is written at the same moment the "new version available" FCM `version_update` broadcast is sent, via `scripts/app-config-utils.js` `publishLatestVersion(db, version)`: the delayed production path (`populate-leaderboard.js` → `sendDueBuildNotification`, ~2h after deploy) and the manual `notify-new-build.js` both call it. Timing it with the broadcast keeps the prompt aligned with store propagation. The write never throws — a config-write failure won't abort the notification job.
- On startup `UpdateChecker.check(getAppVersion(), getAppVersionCode())` compares `latestVersion` to the running versionName via `isNewerVersion` (numeric, component-wise — `3.10` beats `3.9`; build suffixes ignored) for the soft prompt, and `minSupportedVersionCode` to the running version code for the forced prompt; shows `VersionUpdateDialog` when either fires.
- Dismissal is per-version: "Later" persists the version (`multiplatform-settings` key `update_prompt_dismissed_version`) so it never shows again for that release; a higher `latestVersion` prompts again. "Update now" only opens the store (not a dismissal), so an incomplete update still reminds next launch.
- Gated by its own `UPDATE_PROMPT_ENABLED` flag in `App.kt`, independent of the globally-suppressed `APP_ANNOUNCEMENTS_ENABLED`; the soft prompt is suppressed during onboarding (the forced block is not).
- Tapping the FCM `version_update` notification feeds the same dialog: `newVersionAvailable` (from the launch intent) shows the prompt immediately for the pushed version — the explicit tap bypasses an earlier per-version "Later" dismissal but still requires the version to be strictly newer than the running build; a blank/absent pushed version falls back to the remote-config check. The old announcement-gated `pendingVersionUpdate` dialog was removed (it was dead behind `APP_ANNOUNCEMENTS_ENABLED = false` and its non-null state also blocked the remote-config prompt).
- Reuses the existing `version_update_*` string keys and adds `force_update_{title,description}` (all four locales); shares the cross-platform `getAppVersion()`/`getAppVersionCode()` expect/actuals.
- Tests: `commonTest/domain/AppUpdateConfigTest.kt`, `commonTest/data/update/UpdatePromptStoreTest.kt`, `commonTest/data/update/UpdateCheckerTest.kt`.

### Required-field write gate (`schemaVersion`)

Server-side hard backstop that pairs with the force-update prompt: builds that predate this field are **denied at the DB** on the main competition write path, so an obsolete client cannot save salawat even if it dodges the update dialog.

- Core files: `database.rules.json` (`mohamed_lovers/$round/players/$uid`), `data/firebase/MohamedLoversFirebaseClient.kt` (`SCHEMA_VERSION_KEY`, `CLIENT_SCHEMA_VERSION`).
- The player node's `.validate` requires `hasChildren(['uid', 'schemaVersion'])` and `schemaVersion` validates as `isNumber() && >= 1`. RTDB validates the **merged** post-write state, so any patch that omits `schemaVersion` is rejected once the node doesn't already carry it — i.e. every write from a build shipped before this change.
- The client stamps `schemaVersion = CLIENT_SCHEMA_VERSION` (currently `1`) on **every** player write — both `playerPatch(...)` and the hand-built `fields` map in `incrementSession`. Bump `CLIENT_SCHEMA_VERSION` only when the player-write contract changes.
- Scope is the **main competition only** (`mohamed_lovers` players); the 7 challenge nodes and `ten_days` are not gated. Admin scripts bypass rules (Admin SDK), so server writes need no `schemaVersion`.
- **Rollout order matters** — deploying the rule denies all not-yet-updated clients immediately. Ship the new build first (it also carries the existing force-update code), publish and let the store propagate, set `mohamed_lovers/app_config/minSupportedVersionCode` to the new build's version code (force-update UX), **then** `firebase deploy --only database` (the hard backstop).

### Voice dhikr data collection

Settings entry that recruits volunteers to record dhikr audio for training a sound-based dhikr counter.

- Core files: `ui/settings/VoiceDhikrScreen.kt`, `ui/settings/SettingsScreen.kt` (`onOpenVoiceDhikr` + "help us build" section), `App.kt` (`showVoiceDhikr` route + back handler).
- Purely informational — no recording happens in-app and nothing is persisted or written to Firebase. The screen explains why recordings are collected, lists the three participation steps, and its "start" button hands off to an external Google Apps Script form (`VOICE_DHIKR_FORM_URL`) via `LocalUriHandler.openUri`.
- Analytics: `AppAnalytics.OPEN_VOICE_DHIKR` (settings row tapped), `AppAnalytics.VOICE_DHIKR_FORM_OPENED` (external form opened), plus a `VoiceDhikrScreen` view event.
- Strings are the `settings_voice_dhikr_*` / `voice_dhikr_*` keys in all four locales.

## Firebase RTDB structure

```
mohamed_lovers/
├── allTimeTotal                          # aggregate across all rounds (read-only to client)
├── app_config/                           # remote config (read-only to client)
│   ├── latestVersion                     # latest published versionName; drives the soft startup update prompt
│   └── minSupportedVersionCode           # lowest allowed integer versionCode; lower builds are force-blocked to update
├── abnormal_users/{dateKey}/{uid}        # server-only: {count,totalCount,countryCode} for users > 12k/day
├── users/{uid}/                          # per-device user data
│   ├── fcmToken, installDate, lastOpenDate, lastRivalNotifDate
│   ├── reminderNotifsEnabled            # client opt-in for notify-users.js push (Settings; absent = on)
│   ├── leaderboardNotifsEnabled         # client opt-in for populate-leaderboard.js push (Settings; absent = on)
│   ├── scoreHistory/{dateKey}            # server-only daily per-user score snapshot (that day's total)
│   ├── paceFlags/{dateKey}               # server-only: {totalCount,dayOfRound,threshold,countryCode} when round total > dayOfRound×11k
│   └── achievements/{roundKey}/          # rank, score, date
└── {roundKey}/                           # e.g. "2026-05-16" (next Friday Cairo date)
    ├── roundTotal, roundPlayerCount      # server-computed aggregates
    ├── leaderboard/                      # server-populated top-N
    └── players/{uid}/                    # client-writable: uid, schemaVersion, totalCount, todayCount, updatedAt, countryCode, dailyBadge, roundStreak
        └── externalLog/{yyyy-MM-dd HH;mm} # client-written audit entry per external/manual batch > 2,000
```

**Round key convention:** `YYYY-MM-DD` of the _next_ Friday in Cairo timezone (`Africa/Cairo`). Round resets at 19:00 Cairo time (16:00 UTC) on Friday.

Security rules live in `database.rules.json`. Deploy with: `firebase deploy --only database`

## Firestore (Phase 1 — dual-write migration)

The app and scripts dual-write to both RTDB and Firestore. RTDB remains the source of truth for reads. Phase 2 will switch reads to Firestore and remove RTDB.

### Firestore collections

| Collection | Maps to RTDB path | Purpose |
|------------|-------------------|---------|
| `mohamed_lovers_rounds/{roundKey}` | `mohamed_lovers/{roundKey}/` | Round metadata (roundTotal, roundPlayerCount) |
| `…/players/{uid}` | `…/players/{uid}` | Per-player data |
| `…/leaderboard/{rank}` | `…/leaderboard/{rank}` | Server-computed top-10 |
| `…/dailyLeaderboard/{rank}` | `…/dailyLeaderboard/{rank}` | Server-computed daily top-10 |
| `mohamed_lovers_users/{uid}` | `mohamed_lovers/users/{uid}/` | User profile (fcmToken, dates, prefs) |
| `…/achievements/{roundKey}` | `…/achievements/{roundKey}` | Per-round achievement |
| `…/purchases/{productId}` | `…/purchases/{productId}` | Purchase metadata |
| `mohamed_lovers_meta/stats` | `mohamed_lovers/allTimeTotal` | Global aggregate stats |
| `dhikr_challenge/{dateKey}` | `100_challenge/{dateKey}/` | Daily dhikr challenge |
| `baqiyat_challenge/{dateKey}` | `baqiyat_saliha/{dateKey}/` | Daily baqiyat challenge |
| `istighfar_challenge/{dateKey}` | `istighfar_challenge/{dateKey}/` | Daily istighfar challenge |
| `ten_days/{periodKey}` | `ten_days_dhul_hijjah/{periodKey}/` | Ten-days event |

### Key files

- `app/.../firebase/FirestoreMirror.kt` — app-side fire-and-forget dual-writer
- `scripts/firestore-utils.js` — server-side Firestore mirror utilities
- `firestore.rules` — Firestore security rules

### FCM during migration

FCM notifications are sent from RTDB scripts only (no duplication). All notifications in `generate-stats.js` (daily top-3, dhikr rank-1, baqiyat rank-1) are active. Engagement notifications (top-3 position changes, dropout, idle) in `leaderboard-utils.js` remain active. `notify-users.js` is unchanged.

Deploy rules: `firebase deploy --only firestore:rules`

## Server-side scripts

Two Node.js runtimes use `firebase-admin` v12; a third (Deno) only triggers a workflow:

| Directory | Runtime | Purpose |
|-----------|---------|---------|
| `scripts/` | GitHub Actions cron | Admin scripts: notifications, leaderboard, stats |
| `functions/` | Cloud Functions (Node 20) | Firebase-triggered functions |
| `deno-scheduler/` | Deno Deploy cron | Dispatches `leaderboard-populate.yml` every 30 min + `aggregate-all-time.yml` Fridays at 19:10 Cairo via the GitHub REST API (Deno Cron is precise; GitHub `schedule:` cron is best-effort). Needs `GITHUB_TOKEN` env var. |

### GitHub Actions workflows

| Workflow | Schedule | Script |
|----------|----------|--------|
| `build.yml` | PR to main | Android + iOS CI build |
| `deploy.yml` | Manual dispatch | Google Play release (iOS commented out) |
| `leaderboard-populate.yml` | Deno Deploy cron, every 30 min (workflow_dispatch only) | `scripts/populate-leaderboard.js` |
| `notify-users.yml` | Cairo-aware schedule, Friday hourly | `scripts/notify-users.js` |
| `update-stats.yml` | Daily 23:45 Cairo | `scripts/generate-stats.js` |
| `delete-inactive-users.yml` | Daily 03:00 UTC (~05:00 Cairo) + workflow_dispatch | `scripts/delete-inactive-users.js` — prunes stale users (0 current-round `totalCount` ≥3d inactive; positive current-round `totalCount` >10d) and current-round players (cascade + 0 score for two days) |
| `aggregate-all-time.yml` | Deno Deploy cron, Fridays at 19:10 Cairo (workflow_dispatch only) — closes the round and seeds the new round's leaderboard | `scripts/aggregate-all-time.js` |

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
- **No hardcoded strings in UI**: All user-visible text must use `stringResource(Res.string.…)`. Never pass raw Arabic/English literals to `Text()` composables. Add new keys to `values/strings.xml` (Arabic default) and immediately add translations to `values-en/strings.xml`, `values-ur/strings.xml`, and `values-zh/strings.xml`.
- **String resource workflow**: When adding a new string resource, always add it to all 4 locale files in one pass: `values/` (Arabic), `values-en/`, `values-ur/`, `values-zh/`. Check for duplicates before inserting.
- **Challenge tap counters must not re-render the whole screen** (known issue fixed in #139 — "Stop full-screen re-render on 100-dhikr tap"). A tap must update only the counter and its directly related parts, never the whole hero. Concretely: (1) the full-screen tap surface's `clickable` must pass `indication = null` with a dedicated `remember { MutableInteractionSource() }` so there is no full-screen ripple; (2) never drive a full-screen background/animation off the count (no count-keyed `animateFloatAsState` gradients/overlays — keep the background fixed); (3) prefer isolating the running count in its own `StateFlow`/leaf composable so only the number/ring recompose. Verify a new or edited challenge screen against all three before shipping.
- **Challenge screen layout**: every challenge screen shows the **hadith transcript** (the narration text + its reference) on the screen itself; the detailed reward breakdown lives behind the "what you gain" reward button/sheet, not on the main surface. Rewards are surfaced only on reaching the daily goal — no sub-goal reward popups.
- **Challenge floating bubble**: a new tappable count-challenge should also get a floating bubble. Add a `FloatingBubbleService.BubbleType` (id == its `ChallengeType.id`) and update every exhaustive `when` (openChallengeAction, injected store, `watchedCountKey`, `currentCount`, `themeFor`, `handleTap`), add its accent in `PlatformActions.android.kt`'s `ChallengeBubbleButton`, and place `ChallengeBubbleButton(ChallengeType.X.id)` on the challenge screen. (Reading-only challenges like al-baqara are the exception — no bubble, no FCM.)

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`MahmoudMabrok/SaloAleh`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical label strings — no overrides. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context repo — one `CONTEXT.md` + `docs/adr/` at root. See `docs/agents/domain.md`.
