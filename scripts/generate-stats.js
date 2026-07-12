// Reads live data from Firebase RTDB and writes stats.json to the repo root.
// Run daily via GitHub Actions (update-stats.yml) so landing.html always shows
// current numbers without any client-side Firebase credentials.
const admin = require('firebase-admin');
const fs    = require('fs');
const path  = require('path');
const {
  mirrorYesterdayTotalScores,
  mirrorDailyBadgeClear,
  mirrorRoundStreakClear,
  mirrorDhikrAggregateAndClean,
  mirrorBaqiyatAggregateAndClean,
  mirrorIstighfarAggregateAndClean,
  mirrorQuranAggregateAndClean,
  mirrorHeroes,
} = require('./firestore-utils');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL    = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Mirrors cairoRoundKey() in other scripts — returns the active round's key.
function cairoRoundKey() {
  const now  = new Date();
  const zone = 'Africa/Cairo';
  const weekdayStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(now);
  const dayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const jsDow  = dayMap[weekdayStr];
  const hourStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(now);
  const cairoHour = parseInt(hourStr, 10);
  let daysToFriday = (5 - jsDow + 7) % 7;
  if (daysToFriday === 0 && cairoHour >= 19) daysToFriday = 7;
  const fridayDate = new Date(now.getTime() + daysToFriday * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
}

async function main() {
  const db       = admin.database();
  const roundKey = cairoRoundKey();
  console.log(`Active round: ${roundKey}`);

  const [allTimeTotalSnap, playersSnap, roundTotalSnap, leaderboardSnap, dailyLeaderboardSnap] = await Promise.all([
    db.ref('mohamed_lovers/allTimeTotal').get(),
    db.ref(`mohamed_lovers/${roundKey}/players`).get(),
    db.ref(`mohamed_lovers/${roundKey}/roundTotal`).get(),
    db.ref(`mohamed_lovers/${roundKey}/leaderboard`).get(),
    db.ref(`mohamed_lovers/${roundKey}/dailyLeaderboard`).get(),
  ]);

  const weekSalawat    = roundTotalSnap.val()    || 0;
  const allTimeSalawat = (allTimeTotalSnap.val() || 0) + weekSalawat;

  // Compute today's count by diffing against yesterday's file (same round only)
  const dateStr = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
  const statsDir = path.join(__dirname, '..', 'stats');
  const yesterdayDate = new Date(Date.now() - 86400000);
  const yesterdayStr  = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(yesterdayDate);
  let prevWeekSalawat = 0;
  try {
    const prev = JSON.parse(fs.readFileSync(path.join(statsDir, `${yesterdayStr}.json`), 'utf8'));
    if (prev.roundKey === roundKey) prevWeekSalawat = prev.weekSalawat || 0;
  } catch {}
  const todaySalawat = Math.max(0, weekSalawat - prevWeekSalawat);

  let activePlayers = 0;
  let topScore      = 0;
  const countries   = new Set();
  const yesterdayTotalScoreUpdates = {};

  if (playersSnap.exists()) {
    playersSnap.forEach(child => {
      const data  = child.val();
      const score = data?.totalCount || 0;
      activePlayers++;
      if (score > topScore) topScore = score;
      if (data?.countryCode) countries.add(data.countryCode);
      if (child.key) {
        yesterdayTotalScoreUpdates[
          `mohamed_lovers/${roundKey}/players/${child.key}/yesterdayTotalScore`
        ] = score;
      }
    });
  }

  const leaderboard = [];
  if (leaderboardSnap.exists()) {
    const lb = leaderboardSnap.val();
    for (let i = 1; i <= 10; i++) {
      const entry = lb[String(i)];
      if (entry) leaderboard.push(entry);
    }
  }

  const stats = {
    allTimeSalawat,
    weekSalawat,
    todaySalawat,
    activePlayers,
    countriesCount: countries.size,
    countries: [...countries].sort(),
    topScore,
    roundKey,
    leaderboard,
    updatedAt: new Date().toISOString(),
  };

  if (Object.keys(yesterdayTotalScoreUpdates).length > 0) {
    await db.ref('/').update(yesterdayTotalScoreUpdates);
    console.log(`Updated yesterdayTotalScore for ${Object.keys(yesterdayTotalScoreUpdates).length} player(s).`);
    // Phase 1: mirror to Firestore
    await mirrorYesterdayTotalScores(admin.firestore(), roundKey, yesterdayTotalScoreUpdates);
  }

  // Clear dailyBadge for all players (midnight reset)
  const badgeUpdates = {};
  if (playersSnap.exists()) {
    playersSnap.forEach((child) => {
      if (child.val().dailyBadge) {
        badgeUpdates[`mohamed_lovers/${roundKey}/players/${child.key}/dailyBadge`] = null;
      }
    });
  }
  // Also clear from leaderboard entries
  if (leaderboardSnap.exists()) {
    leaderboardSnap.forEach((child) => {
      if (child.val().dailyBadge) {
        badgeUpdates[`mohamed_lovers/${roundKey}/leaderboard/${child.key}/dailyBadge`] = null;
      }
    });
  }
  if (Object.keys(badgeUpdates).length > 0) {
    await db.ref('/').update(badgeUpdates);
    console.log(`Cleared ${Object.keys(badgeUpdates).length} dailyBadge fields`);
    // Phase 1: mirror to Firestore
    const badgePlayerUids = [];
    const badgeLbKeys = [];
    if (playersSnap.exists()) {
      playersSnap.forEach((child) => {
        if (child.val().dailyBadge && child.key) badgePlayerUids.push(child.key);
      });
    }
    if (leaderboardSnap.exists()) {
      leaderboardSnap.forEach((child) => {
        if (child.val().dailyBadge && child.key) badgeLbKeys.push(child.key);
      });
    }
    await mirrorDailyBadgeClear(admin.firestore(), roundKey, badgePlayerUids, badgeLbKeys);
  }

  // Break the round-streak badge for players who sent no salawat today. A player is
  // inactive today when their totalCount has not grown since the previous run's
  // snapshot (yesterdayTotalScore — the same signal the daily leaderboard uses), so
  // their leaderboard streak badge must disappear. Active players keep the value the
  // client last published.
  const streakUpdates = {};
  const brokenStreakUids = new Set();
  if (playersSnap.exists()) {
    playersSnap.forEach((child) => {
      const data = child.val();
      const streak = data?.roundStreak || 0;
      if (streak > 0 && child.key) {
        const prevScore = data?.yesterdayTotalScore || 0;
        const curScore = data?.totalCount || 0;
        if (curScore <= prevScore) {
          streakUpdates[`mohamed_lovers/${roundKey}/players/${child.key}/roundStreak`] = null;
          brokenStreakUids.add(child.key);
        }
      }
    });
  }
  // Also clear from leaderboard entries (keyed by rank; match on uid).
  const brokenStreakLbKeys = [];
  if (leaderboardSnap.exists()) {
    leaderboardSnap.forEach((child) => {
      const entry = child.val();
      if (entry && entry.roundStreak && brokenStreakUids.has(entry.uid) && child.key) {
        streakUpdates[`mohamed_lovers/${roundKey}/leaderboard/${child.key}/roundStreak`] = null;
        brokenStreakLbKeys.push(child.key);
      }
    });
  }
  if (Object.keys(streakUpdates).length > 0) {
    await db.ref('/').update(streakUpdates);
    console.log(`Broke ${brokenStreakUids.size} inactive round-streak(s)`);
    // Phase 1: mirror to Firestore
    await mirrorRoundStreakClear(admin.firestore(), roundKey, [...brokenStreakUids], brokenStreakLbKeys);
  }

  if (!fs.existsSync(statsDir)) fs.mkdirSync(statsDir);
  const outPath = path.join(statsDir, `${dateStr}.json`);
  fs.writeFileSync(outPath, JSON.stringify(stats, null, 2));
  console.log(`stats/${dateStr}.json written:`, stats);

  await sendDailyTop3Notifications(db, dailyLeaderboardSnap);
  await sendDhikrChallengeRank1Notification(db);
  await sendBaqiyatChallengeRank1Notification(db);
  await sendIstighfarChallengeRank1Notification(db);
  await sendZabadChallengeRank1Notification(db);
  await sendGharsChallengeRank1Notification(db);
  await sendQuranChallengeRank1Notification(db);
  // Persist the day's champions BEFORE the per-challenge day nodes are deleted by
  // the aggregate-and-clean steps below (those remove 100_challenge/{today} etc).
  await persistHeroes(db, dailyLeaderboardSnap);
  await aggregateAndCleanDhikrChallenge(db);
  await aggregateAndCleanBaqiyatChallenge(db);
  await aggregateAndCleanIstighfarChallenge(db);
  await aggregateAndCleanZabadChallenge(db);
  await aggregateAndCleanGharsChallenge(db);
  await aggregateAndCleanQuranChallenge(db);

  process.exit(0);
}

async function sendDailyTop3Notifications(db, dailyLeaderboardSnap) {
  if (!dailyLeaderboardSnap.exists()) {
    console.log('[daily-top3] no daily leaderboard data — skip');
    return;
  }
  const lb = dailyLeaderboardSnap.val();
  if (lb.isFinal) {
    console.log('[daily-top3] round is final — skip');
    return;
  }

  const medals = ['🥇', '🥈', '🥉'];
  const nameParts = [];
  for (let rank = 1; rank <= 3; rank++) {
    const entry = lb[String(rank)];
    if (!entry?.uid) break;
    const name = entry.nickname || entry.uid.slice(-6).toUpperCase();
    nameParts.push(`${medals[rank - 1]} ${name}`);
  }

  if (nameParts.length === 0) {
    console.log('[daily-top3] no entries — skip');
    return;
  }

  const title = 'أبطال اليوم 🌟';
  const body = `تهانينا للمتصدرين في الصلاة على النبي ﷺ اليوم: ${nameParts.join(' | ')}`;

  const msgId = await admin.messaging().send({
    topic: 'leaderboard_notifs',
    notification: { title, body },
    data: { title, body, notification_type: 'daily_top3' },
  });
  console.log(`[daily-top3] broadcast to topic "general" msgId=${msgId}`);
}

async function sendDhikrChallengeRank1Notification(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  console.log(`[dhikr-rank1] checking 100_challenge/${today}/leaderboard for rank 1 winner`);
  const rank1Snap = await db.ref(`100_challenge/${today}/leaderboard/0`).get();

  if (!rank1Snap.exists()) {
    console.log('[dhikr-rank1] no rank 1 entry in leaderboard — skip');
    return;
  }

  const rank1Entry = rank1Snap.val() || {};
  const rank1Uid = rank1Entry.uid;
  const rank1Count = rank1Entry.count || 0;

  if (!rank1Uid || rank1Count === 0) {
    console.log('[dhikr-rank1] rank 1 entry missing uid or count — skip');
    return;
  }

  const name = typeof rank1Entry.nickname === 'string' && rank1Entry.nickname.trim()
    ? rank1Entry.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تحدي الـ١٠٠ 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي الـ١٠٠ ذكر اليوم بـ ${rank1Count} ذكراً — جزاك الله خيراً!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'dhikr_challenge_rank1' },
    });
    console.log(`[dhikr-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[dhikr-rank1] send failed: ${e.message}`);
  }
}

async function sendBaqiyatChallengeRank1Notification(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  console.log(`[baqiyat-rank1] checking baqiyat_saliha/${today}/leaderboard for rank 1 winner`);
  const rank1Snap = await db.ref(`baqiyat_saliha/${today}/leaderboard/0`).get();

  if (!rank1Snap.exists()) {
    console.log('[baqiyat-rank1] no rank 1 entry in leaderboard — skip');
    return;
  }

  const rank1Entry = rank1Snap.val() || {};
  const rank1Uid = rank1Entry.uid;
  const rank1Count = rank1Entry.count || 0;

  if (!rank1Uid || rank1Count === 0) {
    console.log('[baqiyat-rank1] rank 1 entry missing uid or count — skip');
    return;
  }

  const name = typeof rank1Entry.nickname === 'string' && rank1Entry.nickname.trim()
    ? rank1Entry.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في الباقيات الصالحات 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي الباقيات الصالحات اليوم بـ ${rank1Count} دورة — جزاك الله خيراً!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'baqiyat_challenge_rank1' },
    });
    console.log(`[baqiyat-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[baqiyat-rank1] send failed: ${e.message}`);
  }
}

async function sendIstighfarChallengeRank1Notification(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  console.log(`[istighfar-rank1] checking istighfar_challenge/${today}/leaderboard for rank 1 winner`);
  const rank1Snap = await db.ref(`istighfar_challenge/${today}/leaderboard/0`).get();

  if (!rank1Snap.exists()) {
    console.log('[istighfar-rank1] no rank 1 entry in leaderboard — skip');
    return;
  }

  const rank1Entry = rank1Snap.val() || {};
  const rank1Uid = rank1Entry.uid;
  const rank1Count = rank1Entry.count || 0;

  if (!rank1Uid || rank1Count === 0) {
    console.log('[istighfar-rank1] rank 1 entry missing uid or count — skip');
    return;
  }

  const name = typeof rank1Entry.nickname === 'string' && rank1Entry.nickname.trim()
    ? rank1Entry.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في الاستغفار 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي الاستغفار اليوم بـ ${rank1Count} مرة — غفر الله لك!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'istighfar_challenge_rank1' },
    });
    console.log(`[istighfar-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[istighfar-rank1] send failed: ${e.message}`);
  }
}

async function sendQuranChallengeRank1Notification(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  console.log(`[quran-rank1] checking quran_challenge/${today}/leaderboard for rank 1 winner`);
  const rank1Snap = await db.ref(`quran_challenge/${today}/leaderboard/0`).get();

  if (!rank1Snap.exists()) {
    console.log('[quran-rank1] no rank 1 entry in leaderboard — skip');
    return;
  }

  const rank1Entry = rank1Snap.val() || {};
  const rank1Uid = rank1Entry.uid;
  const rank1Count = rank1Entry.count || 0;

  if (!rank1Uid || rank1Count === 0) {
    console.log('[quran-rank1] rank 1 entry missing uid or count — skip');
    return;
  }

  const name = typeof rank1Entry.nickname === 'string' && rank1Entry.nickname.trim()
    ? rank1Entry.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تلاوة القرآن 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي تلاوة القرآن اليوم بـ ${rank1Count} صفحة — بارك الله فيك!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'quran_challenge_rank1' },
    });
    console.log(`[quran-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[quran-rank1] send failed: ${e.message}`);
  }
}

// Persists the day's top-3 champions across all active challenges to a single
// RTDB node (mohamed_lovers/heroes) that the app reads (read-only). Overwritten
// daily. Mirrored to Firestore per the Phase-1 dual-write convention.
async function sendZabadChallengeRank1Notification(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  console.log(`[zabad-rank1] checking zabad_challenge/${today}/leaderboard for rank 1 winner`);
  const rank1Snap = await db.ref(`zabad_challenge/${today}/leaderboard/0`).get();

  if (!rank1Snap.exists()) {
    console.log('[zabad-rank1] no rank 1 entry in leaderboard — skip');
    return;
  }

  const rank1Entry = rank1Snap.val() || {};
  const rank1Uid = rank1Entry.uid;
  const rank1Count = rank1Entry.count || 0;

  if (!rank1Uid || rank1Count === 0) {
    console.log('[zabad-rank1] rank 1 entry missing uid or count — skip');
    return;
  }

  const name = typeof rank1Entry.nickname === 'string' && rank1Entry.nickname.trim()
    ? rank1Entry.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تسبيح المئة 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي سبحان الله وبحمده اليوم بـ ${rank1Count} تسبيحة!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'zabad_challenge_rank1' },
    });
    console.log(`[zabad-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[zabad-rank1] send failed: ${e.message}`);
  }
}

async function sendGharsChallengeRank1Notification(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  console.log(`[ghars-rank1] checking ghars_challenge/${today}/leaderboard for rank 1 winner`);
  const rank1Snap = await db.ref(`ghars_challenge/${today}/leaderboard/0`).get();

  if (!rank1Snap.exists()) {
    console.log('[ghars-rank1] no rank 1 entry in leaderboard — skip');
    return;
  }

  const rank1Entry = rank1Snap.val() || {};
  const rank1Uid = rank1Entry.uid;
  const rank1Count = rank1Entry.count || 0;

  if (!rank1Uid || rank1Count === 0) {
    console.log('[ghars-rank1] rank 1 entry missing uid or count — skip');
    return;
  }

  const name = typeof rank1Entry.nickname === 'string' && rank1Entry.nickname.trim()
    ? rank1Entry.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'غارس اليوم في تحدي الغَرْس 🌴';
  const body = `تهانينا لـ ${name} على التصدر في تحدي سبحان الله العظيم وبحمده اليوم بـ ${rank1Count} نخلة!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'ghars_challenge_rank1' },
    });
    console.log(`[ghars-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[ghars-rank1] send failed: ${e.message}`);
  }
}

async function persistHeroes(db, dailyLeaderboardSnap) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  const heroName = (entry) => {
    const nk = typeof entry.nickname === 'string' ? entry.nickname.trim() : '';
    if (nk) return nk.slice(0, 20);
    const uid = typeof entry.uid === 'string' ? entry.uid : '';
    return uid ? uid.slice(-6).toUpperCase() : '';
  };

  // Salawat daily leaderboard is 1-indexed ("1".."10") and uses `score`.
  const salawat = [];
  if (dailyLeaderboardSnap.exists()) {
    const lb = dailyLeaderboardSnap.val() || {};
    if (!lb.isFinal) {
      for (let rank = 1; rank <= 3; rank++) {
        const entry = lb[String(rank)];
        if (!entry?.uid) break;
        salawat.push({
          rank,
          name: heroName(entry),
          count: entry.score || 0,
          countryCode: entry.countryCode || '',
        });
      }
    }
  }

  // Daily count challenges use a 0-indexed leaderboard node and `count`.
  async function challengeTop3(path) {
    const snap = await db.ref(path).get();
    if (!snap.exists()) return [];
    const lb = snap.val() || {};
    const out = [];
    for (let i = 0; i < 3; i++) {
      const entry = lb[String(i)];
      if (!entry?.uid) break;
      out.push({
        rank: i + 1,
        name: heroName(entry),
        count: entry.count || 0,
        countryCode: entry.countryCode || '',
      });
    }
    return out;
  }

  const [dhikr, baqiyat, istighfar, quran] = await Promise.all([
    challengeTop3(`100_challenge/${today}/leaderboard`),
    challengeTop3(`baqiyat_saliha/${today}/leaderboard`),
    challengeTop3(`istighfar_challenge/${today}/leaderboard`),
    challengeTop3(`quran_challenge/${today}/leaderboard`),
  ]);

  const heroes = {
    date: today,
    updatedAt: new Date().toISOString(),
    challenges: { salawat, dhikr, baqiyat, istighfar, quran },
  };

  // RTDB is the source of truth; overwrite the whole node so stale entries from
  // yesterday never linger.
  await db.ref('mohamed_lovers/heroes').set(heroes);
  console.log(
    `[heroes] persisted for ${today}: salawat=${salawat.length} dhikr=${dhikr.length} baqiyat=${baqiyat.length} istighfar=${istighfar.length} quran=${quran.length}`,
  );

  // Phase 1: mirror to Firestore.
  await mirrorHeroes(admin.firestore(), heroes);
}

async function aggregateAndCleanDhikrChallenge(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  const [todayTotalSnap, globalTotalSnap] = await Promise.all([
    db.ref(`100_challenge/${today}/totalTodayDhikr`).get(),
    db.ref('100_challenge/totalDhkr').get(),
  ]);

  const todayTotal  = todayTotalSnap.val()  || 0;
  const globalTotal = globalTotalSnap.val() || 0;

  console.log(`[dhikr-aggregate] today=${today} todayTotal=${todayTotal} globalBefore=${globalTotal}`);

  if (todayTotal === 0) {
    console.log('[dhikr-aggregate] todayTotal is 0 — skip update and delete');
    return;
  }

  await db.ref('100_challenge/totalDhkr').set(globalTotal + todayTotal);
  console.log(`[dhikr-aggregate] totalDhkr updated: ${globalTotal} → ${globalTotal + todayTotal}`);

  await db.ref(`100_challenge/${today}`).remove();
  console.log(`[dhikr-aggregate] deleted 100_challenge/${today}`);

  // Phase 1: mirror to Firestore
  await mirrorDhikrAggregateAndClean(admin.firestore(), today, todayTotal, globalTotal + todayTotal);
}

async function aggregateAndCleanBaqiyatChallenge(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  const [todayTotalSnap, globalTotalSnap] = await Promise.all([
    db.ref(`baqiyat_saliha/${today}/totalTodayBaqiyat`).get(),
    db.ref('baqiyat_saliha/totalBaqiyat').get(),
  ]);

  const todayTotal = todayTotalSnap.val() || 0;
  const globalTotal = globalTotalSnap.val() || 0;

  console.log(`[baqiyat-aggregate] today=${today} todayTotal=${todayTotal} globalBefore=${globalTotal}`);

  if (todayTotal === 0) {
    console.log('[baqiyat-aggregate] todayTotal is 0 — skip update and delete');
    return;
  }

  await db.ref('baqiyat_saliha/totalBaqiyat').set(globalTotal + todayTotal);
  console.log(`[baqiyat-aggregate] totalBaqiyat updated: ${globalTotal} → ${globalTotal + todayTotal}`);

  await db.ref(`baqiyat_saliha/${today}`).remove();
  console.log(`[baqiyat-aggregate] deleted baqiyat_saliha/${today}`);

  // Phase 1: mirror to Firestore
  await mirrorBaqiyatAggregateAndClean(admin.firestore(), today, todayTotal, globalTotal + todayTotal);
}

async function aggregateAndCleanIstighfarChallenge(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  const [todayTotalSnap, globalTotalSnap] = await Promise.all([
    db.ref(`istighfar_challenge/${today}/totalTodayIstighfar`).get(),
    db.ref('istighfar_challenge/totalIstighfar').get(),
  ]);

  const todayTotal = todayTotalSnap.val() || 0;
  const globalTotal = globalTotalSnap.val() || 0;

  console.log(`[istighfar-aggregate] today=${today} todayTotal=${todayTotal} globalBefore=${globalTotal}`);

  if (todayTotal === 0) {
    console.log('[istighfar-aggregate] todayTotal is 0 — skip update and delete');
    return;
  }

  await db.ref('istighfar_challenge/totalIstighfar').set(globalTotal + todayTotal);
  console.log(`[istighfar-aggregate] totalIstighfar updated: ${globalTotal} → ${globalTotal + todayTotal}`);

  await db.ref(`istighfar_challenge/${today}`).remove();
  console.log(`[istighfar-aggregate] deleted istighfar_challenge/${today}`);

  // Phase 1: mirror to Firestore
  await mirrorIstighfarAggregateAndClean(admin.firestore(), today, todayTotal, globalTotal + todayTotal);
}

async function aggregateAndCleanZabadChallenge(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
  const [todaySnap, globalSnap] = await Promise.all([
    db.ref(`zabad_challenge/${today}/totalTodayZabad`).get(),
    db.ref('zabad_challenge/totalZabad').get(),
  ]);
  const todayTotal = todaySnap.val() || 0;
  const globalTotal = globalSnap.val() || 0;
  if (todayTotal === 0) return;
  await db.ref('zabad_challenge/totalZabad').set(globalTotal + todayTotal);
  await db.ref(`zabad_challenge/${today}`).remove();
  console.log(`[zabad-aggregate] totalZabad updated: ${globalTotal} → ${globalTotal + todayTotal}`);
}

async function aggregateAndCleanGharsChallenge(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
  const [todaySnap, globalSnap] = await Promise.all([
    db.ref(`ghars_challenge/${today}/totalTodayGhars`).get(),
    db.ref('ghars_challenge/totalGhars').get(),
  ]);
  const todayTotal = todaySnap.val() || 0;
  const globalTotal = globalSnap.val() || 0;
  if (todayTotal === 0) return;
  await db.ref('ghars_challenge/totalGhars').set(globalTotal + todayTotal);
  await db.ref(`ghars_challenge/${today}`).remove();
  console.log(`[ghars-aggregate] totalGhars updated: ${globalTotal} → ${globalTotal + todayTotal}`);
}

async function aggregateAndCleanQuranChallenge(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  const [todayTotalSnap, globalTotalSnap] = await Promise.all([
    db.ref(`quran_challenge/${today}/totalTodayQuran`).get(),
    db.ref('quran_challenge/totalQuran').get(),
  ]);

  const todayTotal = todayTotalSnap.val() || 0;
  const globalTotal = globalTotalSnap.val() || 0;

  console.log(`[quran-aggregate] today=${today} todayTotal=${todayTotal} globalBefore=${globalTotal}`);

  if (todayTotal === 0) {
    console.log('[quran-aggregate] todayTotal is 0 — skip update and delete');
    return;
  }

  await db.ref('quran_challenge/totalQuran').set(globalTotal + todayTotal);
  console.log(`[quran-aggregate] totalQuran updated: ${globalTotal} → ${globalTotal + todayTotal}`);

  await db.ref(`quran_challenge/${today}`).remove();
  console.log(`[quran-aggregate] deleted quran_challenge/${today}`);

  // Phase 1: mirror to Firestore
  await mirrorQuranAggregateAndClean(admin.firestore(), today, todayTotal, globalTotal + todayTotal);
}

main().catch(err => { console.error(err); process.exit(1); });
