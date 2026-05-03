// Runs once per day. Sums roundTotal from all non-current rounds and writes
// the result to mohamed_lovers/allTimeTotal. Current round is excluded because
// populate-leaderboard.js keeps its roundTotal live on every hourly run.
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Mirrors cairoRoundKey() in populate-leaderboard.js.
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
  console.log(`Current round: ${currentRound} — excluded from allTimeTotal`);

  const db = admin.database();
  const rootSnapshot = await db.ref('mohamed_lovers').get();

  if (!rootSnapshot.exists()) {
    console.log('No data found under mohamed_lovers.');
    process.exit(0);
  }

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
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
