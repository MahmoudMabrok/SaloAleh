/**
 * @file delete-inactive-users.js
 * @description Daily cleanup of stale user profiles from the SaloAleh backend.
 *
 * Runs once a day via GitHub Actions (delete-inactive-users.yml). Scans every
 * user under `mohamed_lovers/users/{uid}` and prunes the ones that have gone
 * inactive, using a two-tier threshold that keeps engaged users longer:
 *
 *   - No achievement node  → delete when inactive for NO_ACH_DAYS days or more
 *                            (default 3; "did not open the app for N days or more").
 *   - Has achievement node → delete when inactive for MORE THAN WITH_ACH_DAYS
 *                            days (default 7; engaged users get a longer grace
 *                            period before they are pruned).
 *
 * "Inactive" is measured in Cairo calendar days since the user's last activity:
 * `lastOpenDate` when present, otherwise `installDate`. A user with neither date
 * recorded has no evidence of activity and is treated as eligible for deletion.
 *
 * Deletion removes the whole `mohamed_lovers/users/{uid}` node (which includes
 * that user's `achievements` child) in a single batched multi-path RTDB update,
 * and mirrors the delete to Firestore (`mohamed_lovers_users/{uid}`) per the
 * Phase-1 dual-write convention. It does NOT touch round `players/{uid}` nodes —
 * those age out with their round.
 *
 * ## Environment Variables
 *
 * | Variable                 | Type   | Default | Description                                            |
 * |--------------------------|--------|---------|--------------------------------------------------------|
 * | FIREBASE_SERVICE_ACCOUNT | string | —       | JSON service account key (required)                    |
 * | FIREBASE_DATABASE_URL    | string | —       | RTDB URL (required)                                    |
 * | NO_ACH_DAYS              | number | 3       | Inactivity threshold (>=) for users with no achievement|
 * | WITH_ACH_DAYS            | number | 7       | Inactivity threshold (>) for users with an achievement |
 * | DRY_RUN                  | string | "false" | When "true", logs what would be deleted without writing|
 */

const admin = require('firebase-admin');
const { mirrorUsersDelete } = require('./firestore-utils');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

/**
 * Returns today's date in Cairo timezone as an ISO date string (YYYY-MM-DD).
 * @returns {string}
 */
function cairoToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

/**
 * Returns the number of calendar days between two ISO date strings.
 * @param {string} dateStr1 - Earlier date (YYYY-MM-DD)
 * @param {string} dateStr2 - Later date (YYYY-MM-DD)
 * @returns {number} Positive integer when dateStr2 > dateStr1
 */
function daysBetween(dateStr1, dateStr2) {
  const d1 = new Date(dateStr1);
  const d2 = new Date(dateStr2);
  return Math.round((d2 - d1) / 86400000);
}

/**
 * True when the user object carries at least one achievement entry.
 * @param {object} user - the value of mohamed_lovers/users/{uid}
 * @returns {boolean}
 */
function hasAchievement(user) {
  const ach = user && user.achievements;
  return !!ach && typeof ach === 'object' && Object.keys(ach).length > 0;
}

async function main() {
  console.log('[delete-inactive-users] ===== run start =====');
  const db = admin.database();
  const today = cairoToday();

  const noAchDays = parseInt(process.env.NO_ACH_DAYS || '3', 10);
  const withAchDays = parseInt(process.env.WITH_ACH_DAYS || '7', 10);
  const dryRun = process.env.DRY_RUN === 'true';

  console.log(`[delete-inactive-users] today=${today} noAchDays>=${noAchDays} withAchDays>${withAchDays} dryRun=${dryRun}`);

  const usersSnap = await db.ref('mohamed_lovers/users').get();
  if (!usersSnap.exists()) {
    console.log('[delete-inactive-users] no users found — exiting');
    process.exit(0);
  }

  const totalUsers = usersSnap.size;
  console.log(`[delete-inactive-users] loaded ${totalUsers} user(s)`);

  const deletions = {}; // RTDB multi-path update: node path → null
  const deletedUids = [];
  const counts = { deleted_no_ach: 0, deleted_with_ach: 0, kept: 0, no_dates: 0 };

  usersSnap.forEach(userSnap => {
    const uid = userSnap.key;
    const user = userSnap.val() || {};

    const lastActive = user.lastOpenDate || user.installDate || null;
    const withAch = hasAchievement(user);
    const daysInactive = lastActive ? daysBetween(lastActive, today) : null;

    // No date recorded → no evidence of activity → eligible for deletion.
    if (daysInactive === null) {
      counts.no_dates++;
      console.log(`[delete-inactive-users] uid=${uid} no lastOpenDate/installDate → delete (withAch=${withAch})`);
      deletions[`mohamed_lovers/users/${uid}`] = null;
      deletedUids.push(uid);
      if (withAch) counts.deleted_with_ach++; else counts.deleted_no_ach++;
      return;
    }

    // "or more" for no-achievement (>=); "more than" for achievement (>).
    const eligible = withAch ? daysInactive > withAchDays : daysInactive >= noAchDays;

    if (eligible) {
      console.log(`[delete-inactive-users] uid=${uid} daysInactive=${daysInactive} withAch=${withAch} → delete`);
      deletions[`mohamed_lovers/users/${uid}`] = null;
      deletedUids.push(uid);
      if (withAch) counts.deleted_with_ach++; else counts.deleted_no_ach++;
    } else {
      counts.kept++;
      console.log(`[delete-inactive-users] uid=${uid} daysInactive=${daysInactive} withAch=${withAch} → keep`);
    }
  });

  console.log(`[delete-inactive-users] counts: ${JSON.stringify(counts)}`);
  console.log(`[delete-inactive-users] ${deletedUids.length}/${totalUsers} user(s) selected for deletion`);

  if (deletedUids.length === 0) {
    console.log('[delete-inactive-users] nothing to delete — done');
    process.exit(0);
  }

  if (dryRun) {
    console.log('[delete-inactive-users] DRY_RUN — no writes performed');
    console.log(`[delete-inactive-users] would delete: ${deletedUids.join(', ')}`);
    process.exit(0);
  }

  // Single batched multi-path update; each path set to null removes that node.
  await db.ref('/').update(deletions);
  console.log(`[delete-inactive-users] deleted ${deletedUids.length} user node(s) from RTDB`);

  // Phase 1: mirror the deletes to Firestore (no-op while the kill-switch is off).
  await mirrorUsersDelete(admin.firestore(), deletedUids);

  console.log('[delete-inactive-users] ===== run end =====');
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
