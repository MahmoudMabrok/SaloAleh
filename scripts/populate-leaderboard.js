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
  const snapshot = await db
    .ref(`mohamed_lovers/${roundKey}/players`)
    .orderByChild('totalCount')
    .limitToLast(10)
    .get();

  if (!snapshot.exists()) {
    console.log('No players found. Writing empty leaderboard.');
    await db.ref(`mohamed_lovers/${roundKey}/leaderboard`).set({ isFinal });
    process.exit(0);
  }

  const players = [];
  snapshot.forEach(child => {
    const data = child.val();
    if (data && typeof data.uid === 'string' && typeof data.totalCount === 'number') {
      players.push({
        uid: data.uid,
        score: data.totalCount,
        updatedAt: data.updatedAt || 0,
        countryCode: typeof data.countryCode === 'string' ? data.countryCode : 'NA',
      });
    }
  });

  players.sort((a, b) => b.score - a.score || b.updatedAt - a.updatedAt);

  const leaderboard = { isFinal };
  players.forEach((player, i) => {
    leaderboard[String(i + 1)] = {
      rank: i + 1,
      uid: player.uid,
      score: player.score,
      countryCode: player.countryCode,
    };
  });

  await db.ref(`mohamed_lovers/${roundKey}/leaderboard`).set(leaderboard);
  console.log(`Wrote ${players.length} entries.`);
  console.log(JSON.stringify(leaderboard, null, 2));
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
