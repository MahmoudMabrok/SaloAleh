// Builds the daily أهل الذكر snapshot (public/ahl-adhikr JSON) from Firebase RTDB.
//
// Data sources (mirroring scripts/populate-leaderboard.js):
//   * the 9 daily challenges — readChallengeRankedUsers() over today's day node
//     (same source the leaderboards rank from; each user's continuous streak is
//     written by their own client). Day nodes are deleted by generate-stats.js at
//     23:45 Cairo, so this must run before that (the workflow fires at 22:30 Cairo).
//   * salawat — RTDB has no cross-round streak (roundStreak is round-scoped), so a
//     small server-owned ledger at ahl_adhikr_state/salawat/{uid} is maintained here,
//     idempotently, from today's activity (totalCount - yesterdayTotalScore > 0).
//
// Output: ahl-adhikr/{date}.json + ahl-adhikr/latest.json, written atomically
// (tmp + rename) and only after validateSnapshot passes — on any failure the last
// valid snapshot stays untouched. Same day + same data = same bytes (idempotent).
//
// Usage:
//   node scripts/generate-ahl-adhikr.js [--dry-run] [--date YYYY-MM-DD]
//   Env: FIREBASE_SERVICE_ACCOUNT, FIREBASE_DATABASE_URL

const fs = require('fs');
const path = require('path');

const {
  CHALLENGE_KEYS,
  cairoDateStr,
  buildSnapshot,
  nextSalawatStreaks,
  validateSnapshot,
} = require('./ahl-adhikr-utils');
const {
  DHIKR_CHALLENGE_ROOT,
  BAQIYAT_CHALLENGE_ROOT,
  ISTIGHFAR_CHALLENGE_ROOT,
  ZABAD_CHALLENGE_ROOT,
  GHARS_CHALLENGE_ROOT,
  QURAN_CHALLENGE_ROOT,
  ALF_HASANA_CHALLENGE_ROOT,
  KALIMAT_CHALLENGE_ROOT,
  HAWQALA_CHALLENGE_ROOT,
  MOHAMED_LOVERS_ROOT,
  readChallengeRankedUsers,
  cairoToday,
} = require('./leaderboard-utils');

// Challenge key → RTDB root. albaqara has no app challenge screen today; the
// issue's canonical list is the 10 above.
const CHALLENGE_ROOTS = {
  dhikr: DHIKR_CHALLENGE_ROOT,
  baqiyat: BAQIYAT_CHALLENGE_ROOT,
  istighfar: ISTIGHFAR_CHALLENGE_ROOT,
  zabad: ZABAD_CHALLENGE_ROOT,
  quran: QURAN_CHALLENGE_ROOT,
  ghars: GHARS_CHALLENGE_ROOT,
  alf_hasana: ALF_HASANA_CHALLENGE_ROOT,
  kalimat: KALIMAT_CHALLENGE_ROOT,
  hawqala: HAWQALA_CHALLENGE_ROOT,
};

const SALAWAT_STATE_ROOT = 'ahl_adhikr_state/salawat';
const OUTPUT_DIR = path.join(__dirname, '..', 'ahl-adhikr');

const RETRY_ATTEMPTS = 3;
const RETRY_BASE_DELAY_MS = 500;

// Transient RTDB failures (socket resets, 5xx) should not discard a whole day's snapshot.
// Retries with a linear backoff; a real outage still fails after the last attempt.
async function withRetry(label, fn, attempts = RETRY_ATTEMPTS, baseDelayMs = RETRY_BASE_DELAY_MS) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error;
      if (attempt < attempts) {
        const delay = baseDelayMs * attempt;
        console.warn(`[retry] ${label} failed (attempt ${attempt}/${attempts}): ${error.message}; retrying in ${delay}ms`);
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
  }
  throw new Error(`${label} failed after ${attempts} attempts: ${lastError && lastError.message}`);
}

function parseArgs(argv) {
  const args = { dryRun: false, date: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--dry-run') args.dryRun = true;
    else if (argv[i] === '--date') args.date = argv[++i];
    else if (argv[i] === '--help' || argv[i] === '-h') args.help = true;
  }
  if (args.date && !/^\d{4}-\d{2}-\d{2}$/.test(args.date)) {
    throw new Error(`Invalid --date: "${args.date}" — expected YYYY-MM-DD`);
  }
  return args;
}

// Round key of the round whose Friday close is on/after `dateStr` — the round the
// given Cairo day belongs to. Mirrors cairoRoundKey() in populate-leaderboard.js
// but anchored to an explicit date instead of now.
function roundKeyForDate(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number);
  const utc = Date.UTC(y, m - 1, d);
  // 1970-01-01 was a Thursday (JS weekday 4); Friday is weekday 5.
  const weekday = (Math.floor(utc / 86400000) + 4) % 7;
  const daysToFriday = (5 - weekday + 7) % 7;
  const friday = new Date(utc + daysToFriday * 86400000);
  return friday.toISOString().slice(0, 10);
}

// Reads today's ranked users (streak included) for each of the 9 challenges.
async function readChallengeStreaks(db, dateStr) {
  const result = {};
  for (const key of Object.keys(CHALLENGE_ROOTS)) {
    const ranked = await withRetry(
      `read challenge ${key}`,
      () => readChallengeRankedUsers(db, CHALLENGE_ROOTS[key], dateStr),
    );
    result[key] = ranked;
    const eligible = ranked.filter(u => u.streak >= 30).length;
    console.log(`[challenge:${key}] users=${ranked.length} streak>=30=${eligible}`);
  }
  return result;
}

// Salawat: reads today's active uids (positive server-derived day total) plus the
// ledger, then computes the new streaks. Returns { streaks, state }.
async function computeSalawatStreaks(db, dateStr) {
  const roundKey = roundKeyForDate(dateStr);
  const playersSnap = await withRetry(
    'read salawat players',
    () => db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/players`).get(),
  );
  const activeUids = [];
  if (playersSnap.exists()) {
    playersSnap.forEach(child => {
      const p = child.val() || {};
      const baseline = typeof p.yesterdayTotalScore === 'number' ? p.yesterdayTotalScore : 0;
      const total = typeof p.totalCount === 'number' ? p.totalCount : 0;
      if (total - baseline > 0) activeUids.push(child.key);
    });
  }
  console.log(`[salawat] round=${roundKey} activeToday=${activeUids.length}`);

  const stateSnap = await withRetry('read salawat ledger', () => db.ref(SALAWAT_STATE_ROOT).get());
  const prevState = stateSnap.exists() ? stateSnap.val() : {};
  const { state, streaks } = nextSalawatStreaks(prevState, activeUids, dateStr);
  return { streaks, state };
}

// Atomic multi-file write: both files are staged in a sibling tmp dir and renamed
// into place only after validation, so a reader never sees a partial file.
function writeSnapshotFiles(snapshot, dateStr, outDir = OUTPUT_DIR) {
  const json = JSON.stringify(snapshot, null, 2);
  const stage = fs.mkdtempSync(path.join(path.dirname(outDir), '.ahl-adhikr-tmp-'));
  try {
    const latestPath = path.join(stage, 'latest.json');
    const datedPath = path.join(stage, `${dateStr}.json`);
    fs.writeFileSync(latestPath, json);
    fs.writeFileSync(datedPath, json);
    fs.mkdirSync(outDir, { recursive: true });
    fs.renameSync(latestPath, path.join(outDir, 'latest.json'));
    fs.renameSync(datedPath, path.join(outDir, `${dateStr}.json`));
  } finally {
    fs.rmSync(stage, { recursive: true, force: true });
  }
  return json;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log('Usage: node scripts/generate-ahl-adhikr.js [--dry-run] [--date YYYY-MM-DD]');
    process.exit(0);
  }

  const dateStr = args.date || cairoToday();
  console.log(`Ahl Adhikr snapshot for ${dateStr}${args.dryRun ? ' (dry-run)' : ''}`);

  const admin = require('firebase-admin'); // lazy, so tests can require this file
  admin.initializeApp({
    credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)),
    databaseURL: process.env.FIREBASE_DATABASE_URL,
  });
  const db = admin.database();

  // Reads first — any failure here exits before a single file is touched.
  const challenges = await readChallengeStreaks(db, dateStr);
  const salawat = await computeSalawatStreaks(db, dateStr);

  const snapshot = buildSnapshot({ dateStr, challenges, salawatStreaks: salawat.streaks });
  const counts = validateSnapshot(snapshot);

  const total = Object.values(counts)
    .map(m => m['30_days'])
    .reduce((a, b) => a + b, 0);
  console.log(`Validation ok — ${total} users at 30+ days across ${CHALLENGE_KEYS.length} challenges`);
  for (const key of CHALLENGE_KEYS) {
    const c = counts[key];
    console.log(`  ${key}: 30=${c['30_days']} 60=${c['60_days']} 90=${c['90_days']}`);
  }

  if (args.dryRun) {
    console.log('Dry-run: not writing files, not updating the salawat ledger.');
    console.log('--- SNAPSHOT BEGIN ---');
    console.log(JSON.stringify(snapshot, null, 2));
    console.log('--- SNAPSHOT END ---');
    return;
  }

  // Ledger update after validation, before the files — a crash between the two is
  // recoverable (the ledger write is idempotent for the same day), while the
  // reverse order would drift the ledger ahead of the published snapshot.
  await withRetry('write salawat ledger', () => db.ref(SALAWAT_STATE_ROOT).set(salawat.state));
  console.log(`Salawat ledger updated (${Object.keys(salawat.state).length} streaks kept)`);

  const json = writeSnapshotFiles(snapshot, dateStr);
  console.log(`Wrote ahl-adhikr/latest.json and ahl-adhikr/${dateStr}.json (${json.length} bytes)`);
}

if (require.main === module) {
  main().catch(err => {
    console.error('FAILED — last valid snapshot left untouched:', err.message);
    process.exit(1);
  });
}

module.exports = { parseArgs, roundKeyForDate, writeSnapshotFiles, withRetry, OUTPUT_DIR };
