// Read-only on Firebase: rebuilds the public round-results/ summary for a round that
// was already closed by aggregate-all-time.js (e.g. a round that closed before
// round-results existed). It only READS mohamed_lovers/<round>/players, roundTotal and
// roundPlayerCount, then writes round-results/<round>.json (+ latest.json / latest.md)
// to the repo checkout. It never writes to RTDB or Firestore, so it is safe to re-run:
// no allTimeTotal, medal, achievement, winner-code or leaderboard changes.
const admin = require('firebase-admin');
const { buildRoundResults, extractRoundPlayers, writeRoundResults } = require('./round-results-utils');

function roundKey() {
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

async function main() {
  const round = roundKey();
  console.log(`Round: ${round}`);

  admin.initializeApp({
    credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)),
    databaseURL: process.env.FIREBASE_DATABASE_URL,
  });
  const db = admin.database();

  // Reads only (.get()).
  const [playersSnap, totalSnap, countSnap] = await Promise.all([
    db.ref(`mohamed_lovers/${round}/players`).get(),
    db.ref(`mohamed_lovers/${round}/roundTotal`).get(),
    db.ref(`mohamed_lovers/${round}/roundPlayerCount`).get(),
  ]);

  const { players, roundTotal } = extractRoundPlayers(playersSnap.exists() ? playersSnap.val() : null);
  if (players.length === 0) {
    console.error(`No players found for ${round} — nothing written.`);
    process.exit(1);
  }

  // Same values aggregate-all-time.js passes (the stored close-time totals),
  // falling back to the sum/count of the players read here.
  const storedTotal = totalSnap.val();
  const storedCount = countSnap.val();
  const results = buildRoundResults({
    round,
    players,
    roundTotal: typeof storedTotal === 'number' ? storedTotal : roundTotal,
    playerCount: typeof storedCount === 'number' ? storedCount : players.length,
  });
  const written = writeRoundResults(results);
  console.log(`Round results written: ${written.join(', ')}`);
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
