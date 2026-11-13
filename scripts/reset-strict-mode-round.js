// Hard-reset server-side restricted mode for every quarantined user at round close.
//
//   node scripts/reset-strict-mode-round.js            # dry run
//   node scripts/reset-strict-mode-round.js --apply    # clear strictMode flags
//
// The reset intentionally preserves abnormal_users, paceFlags, scoreHistory, and achievements.
// Those are audit/history data. It only removes the active server quarantine and records an
// auditable badgeAdjustment for each affected account.
const admin = require('firebase-admin');

const APPLY = process.argv.includes('--apply');
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT;

function closedRoundKey() {
  const override = process.env.CLOSED_ROUND || process.argv.find((arg) => /^\d{4}-\d{2}-\d{2}$/.test(arg));
  if (override) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(override)) {
      throw new Error(`Invalid CLOSED_ROUND: "${override}" — expected YYYY-MM-DD`);
    }
    return override;
  }
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

async function main() {
  if (!serviceAccountJson || !databaseURL) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT and FIREBASE_DATABASE_URL are required');
  }

  const roundKey = closedRoundKey();
  admin.initializeApp({
    credential: admin.credential.cert(JSON.parse(serviceAccountJson)),
    databaseURL,
  });
  const db = admin.database();
  const snapshot = await db.ref('mohamed_lovers/strictMode').get();
  const strictModeState = snapshot.val() || {};
  const uids = Object.keys(strictModeState);

  console.log(`Mode: ${APPLY ? 'APPLY' : 'DRY RUN (pass --apply to write)'}`);
  console.log(`Round close: ${roundKey}`);
  console.log(`Restricted accounts found: ${uids.length}`);

  if (uids.length === 0) {
    console.log('Nothing to reset.');
    return;
  }

  const auditKey = `${roundKey} reset ${new Date().toISOString().slice(11, 19).replaceAll(':', ';')}`;
  const updates = {};
  for (const uid of uids) {
    const previous = strictModeState[uid] || {};
    console.log(`  ${uid}: ${previous.reason || 'unknown'} -> unrestricted`);
    updates[`mohamed_lovers/strictMode/${uid}`] = null;
    updates[`mohamed_lovers/users/${uid}/badgeAdjustments/${auditKey}`] = {
      case: 'strict_mode_round_reset',
      at: Date.now(),
      progress: typeof previous.cleanDays === 'number' ? previous.cleanDays : 0,
      badge: 'strict_mode_reset',
      badgeValue: 0,
    };
  }

  if (!APPLY) {
    console.log(`Dry run only. Would clear ${uids.length} restricted account(s).`);
    return;
  }

  await db.ref('/').update(updates);
  console.log(`Hard reset applied to ${uids.length} restricted account(s).`);
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
