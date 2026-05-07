# User Retention Enhancement — Design Spec

**Date:** 2026-05-07  
**Status:** Approved  
**Scope:** Smart Notification Layer + Mid-Week Engagement Loop  
**Constraint:** Firebase ecosystem only (Remote Config, Analytics, RTDB, FCM — no new services)

---

## Problem

Three compounding retention gaps:

1. **Day 1–7 drop-off** — new users install, tap once, never return
2. **Weekly churn** — users active 1–2 rounds then disappear
3. **Daily engagement** — users open weekly but not daily, missing Friday bonus and breaking streaks

**Success metrics:** daily opens per week, round completion rate (users who start a round finish it).

**Target users:** Arabic-speaking Muslims with both spiritual and competitive motivations.

---

## Architecture Overview

Two independent systems, both layered on existing infrastructure:

```
Script (GH Actions) → RTDB user segments → FCM → device
Client (app open)   → DailyGoalStore     → local notification
Round ends          → RTDB isFinal flag  → RoundRecapScreen on next open
```

**Smart Notifications** — Node.js script in `/scripts/` (same pattern as `populate-leaderboard.js`). Runs every 6h via GitHub Actions. Reads RTDB user activity, identifies at-risk segments, sends FCM messages via Firebase Admin SDK. Remote Config holds message templates + kill switches per notification type.

**Mid-Week Loop** — Pure client-side. New `DailyGoalStore` in commonMain. Existing `RetentionCheckWorker` extended for goal progress evaluation. New `RoundRecapScreen` composable shown on first open after a round ends.

No new services. No breaking RTDB schema changes.

---

## Smart Notification Layer

### Trigger Segments

| Segment | Condition | Arabic Message |
|---|---|---|
| Day-1 lapsed | No tap since install day, now day 2 | "لم تبدأ بعد — الجمعة القادمة فرصتك" |
| Mid-week inactive | No open in 3+ days, round still active | "مضاعفة الجمعة بعد X أيام — أين أنت؟" |
| Rival alert | Gap to next-rank player ≤ 50 taps | "EG•A3F9 يتقدم عليك بـ 30 صلاة فقط" |
| Round-end recap | `isFinal=true`, user played that round | "انتهت الجولة — أنت في المرتبة #X" |
| Streak at risk | Has 5+ day streak, no open today | "سلسلتك 7 أيام على المحك — افتح الآن" |

### Remote Config Keys

| Key | Type | Default | Purpose |
|---|---|---|---|
| `notif_rival_enabled` | bool | true | Kill switch for rival alerts |
| `notif_midweek_enabled` | bool | true | Kill switch for mid-week inactive |
| `notif_rival_threshold` | int | 50 | Tap gap to trigger rival alert |
| `notif_messages_ar` | JSON | — | Message copy, A/B testable without deploy |

### FCM Strategy

- **Topic-based:** Day-1 lapsed, mid-week inactive, streak at risk (simpler, no token needed)
- **Targeted by uid:** Rival alert, round-end recap (personalized content requires FCM token)
- FCM token stored at `users/{uid}/fcmToken` in RTDB (already written on app start)

### Debounce

Rival alert: max 1 per day per user. Flag written to `users/{uid}/lastRivalNotifDate`. Script skips user if flag matches today's date (Cairo timezone).

### Script Schedule

Every 6h via GitHub Actions cron (`0 */6 * * *`). More reliable than the 30-min leaderboard script given GitHub Actions scheduler non-determinism.

---

## Mid-Week Engagement Loop

### Daily Mini-Goals

New `DailyGoalStore` in `commonMain/data/` tracks per-day tap target. Goals scale Mon→Fri:

| Day | Target | Rationale |
|---|---|---|
| Mon | 33 | Tasbih unit |
| Tue | 66 | Double |
| Wed | 100 | Century milestone |
| Thu | 133 | Building to Friday |
| Fri | 200 | 2× bonus day |

- Targets configurable via Remote Config key `daily_goals_json` — JSON format: `{"mon":33,"tue":66,"wed":100,"thu":133,"fri":200}`. Fallback to hardcoded defaults if Remote Config fetch fails or key absent.
- Store persists `lastGoalCompletedDate` — resets at midnight Cairo time (mirrors existing `CompetitionWindowUtils` logic)
- Goal completion triggers local notification: "أحسنت! هدف اليوم اكتمل"
- `DailyGoalStore` exposes: `todayTarget(): Int`, `todayProgress(): Int`, `isGoalComplete(): Boolean`

### Streak Protection

Current behavior: one missed day breaks streak completely.

New behavior: one grace skip per 7-day window.

**`EngagementStore` additions:**
- `gracePeriodUsed: Boolean`
- `lastGraceDate: String` (ISO date)

**Logic:**
1. Day-2 miss detected → consume grace if available, show warning: "يوم واحد فقط للحماية متبقٍ هذا الأسبوع"
2. Day-3 miss (or grace already used) → streak breaks normally
3. Grace resets when a new 7-day window starts (tracked via `lastGraceDate`)
4. Grace not stackable — only one available per window

### Round Recap Screen

Shown once per completed round as a bottom sheet over the main screen (same pattern as existing leaderboard sheet).

**Trigger:** `isFinal=true` on current round AND `recapShownForRound` in `MohamedLoversSessionStore` ≠ current round key.

**Content:**
- Final rank + total players: "جئت في المرتبة #4 من 127 مصلياً"
- Personal best indicator (new `personalBestRank: Int` stored in `SessionStore`)
- Taps this round vs last round delta (e.g. "+120 عن الأسبوع الماضي")
- Single CTA button: "ابدأ الجولة الجديدة" — dismisses sheet

**Data source:** Last-round data already in RTDB (`mohamed_lovers/{roundKey}/leaderboard` + `roundPlayerCount`). No new fetch needed beyond what ViewModel already loads.

---

## RTDB Schema Additions

```
users/
  {uid}/
    fcmToken: String          # already written
    lastRivalNotifDate: String # new — ISO date, Cairo TZ
    installDate: String        # new — written on first launch
    lastOpenDate: String       # new — written from client on each foreground resume (app start or return from background)
```

Existing player nodes under `mohamed_lovers/{roundKey}/players/{uid}` unchanged.

---

## Implementation Phases

**Phase 1 — Client (Mid-Week Loop):**
1. `DailyGoalStore` — commonMain, with Remote Config fetch for targets
2. Streak protection — extend `EngagementStore`
3. `RoundRecapSheet` composable — reuse leaderboard sheet pattern
4. Wire recap trigger into `MohamedLoversViewModel`

**Phase 2 — Backend (Smart Notifications):**
1. RTDB user activity writes (`installDate`, `lastOpenDate`, `fcmToken`) — client
2. `notify-users.js` script — segments + FCM sends
3. GitHub Actions workflow `notify-users.yml` — 6h cron
4. Remote Config schema setup

---

## Out of Scope

- Social features (rival tracking on leaderboard, shareable cards) — Phase 3
- iOS-specific notification scheduling changes beyond existing `NotificationScheduler`
- Analytics dashboards (Firebase Analytics events to be defined during implementation)
