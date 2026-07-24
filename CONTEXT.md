# SaloAleh — Domain Context

Read this file before exploring the codebase. It covers vocabulary, file map, data flows, and ViewModel contracts.

---

## Glossary

| Term | Definition |
|------|-----------|
| **Round** | 1-week competition cycle. Ends every Friday at 19:00 Cairo time. Identified by `roundKey`. |
| **Round Key** | `"YYYY-MM-DD"` string of the Friday a round ends. Shards all Firebase data. |
| **Tap / Click** | One salawat invocation. Counted locally in `MohamedLoversPendingSession.clickCount` until flushed to Firebase. |
| **Salawat** | Islamic blessing prayer on the Prophet. Users tap to count how many they say. |
| **Salawat Variant** | One of 6 text alternatives for the salawat wording shown in the tap animation. |
| **Pending Session** | Unsync'd local tap buffer for a round, persisted in Settings and flushed to Firebase periodically (~90 s) or on app resume. |
| **Leaderboard** | Real-time ranked list (top-10) per round from Firebase at `{roundKey}/leaderboard`. Can toggle to **daily** scope. |
| **Daily Leaderboard** | Shows today's scores only instead of round cumulative. Toggled by `useDailyLeaderboard`; separate Firebase path. |
| **Rival** | The player ranked directly above or below the current user, used for overtake alerts. |
| **Rank** | Ordinal position (1–10+) on the leaderboard. Computed server-side. |
| **allTimeTotal** | Aggregate tap count across all rounds — read-only display only. |
| **Round Total / Player Count** | Server-computed aggregates per round: total taps and participant count. |
| **Daily Badge** | Achievement icon for today's tap count. Thresholds: Sprout(100)→Heart(200)→Tasbih(500)→Dome(1000)→Crescent(2000)→Crown(5000). Resets daily at 19:00. |
| **Milestone** | First time a user hits a daily badge threshold → triggers confetti animation. |
| **Grace Period** | 2-day absence allowance in streak tracking. Usable once per absence window. |
| **Achievement** | Sealed union: `StreakBadge` (7-day or 30-day streak) or `RankAchievement` (top-3 round finish). |
| **Personal Best Rank** | Lowest (best) rank ever achieved, tracked in `MohamedLoversSessionStore`. |
| **Score Masking** | Premium feature hiding the user's score from others. Round-scoped; cleared on new round. |
| **Supporter** | User who purchased a support tier; shows a badge on the leaderboard. |
| **UID** | SHA-256 of a persisted random UUID. No Firebase Auth used. |
| **Ten Days** | Seasonal (Dhul-Hijjah) competition across 9 days. Tracks dhikr types, takbeer, fasting, sadaqah. |
| **Dhikr Types** | Five Islamic phrases counted in Ten Days: SubhanAllah, Alhamdulillah, AllahuAkbar, LaIlahaIllallah, LaHawla. |
| **Dhikr Challenge** | Daily standalone challenge — tap goal of 100 dhikr, ranked against all participants. |
| **Takbeer Session** | Group ritual: 2–10 participants in a ring; audio plays per turn, cycling through each. |
| **Status** | UI indicator: `WaitingNetwork` (no network time) · `FirebaseOff` (no Firebase) · `Open` (round active). |

---

## File Map — `commonMain`

All paths relative to `app/src/commonMain/kotlin/tools/mo3ta/salo/`.

### Domain Models

| File | Contents |
|------|----------|
| `domain/MohamedLoversModels.kt` | Core entities: `MohamedLoversPlayer`, `MohamedLoversPendingSession`, `MohamedLoversCompetitionWindow`, `FirebaseLeaderboard(Entry)` |
| `domain/DailyBadgeModels.kt` | `DailyBadge` enum with tap thresholds (Sprout→Crown) |
| `domain/EngagementModels.kt` | Streak/open-count tracking, `BadgeType`, `Achievement` sealed union |
| `domain/DhikrChallengeModels.kt` | `DhikrChallengeDayStats` (rank, participantCount, totalTodayDhikr); daily goal = 100 |

### Repository

| File | Role |
|------|------|
| `domain/MohamedLoversRepository.kt` | Facade for all Mohamed Lovers ops: bootstrap, leaderboard observe/fetch, tap registration, pending session flush, score reset, nickname/badge writes, achievements |

### Data — Firebase Clients

| File | Firebase paths |
|------|----------------|
| `data/firebase/MohamedLoversFirebaseClient.kt` | `mohamed_lovers/{roundKey}/{players, leaderboard, dailyLeaderboard, roundTotal, roundPlayerCount}` |
| `data/tendays/TenDaysFirebaseClient.kt` | `ten_days_dhul_hijjah/{periodKey}/{leaderboard, playerCount, players}` |
| `data/dhikr/DhikrChallengeFirebaseClient.kt` | Daily dhikr challenge leaderboard + user day writes |

### Data — Local Stores (Settings-backed)

| Store | Persists |
|-------|----------|
| `data/session/MohamedLoversSessionStore.kt` | Pending clicks, UID, personal best rank, last-round taps, last salawat timestamp, nickname, FCM token, install date, milestones |
| `data/engagement/EngagementStore.kt` | Open count, streak, grace usage, badge unlock dates |
| `data/engagement/DailyGoalStore.kt` | Daily tap targets per day-of-week (Mon=33…Fri=200), today's progress |
| `data/tendays/TenDaysStore.kt` | Per-day dhikr counts, takbeer, fasting, sadaqah, auto-play settings |
| `data/dhikr/DhikrChallengeStore.kt` | Today's dhikr count + previous day's unsync'd count |
| `data/hadith/DailyHadithStore.kt` | 10 curated hadiths, startup display flag, current index |
| `data/billing/PremiumStore.kt` | Purchase flags, subscription state, score masking round-scoped flag |
| `data/notification/NotificationSettingsStore.kt` | Daily/Friday/server/leaderboard notif toggles, UI tooltip flags |
| `data/salawat/SalawatVariantStore.kt` | Selected salawat variant (0–5) |
| `data/language/LanguageStore.kt` | Selected language tag |

### Data — Time & Utilities

| File | Role |
|------|------|
| `data/time/CompetitionWindowUtils.kt` | Computes `roundEnd` = next Friday 19:00 Cairo; derives `roundKey` |
| `data/time/NetworkTimeProvider.kt` | Network time sync + competition window polling |
| `data/time/FinalMinutesTick.kt` | Round-end countdown logic |
| `data/MilestoneTracker.kt` | Detects first daily badge threshold hit → triggers celebration |

### Presentation — ViewModels

| ViewModel | Screen it drives |
|-----------|----------------|
| `presentation/MohamedLoversViewModel.kt` | Main tap counter + leaderboard |
| `presentation/TenDaysViewModel.kt` | Ten Days dhikr/takbeer grid |
| `presentation/DhikrChallengeViewModel.kt` | Daily dhikr challenge |
| `presentation/AchievementsViewModel.kt` | Badge & rank achievement display |
| `presentation/HadithListViewModel.kt` | Static hadith list |
| `presentation/TakbeerSessionViewModel.kt` | Group takbeer ring session |

### UI — Main Screens

| Screen | What it shows |
|--------|--------------|
| `ui/MohamedLoversScreen.kt` | Tap counter, leaderboard top-10, self rank chip, round-end time, Firebase status, daily goal bar, daily badge, hadith banner |
| `ui/tendays/TenDaysScreen.kt` | 9-day tabs, dhikr grid (5 types), takbeer counter, fasting/sadaqah toggles, leaderboard, auto-play settings |
| `ui/HadithListScreen.kt` | Scrollable list of 10 hadiths |
| `ui/AchievementsScreen.kt` | Streak badges (7/30-day), rank achievements per round |
| `ui/takbeer/TakbeerSessionScreen.kt` | Ring UI, current turn highlight, participant count input |
| `ui/settings/SettingsScreen.kt` | All user toggles and preferences |
| `ui/OnboardingScreen.kt` | First-launch nickname + notification opt-in |

### UI — Transient Overlays & Sheets

| Component | Purpose |
|-----------|---------|
| `ui/components/RoundEndBanner.kt` | Slide-in banner: rank, taps, personal best |
| `ui/components/RankMovementBanner.kt` | "You climbed to #3!" / "You dropped to #8" |
| `ui/components/OvertakeOverlay.kt` | Full-screen overtake celebration |
| `ui/components/MilestoneCelebration.kt` | Confetti on first daily badge tier hit |
| `ui/components/ManualSalawatSheet.kt` | QR scan or number input to import taps |
| `ui/components/RoundRecapSheet.kt` | Round summary: rank, players, personal best, tap delta |
| `ui/components/DailyBadgeTiersSheet.kt` | Badge tier progression with current highlighted |
| `ui/components/UserAchievementsSheet.kt` | Historical rank achievements per round |

---

## Key Data Flows

### Tap → Persist → Sync to Firebase

1. User taps → `MohamedLoversViewModel.incrementSessionClick()` → `repository.registerLocalTap(roundKey)`
2. `registerLocalTap()` → `sessionStore.incrementPendingClick()` (Settings write) → UI state `sessionClicks` updates instantly
3. Background coroutine `flushPendingSession()` fires every ~90 s or on app resume
4. Flush calls `firebaseClient.incrementSession(roundKey, uid, count, countryCode)` → RTDB `ServerValue.increment()` + timestamp
5. On success → `sessionStore.decrementPendingClick()` clears local buffer
6. Next leaderboard poll reflects updated score in top-10

### Round End Detection & Results

1. `bootstrap()` on each app session fetches network time → computes `roundEnd` (next Friday 19:00 Cairo)
2. When `now() >= roundEndInstant` → new round detected; `startFinalMinutesTimer()` shows countdown UX
3. Server cron finalizes leaderboard (marks `isFinal=true`); ViewModel detects on next poll
4. `RoundEndResultsScreen` shows recap: rank, players, personal best check, tap delta
5. Top-3 achievements written to `users/{uid}/achievements/{roundKey}`

### Daily Badge Flow

1. Tap count crosses threshold → `DailyBadge.fromTapCount(count)` → badge stored in UI state
2. `MilestoneTracker.onMilestoneReached()` checks if first time today → triggers `MilestoneCelebration`
3. Background sync writes `players/{uid}.dailyBadge = badgeKey` to Firebase; other players see it on leaderboard

### Score Masking (Premium)

1. User enables masking → `PremiumStore.scoreMaskedRoundKey = currentRoundKey`
2. Firebase write: `players/{uid}.scoreMasked = true`
3. Other players' leaderboard queries see `scoreMasked=true` → display masked badge
4. On new round, `bootstrap()` calls `premiumStore.clearScoreMaskOnNewRound(newRoundKey)` → clears both flags

### Engagement & Streak

1. App launch → `EngagementStore.recordOpen(today)` increments `openCount`, evaluates streak
2. Streak hits 7 or 30 → unlocks `BadgeType.STREAK_7/30`, stores `earnedDate`
3. On first unlock → prompts FCM permission

---

## Write Integrity & Abuse Surface

Scores are competitive, so writes need to be trustworthy. There is **no Firebase Auth** — device identity is a SHA-256 of a locally persisted UUID, which is an identifier, not a credential. The integrity guarantee comes from App Check instead.

### Firebase App Check (primary defense)

`SaloApplication.onCreate()` installs an App Check provider before any Firebase use:

| Build | Provider |
|-------|----------|
| Release | `PlayIntegrityAppCheckProviderFactory` |
| Debug (`BuildConfig.DEBUG`) | `DebugAppCheckProviderFactory` |

Play Integrity attests that the request comes from a genuine, unmodified, Play-installed build of `tools.mo3ta.salo` on a device that passes integrity checks. This closes the **off-device abuse vectors**: raw REST/curl writes to RTDB, scripted clients, and modded/repackaged APKs cannot obtain a valid App Check token, so their writes are rejected regardless of what the security rules allow.

Two things this depends on, both outside the codebase:

- **Enforcement must be enabled per-product in the Firebase console** (Realtime Database, Firestore). Installing the SDK alone only *sends* tokens; it does not reject unattested traffic until enforcement is switched on.
- Devices without Play Services (Huawei / de-Googled ROMs) cannot mint a Play Integrity token, so enforcement is a real availability trade-off for those users. Surfaced historically via the `salo_firebase_error` permission-denied analytics event.

### Defense in depth (rules)

`database.rules.json` still bounds `players/$uid/totalCount` to `previous + 10000` per write. This is a blast-radius cap, not a rate limit — there is no time-based ceiling, so it does not stop sustained inflation.

### What App Check does *not* cover

App Check attests **the app**, not **the user's intent**. A genuine, unmodified build being driven abusively is fully attested and passes every check. The remaining surface is therefore entirely on-device:

- **Auto-clicker apps** — third-party tools using `AccessibilityService.dispatchGesture` (or root `input tap`) to inject synthetic `MotionEvent`s into the real app. Indistinguishable from a real tap to Firebase.
- **Sanctioned bulk-entry paths** — `MohamedLoversViewModel.submitManualSalawat()` (manual entry sheet) and `applyExtensionScore()` (Chrome extension sync) both inject counts with no tapping by design. Any tap-level defense can be routed around through these.

Because these are on-device problems, they can only be addressed on-device — detection in the client, not in rules or App Check.

---

## Expect/Actual Platform Pairs

| Feature | Android | iOS |
|---------|---------|-----|
| `Sha256` | `MessageDigest("SHA-256")` | CryptoKit |
| `HttpClientFactory` | OkHttp engine | Darwin engine |
| `CountryCodeProvider` | `TelephonyManager` | `NSLocale.current.region` |
| `NetworkTimeProvider` | Kronos NTP | NTP via `Network` framework |
| `NotificationScheduler` | WorkManager / AlarmManager | `UserNotifications` framework |
| `PlatformActions` — toast | `Toast.makeText` | `UIAlertController` |
| `PlatformActions` — clipboard | `ClipboardManager` | `UIPasteboard` |
| `PlatformActions` — share | `Intent.ACTION_SEND` | `UIActivityViewController` |
| `PlatformActions` — QR scanner | ML Kit Vision | `AVCaptureSession` |
| `PlatformActions` — floating bubble | Overlay Service API | `PresentationController` (iOS 16+) |
| `PlatformActions` — back handler | `onBackPressedDispatcher` | SwiftUI `.environment` modifier |
| `TakbeerSoundPlayer` | MediaPlayer / ExoPlayer | AVAudioPlayer / AVAudioEngine |

---

## ViewModel State Contracts

### `MohamedLoversViewModel`

```
isLoading, isRefreshing          — bootstrap / refresh in progress
isSavingSession                  — Firebase flush in progress
status                           — WaitingNetwork | FirebaseOff | Open
canCount                         — tap button enabled (requires network time)
roundKey, roundEndInstant        — current round identity & boundary
sessionClicks                    — pending unsync'd taps (shown instantly)
syncedTotal                      — Firebase-confirmed total
topPlayers                       — leaderboard top-10
selfEntry, selfInTop             — user's own entry & whether in top-10
roundTotal, roundPlayerCount     — aggregate stats
allTimeTotal                     — cross-round aggregate (display only)
isUsingDailyLeaderboard          — daily vs. round-cumulative scope toggle
showRoundEndResults, recapRank   — recap overlay & its data
milestoneThreshold, currentDailyBadge — daily badge state
rankMovementDelta                — rank change since last check
overtakeRank                     — rival rank for overtake alert
lastSalawatElapsedMinutes        — minutes since last tap (idle indicator)
dailyGoalTarget, dailyGoalProgress — daily goal tracking
```

### `TenDaysViewModel`

```
currentDay                       — selected day (1–9)
days                             — list of TenDaysDayState (dhikrCounts, takbeer, fasting, sadaqah, dayScore)
totalScore                       — sum across all days
selfRank, leaderboard            — user rank + top entries
playerCount                      — total participants
autoPlayTakbeer, takbeerIntervalMinutes, takbeerRepeatCount — auto-play settings
isActive, periodEnded            — season state
```

### `DhikrChallengeViewModel`

```
todayCount                       — dhikr taps today
dailyGoal                        — const 100
rank, participantCount           — user rank + total participants
totalTodayDhikr                  — aggregate across all users today
isLoading, isSyncing, errorMessage
```

### `TakbeerSessionViewModel`

```
phase                            — SETUP | RUNNING
peopleCountInput                 — raw string input (2–10)
canStart                         — valid count entered
currentTurn                      — index of whose turn it is
userIndex                        — peopleCount - 1 (user is always last)
awaitingUser                     — it's the user's turn, waiting for tap
roundsCompleted                  — full cycles finished
```
