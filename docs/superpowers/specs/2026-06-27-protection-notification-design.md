# Protection Notification Service — Design Spec
_Date: 2026-06-27_

## Goal

When the user completes 100 dhikr taps in the Dhikr Challenge screen, start a persistent foreground notification announcing they are under spiritual protection. The notification stays until a time-of-day cutoff (sunset or sunrise, approximated by fixed local clock times). Android only (POC scope).

## Message Logic

| Tap time (device local clock) | Notification text | Service stops at |
|---|---|---|
| Before 12:00 | انت في حرز من الشيطان حتى المساء | 18:00 same day |
| 12:00 or after | انت في حرز من الشيطان حتى الصبح | 06:00 next day |

Cutoff is computed as `ms = targetCalendar.timeInMillis - System.currentTimeMillis()` — no location permission required.

## Architecture

KMP-clean: the ViewModel lives in `commonMain` and must not reference Android APIs. The existing `celebrationMilestone` field in `DhikrChallengeUiState` is already emitted on every 100-tap milestone. The Android Compose UI layer observes it and starts the service — no new `expect`/`actual` needed.

```
onDhikrTap() → state.celebrationMilestone = 100
  → Android UI LaunchedEffect observes milestone
  → startForegroundService(ProtectionNotificationService)
  → Service: checks local hour → picks message & cutoff
  → Handler.postDelayed(stopSelf, msUntilCutoff)
```

If the user hits another 100 while the service is running (e.g. reaches 200), the service is started again with a fresh intent — `onStartCommand` re-evaluates the current time and resets the stop timer.

## Components

### New: `ProtectionNotificationService.kt` (androidMain)
- Extends `Service`
- `onStartCommand`: reads current `Calendar.HOUR_OF_DAY`, picks message and cutoff time, posts `startForeground` with ongoing notification, schedules `stopSelf()` via `Handler.postDelayed`
- `onDestroy`: cancels the handler callback, calls `stopForeground`
- Companion: `isRunning: StateFlow<Boolean>` (mirrors `FloatingBubbleService` pattern)

### Modified: `NotificationChannels.kt` (androidMain)
- Add `CHANNEL_PROTECTION = "channel_protection"`
- Add `NOTIF_ID_PROTECTION = 1006`
- Add channel creation in `createAll()` with `IMPORTANCE_LOW`, no sound, no vibration

### Modified: `DhikrRewardsScreen.kt` or dhikr tap screen (commonMain/androidMain)
- Add `LaunchedEffect(state.celebrationMilestone)` — when milestone > 0 and `% 100 == 0`, call platform action to start service
- Use `LocalContext.current` on Android side to fire `startForegroundService`

### Modified: `AndroidManifest.xml`
- Register `ProtectionNotificationService` with `android:foregroundServiceType="specialUse"` (or omit type for API < 34 compatibility — use `dataSync` as safe fallback)

### String resources (all 4 locales)
| Key | AR | EN | UR | ZH |
|---|---|---|---|---|
| `protection_till_evening` | انت في حرز من الشيطان حتى المساء | You are under divine protection till evening | آپ شام تک شیطان سے محفوظ ہیں | 您受到保护直到傍晚 |
| `protection_till_morning` | انت في حرز من الشيطان حتى الصبح | You are under divine protection till morning | آپ صبح تک شیطان سے محفوظ ہیں | 您受到保护直到早晨 |

## Out of Scope (POC)
- iOS equivalent
- Exact astronomical sunset/sunrise (requires location permission)
- Persisting service across device reboot
- User toggle to disable this feature
