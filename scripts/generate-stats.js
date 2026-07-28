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
  mirrorAlBaqaraAggregateAndClean,
  mirrorQuranAggregateAndClean,
  mirrorHeroes,
} = require('./firestore-utils');
const {
  DHIKR_CHALLENGE_ROOT,
  BAQIYAT_CHALLENGE_ROOT,
  ISTIGHFAR_CHALLENGE_ROOT,
  ZABAD_CHALLENGE_ROOT,
  GHARS_CHALLENGE_ROOT,
  QURAN_CHALLENGE_ROOT,
  ALBAQARA_CHALLENGE_ROOT,
  ALF_HASANA_CHALLENGE_ROOT,
  readChallengeRankedUsers,
  awardChallengeMedals,
  cairoToday,
  buildDailyScoreSnapshots,
  roundDayNumber,
  ABNORMAL_DAILY_THRESHOLD,
  PACE_DAILY_INCREMENT,
} = require('./leaderboard-utils');

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
  const playerSnapshots = [];

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
        playerSnapshots.push({
          uid: child.key,
          totalCount: score,
          // Read BEFORE the yesterdayTotalScore update above is applied — still yesterday's value,
          // so the todayCount fallback (totalCount - yesterdayTotalScore) stays correct.
          todayCount: typeof data?.todayCount === 'number' ? data.todayCount : null,
          yesterdayTotalScore: data?.yesterdayTotalScore || 0,
          countryCode: typeof data?.countryCode === 'string' ? data.countryCode : 'NA',
        });
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

  // Per-user daily close: append a score-history snapshot for each active player, flag anyone who
  // exceeded the abnormal daily threshold for admin review, flag anyone whose cumulative round
  // total outran the day-of-round pace ceiling (day N * 11k) into users/{uid}/paceFlags/{dateKey},
  // and reset todayCount so the next Cairo day starts fresh (the daily leaderboard ranks on the
  // client-pushed todayCount).
  const roundDay = roundDayNumber(roundKey, dateStr);
  const { scoreHistoryUpdates, abnormalUpdates, todayCountResets, paceFlagUpdates } =
    buildDailyScoreSnapshots({ players: playerSnapshots, dateKey: dateStr, roundKey, roundDay });
  const dailyCloseUpdates = { ...scoreHistoryUpdates, ...abnormalUpdates, ...todayCountResets, ...paceFlagUpdates };
  if (Object.keys(dailyCloseUpdates).length > 0) {
    await db.ref('/').update(dailyCloseUpdates);
    console.log(
      `Daily close: ${Object.keys(scoreHistoryUpdates).length} history snapshot(s), ` +
      `${Object.keys(abnormalUpdates).length} abnormal user(s) (> ${ABNORMAL_DAILY_THRESHOLD}/day), ` +
      `${Object.keys(paceFlagUpdates).length} pace flag(s) (> day ${roundDay} × ${PACE_DAILY_INCREMENT} = ${roundDay * PACE_DAILY_INCREMENT}), ` +
      `${Object.keys(todayCountResets).length} todayCount reset(s).`
    );
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
  await sendAlfHasanaChallengeRank1Notification(db);
  // Persist the day's champions BEFORE the per-challenge day nodes are deleted by
  // the aggregate-and-clean steps below (those remove 100_challenge/{today} etc).
  await persistHeroes(db, dailyLeaderboardSnap);
  // Award each challenge's daily podium medals (🥇/🥈/🥉 to the day's top-3) from the
  // live end-of-day counts, BEFORE the aggregate-and-clean steps delete the day nodes.
  await awardAllChallengeMedals(db);
  await aggregateAndCleanDhikrChallenge(db);
  await aggregateAndCleanBaqiyatChallenge(db);
  await aggregateAndCleanIstighfarChallenge(db);
  await aggregateAndCleanZabadChallenge(db);
  await aggregateAndCleanGharsChallenge(db);
  await aggregateAndCleanQuranChallenge(db);
  await aggregateAndCleanAlBaqaraChallenge(db);
  await aggregateAndCleanAlfHasanaChallenge(db);

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
  const today = cairoToday();

  console.log(`[dhikr-rank1] computing rank 1 winner from live 100_challenge/${today} counts`);
  const rankedUsers = await readChallengeRankedUsers(db, DHIKR_CHALLENGE_ROOT, today);
  const winner = rankedUsers[0];

  if (!winner || !winner.uid || !winner.count) {
    console.log('[dhikr-rank1] no eligible participant — skip');
    return;
  }

  const rank1Uid = winner.uid;
  const rank1Count = winner.count;
  const name = winner.nickname && winner.nickname.trim()
    ? winner.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تحدي أهل لا إله إلا الله 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي أهل لا إله إلا الله اليوم بـ ${rank1Count} ذكراً — جزاك الله خيراً!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'dhikr_challenge_rank1', notification_action: 'open_dhikr_challenge' },
    });
    console.log(`[dhikr-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[dhikr-rank1] send failed: ${e.message}`);
  }
}

async function sendBaqiyatChallengeRank1Notification(db) {
  const today = cairoToday();

  console.log(`[baqiyat-rank1] computing rank 1 winner from live baqiyat_saliha/${today} counts`);
  const rankedUsers = await readChallengeRankedUsers(db, BAQIYAT_CHALLENGE_ROOT, today);
  const winner = rankedUsers[0];

  if (!winner || !winner.uid || !winner.count) {
    console.log('[baqiyat-rank1] no eligible participant — skip');
    return;
  }

  const rank1Uid = winner.uid;
  const rank1Count = winner.count;
  const name = winner.nickname && winner.nickname.trim()
    ? winner.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تحدي الباقيات الصالحات 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي الباقيات الصالحات اليوم بـ ${rank1Count} دورة — جزاك الله خيراً!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'baqiyat_challenge_rank1', notification_action: 'open_baqiyat_challenge' },
    });
    console.log(`[baqiyat-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[baqiyat-rank1] send failed: ${e.message}`);
  }
}

async function sendIstighfarChallengeRank1Notification(db) {
  const today = cairoToday();

  console.log(`[istighfar-rank1] computing rank 1 winner from live istighfar_challenge/${today} counts`);
  const rankedUsers = await readChallengeRankedUsers(db, ISTIGHFAR_CHALLENGE_ROOT, today);
  const winner = rankedUsers[0];

  if (!winner || !winner.uid || !winner.count) {
    console.log('[istighfar-rank1] no eligible participant — skip');
    return;
  }

  const rank1Uid = winner.uid;
  const rank1Count = winner.count;
  const name = winner.nickname && winner.nickname.trim()
    ? winner.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تحدي واستغفروه 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي واستغفروه اليوم بـ ${rank1Count} مرة — غفر الله لك!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'istighfar_challenge_rank1', notification_action: 'open_istighfar_challenge' },
    });
    console.log(`[istighfar-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[istighfar-rank1] send failed: ${e.message}`);
  }
}

async function sendQuranChallengeRank1Notification(db) {
  const today = cairoToday();

  console.log(`[quran-rank1] computing rank 1 winner from live quran_challenge/${today} counts`);
  const rankedUsers = await readChallengeRankedUsers(db, QURAN_CHALLENGE_ROOT, today);
  const winner = rankedUsers[0];

  if (!winner || !winner.uid || !winner.count) {
    console.log('[quran-rank1] no eligible participant — skip');
    return;
  }

  const rank1Uid = winner.uid;
  const rank1Count = winner.count;
  const name = winner.nickname && winner.nickname.trim()
    ? winner.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تحدي القرآن الكريم 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي القرآن الكريم اليوم بـ ${rank1Count} صفحة — بارك الله فيك!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'quran_challenge_rank1', notification_action: 'open_quran_challenge' },
    });
    console.log(`[quran-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[quran-rank1] send failed: ${e.message}`);
  }
}

async function sendAlfHasanaChallengeRank1Notification(db) {
  const today = cairoToday();

  console.log(`[alf_hasana-rank1] computing rank 1 winner from live alf_hasana_challenge/${today} counts`);
  const rankedUsers = await readChallengeRankedUsers(db, ALF_HASANA_CHALLENGE_ROOT, today);
  const winner = rankedUsers[0];

  if (!winner || !winner.uid || !winner.count) {
    console.log('[alf_hasana-rank1] no eligible participant — skip');
    return;
  }

  const rank1Uid = winner.uid;
  const rank1Count = winner.count;
  const name = winner.nickname && winner.nickname.trim()
    ? winner.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تحدي ألف حسنة 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي ألف حسنة اليوم بـ ${rank1Count} تسبيحة — بارك الله فيك!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'alf_hasana_challenge_rank1', notification_action: 'open_alf_hasana_challenge' },
    });
    console.log(`[alf_hasana-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[alf_hasana-rank1] send failed: ${e.message}`);
  }
}

// Persists the day's top-3 champions across all active challenges to a single
// RTDB node (mohamed_lovers/heroes) that the app reads (read-only). Overwritten
// daily. Mirrored to Firestore per the Phase-1 dual-write convention.
async function sendZabadChallengeRank1Notification(db) {
  const today = cairoToday();

  console.log(`[zabad-rank1] computing rank 1 winner from live zabad_challenge/${today} counts`);
  const rankedUsers = await readChallengeRankedUsers(db, ZABAD_CHALLENGE_ROOT, today);
  const winner = rankedUsers[0];

  if (!winner || !winner.uid || !winner.count) {
    console.log('[zabad-rank1] no eligible participant — skip');
    return;
  }

  const rank1Uid = winner.uid;
  const rank1Count = winner.count;
  const name = winner.nickname && winner.nickname.trim()
    ? winner.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'بطل اليوم في تحدي تسبيح المئة 🏆';
  const body = `تهانينا لـ ${name} على التصدر في تحدي تسبيح المئة اليوم بـ ${rank1Count} تسبيحة!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'zabad_challenge_rank1', notification_action: 'open_zabad_challenge' },
    });
    console.log(`[zabad-rank1] sent to topic "challenges" uid=${rank1Uid} name="${name}" count=${rank1Count} msgId=${msgId}`);
  } catch (e) {
    console.error(`[zabad-rank1] send failed: ${e.message}`);
  }
}

async function sendGharsChallengeRank1Notification(db) {
  const today = cairoToday();

  console.log(`[ghars-rank1] computing rank 1 winner from live ghars_challenge/${today} counts`);
  const rankedUsers = await readChallengeRankedUsers(db, GHARS_CHALLENGE_ROOT, today);
  const winner = rankedUsers[0];

  if (!winner || !winner.uid || !winner.count) {
    console.log('[ghars-rank1] no eligible participant — skip');
    return;
  }

  const rank1Uid = winner.uid;
  const rank1Count = winner.count;
  const name = winner.nickname && winner.nickname.trim()
    ? winner.nickname.trim()
    : rank1Uid.slice(-6).toUpperCase();

  const title = 'غارس اليوم في تحدي اغرس نخلة 🌴';
  const body = `تهانينا لـ ${name} على التصدر في تحدي اغرس نخلة اليوم بـ ${rank1Count} نخلة!`;

  try {
    const msgId = await admin.messaging().send({
      topic: 'challenges',
      notification: { title, body },
      data: { title, body, notification_type: 'ghars_challenge_rank1', notification_action: 'open_ghars_challenge' },
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

  // Daily count challenges: recompute the top-3 from the live per-user counts
  // (same source the winner notifications use) instead of the periodically-written
  // leaderboard node, so the persisted heroes reflect the final end-of-day counts.
  async function challengeTop3(rootPath) {
    const rankedUsers = await readChallengeRankedUsers(db, rootPath, today);
    return rankedUsers.slice(0, 3).map((user, i) => ({
      rank: i + 1,
      name: heroName(user),
      count: user.count || 0,
      countryCode: user.countryCode || '',
    }));
  }

  const [dhikr, baqiyat, istighfar, quran, zabad, ghars, albaqara, alfHasana] = await Promise.all([
    challengeTop3(DHIKR_CHALLENGE_ROOT),
    challengeTop3(BAQIYAT_CHALLENGE_ROOT),
    challengeTop3(ISTIGHFAR_CHALLENGE_ROOT),
    challengeTop3(QURAN_CHALLENGE_ROOT),
    challengeTop3(ZABAD_CHALLENGE_ROOT),
    challengeTop3(GHARS_CHALLENGE_ROOT),
    challengeTop3(ALBAQARA_CHALLENGE_ROOT),
    challengeTop3(ALF_HASANA_CHALLENGE_ROOT),
  ]);

  const heroes = {
    date: today,
    updatedAt: new Date().toISOString(),
    challenges: { salawat, dhikr, baqiyat, istighfar, quran, zabad, ghars, albaqara, alf_hasana: alfHasana },
  };

  // RTDB is the source of truth; overwrite the whole node so stale entries from
  // yesterday never linger.
  await db.ref('mohamed_lovers/heroes').set(heroes);
  console.log(
    `[heroes] persisted for ${today}: salawat=${salawat.length} dhikr=${dhikr.length} baqiyat=${baqiyat.length} istighfar=${istighfar.length} quran=${quran.length} zabad=${zabad.length} ghars=${ghars.length} albaqara=${albaqara.length} alf_hasana=${alfHasana.length}`,
  );

  // Phase 1: mirror to Firestore.
  await mirrorHeroes(admin.firestore(), heroes);
}

// Awards daily podium medals for every count challenge. Each award is guarded and
// wrapped so one challenge's failure never blocks the others or the cleanup steps.
async function awardAllChallengeMedals(db) {
  const today = cairoToday();
  const roots = [
    DHIKR_CHALLENGE_ROOT,
    BAQIYAT_CHALLENGE_ROOT,
    ISTIGHFAR_CHALLENGE_ROOT,
    ZABAD_CHALLENGE_ROOT,
    GHARS_CHALLENGE_ROOT,
    QURAN_CHALLENGE_ROOT,
    ALBAQARA_CHALLENGE_ROOT,
    ALF_HASANA_CHALLENGE_ROOT,
  ];
  for (const root of roots) {
    try {
      await awardChallengeMedals(db, admin, root, today);
    } catch (e) {
      console.error(`[medals:${root}] award failed: ${e.message}`);
    }
  }
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

async function aggregateAndCleanAlBaqaraChallenge(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  const [todayTotalSnap, globalTotalSnap] = await Promise.all([
    db.ref(`albaqara_challenge/${today}/totalTodayAlBaqara`).get(),
    db.ref('albaqara_challenge/totalAlBaqara').get(),
  ]);

  const todayTotal = todayTotalSnap.val() || 0;
  const globalTotal = globalTotalSnap.val() || 0;

  console.log(`[albaqara-aggregate] today=${today} todayTotal=${todayTotal} globalBefore=${globalTotal}`);

  if (todayTotal === 0) {
    console.log('[albaqara-aggregate] todayTotal is 0 — skip update and delete');
    return;
  }

  await db.ref('albaqara_challenge/totalAlBaqara').set(globalTotal + todayTotal);
  console.log(`[albaqara-aggregate] totalAlBaqara updated: ${globalTotal} → ${globalTotal + todayTotal}`);

  await db.ref(`albaqara_challenge/${today}`).remove();
  console.log(`[albaqara-aggregate] deleted albaqara_challenge/${today}`);

  // Phase 1: mirror to Firestore
  await mirrorAlBaqaraAggregateAndClean(admin.firestore(), today, todayTotal, globalTotal + todayTotal);
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

async function aggregateAndCleanAlfHasanaChallenge(db) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  const [todayTotalSnap, globalTotalSnap] = await Promise.all([
    db.ref(`alf_hasana_challenge/${today}/totalTodayAlfHasana`).get(),
    db.ref('alf_hasana_challenge/totalAlfHasana').get(),
  ]);

  const todayTotal = todayTotalSnap.val() || 0;
  const globalTotal = globalTotalSnap.val() || 0;

  console.log(`[alf_hasana-aggregate] today=${today} todayTotal=${todayTotal} globalBefore=${globalTotal}`);

  if (todayTotal === 0) {
    console.log('[alf_hasana-aggregate] todayTotal is 0 — skip update and delete');
    return;
  }

  await db.ref('alf_hasana_challenge/totalAlfHasana').set(globalTotal + todayTotal);
  console.log(`[alf_hasana-aggregate] totalAlfHasana updated: ${globalTotal} → ${globalTotal + todayTotal}`);

  await db.ref(`alf_hasana_challenge/${today}`).remove();
  console.log(`[alf_hasana-aggregate] deleted alf_hasana_challenge/${today}`);
}

main().catch(err => { console.error(err); process.exit(1); });
