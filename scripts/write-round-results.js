// Read-only on Firebase: rebuilds the public round-results summary for a round that
// was already closed by aggregate-all-time.js (e.g. a round that closed before
// round-results existed). It only READS mohamed_lovers/<round>/players, roundTotal and
// roundPlayerCount. It never writes to RTDB or Firestore and never writes into
// round-results/, so it is safe to re-run: no allTimeTotal, medal, achievement,
// winner-code, leaderboard or repo changes.
//
// Output:
//   - $GITHUB_STEP_SUMMARY (when set): the Arabic markdown table + the JSON in a ```json block
//   - stdout: the JSON between JSON_BEGIN / JSON_END markers (readable via job logs)
const fs = require('fs');
const { buildRoundResults, extractRoundPlayers, renderRoundResultsMarkdown } = require('./round-results-utils');

const JSON_BEGIN = '=== ROUND_RESULTS_JSON_BEGIN ===';
const JSON_END = '=== ROUND_RESULTS_JSON_END ===';

// Pure: the job-summary markdown and the marked log block for a results object.
function formatRoundResultsOutput(results) {
  const json = JSON.stringify(results, null, 2);
  return {
    summary: `${renderRoundResultsMarkdown(results)}\n\`\`\`json\n${json}\n\`\`\`\n`,
    log: `${JSON_BEGIN}\n${json}\n${JSON_END}`,
  };
}

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

  const admin = require('firebase-admin'); // lazy, so tests can require this file
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
  const { summary, log } = formatRoundResultsOutput(results);
  console.log(log);
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, summary);
    console.log('Round results written to the job summary.');
  }
  process.exit(0);
}

module.exports = { JSON_BEGIN, JSON_END, formatRoundResultsOutput };

if (require.main === module) {
  main().catch(err => { console.error(err); process.exit(1); });
}
