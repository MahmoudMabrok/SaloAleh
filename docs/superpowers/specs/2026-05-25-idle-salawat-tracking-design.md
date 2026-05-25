# Idle Salawat Tracking — Design Spec

**Date:** 2026-05-25
**Approach:** A — Pure Client Timer + Server `updatedAt` Check

## Overview

Two-part feature:
1. **Client-side idle banner** — always shows time since last salawat tap, below tap button, disappears momentarily on tap
2. **Server-side idle FCM** — populate-leaderboard.js detects players idle >8 hours and sends push notification, debounced once per Cairo calendar day

## Part 1: Client-Side Idle Banner

### Data Layer

- Store `last_salawat_timestamp` (Long, epoch ms) in `Settings` (multiplatform-settings via `MohamedLoversSessionStore` or ViewModel direct access)
- Written on every `onCountClick()` call — immediate, before flush
- Persists across app restarts

### Presentation Layer (MohamedLoversViewModel)

- New state field: `lastSalawatElapsed: Duration?` (null = never tapped this round)
- Coroutine ticker every 60 seconds recomputes `now - lastSalawatTimestamp`
- On tap: update timestamp immediately, elapsed resets to 0
- Expose formatted Arabic string via duration formatter

### UI Layer (MohamedLoversScreen)

- New `IdleBanner` composable positioned below the tap button
- Visible when `lastSalawatElapsed != null` and elapsed >= 1 minute
- Shows formatted duration: "لم تصلِّ منذ X"
- `onClick` → calls `viewModel.onCountClick()` (same as tap)
- Disappears momentarily on tap (elapsed = 0), reappears after ticker fires (~60s)
- Styled as subtle encouraging card/chip — not alarming

### Duration Formatting

| Elapsed | Arabic Display |
|---------|---------------|
| < 1 min | Banner hidden |
| 1-59 min | "لم تصلِّ منذ ٥ دقائق" |
| 1-23 hours | "لم تصلِّ منذ ٣ ساعات" |
| 1+ days | "لم تصلِّ منذ يومين" / "لم تصلِّ منذ ٣ أيام" |

Arabic plural forms per unit:
- Minutes: دقيقة (1) / دقيقتين (2) / دقائق (3+)
- Hours: ساعة (1) / ساعتين (2) / ساعات (3+)
- Days: يوم (1) / يومين (2) / أيام (3+)

Separate string resource keys per unit and plural form. Common formatting function selects correct key by count.

Localized for all 4 locales: AR (default), EN, UR, ZH — each with appropriate plural patterns.

### String Resources

New keys added to all 4 locale files (`values/strings.xml`, `values-en/strings.xml`, `values-ur/strings.xml`, `values-zh/strings.xml`):
- `idle_banner_prefix` — "لم تصلِّ منذ" / "You haven't sent salawat for" / etc.
- `idle_unit_minutes_one` / `idle_unit_minutes_two` / `idle_unit_minutes_plural`
- `idle_unit_hours_one` / `idle_unit_hours_two` / `idle_unit_hours_plural`
- `idle_unit_days_one` / `idle_unit_days_two` / `idle_unit_days_plural`

## Part 2: Server-Side Idle FCM in populate-leaderboard.js

### Detection Logic

Added as new segment after existing top-3 and dropout notification logic:

1. Iterate all players in current round
2. For each player: compute `Date.now() - player.updatedAt`
3. If gap > 8 hours (28,800,000 ms) → candidate
4. Skip if `totalCount === 0` and no `updatedAt` (never participated)
5. Skip if round is final (`isRoundFinal()` — Friday >= 19:00 Cairo)

### FCM Token Resolution

- Player node has `uid` → fetch `/users/{uid}/fcmToken`
- Skip players with no token

### Debounce

- New RTDB field: `/users/{uid}/lastIdleNotifDate` (ISO date string)
- Before sending: check `lastIdleNotifDate !== cairoToday()`
- After sending: write `lastIdleNotifDate = cairoToday()`
- Effect: maximum 1 idle FCM per user per Cairo calendar day

### FCM Message

```
Title: أين صلاتك على النبي ﷺ؟
Body: لم نرك منذ فترة — عُد وأحيِ ذكر الحبيب ﷺ
```

Hybrid payload format (notification + data) matching existing pattern for click-to-open behavior.

### Edge Cases

- Players with no `updatedAt` → skip
- Round final state → skip all idle notifications
- No FCM token → skip silently
- Admin SDK bypasses security rules — no `database.rules.json` change needed for `lastIdleNotifDate`

## Files Modified

| File | Change |
|------|--------|
| `app/src/commonMain/kotlin/.../presentation/MohamedLoversViewModel.kt` | Add timestamp storage, ticker, elapsed state |
| `app/src/commonMain/kotlin/.../ui/MohamedLoversScreen.kt` | Add `IdleBanner` composable below tap button |
| `app/src/commonMain/resources/values/strings.xml` | Add idle banner string keys (AR) |
| `app/src/commonMain/resources/values-en/strings.xml` | Add idle banner string keys (EN) |
| `app/src/commonMain/resources/values-ur/strings.xml` | Add idle banner string keys (UR) |
| `app/src/commonMain/resources/values-zh/strings.xml` | Add idle banner string keys (ZH) |
| `scripts/populate-leaderboard.js` | Add idle >8h detection + FCM send + debounce |

## Not In Scope

- No changes to `database.rules.json` (admin SDK bypasses)
- No changes to `notify-users.js` (detection lives in populate-leaderboard)
- No new Firebase fields on player node (uses existing `updatedAt`)
- No idle tracking for TenDays/Takbeer flows
