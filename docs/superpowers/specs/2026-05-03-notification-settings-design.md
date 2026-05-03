# Notification Settings — Design Spec
Date: 2026-05-03

## Overview

Add a settings screen (accessible via ⚙️ gear icon in the top app bar) that lets users toggle two notification types:
- **Daily reminder** — fires once per day
- **Friday notifications** — fires every hour from 9am to 5pm (9 notifications per Friday)

Both work on Android and iOS. All preferences persisted locally per device.

---

## Section 1 — Data Layer

**New file:** `app/src/commonMain/kotlin/tools/mo3ta/salo/data/notification/NotificationSettingsStore.kt`

```kotlin
class NotificationSettingsStore(private val settings: Settings) {
    var dailyEnabled: Boolean
        get() = settings.getBoolean("notif_daily_enabled", true)
        set(v) = settings.putBoolean("notif_daily_enabled", v)

    var fridayEnabled: Boolean
        get() = settings.getBoolean("notif_friday_enabled", true)
        set(v) = settings.putBoolean("notif_friday_enabled", v)
}
```

- Keys: `notif_daily_enabled`, `notif_friday_enabled` (both default `true`)
- Registered in Koin `AppModule` as a single instance using the existing `Settings` binding
- No changes to `EngagementStore`

---

## Section 2 — Platform Scheduling (expect/actual)

**New file:** `app/src/commonMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`

```kotlin
expect object NotificationScheduler {
    fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean)
}
```

Called whenever a toggle changes and on every app launch.

### Android actual (`androidMain`)

File: `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`

- `dailyEnabled=true` → enqueue `DailyNotificationWorker` with `ExistingPeriodicWorkPolicy.KEEP`, 1-day interval, tag `"daily_notification"`
- `dailyEnabled=false` → `WorkManager.cancelUniqueWork("daily_notification")`
- `fridayEnabled=true` → enqueue new `FridayNotificationWorker`, 1-hour interval, tag `"friday_notification"`
- `fridayEnabled=false` → `WorkManager.cancelUniqueWork("friday_notification")`

**New file:** `app/src/androidMain/kotlin/tools/mo3ta/salo/notification/FridayNotificationWorker.kt`

`doWork()` logic:
1. Get current datetime in Cairo timezone (`TimeZone.of("Africa/Cairo")`)
2. If not Friday → return `Result.success()` (no-op)
3. If current hour not in `9..17` → return `Result.success()` (no-op)
4. Post notification on `CHANNEL_FRIDAY`

**New channel:** Add `CHANNEL_FRIDAY = "channel_friday"` to `NotificationChannels`, label `إشعارات الجمعة`, importance `IMPORTANCE_DEFAULT`.

### iOS actual (`iosMain`)

File: `app/src/iosMain/kotlin/tools/mo3ta/salo/notification/NotificationScheduler.kt`

Uses `UNUserNotificationCenter`:

- `dailyEnabled=true` → schedule 1 repeating `UNCalendarNotificationTrigger` at 09:00 daily, identifier `"notif_daily"`
- `dailyEnabled=false` → `removePendingNotificationRequests(["notif_daily"])`
- `fridayEnabled=true` → schedule 9 repeating `UNCalendarNotificationTrigger` for Friday (weekday=6) at hours 9–17, identifiers `"notif_friday_9"` … `"notif_friday_17"`
- `fridayEnabled=false` → remove all 9 Friday identifiers

Triggers use device local time (user sets device to Cairo timezone, standard assumption).

---

## Section 3 — Settings Screen UI

**New file:** `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/SettingsScreen.kt`

- Full screen composable
- Navigation: boolean state `showSettings` in `App.kt` — no nav library
- No ViewModel — reads/writes `NotificationSettingsStore` directly via Koin `get()`

Structure:
```
SettingsScreen
  ├── TopAppBar("الإعدادات", back arrow → showSettings=false)
  └── Column
        └── Section header "الإشعارات"
              ├── SettingToggleRow("تذكير يومي", "مرة واحدة يوميًا", dailyEnabled, onDailyToggle)
              └── SettingToggleRow("إشعارات الجمعة", "كل ساعة من 9ص – 5م", fridayEnabled, onFridayToggle)
```

`onDailyToggle` / `onFridayToggle`:
1. Write new value to `NotificationSettingsStore`
2. Call `NotificationScheduler.apply(daily, friday)`

`SettingToggleRow` — private composable within the file: label + subtitle + `Switch`.

**Change to `App.kt`:** Add `⚙️` `IconButton` to the existing top app bar that sets `showSettings = true`.

---

## Section 4 — App Launch & Permission Wiring

### Android `MainActivity`

Replace current `NotificationScheduler.schedule(this)` call with:
```kotlin
val store = get<NotificationSettingsStore>()
NotificationScheduler.apply(store.dailyEnabled, store.fridayEnabled)
```

Ensures WorkManager state matches stored prefs on every launch (fresh install, reinstall, update).

### iOS `iOSApp.swift` / `App.kt`

On app start (after permission granted), call `NotificationScheduler.apply(store.dailyEnabled, store.fridayEnabled)` — re-schedules iOS `UNUserNotificationCenter` triggers to match current prefs.

### Koin

Add to `AppModule`:
```kotlin
single { NotificationSettingsStore(get()) }
```

---

## Decisions

| Decision | Choice | Reason |
|---|---|---|
| Settings placement | ⚙️ top app bar icon | Doesn't crowd bottom nav; standard pattern |
| Screen style | Full screen, simple toggles | No time picker needed; minimal UI |
| Storage | Separate `NotificationSettingsStore` | Keeps `EngagementStore` focused on engagement |
| iOS scheduling | `UNCalendarNotificationTrigger` repeating | Native, reliable, no background task needed |
| Android Friday | 1-hour `PeriodicWorkRequest` with day/hour guard | WorkManager min interval is 15min; 1hr is safe |
| Existing `NotificationScheduler` (androidMain) | Replace with `actual object` | Current plain object conflicts with new expect/actual; file is replaced not renamed |
| Friday range | 9am – 5pm device local time | User confirmed; 9 notifications per Friday |
| Daily time (iOS) | Fixed 9:00am | No time picker in scope; sensible default |
