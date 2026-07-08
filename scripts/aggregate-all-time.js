// Runs once per week on Fridays after the 19:00 Cairo round close. Sums all past
// rounds (including the one that just closed) into allTimeTotal, then writes
// achievement records for every finisher so the app can surface them
// to users who missed the live isFinal event. Top-5 finishers also receive
// a unique winnerCode in their achievement record. Also closes out the round's
// leaderboard (final ranks) and seeds the brand-new round's leaderboard, so the
// whole round-boundary handoff happens from this single cron slot instead of a
// separate leaderboard-populate dispatch.
const admin = require('firebase-admin');
const { addDaysToDateKey, populateMohamedLoversRound } = require('./leaderboard-utils');
const { mirrorAllTimeTotal, mirrorAchievements, mirrorUserAllTimeTotals } = require('./firestore-utils');

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
  } else {
    const playersSnap = await db.ref(`mohamed_lovers/${closedRound}/players`).get();
    const players = [];
    let roundTotal = 0;

    if (playersSnap.exists()) {
      playersSnap.forEach(child => {
        const data = child.val();
        const uid = data?.uid;
        const totalCount = typeof data?.totalCount === 'number' ? data.totalCount : 0;
        if (typeof uid === 'string' && totalCount > 0) {
          players.push({ uid, score: totalCount, updatedAt: data.updatedAt || 0 });
          roundTotal += totalCount;
        }
      });
      console.log(`Closed round total: ${roundTotal}`);
    }

    const previousTotal = rootSnapshot.child('allTimeTotal').val() || 0;
    const allTimeTotal = previousTotal + roundTotal;
    await db.ref('mohamed_lovers/allTimeTotal').set(allTimeTotal);
    console.log(`allTimeTotal: ${previousTotal} + ${roundTotal} (closed round) = ${allTimeTotal}`);

    // Phase 1: mirror allTimeTotal to Firestore
    await mirrorAllTimeTotal(admin.firestore(), allTimeTotal);

    // Per-user all-time total: add this round's score to each player's lifetime total
    if (players.length > 0) {
      const userTotalWrites = {};
      for (const player of players) {
        const prevSnap = await db.ref(`mohamed_lovers/users/${player.uid}/allTimeTotal`).get();
        const prevTotal = (prevSnap.exists() && typeof prevSnap.val() === 'number') ? prevSnap.val() : 0;
        const newTotal = prevTotal + player.score;
        userTotalWrites[`mohamed_lovers/users/${player.uid}/allTimeTotal`] = newTotal;
      }
      await db.ref('/').update(userTotalWrites);
      console.log(`Updated allTimeTotal for ${Object.keys(userTotalWrites).length} users.`);

      // Phase 1: mirror per-user allTimeTotal to Firestore
      await mirrorUserAllTimeTotals(admin.firestore(), userTotalWrites);
    }

    if (!playersSnap.exists() || players.length === 0) {
      console.log(`No players found for ${closedRound} — no history/leaderboard written.`);
    } else {
      // Same tie-break as populateMohamedLoversRound's ranking, so achievement
      // rank always agrees with the final rank written to each player node.
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

      await db.ref('/').update(writes);
      console.log(`Wrote ${Object.keys(writes).length} achievement entries.`);

      // Phase 1: mirror achievements to Firestore
      await mirrorAchievements(admin.firestore(), writes);

      // Close the round: write final per-player ranks + leaderboard + dailyLeaderboard
      // (isFinal: true) via the same builder used for the periodic in-round runs.
      await populateMohamedLoversRound(db, admin, closedRound, true);
      console.log(`Round ${closedRound} closed.`);
    }
  }

  // Seed the brand-new round's leaderboard/roundTotal/roundPlayerCount right away,
  // instead of leaving it for the next generic leaderboard-populate cron slot.
  const newRoundKey = addDaysToDateKey(closedRound, 7);
  console.log(`New round: ${newRoundKey}`);
  await populateMohamedLoversRound(db, admin, newRoundKey, false);

  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
