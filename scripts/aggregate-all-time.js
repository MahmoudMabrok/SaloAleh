// Runs once per week on Fridays at 19:00 Cairo (17:00 UTC). Sums all past
// rounds (including the one that just closed) into allTimeTotal, then writes
// achievement records for every top-10 finisher so the app can surface them
// to users who missed the live isFinal event.
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Returns today's Cairo date — the round that just closed.
// Reliable only when called on Friday after 18:00 Cairo (matches this cron).
function closedRoundKey() {
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

  // At 19:00 Cairo on Friday, cairoRoundKey() returns NEXT Friday, so the
  // just-closed round is included in the sum automatically.
  let allTimeTotal = 0;
  rootSnapshot.forEach(child => {
    const key = child.key;
    if (!ROUND_KEY_RE.test(key) || key === currentRound) return;
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

  // Write round-history records for every player who participated in the closed round.
  // The app decides whether to surface each entry as an achievement badge (rank 1–10)
  // or plain history based on the stored rank value.
  const playersSnap = await db.ref(`mohamed_lovers/${closedRound}/players`).get();
  if (!playersSnap.exists()) {
    console.log(`No players found for ${closedRound} — no history written.`);
    process.exit(0);
  }

  const writes = {};
  playersSnap.forEach(child => {
    const data = child.val();
    const uid = data?.uid;
    const rank = data?.rank;
    const score = typeof data?.totalCount === 'number' ? data.totalCount : 0;
    if (typeof uid !== 'string' || typeof rank !== 'number' || score <= 0) return;
    writes[`mohamed_lovers/users/${uid}/achievements/${closedRound}`] = {
      rank,
      score,
      date: closedRound,
    };
    console.log(`  History: uid=${uid} rank=${rank} score=${score}`);
  });

  if (Object.keys(writes).length === 0) {
    console.log('No top-10 entries — no achievements written.');
  } else {
    await db.ref('/').update(writes);
    console.log(`Wrote ${Object.keys(writes).length} achievement entries.`);
  }

  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
