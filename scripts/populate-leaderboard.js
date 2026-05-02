// Reads top-10 players for the current round from Firebase RTDB and writes
// them to the leaderboard node. Intended to run after round close (Friday 6pm Cairo).
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const explicitRoundKey = process.env.ROUND_KEY; // optional override

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

function cairoDateString() {
  // Egypt is UTC+2 (winter) / UTC+3 (summer). Use toLocaleString with the IANA zone.
  const now = new Date();
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  const y = parts.find(p => p.type === 'year').value;
  const m = parts.find(p => p.type === 'month').value;
  const d = parts.find(p => p.type === 'day').value;
  return `${y}-${m}-${d}`;
}

async function main() {
  const roundKey = explicitRoundKey || cairoDateString();
  console.log(`Round key: ${roundKey}`);

  const db = admin.database();
  const snapshot = await db
    .ref(`mohamed_lovers/${roundKey}/players`)
    .orderByChild('totalCount')
    .limitToLast(10)
    .get();

  if (!snapshot.exists()) {
    console.log('No players found for this round. Exiting.');
    process.exit(0);
  }

  const players = [];
  snapshot.forEach(child => {
    const data = child.val();
    if (data && typeof data.uid === 'string' && typeof data.totalCount === 'number') {
      players.push({ uid: data.uid, score: data.totalCount, updatedAt: data.updatedAt || 0 });
    }
  });

  players.sort((a, b) => b.score - a.score || b.updatedAt - a.updatedAt);

  const leaderboard = {};
  players.forEach((player, i) => {
    leaderboard[String(i + 1)] = { rank: i + 1, uid: player.uid, score: player.score };
  });

  await db.ref(`mohamed_lovers/${roundKey}/leaderboard`).set(leaderboard);

  console.log(`Wrote ${players.length} entries:`);
  console.log(JSON.stringify(leaderboard, null, 2));
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
