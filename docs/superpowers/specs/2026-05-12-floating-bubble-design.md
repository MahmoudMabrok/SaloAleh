# Floating Bubble Feature — Design Spec

**Date:** 2026-05-12
**Platform:** Android only
**Status:** Approved

---

## Overview

A floating overlay bubble that lets users increment their salawat count while using other apps. Launched from the main counter screen. Every 10 minutes it shows a reminder tooltip "اللهم صل علي محمد وال محمد" for 5 seconds above the bubble, then hides it. The bubble stays on screen until the user taps the close button.

---

## Architecture

### New files (androidMain)

| File | Role |
|------|------|
| `FloatingBubbleService.kt` | Foreground service — draws overlay, manages timer, handles tap/close |
| `ui/FloatingBubbleView.kt` | Programmatic Android View — bubble circle + close button + tooltip |
| `ui/BubblePermissionHandler.kt` | Checks `Settings.canDrawOverlays()`, routes to system settings if missing |

### Modified files

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Add `SYSTEM_ALERT_WINDOW` permission + register `FloatingBubbleService` |
| `ui/PlatformActions.android.kt` | `actual` impl for `startFloatingBubble()` / `stopFloatingBubble()` |
| `ui/PlatformActions.ios.kt` | `actual` no-ops for `startFloatingBubble()` / `stopFloatingBubble()` (feature is Android-only) |
| `ui/PlatformActions.kt` | `expect` declarations for `startFloatingBubble()` / `stopFloatingBubble()` |
| `ui/MohamedLoversScreen.kt` | Start bubble button (calls `PlatformActions`; iOS no-op means button can exist in common UI) |

---

## Bubble UI

**Normal state:**
- Green gradient circle, 68dp diameter
- Small Arabic label "صلوات" above session count number
- Red ✕ close button (20dp) pinned to top-right corner of bubble
- Draggable anywhere on screen

**Reminder state (every 10 min, 5s duration):**
- Tooltip card appears above bubble with downward arrow
- Text: `اللهم صل علي محمد وال محمد` (RTL, serif)
- Frosted glass style (semi-transparent white, blur)
- Fades in → 5s → fades out
- Bubble itself does not change

---

## Behavior

### Start flow
1. User taps "Start Bubble" on main counter screen
2. `PlatformActions.startFloatingBubble()` called
3. `BubblePermissionHandler` checks `Settings.canDrawOverlays(context)`
   - If **false**: open `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` → user returns → taps again
   - If **true**: `startForegroundService(FloatingBubbleService)`
4. Service calls `startForeground()` with a minimal ongoing notification (required by Android)
5. `WindowManager.addView(FloatingBubbleView, params)`
6. 10-minute repeating coroutine timer starts

### Count increment
- User taps bubble → `EngagementStore.incrementTodayCount()` (same Koin instance, same SharedPreferences)
- Session count on bubble updates immediately (local state in service)
- In-app counter reconciles on next app foreground (no real-time sync needed)

### Reminder cycle (every 10 min)
1. Timer fires → animate tooltip in (fade, ~300ms)
2. Wait 5 seconds
3. Animate tooltip out (fade, ~300ms)
4. Timer resets for next 10-minute cycle

### Close
- User taps ✕ → `WindowManager.removeView()` → `stopSelf()`
- Ongoing notification is cancelled automatically when service stops

---

## Permissions

| Permission | Purpose | Grant method |
|-----------|---------|-------------|
| `SYSTEM_ALERT_WINDOW` | Draw over other apps | User grants via system Settings page (one-time) |
| `FOREGROUND_SERVICE` | Run foreground service | Normal permission, auto-granted |

---

## Constraints

- Android 8.0+ (API 26+) required for `TYPE_APPLICATION_OVERLAY` window type
- `SYSTEM_ALERT_WINDOW` cannot be granted via `requestPermissions()` — must use `ACTION_MANAGE_OVERLAY_PERMISSION`
- Foreground service requires an ongoing notification — keep it minimal and silent
- Count sync is eventual (not real-time): bubble writes to same SharedPreferences as app; reconciles on app open

---

## Out of scope

- iOS support (Android only)
- Real-time count sync while app is open
- Scheduling/auto-starting bubble at specific times
