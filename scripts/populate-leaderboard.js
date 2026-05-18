// Reads top-10 players for the active round from Firebase RTDB and writes
// them to the leaderboard node. Runs hourly; detects isFinal automatically.
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const explicitRoundKey = process.env.ROUND_KEY || '';

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Mirrors CompetitionWindowUtils.kt: roundKey = date of next Friday 18:00 Cairo.
// If now IS past that boundary, round already closed — advance to following Friday.
function cairoRoundKey() {
  const now = new Date();
  const zone = 'Africa/Cairo';

  const weekdayStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(now);
  const dayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const jsDow = dayMap[weekdayStr];

  const hourStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(now);
  const cairoHour = parseInt(hourStr, 10);

  let daysToFriday = (5 - jsDow + 7) % 7;
  // If today is Friday and round has already closed (>= 18:00 Cairo), next round is 7 days away
  if (daysToFriday === 0 && cairoHour >= 18) daysToFriday = 7;

  const fridayDate = new Date(now.getTime() + daysToFriday * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone,
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
}

// Round is final when we are at or past Friday 18:00 Cairo for that roundKey date.
function isRoundFinal(roundKey) {
  const now = new Date();
  const zone = 'Africa/Cairo';
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: zone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', hour12: false,
  });
  const parts = Object.fromEntries(fmt.formatToParts(now).map(p => [p.type, p.value]));
  const cairoDate = `${parts.year}-${parts.month}-${parts.day}`;
  const cairoHour = parseInt(parts.hour, 10);
  if (cairoDate > roundKey) return true;
  if (cairoDate === roundKey && cairoHour >= 18) return true;
  return false;
}

async function main() {
  const roundKey = explicitRoundKey || cairoRoundKey();
  const isFinal = isRoundFinal(roundKey);
  console.log(`Round key: ${roundKey} | isFinal: ${isFinal}`);

  const db = admin.database();
  const playersRef = db.ref(`mohamed_lovers/${roundKey}/players`);

  // Single ordered query — ascending by totalCount; we reverse for ranking.
  const allPlayersSnapshot = await playersRef.orderByChild('totalCount').get();

  if (!allPlayersSnapshot.exists()) {
    console.log('No players found. Writing empty leaderboard.');
    await Promise.all([
      db.ref(`mohamed_lovers/${roundKey}/leaderboard`).set({ isFinal }),
      db.ref(`mohamed_lovers/${roundKey}/roundTotal`).set(0),
      db.ref(`mohamed_lovers/${roundKey}/roundPlayerCount`).set(0),
    ]);
    process.exit(0);
  }

  // Build full list (Firebase returns ascending order from orderByChild).
  const allPlayers = [];
  let roundTotal = 0;
  allPlayersSnapshot.forEach(child => {
    const data = child.val();
    if (data && typeof data.uid === 'string' && typeof data.totalCount === 'number') {
      roundTotal += data.totalCount;
      allPlayers.push({
        uid: data.uid,
        score: data.totalCount,
        updatedAt: data.updatedAt || 0,
        countryCode: typeof data.countryCode === 'string' ? data.countryCode : 'NA',
        yesterdayTotalScore: typeof data.yesterdayTotalScore === 'number' ? data.yesterdayTotalScore : 0,
      });
    }
  });

  // Sort descending to assign ranks (highest score = rank 1).
  allPlayers.sort((a, b) => b.score - a.score || b.updatedAt - a.updatedAt);

  const roundPlayerCount = allPlayers.length;

  // Write rank into each player node, then build top-10 leaderboard.
  const rankUpdates = {};
  allPlayers.forEach((player, i) => {
    rankUpdates[`mohamed_lovers/${roundKey}/players/${player.uid}/rank`] = i + 1;
  });

  const top10 = allPlayers.slice(0, 10);
  const leaderboard = { isFinal };
  top10.forEach((player, i) => {
    leaderboard[String(i + 1)] = {
      rank: i + 1,
      uid: player.uid,
      score: player.score,
      countryCode: player.countryCode,
    };
  });

  // Daily leaderboard: rank by todayScore = totalCount - yesterdayTotalScore.
  const dailyPlayers = allPlayers.map(p => ({
    ...p,
    dailyScore: Math.max(0, p.score - (p.yesterdayTotalScore || 0)),
  }));
  dailyPlayers.sort((a, b) => b.dailyScore - a.dailyScore || b.updatedAt - a.updatedAt);
  const dailyTop10 = dailyPlayers.slice(0, 10);
  const dailyLeaderboard = { isFinal };
  dailyTop10.forEach((player, i) => {
    dailyLeaderboard[String(i + 1)] = {
      rank: i + 1,
      uid: player.uid,
      score: player.dailyScore,
      countryCode: player.countryCode,
    };
  });

  // Read old leaderboards before overwriting — used for rank-diff and drop-out detection.
  let droppedUids = [];
  const oldLbSnap = await db.ref(`mohamed_lovers/${roundKey}/leaderboard`).get();
  const oldDailyLbSnap = await db.ref(`mohamed_lovers/${roundKey}/dailyLeaderboard`).get();

  // Build uid→rank maps from old leaderboards.
  function buildOldRankMap(snap) {
    const map = {};
    if (!snap.exists()) return map;
    const val = snap.val();
    for (let i = 1; i <= 10; i++) {
      const entry = val[String(i)];
      if (entry?.uid) map[entry.uid] = entry.rank;
    }
    return map;
  }
  const oldRanks = buildOldRankMap(oldLbSnap);
  const oldDailyRanks = buildOldRankMap(oldDailyLbSnap);

  // Compute rankChange for each entry: "same", "up", "down", or "new".
  function computeRankChange(uid, newRank, oldRankMap) {
    const oldRank = oldRankMap[uid];
    if (oldRank == null) return 'new';
    if (oldRank === newRank) return 'same';
    return newRank < oldRank ? 'up' : 'down';
  }

  // Enrich weekly leaderboard entries with rankChange.
  top10.forEach((player, i) => {
    const rank = i + 1;
    leaderboard[String(rank)].rankChange = computeRankChange(player.uid, rank, oldRanks);
  });

  // Enrich daily leaderboard entries with rankChange.
  dailyTop10.forEach((player, i) => {
    const rank = i + 1;
    dailyLeaderboard[String(rank)].rankChange = computeRankChange(player.uid, rank, oldDailyRanks);
  });

  // Drop-out detection.
  if (!isFinal && oldLbSnap.exists()) {
    const oldTop10Uids = new Set(Object.values(oldRanks).length ? Object.keys(oldRanks) : []);
    const newTop10Uids = new Set(top10.map(p => p.uid));
    droppedUids = [...oldTop10Uids].filter(uid => !newTop10Uids.has(uid));
    console.log(`Drop-out detection: old=${oldTop10Uids.size} new=${newTop10Uids.size} dropped=${droppedUids.length}`);
  }

  await Promise.all([
    db.ref('/').update(rankUpdates),
    db.ref(`mohamed_lovers/${roundKey}/leaderboard`).set(leaderboard),
    db.ref(`mohamed_lovers/${roundKey}/dailyLeaderboard`).set(dailyLeaderboard),
    db.ref(`mohamed_lovers/${roundKey}/roundTotal`).set(roundTotal),
    db.ref(`mohamed_lovers/${roundKey}/roundPlayerCount`).set(roundPlayerCount),
  ]);
  console.log(`Wrote ${top10.length} leaderboard + ${dailyTop10.length} daily entries. roundTotal=${roundTotal} players=${roundPlayerCount}`);
  console.log(JSON.stringify(leaderboard, null, 2));

  // Top-3 change notifications — detect drops from top 3 and position losses.
  if (!isFinal) {
    const top3Notifs = [];

    // Players who were in top 3 but dropped out (now rank 4+ or gone).
    for (const [uid, oldRank] of Object.entries(oldRanks)) {
      if (oldRank > 3) continue;
      const newEntry = top10.find(p => p.uid === uid);
      const newRank = newEntry ? top10.indexOf(newEntry) + 1 : null;
      if (newRank == null || newRank > 3) {
        top3Notifs.push({ uid, event: 'dropped', oldRank, newRank });
      } else if (newRank > oldRank) {
        top3Notifs.push({ uid, event: 'lost_position', oldRank, newRank });
      }
    }

    if (top3Notifs.length > 0) {
      console.log(`Top-3 changes: ${top3Notifs.length} notification(s) to send`);
      const top3Messages = {
        dropped: {
          title: 'مكانك بين المحبين يناديك 🤍',
          body: 'كنت من أكثر المصلّين على النبي ﷺ — لا تتوقف، فالصلاة عليه نور وشفاعة يوم القيامة!',
        },
        lost_position: {
          title: 'المنافسة تشتد بين المحبين 🔥',
          body: 'تراجع ترتيبك بين أكثر المصلّين على النبي ﷺ — زِد صلواتك وارتقِ، فأقربكم مني مجلسًا أكثركم صلاةً عليّ!',
        },
      };
      const top3Promises = top3Notifs.map(async ({ uid, event }) => {
        const userSnap = await db.ref(`mohamed_lovers/users/${uid}`).get();
        const user = userSnap.val();
        if (!user?.fcmToken) { console.log(`  top3 uid=${uid}: no FCM token — skip`); return; }
        const msg = top3Messages[event];
        return admin.messaging().send({
          token: user.fcmToken,
          notification: { title: msg.title, body: msg.body },
          data: { title: msg.title, body: msg.body },
        })
          .then(msgId => console.log(`  top3 uid=${uid} (${event}): sent msgId=${msgId}`))
          .catch(e => console.error(`  top3 uid=${uid} (${event}): send failed: ${e.message}`));
      });
      await Promise.all(top3Promises);
    }
  }

  // Notify dropped-out users — once per round (debounced via lastDropOutNotifRound).
  if (droppedUids.length > 0) {
    console.log(`Notifying ${droppedUids.length} dropped user(s)...`);
    const fcmUpdates = {};
    const notifPromises = droppedUids.map(async uid => {
      const userSnap = await db.ref(`mohamed_lovers/users/${uid}`).get();
      const user = userSnap.val();
      if (!user?.fcmToken) { console.log(`  uid=${uid}: no FCM token — skip`); return; }
      if (user.lastDropOutNotifRound === roundKey) { console.log(`  uid=${uid}: already notified this round — skip`); return; }
      fcmUpdates[`mohamed_lovers/users/${uid}/lastDropOutNotifRound`] = roundKey;
      return admin.messaging().send({
        token: user.fcmToken,
        notification: { title: 'خرجت من قائمة الأوائل 😔', body: 'مكانك بين المحبين يستحق المنافسة — عُد وصلِّ على النبي ﷺ الآن!' },
        data: { title: 'خرجت من قائمة الأوائل 😔', body: 'مكانك بين المحبين يستحق المنافسة — عُد وصلِّ على النبي ﷺ الآن!' },
      })
        .then(msgId => console.log(`  uid=${uid}: sent dropout alert msgId=${msgId}`))
        .catch(e => console.error(`  uid=${uid}: send failed: ${e.message}`));
    });
    await Promise.all(notifPromises);
    if (Object.keys(fcmUpdates).length > 0) {
      await db.ref('/').update(fcmUpdates);
      console.log(`Wrote ${Object.keys(fcmUpdates).length} lastDropOutNotifRound flag(s)`);
    }
  }

  // --- Ten Days of Dhul Hijjah leaderboard ---
  await populateTenDaysLeaderboard(db);

  process.exit(0);
}

function isTenDaysPeriodActive(periodKey) {
  const zone = 'Africa/Cairo';
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
  const start = new Date(periodKey + 'T00:00:00');
  const endDate = new Date(start.getTime() + 9 * 86400000);
  const end = new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(endDate);
  return today >= periodKey && today < end;
}

async function populateTenDaysLeaderboard(db) {
  const root = 'ten_days_dhul_hijjah';
  const periodKeys = ['2026-05-18'];

  for (const periodKey of periodKeys) {
    const active = isTenDaysPeriodActive(periodKey);
    console.log(`\n--- Ten Days Leaderboard [${periodKey}] active=${active} ---`);

    const playersSnap = await db.ref(`${root}/${periodKey}/players`).orderByChild('totalScore').get();
    if (!playersSnap.exists()) {
      console.log('No ten-days players found.');
      await db.ref(`${root}/${periodKey}/leaderboard`).set({});
      continue;
    }

    const allPlayers = [];
    playersSnap.forEach(child => {
      const data = child.val();
      if (data && typeof data.uid === 'string' && typeof data.totalScore === 'number') {
        allPlayers.push({
          uid: data.uid,
          totalScore: data.totalScore,
          updatedAt: data.updatedAt || 0,
          countryCode: typeof data.countryCode === 'string' ? data.countryCode : '',
        });
      }
    });

    allPlayers.sort((a, b) => b.totalScore - a.totalScore || b.updatedAt - a.updatedAt);

    const top10 = allPlayers.slice(0, 10);

    // Read old leaderboard for rank-diff and drop-out detection.
    const oldLbSnap = await db.ref(`${root}/${periodKey}/leaderboard`).get();
    const oldRanks = buildOldRankMap(oldLbSnap);

    const leaderboard = {};
    top10.forEach((player, i) => {
      const rank = i + 1;
      leaderboard[String(rank)] = {
        rank,
        uid: player.uid,
        totalScore: player.totalScore,
        countryCode: player.countryCode,
        rankChange: computeRankChange(player.uid, rank, oldRanks),
      };
    });

    const rankUpdates = {};
    allPlayers.forEach((player, i) => {
      rankUpdates[`${root}/${periodKey}/players/${player.uid}/rank`] = i + 1;
    });

    await Promise.all([
      db.ref('/').update(rankUpdates),
      db.ref(`${root}/${periodKey}/leaderboard`).set(leaderboard),
    ]);
    console.log(`Wrote ${top10.length} ten-days leaderboard entries (${allPlayers.length} total players).`);

    // Notifications only when period is active.
    if (!active) continue;

    // Top-3 change notifications.
    const top3Notifs = [];
    for (const [uid, oldRank] of Object.entries(oldRanks)) {
      if (oldRank > 3) continue;
      const newEntry = top10.find(p => p.uid === uid);
      const newRank = newEntry ? top10.indexOf(newEntry) + 1 : null;
      if (newRank == null || newRank > 3) {
        top3Notifs.push({ uid, event: 'dropped', oldRank, newRank });
      } else if (newRank > oldRank) {
        top3Notifs.push({ uid, event: 'lost_position', oldRank, newRank });
      }
    }

    if (top3Notifs.length > 0) {
      console.log(`Ten-days top-3 changes: ${top3Notifs.length} notification(s)`);
      const top3Messages = {
        dropped: {
          title: 'مكانك بين المتسابقين يناديك 🤍',
          body: 'كنت من أعلى المتنافسين في عشر ذي الحجة — لا تتوقف، فالعمل الصالح في هذه الأيام أحب إلى الله!',
        },
        lost_position: {
          title: 'المنافسة تشتد في العشر 🔥',
          body: 'تراجع ترتيبك في عشر ذي الحجة — زِد من عملك الصالح وارتقِ!',
        },
      };
      const top3Promises = top3Notifs.map(async ({ uid, event }) => {
        const userSnap = await db.ref(`mohamed_lovers/users/${uid}`).get();
        const user = userSnap.val();
        if (!user?.fcmToken) { console.log(`  ten-days top3 uid=${uid}: no FCM token — skip`); return; }
        const msg = top3Messages[event];
        return admin.messaging().send({
          token: user.fcmToken,
          notification: { title: msg.title, body: msg.body },
          data: { title: msg.title, body: msg.body },
        })
          .then(msgId => console.log(`  ten-days top3 uid=${uid} (${event}): sent msgId=${msgId}`))
          .catch(e => console.error(`  ten-days top3 uid=${uid} (${event}): send failed: ${e.message}`));
      });
      await Promise.all(top3Promises);
    }

    // Drop-out detection: users who were in top 10 but no longer.
    let droppedUids = [];
    if (oldLbSnap.exists()) {
      const oldTop10Uids = new Set(Object.keys(oldRanks));
      const newTop10Uids = new Set(top10.map(p => p.uid));
      droppedUids = [...oldTop10Uids].filter(uid => !newTop10Uids.has(uid));
      console.log(`Ten-days drop-out: old=${oldTop10Uids.size} new=${newTop10Uids.size} dropped=${droppedUids.length}`);
    }

    if (droppedUids.length > 0) {
      console.log(`Notifying ${droppedUids.length} ten-days dropped user(s)...`);
      const notifPromises = droppedUids.map(async uid => {
        const userSnap = await db.ref(`mohamed_lovers/users/${uid}`).get();
        const user = userSnap.val();
        if (!user?.fcmToken) { console.log(`  ten-days uid=${uid}: no FCM token — skip`); return; }
        return admin.messaging().send({
          token: user.fcmToken,
          notification: { title: 'خرجت من قائمة العشر الأوائل 😔', body: 'مكانك في عشر ذي الحجة يستحق المنافسة — عُد وزِد من عملك الصالح!' },
          data: { title: 'خرجت من قائمة العشر الأوائل 😔', body: 'مكانك في عشر ذي الحجة يستحق المنافسة — عُد وزِد من عملك الصالح!' },
        })
          .then(msgId => console.log(`  ten-days uid=${uid}: sent dropout alert msgId=${msgId}`))
          .catch(e => console.error(`  ten-days uid=${uid}: send failed: ${e.message}`));
      });
      await Promise.all(notifPromises);
    }
  }
}

main().catch(err => { console.error(err); process.exit(1); });
