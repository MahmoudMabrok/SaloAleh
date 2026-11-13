// One-off admin clamp for accounts confirmed to have inflated competition scores.
//
//   node scripts/clamp-flagged-players.js            # DRY RUN (default) — prints the plan
//   node scripts/clamp-flagged-players.js --apply    # writes the clamps
//
// For every reviewed account, each round's `totalCount` is clamped to what the pace ceiling
// allows (competition day × PACE_DAILY_INCREMENT), optionally tightened further by a per-round
// `maxTotal` override derived from the evidence in the review. The correction is recorded under
// `users/{uid}/badgeAdjustments/{timeKey}` for the audit trail (admin SDK bypasses rules).
//
// Evidence for each account lives in the DB export under docs/ and in the nightly
// abnormal_users / paceFlags / badgeAdjustments nodes; re-check before applying.
const admin = require('firebase-admin');
const { PACE_DAILY_INCREMENT, addDaysToDateKey, cairoToday, roundDayNumber } = require('./leaderboard-utils');

const APPLY = process.argv.includes('--apply');
const CLOSED_ROUND_DAYS = 7; // a finished round may legitimately reach 7 × the daily pace
const ROUNDS_BACK = 26;      // how many past round keys to inspect
const MAGDA_UID = '5472bc710d53758f8681367b9a447e30b6e89833cf0970dd52cbdae297c84f24';
const magdaTargetArg = process.argv.find((arg) => arg.startsWith('--magda-target-total='));
const MAGDA_TARGET_TOTAL = magdaTargetArg ? Number(magdaTargetArg.split('=')[1]) : null;

const REVIEWED_USERS = [
  {
    uid: '5472bc710d53758f8681367b9a447e30b6e89833cf0970dd52cbdae297c84f24',
    note: 'ماجده ابراهيم دسوقي — 16 pace flags, forged daily badges (badge_above_progress, progress 0)',
    maxTotal: {},
  },
  {
    uid: '498e706ae7dcb9e2875c518d995bb1f7c80e03d727db3f32d9f0478bfca84b07',
    note: 'اسماء — 12,427 on 2026-11-10 (abnormal), forged star badge with zero progress',
    maxTotal: {
      // 30,863 − (12,427 − 12,000) abnormal excess on 2026-11-10.
      '2026-11-13': 30436,
    },
  },
  {
    uid: '17ce92d704c1ec31092304af7633f1306481c22b11db23d29ce1c2561b08e153',
    note: 'فيصل — 12,111 on 2026-10-21 (abnormal), external-heavy rounds, forged badges',
    maxTotal: {},
  },
];

function cairoRoundKeyFromDate(date) {
  const zone = 'Africa/Cairo';
  const weekday = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(date);
  const dayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const jsDow = dayMap[weekday];
  const hour = parseInt(
    new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(date),
    10,
  );
  let daysToFriday = (5 - jsDow + 7) % 7;
  if (daysToFriday === 0 && hour >= 19) daysToFriday = 7;
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(date.getTime() + daysToFriday * 86400000));
}

async function main() {
  const serviceAccount = process.env.FIREBASE_SERVICE_ACCOUNT;
  const databaseURL = process.env.FIREBASE_DATABASE_URL;
  if (!serviceAccount || !databaseURL) {
    console.error('FIREBASE_SERVICE_ACCOUNT and FIREBASE_DATABASE_URL are required');
    process.exit(1);
  }
  admin.initializeApp({ credential: admin.credential.cert(JSON.parse(serviceAccount)), databaseURL });
  const db = admin.database();

  const today = cairoToday();
  const currentRoundKey = cairoRoundKeyFromDate(new Date());
  const roundKeys = [];
  for (let i = 0; i < ROUNDS_BACK; i++) {
    roundKeys.push(addDaysToDateKey(currentRoundKey, -7 * i));
  }

  console.log(`Mode: ${APPLY ? 'APPLY' : 'DRY RUN (pass --apply to write)'}`);
  console.log(`Current round: ${currentRoundKey} | today: ${today}\n`);

  const updates = {};
  let changed = 0;

  for (const user of REVIEWED_USERS) {
    console.log(`\n${user.uid}\n  ${user.note}`);
    for (const roundKey of roundKeys) {
      const snap = await db.ref(`mohamed_lovers/${roundKey}/players/${user.uid}`).get();
      if (!snap.exists()) continue;
      const player = snap.val() || {};
      const total = typeof player.totalCount === 'number' ? player.totalCount : 0;
      if (total <= 0) continue;

      const paceDays = roundKey === currentRoundKey
        ? Math.max(1, roundDayNumber(roundKey, today))
        : CLOSED_ROUND_DAYS;
      const paceCeiling = paceDays * PACE_DAILY_INCREMENT;
      const override = user.maxTotal?.[roundKey];
      const effectiveMax = Math.min(paceCeiling, typeof override === 'number' ? override : Infinity);
      if (total <= effectiveMax) continue;

      changed++;
      console.log(`  ${roundKey}: ${total} -> ${effectiveMax}  (pace ${paceDays}d × ${PACE_DAILY_INCREMENT}${override != null ? `, evidence max ${override}` : ''})`);
      updates[`mohamed_lovers/users/${user.uid}/badgeAdjustments/${new Date().toISOString().slice(0, 16).replace('T', ' ').replace(':', ';')}`] = {
        case: 'anticheat_clamp',
        at: Date.now(),
        progress: total,
        badge: 'clamp',
        badgeValue: effectiveMax,
      };
      updates[`mohamed_lovers/${roundKey}/players/${user.uid}/totalCount`] = effectiveMax;
      // Keep the day baseline from exceeding the clamped total, so the next day's growth
      // accounting starts from the corrected number instead of a negative day total.
      const baseline = typeof player.yesterdayTotalScore === 'number' ? player.yesterdayTotalScore : 0;
      if (baseline > effectiveMax) {
        updates[`mohamed_lovers/${roundKey}/players/${user.uid}/yesterdayTotalScore`] = effectiveMax;
      }
    }
  }

  if (MAGDA_TARGET_TOTAL !== null) {
    if (!APPLY) {
      throw new Error('--magda-target-total requires --apply');
    }
    if (!Number.isInteger(MAGDA_TARGET_TOTAL) || MAGDA_TARGET_TOTAL < 0) {
      throw new Error('--magda-target-total must be a non-negative integer');
    }
    const magdaRef = db.ref(`mohamed_lovers/${currentRoundKey}/players/${MAGDA_UID}`);
    const magdaSnapshot = await magdaRef.get();
    const currentTotal = Number(magdaSnapshot.val()?.totalCount || 0);
    updates[`mohamed_lovers/${currentRoundKey}/players/${MAGDA_UID}/totalCount`] = MAGDA_TARGET_TOTAL;
    updates[`mohamed_lovers/users/${MAGDA_UID}/badgeAdjustments/${new Date().toISOString().slice(0, 16).replace('T', ' ').replace(':', ';')}`] = {
      case: 'manual_score_override',
      at: Date.now(),
      progress: currentTotal,
      badge: 'manual_override',
      badgeValue: MAGDA_TARGET_TOTAL,
    };
    changed++;
    console.log(`\nManual Magda override: ${currentRoundKey}: ${currentTotal} -> ${MAGDA_TARGET_TOTAL}`);
  }

  if (changed === 0) {
    console.log('\nNothing to clamp — every reviewed round is already within the pace ceiling.');
    return;
  }

  console.log(`\n${changed} round(s) to clamp.`);
  if (!APPLY) {
    console.log('Dry run only. Re-run with --apply to write these changes.');
    return;
  }
  await db.ref('/').update(updates);
  console.log(`Applied ${Object.keys(updates).length} update(s). Re-run without --apply to verify.`);
}

let exitCode = 0;
main()
  .catch((error) => {
    console.error(error);
    exitCode = 1;
  })
  .finally(async () => {
    if (admin.apps.length) await admin.app().delete();
    process.exit(exitCode);
  });
