/**
 * @file notify-users.js
 * @description Server-side push notification script for the SaloAleh app.
 *
 * Runs every 6 hours via GitHub Actions. Reads per-user activity data from
 * Firebase Realtime Database (RTDB), evaluates five retention segments, and
 * sends targeted FCM messages to at-risk users via Firebase Admin SDK.
 *
 * ## Flow
 *
 *   GitHub Actions (cron 0 */6 * * *)
 *       │
 *       ▼
 *   Resolve round context
 *       │  cairoRoundKey()  → next Friday's date in Cairo time (YYYY-MM-DD)
 *       │  cairoToday()     → today's date in Cairo time
 *       │  isRoundFinal()   → true if current round has closed (Friday ≥ 18:00 Cairo)
 *       │
 *       ▼
 *   Load all users from RTDB  →  mohamed_lovers/users/
 *       │
 *       ▼
 *   Load 10th-place score for rival threshold check (if round active)
 *       │  path: mohamed_lovers/{roundKey}/leaderboard/10
 *       │
 *       ▼
 *   For each user with an FCM token, evaluate segments in priority order:
 *       │
 *       ├─ Segment 1: Day-1 lapsed
 *       │    Condition: installed yesterday, never opened
 *       │    Message:   "لم تبدأ بعد — الجمعة القادمة فرصتك"
 *       │
 *       ├─ Segment 2: Mid-week inactive  (env: NOTIF_MIDWEEK_ENABLED)
 *       │    Condition: round active, no open in 3+ days
 *       │    Message:   "مضاعفة الجمعة بعد X أيام — أين أنت؟"
 *       │
 *       ├─ Segment 3: Round-end recap
 *       │    Condition: round final, user hasn't opened today
 *       │    Message:   "انتهت الجولة! 🏆"
 *       │
 *       ├─ Segment 4: Streak at risk
 *       │    Condition: round active, last open was yesterday (not today)
 *       │    Message:   "لا تنقطع سلسلتك! 🔥"
 *       │
 *       └─ Segment 5: Rival alert  (env: NOTIF_RIVAL_ENABLED)
 *            Condition: round active, user's score is within NOTIF_RIVAL_THRESHOLD of 10th place,
 *                        not already notified today (debounced via lastRivalNotifDate)
 *            Message:   "أنت على بُعد X صلاة من دخول قائمة الأوائل!"
 *            Side effect: writes lastRivalNotifDate to RTDB to prevent repeat sends
 *       │
 *       ▼
 *   await Promise.all(sendPromises)
 *       │
 *       ▼
 *   Batch-write rival debounce flags to RTDB
 *
 * ## Environment Variables
 *
 * | Variable                    | Type    | Default | Description                                  |
 * |-----------------------------|---------|---------|----------------------------------------------|
 * | FIREBASE_SERVICE_ACCOUNT    | string  | —       | JSON service account key (required)          |
 * | FIREBASE_DATABASE_URL       | string  | —       | RTDB URL (required)                          |
 * | NOTIF_RIVAL_ENABLED         | string  | "true"  | Set to "false" to disable rival alerts       |
 * | NOTIF_MIDWEEK_ENABLED       | string  | "true"  | Set to "false" to disable mid-week alerts    |
 * | NOTIF_RIVAL_THRESHOLD       | number  | 200     | Max gap to 10th place that triggers rival    |
 *
 * ## RTDB Schema (read/write paths)
 *
 *   mohamed_lovers/
 *     users/{uid}/
 *       fcmToken          string   — FCM device token (written by client)
 *       installDate       string   — ISO date of first launch (written by client)
 *       lastOpenDate      string   — ISO date of last foreground open (written by client)
 *       lastRivalNotifDate string  — ISO date of last rival alert (written by this script)
 *     {roundKey}/
 *       leaderboard/10    object   — 10th-place entry { score: number }
 *       players/{uid}/
 *         totalCount      number   — user's tap count for the round
 */

const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

/**
 * Returns the round key (YYYY-MM-DD) for the upcoming Friday at 18:00 Cairo time.
 *
 * Mirrors `CompetitionWindowUtils.kt` on the client. A round "ends" on Friday
 * at 18:00 Africa/Cairo. If today is Friday before 18:00, this Friday is still
 * the active round; if it's Friday at or after 18:00, the next Friday is returned.
 *
 * @returns {string} ISO date string of the next round-closing Friday (e.g. "2026-05-08")
 */
function cairoRoundKey() {
  const now = new Date();
  const zone = 'Africa/Cairo';
  const weekdayStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(now);
  const dayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const jsDow = dayMap[weekdayStr];
  const hourStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(now);
  const cairoHour = parseInt(hourStr, 10);
  let daysToFriday = (5 - jsDow + 7) % 7;
  if (daysToFriday === 0 && cairoHour >= 18) daysToFriday = 7;
  const fridayDate = new Date(now.getTime() + daysToFriday * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
}

/**
 * Returns today's date in Cairo timezone as an ISO date string (YYYY-MM-DD).
 *
 * @returns {string}
 */
function cairoToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

/**
 * Returns true if the given round has already closed.
 *
 * A round is final when Cairo local time is past Friday 18:00 on the round's date,
 * or any date after the round's Friday.
 *
 * @param {string} roundKey - ISO date string of the round's Friday (e.g. "2026-05-08")
 * @returns {boolean}
 */
function isRoundFinal(roundKey) {
  const now = new Date();
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hour12: false,
  });
  const parts = Object.fromEntries(fmt.formatToParts(now).map(p => [p.type, p.value]));
  const cairoDate = `${parts.year}-${parts.month}-${parts.day}`;
  const cairoHour = parseInt(parts.hour, 10);
  if (cairoDate > roundKey) return true;
  if (cairoDate === roundKey && cairoHour >= 18) return true;
  return false;
}

/**
 * Returns the number of calendar days between two ISO date strings.
 *
 * @param {string} dateStr1 - Earlier date (YYYY-MM-DD)
 * @param {string} dateStr2 - Later date (YYYY-MM-DD)
 * @returns {number} Positive integer when dateStr2 > dateStr1
 */
function daysBetween(dateStr1, dateStr2) {
  const d1 = new Date(dateStr1);
  const d2 = new Date(dateStr2);
  return Math.round((d2 - d1) / 86400000);
}

/**
 * Main entry point. Orchestrates segment evaluation and FCM dispatch.
 *
 * Segment priority (first match wins, user skipped for later segments):
 *   1. Day-1 lapsed   — highest priority, targets new-install drop-off
 *   2. Mid-week inactive — re-engages users quiet for 3+ days
 *   3. Round-end recap — informs users when a round closes
 *   4. Streak at risk  — nudges users who opened yesterday but not today
 *   5. Rival alert     — competitive nudge when close to top-10 (debounced 1/day)
 *
 * Segments 1–4 are mutually exclusive per user (early return). Segment 5 only
 * fires if the user did not match an earlier segment.
 *
 * @returns {Promise<void>}
 */
async function main() {
  console.log('[notify-users] ===== run start =====');
  const db = admin.database();
  const roundKey = cairoRoundKey();
  const today = cairoToday();
  const isFinal = isRoundFinal(roundKey);

  console.log(`[notify-users] round=${roundKey} today=${today} isFinal=${isFinal}`);

  const rivalThreshold = parseInt(process.env.NOTIF_RIVAL_THRESHOLD || '200', 10);
  const rivalEnabled = process.env.NOTIF_RIVAL_ENABLED !== 'false';
  const midweekEnabled = process.env.NOTIF_MIDWEEK_ENABLED !== 'false';

  console.log(`[notify-users] config: rivalEnabled=${rivalEnabled} midweekEnabled=${midweekEnabled} rivalThreshold=${rivalThreshold}`);

  console.log('[notify-users] fetching users...');
  const usersSnap = await db.ref('mohamed_lovers/users').get();
  if (!usersSnap.exists()) { console.log('[notify-users] no users found — exiting'); process.exit(0); }

  const totalUsers = usersSnap.size;
  console.log(`[notify-users] loaded ${totalUsers} user(s)`);

  // Pre-fetch 10th-place score once for all rival-alert evaluations.
  let tenthPlaceScore = null;
  if (rivalEnabled && !isFinal) {
    console.log(`[notify-users] fetching 10th-place score for round ${roundKey}...`);
    const lbSnap = await db.ref(`mohamed_lovers/${roundKey}/leaderboard/10`).get();
    tenthPlaceScore = lbSnap.exists() ? (lbSnap.val()?.score ?? null) : null;
    console.log(`[notify-users] 10th-place score=${tenthPlaceScore}`);
  } else {
    console.log('[notify-users] skipping 10th-place fetch (rivalEnabled=false or round final)');
  }

  const yesterday = new Date(new Date().getTime() - 86400000);
  const yesterdayStr = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(yesterday);

  const sendPromises = [];
  const updates = {};
  const segmentCounts = { day1_lapsed: 0, midweek_inactive: 0, round_end: 0, streak_at_risk: 0, rival_alert: 0, no_token: 0, no_segment: 0 };

  console.log('[notify-users] evaluating segments...');

  usersSnap.forEach(userSnap => {
    const uid = userSnap.key;
    const user = userSnap.val();
    const { fcmToken, installDate, lastOpenDate, lastRivalNotifDate } = user || {};

    if (!fcmToken) {
      console.log(`[notify-users] skip uid=${uid}: no FCM token`);
      segmentCounts.no_token++;
      return;
    }

    const daysInactive = lastOpenDate ? daysBetween(lastOpenDate, today) : null;
    const daysInstalled = installDate ? daysBetween(installDate, today) : null;

    console.log(`[notify-users] uid=${uid} daysInstalled=${daysInstalled} daysInactive=${daysInactive} lastOpenDate=${lastOpenDate ?? 'none'} lastRivalNotifDate=${lastRivalNotifDate ?? 'none'}`);

    // Segment 1: Day-1 lapsed — installed yesterday, never opened since install day.
    if (daysInstalled === 1 && daysInactive >= 1) {
      console.log(`[notify-users] uid=${uid} → segment=day1_lapsed`);
      segmentCounts.day1_lapsed++;
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'السلام عليكم', body: 'لم تبدأ بعد — الجمعة القادمة فرصتك' },
        })
          .then(msgId => console.log(`[notify-users] sent day1_lapsed uid=${uid} msgId=${msgId}`))
          .catch(e => console.error(`[notify-users] failed day1_lapsed uid=${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 2: Mid-week inactive — no open in 3+ days while round is still active.
    if (midweekEnabled && !isFinal && daysInactive >= 3) {
      const daysToFriday = Math.max(0, daysBetween(today, roundKey));
      console.log(`[notify-users] uid=${uid} → segment=midweek_inactive daysToFriday=${daysToFriday}`);
      segmentCounts.midweek_inactive++;
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'نفتقدك 🤍', body: `مضاعفة الجمعة بعد ${daysToFriday} أيام — أين أنت؟` },
        })
          .then(msgId => console.log(`[notify-users] sent midweek_inactive uid=${uid} msgId=${msgId}`))
          .catch(e => console.error(`[notify-users] failed midweek_inactive uid=${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 3: Round-end recap — round has closed, user hasn't opened the app today.
    if (isFinal && lastOpenDate && lastOpenDate < today) {
      console.log(`[notify-users] uid=${uid} → segment=round_end`);
      segmentCounts.round_end++;
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'انتهت الجولة! 🏆', body: 'افتح التطبيق لتعرف ترتيبك النهائي' },
        })
          .then(msgId => console.log(`[notify-users] sent round_end uid=${uid} msgId=${msgId}`))
          .catch(e => console.error(`[notify-users] failed round_end uid=${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 4: Streak at risk — opened yesterday but not today, round still active.
    if (!isFinal && lastOpenDate === yesterdayStr && daysInactive === 1) {
      console.log(`[notify-users] uid=${uid} → segment=streak_at_risk`);
      segmentCounts.streak_at_risk++;
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'لا تنقطع سلسلتك! 🔥', body: 'سلسلتك على المحك — افتح التطبيق الآن' },
        })
          .then(msgId => console.log(`[notify-users] sent streak_at_risk uid=${uid} msgId=${msgId}`))
          .catch(e => console.error(`[notify-users] failed streak_at_risk uid=${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 5: Rival alert — user is outside top 10 but within rivalThreshold taps of 10th place.
    // Debounced to once per day via lastRivalNotifDate written back to RTDB.
    if (rivalEnabled && !isFinal && tenthPlaceScore !== null && lastRivalNotifDate !== today) {
      segmentCounts.rival_alert++;
      sendPromises.push(
        db.ref(`mohamed_lovers/${roundKey}/players/${uid}/totalCount`).get().then(snap => {
          const userScore = snap.val() ?? 0;
          const gap = tenthPlaceScore - userScore;
          console.log(`[notify-users] uid=${uid} rival check: userScore=${userScore} 10thScore=${tenthPlaceScore} gap=${gap}`);
          if (gap > 0 && gap <= rivalThreshold) {
            console.log(`[notify-users] uid=${uid} → segment=rival_alert gap=${gap}`);
            updates[`mohamed_lovers/users/${uid}/lastRivalNotifDate`] = today;
            return admin.messaging().send({
              token: fcmToken,
              notification: { title: 'قريب من الصدارة! 🔥', body: `أنت على بُعد ${gap} صلاة من دخول قائمة الأوائل!` },
            })
              .then(msgId => console.log(`[notify-users] sent rival_alert uid=${uid} gap=${gap} msgId=${msgId}`));
          } else {
            console.log(`[notify-users] uid=${uid} rival gap=${gap} outside threshold — skip`);
          }
        }).catch(e => console.error(`[notify-users] failed rival_alert uid=${uid}: ${e.message}`))
      );
    } else {
      console.log(`[notify-users] uid=${uid} → no segment matched`);
      segmentCounts.no_segment++;
    }
  });

  console.log(`[notify-users] segment counts: ${JSON.stringify(segmentCounts)}`);
  console.log(`[notify-users] awaiting ${sendPromises.length} FCM send(s)...`);

  await Promise.all(sendPromises);

  if (Object.keys(updates).length > 0) {
    console.log(`[notify-users] writing ${Object.keys(updates).length} rival debounce flag(s) to RTDB...`);
    await db.ref('/').update(updates);
    console.log('[notify-users] debounce flags written');
  }

  console.log(`[notify-users] done — ${sendPromises.length} send(s) dispatched`);
  console.log('[notify-users] ===== run end =====');
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
