// Reads user activity from RTDB, evaluates notification segments,
// sends FCM messages for at-risk users. Runs every 6h via GitHub Actions.
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Mirrors CompetitionWindowUtils.kt — next Friday 18:00 Cairo
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

function cairoToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

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

function daysBetween(dateStr1, dateStr2) {
  const d1 = new Date(dateStr1);
  const d2 = new Date(dateStr2);
  return Math.round((d2 - d1) / 86400000);
}

async function main() {
  const db = admin.database();
  const roundKey = cairoRoundKey();
  const today = cairoToday();
  const isFinal = isRoundFinal(roundKey);

  console.log(`Round: ${roundKey} | isFinal: ${isFinal} | Today: ${today}`);

  const rivalThreshold = parseInt(process.env.NOTIF_RIVAL_THRESHOLD || '200', 10);
  const rivalEnabled = process.env.NOTIF_RIVAL_ENABLED !== 'false';
  const midweekEnabled = process.env.NOTIF_MIDWEEK_ENABLED !== 'false';

  const usersSnap = await db.ref('mohamed_lovers/users').get();
  if (!usersSnap.exists()) { console.log('No users found.'); process.exit(0); }

  let tenthPlaceScore = null;
  if (rivalEnabled && !isFinal) {
    const lbSnap = await db.ref(`mohamed_lovers/${roundKey}/leaderboard/10`).get();
    if (lbSnap.exists()) tenthPlaceScore = lbSnap.val()?.score ?? null;
  }

  const yesterday = new Date(new Date().getTime() - 86400000);
  const yesterdayStr = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(yesterday);

  const sendPromises = [];
  const updates = {};

  usersSnap.forEach(userSnap => {
    const uid = userSnap.key;
    const user = userSnap.val();
    const { fcmToken, installDate, lastOpenDate, lastRivalNotifDate } = user || {};

    if (!fcmToken) return;

    const daysInactive = lastOpenDate ? daysBetween(lastOpenDate, today) : null;
    const daysInstalled = installDate ? daysBetween(installDate, today) : null;

    // Segment 1: Day-1 lapsed
    if (daysInstalled === 1 && daysInactive >= 1) {
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'السلام عليكم', body: 'لم تبدأ بعد — الجمعة القادمة فرصتك' },
        }).catch(e => console.error(`day1_lapsed ${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 2: Mid-week inactive (3+ days, round active)
    if (midweekEnabled && !isFinal && daysInactive >= 3) {
      const daysToFriday = Math.max(0, daysBetween(today, roundKey));
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'نفتقدك 🤍', body: `مضاعفة الجمعة بعد ${daysToFriday} أيام — أين أنت؟` },
        }).catch(e => console.error(`midweek_inactive ${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 3: Round-end recap (isFinal, user not opened today)
    if (isFinal && lastOpenDate && lastOpenDate < today) {
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'انتهت الجولة! 🏆', body: 'افتح التطبيق لتعرف ترتيبك النهائي' },
        }).catch(e => console.error(`round_end ${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 4: Streak at risk — opened yesterday but not today
    if (!isFinal && lastOpenDate === yesterdayStr && daysInactive === 1) {
      sendPromises.push(
        admin.messaging().send({
          token: fcmToken,
          notification: { title: 'لا تنقطع سلسلتك! 🔥', body: 'سلسلتك على المحك — افتح التطبيق الآن' },
        }).catch(e => console.error(`streak_at_risk ${uid}: ${e.message}`))
      );
      return;
    }

    // Segment 5: Rival alert (out of top 10, close to entering)
    if (rivalEnabled && !isFinal && tenthPlaceScore !== null && lastRivalNotifDate !== today) {
      sendPromises.push(
        db.ref(`mohamed_lovers/${roundKey}/players/${uid}/totalCount`).get().then(snap => {
          const userScore = snap.val() ?? 0;
          const gap = tenthPlaceScore - userScore;
          if (gap > 0 && gap <= rivalThreshold) {
            updates[`mohamed_lovers/users/${uid}/lastRivalNotifDate`] = today;
            return admin.messaging().send({
              token: fcmToken,
              notification: { title: 'قريب من الصدارة! 🔥', body: `أنت على بُعد ${gap} صلاة من دخول قائمة الأوائل!` },
            });
          }
        }).catch(e => console.error(`rival_alert ${uid}: ${e.message}`))
      );
    }
  });

  await Promise.all(sendPromises);

  if (Object.keys(updates).length > 0) {
    await db.ref('/').update(updates);
    console.log(`Updated ${Object.keys(updates).length} rival notif debounce flags.`);
  }

  console.log(`Processed ${sendPromises.length} notification sends.`);
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
