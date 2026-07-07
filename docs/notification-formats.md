# Notification Formats Catalog

All push/local notification formats sent by the SaloAleh app and server-side scripts.

---

## 1. Server-Side Push Notifications

### 1.1 notify-users.js (Retention Segments)

Runs every 6 hours via GitHub Actions. Evaluates 5 segments per user (first match wins).

| # | Segment | Condition | Title | Body |
|---|---------|-----------|-------|------|
| 1 | Day-1 lapsed | Installed yesterday, never opened | `أهلاً بك في مجلس الصلاة 🌙` | `انضم إلى الأمة وابدأ صلاتك على النبي ﷺ — هذه فرصتك` |
| 2 | Mid-week inactive | Round active, no open in 3+ days | `الأمة تُصلي.. وأنت؟ 🤍` | `بقي {daysToFriday} أيام — عُد وصلِّ على سيد المرسلين ﷺ` |
| 3 | Round-end recap | Round final, user hasn't opened today | `ختمت الجولة بالصلاة 🏆` | `شكر الله سعيكم — افتح التطبيق وشاهد من أحيا ذكر النبي ﷺ هذا الأسبوع` |
| 4 | Streak at risk | Round active, opened yesterday but not today | `أكمل سلسلتك من الصلاة! 🔥` | `لا تقطع يومك دون الصلاة على الحبيب ﷺ — افتح التطبيق الآن` |
| 5 | Rival alert | Round active, user within threshold of 10th place | `فرصتك للتصدر! 🔥` | `{gap} صلاة على النبي ﷺ تكفي لتدخل قائمة أكثر المحبين — هيا!` |

- Opt-out: `reminderNotifsEnabled === false` skips all segments.
- Rival debounce: once per day via `lastRivalNotifDate`.

### 1.2 notify-users.js (Ten Days of Dhul Hijjah)

Runs as part of the same script when a Ten Days period is active.

| # | Segment | Condition | Title | Body |
|---|---------|-----------|-------|------|
| 1 | Inactive reminder | User hasn't updated in 48+ hours | `أيام العشر تمضي ⏳` | `اليوم {dayNum} من عشر ذي الحجة — بقي {daysLeft} أيام، لا تفوّت الأجر!` |
| 2 | Rival alert | User within 200 of 10th place | `فرصتك في العشر! 🔥` | `{gap} نقطة تفصلك عن دخول قائمة الأوائل في عشر ذي الحجة — هيا!` |

### 1.3 leaderboard-utils.js (Leaderboard Notifications)

Runs every ~30 min via `populate-leaderboard.js`.

| # | Segment | Condition | Title | Body |
|---|---------|-----------|-------|------|
| 1 | Top-3 dropped | Was top 3, now rank 4+ or gone | `مكانك بين المحبين يناديك 🤍` | `كنت من أكثر المصلّين على النبي ﷺ — لا تتوقف، فالصلاة عليه نور وشفاعة يوم القيامة!` |
| 2 | Top-3 lost position | Was top 3, rank slipped within top 3 | `المنافسة تشتد بين المحبين 🔥` | `تراجع ترتيبك بين أكثر المصلّين على النبي ﷺ — زِد صلواتك وارتقِ، فأقربكم مني مجلسًا أكثركم صلاةً عليّ!` |
| 3 | Dropped from top 10 | Was in top 10, no longer | `خرجت من قائمة الأوائل 😔` | `مكانك بين المحبين يستحق المنافسة — عُد وصلِّ على النبي ﷺ الآن!` |
| 4 | Idle >8h | Round active, player scored but no update in 8+ hours | `أين صلاتك على النبي ﷺ؟` | `الحبيب لا يغفل عن ذكر محبوبه، فاين انت من ذكر الحبيب المصطفي ﷺ` |

- Opt-out: `leaderboardNotifsEnabled === false` skips all leaderboard notifications.
- Dropped debounce: once per round via `lastDropOutNotifRound`.
- Idle debounce: once per day via `lastIdleNotifDate`.

### 1.4 leaderboard-utils.js — Ten Days Top-3 (commented out)

Currently commented out in `populate-leaderboard.js`. Formats if re-enabled:

| # | Segment | Title | Body |
|---|---------|-------|------|
| 1 | Dropped from top 3 | `مكانك بين المتسابقين يناديك 🤍` | `كنت من أعلى المتنافسين في عشر ذي الحجة — لا تتوقف، فالعمل الصالح في هذه الأيام أحب إلى الله!` |
| 2 | Lost position in top 3 | `المنافسة تشتد في العشر 🔥` | `تراجع ترتيبك في عشر ذي الحجة — زِد من عملك الصالح وارتقِ!` |
| 3 | Dropped from top 10 | `خرجت من قائمة العشر الأوائل 😔` | `مكانك في عشر ذي الحجة يستحق المنافسة — عُد وزِد من عملك الصالح!` |

### 1.5 New Build Notification

Broadcast via FCM topic `general`. Triggered by `schedule-build-notification.js` (schedules) + `populate-leaderboard.js` (delivers when due), or directly by `notify-new-build.js`.

| # | Segment | Title | Body |
|---|---------|-------|------|
| 1 | With version | `تحديث جديد قادم 🎉` | `الإصدار {version} في الطريق إليك — قد يستغرق ظهوره بعض الوقت، تحقق من المتجر قريباً` |
| 2 | Without version | `تحديث جديد قادم 🎉` | `إصدار جديد من التطبيق في الطريق إليك — قد يستغرق ظهوره بعض الوقت، تحقق من المتجر قريباً` |

---

## 2. Client-Side Local Notifications

### 2.1 Daily Reminder (Android + iOS)

Fires daily at 9:00 AM Cairo via exact alarm (Android) or `UNCalendarNotificationTrigger` (iOS).

| Title | Body |
|-------|------|
| `اللهم صلِّ على محمد ﷺ` | `تذكيرك اليومي — اضغط لتشارك الصلاة على النبي` |

- Channel: `channel_daily` (Android).
- Toggle: `dailyEnabled` in `NotificationSettingsStore`.

### 2.2 Friday Reminder (Android + iOS)

Fires every hour from 9:00 to 17:00 on Fridays (Cairo time).

| Title | Body |
|-------|------|
| `اللهم صلِّ على محمد ﷺ` | `يوم الجمعة المبارك — صلّ على النبي الكريم` |

- Channel: `channel_friday` (Android).
- Toggle: `fridayEnabled` in `NotificationSettingsStore`.

### 2.3 Retention Check (Android only)

Fires daily via `RetentionCheckWorker`. Shows when user has missed 1+ days.

| Title | Body |
|-------|------|
| `نفتقدك 🤍` | `مضى {missedDays} يوم/أيام منذ آخر زيارة — لا تنسَ الصلاة على النبي ﷺ` |

- Channel: `channel_retention`.

### 2.4 Protection Notification (Android only — foreground service)

Persistent foreground notification while the "protection" superpowers feature is active.

| Title | Body (before noon) | Body (after noon) |
|-------|-------|-------|
| `اهل لا اله الا الله` | `انت في حرز من الشيطان حتى المساء` | `انت في حرز من الشيطان حتى الصبح` |

- Channel: `channel_protection` (low priority, silent).
- Localized in 4 languages (AR, EN, UR, ZH).

### 2.5 FCM Push Display (Android)

`SaloFirebaseMessagingService` receives all server-sent FCM messages and displays them using the title/body from the `data` payload.

- Channel: `channel_push`.
- No fixed format — displays whatever title/body the server sends (see Section 1).

---

## 3. Notification Channels Summary (Android)

| Channel ID | Name | Priority | Description |
|------------|------|----------|-------------|
| `channel_daily` | تذكير يومي | Default | تذكير يومي بالصلاة على النبي |
| `channel_retention` | نفتقدك | Default | تنبيه عند غيابك عن التطبيق |
| `channel_friday` | إشعارات الجمعة | Default | تذكير بالصلاة على النبي كل ساعة يوم الجمعة |
| `channel_push` | إشعارات عامة | Default | إشعارات من الفريق |
| `channel_bubble` | الفقاعة العائمة | Low | إشعار نشط أثناء استخدام الفقاعة العائمة |
| `channel_protection` | (localized) | Low | (localized) |

---

## 4. User Opt-Out Flags

| Flag (RTDB) | Controls | Default |
|-------------|----------|---------|
| `reminderNotifsEnabled` | All segments in `notify-users.js` + Ten Days reminders | `true` (absent = on) |
| `leaderboardNotifsEnabled` | All segments in `leaderboard-utils.js` (top-3, dropout, idle) | `true` (absent = on) |

| Flag (Local) | Controls |
|-------------|----------|
| `dailyEnabled` | Daily 9 AM reminder |
| `fridayEnabled` | Friday hourly reminders |
