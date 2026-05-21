// Runs once per week on Fridays at 19:00 Cairo (17:00 UTC). Sums all past
// rounds (including the one that just closed) into allTimeTotal, then writes
// achievement records for every finisher so the app can surface them
// to users who missed the live isFinal event. Top-5 finishers also receive
// a unique winnerCode in their achievement record.
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Returns today's Cairo date — the round that just closed.
// Reliable only when called on Friday after 18:00 Cairo (matches this cron).
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

// Mirrors cairoRoundKey() in populate-leaderboard.js — the *current* (next)
// round. At 19:00 Cairo on Friday this returns NEXT Friday's date.
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
    timeZone: zone,
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
}

function generateWinnerCode() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let code = '';
  for (let i = 0; i < 8; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return code;
}

const ROUND_KEY_RE = /^\d{4}-\d{2}-\d{2}$/;

async function main() {
  const currentRound = cairoRoundKey();
  const closedRound = closedRoundKey();
  console.log(`Closed round: ${closedRound} | Next round: ${currentRound}`);

  const db = admin.database();
  const rootSnapshot = await db.ref('mohamed_lovers').get();

  if (!rootSnapshot.exists()) {
    console.log('No data found under mohamed_lovers.');
    process.exit(0);
  }

  // Compute actual (non-doubled) scores for the closed round first,
  // so we can use the corrected roundTotal in the allTimeTotal sum.
  const playersSnap = await db.ref(`mohamed_lovers/${closedRound}/players`).get();
  const players = [];
  let correctedRoundTotal = 0;

  if (playersSnap.exists()) {
    playersSnap.forEach(child => {
      const data = child.val();
      const uid = data?.uid;
      const totalCount = typeof data?.totalCount === 'number' ? data.totalCount : 0;
      const yesterdayTotal = typeof data?.yesterdayTotalScore === 'number' ? data.yesterdayTotalScore : 0;
      // Friday taps are doubled — actual = pre-Friday + (Friday portion / 2)
      const fridayPortion = Math.max(0, totalCount - yesterdayTotal);
      const actualScore = yesterdayTotal + Math.floor(fridayPortion / 2);
      if (typeof uid === 'string' && actualScore > 0) {
        players.push({ uid, score: actualScore, updatedAt: data.updatedAt || 0 });
        correctedRoundTotal += actualScore;
      }
    });
    console.log(`Closed round actual total: ${correctedRoundTotal} (raw roundTotal had Friday 2x)`);
  }

  // Sum allTimeTotal across all past rounds, using corrected total for the closed round.
  let allTimeTotal = 0;
  rootSnapshot.forEach(child => {
    const key = child.key;
    if (!ROUND_KEY_RE.test(key) || key === currentRound) return;
    if (key === closedRound) {
      allTimeTotal += correctedRoundTotal;
      console.log(`  ${key}: roundTotal=${correctedRoundTotal} (Friday-corrected)`);
      return;
    }
    const roundTotal = child.val()?.roundTotal;
    if (typeof roundTotal === 'number') {
      allTimeTotal += roundTotal;
      console.log(`  ${key}: roundTotal=${roundTotal}`);
    } else {
      console.log(`  ${key}: no roundTotal — skipped`);
    }
  });

  await db.ref('mohamed_lovers/allTimeTotal').set(allTimeTotal);
  console.log(`allTimeTotal written: ${allTimeTotal}`);

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

  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
