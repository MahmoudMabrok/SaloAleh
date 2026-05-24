// Runs once per week on Fridays after the 19:00 Cairo round close. Sums all past
// rounds (including the one that just closed) into allTimeTotal, then writes
// achievement records for every finisher so the app can surface them
// to users who missed the live isFinal event. Top-5 finishers also receive
// a unique winnerCode in their achievement record.
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Returns today's Cairo date — the round that just closed.
// Reliable only when called on Friday after 19:00 Cairo (matches this cron).
// Override by setting CLOSED_ROUND=YYYY-MM-DD env var or passing as first arg.
function closedRoundKey() {
  const override = process.env.CLOSED_ROUND || process.argv[2];
  if (override) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(override)) {
      console.error(`Invalid CLOSED_ROUND: "${override}" — expected YYYY-MM-DD`);
      process.exit(1);
    }
    return override;
  }
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

function generateWinnerCode() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let code = '';
  for (let i = 0; i < 8; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return code;
}

async function main() {
  const closedRound = closedRoundKey();
  console.log(`Closed round: ${closedRound}`);

  const db = admin.database();
  const rootSnapshot = await db.ref('mohamed_lovers').get();

  if (!rootSnapshot.exists()) {
    console.log('No data found under mohamed_lovers.');
    process.exit(0);
  }

  const playersSnap = await db.ref(`mohamed_lovers/${closedRound}/players`).get();
  const players = [];
  let roundTotal = 0;

  if (playersSnap.exists()) {
    playersSnap.forEach(child => {
      const data = child.val();
      const uid = data?.uid;
      const totalCount = typeof data?.totalCount === 'number' ? data.totalCount : 0;
      if (typeof uid === 'string' && totalCount > 0) {
        players.push({
          uid,
          score: totalCount,
          updatedAt: data.updatedAt || 0,
          countryCode: typeof data.countryCode === 'string' ? data.countryCode : 'NA',
          scoreMasked: data.scoreMasked === true,
          isSupporter: data.isSupporter === true,
          yesterdayTotalScore: typeof data?.yesterdayTotalScore === 'number' ? data.yesterdayTotalScore : 0,
        });
        roundTotal += totalCount;
      }
    });
    console.log(`Closed round total: ${roundTotal}`);
  }

  const previousTotal = rootSnapshot.child('allTimeTotal').val() || 0;
  const allTimeTotal = previousTotal + roundTotal;
  await db.ref('mohamed_lovers/allTimeTotal').set(allTimeTotal);
  console.log(`allTimeTotal: ${previousTotal} + ${roundTotal} (closed round) = ${allTimeTotal}`);

  if (!playersSnap.exists() || players.length === 0) {
    console.log(`No players found for ${closedRound} — no history written.`);
    process.exit(0);
  }


  players.sort((a, b) => b.score - a.score || b.updatedAt - a.updatedAt);

  const writes = {};
  players.forEach((player, i) => {
    const rank = i + 1;
    const winnerCode = rank <= 5 ? generateWinnerCode() : undefined;

    writes[`mohamed_lovers/users/${player.uid}/achievements/${closedRound}`] = {
      rank,
      score: player.score,
      date: closedRound,
      ...(winnerCode !== undefined && { winnerCode }),
    };
    console.log(`  History: uid=${player.uid} rank=${rank} score=${player.score}${winnerCode ? ` winnerCode=${winnerCode}` : ''}`);
  });

  if (Object.keys(writes).length === 0) {
    console.log('No entries — no achievements written.');
  } else {
    await db.ref('/').update(writes);
    console.log(`Wrote ${Object.keys(writes).length} achievement entries.`);
  }

  // Close the round: write final leaderboards with isFinal: true.
  const top10 = players.slice(0, 10);
  const leaderboard = { isFinal: true };
  top10.forEach((player, i) => {
    const entry = {
      rank: i + 1,
      uid: player.uid,
      score: player.score,
      countryCode: player.countryCode,
    };
    if (player.scoreMasked) entry.scoreMasked = true;
    if (player.isSupporter) entry.isSupporter = true;
    leaderboard[String(i + 1)] = entry;
  });

  const dailyPlayers = [...players].map(p => ({
    ...p,
    dailyScore: Math.max(0, p.score - (p.yesterdayTotalScore || 0)),
  }));
  dailyPlayers.sort((a, b) => b.dailyScore - a.dailyScore || b.updatedAt - a.updatedAt);
  const dailyTop10 = dailyPlayers.slice(0, 10);
  const dailyLeaderboard = { isFinal: true };
  dailyTop10.forEach((player, i) => {
    const entry = {
      rank: i + 1,
      uid: player.uid,
      score: player.dailyScore,
      countryCode: player.countryCode,
    };
    if (player.scoreMasked) entry.scoreMasked = true;
    if (player.isSupporter) entry.isSupporter = true;
    dailyLeaderboard[String(i + 1)] = entry;
  });

  await Promise.all([
    db.ref(`mohamed_lovers/${closedRound}/leaderboard`).set(leaderboard),
    db.ref(`mohamed_lovers/${closedRound}/dailyLeaderboard`).set(dailyLeaderboard),
  ]);
  console.log(`Round ${closedRound} closed: wrote final leaderboard (${top10.length} entries) + daily (${dailyTop10.length} entries) with isFinal=true`);

  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
