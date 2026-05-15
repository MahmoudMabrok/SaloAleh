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
  const dailyTop10 = dailyPlayers.filter(p => p.dailyScore > 0).slice(0, 10);
  const dailyLeaderboard = { isFinal };
  dailyTop10.forEach((player, i) => {
    dailyLeaderboard[String(i + 1)] = {
      rank: i + 1,
      uid: player.uid,
      score: player.dailyScore,
      countryCode: player.countryCode,
    };
  });

  // Detect drop-outs: read old leaderboard before overwriting.
  let droppedUids = [];
  if (!isFinal) {
    const oldLbSnap = await db.ref(`mohamed_lovers/${roundKey}/leaderboard`).get();
    if (oldLbSnap.exists()) {
      const oldLb = oldLbSnap.val();
      const oldTop10Uids = new Set();
      for (let i = 1; i <= 10; i++) {
        const entry = oldLb[String(i)];
        if (entry?.uid) oldTop10Uids.add(entry.uid);
      }
      const newTop10Uids = new Set(top10.map(p => p.uid));
      droppedUids = [...oldTop10Uids].filter(uid => !newTop10Uids.has(uid));
      console.log(`Drop-out detection: old=${oldTop10Uids.size} new=${newTop10Uids.size} dropped=${droppedUids.length}`);
    }
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

  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
