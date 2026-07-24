/**
 * @file delete-inactive-users.js
 * @description Daily cleanup of stale user profiles from the SaloAleh backend.
 *
 * Runs once a day via GitHub Actions (delete-inactive-users.yml). Scans every
 * user under `mohamed_lovers/users/{uid}` and prunes the ones that have gone
 * inactive, using a two-tier threshold keyed on whether the user ever scored
 * (their lifetime `allTimeTotal`, accumulated at each round close):
 *
 *   - Never scored (allTimeTotal == 0) → the user opened the app but left / has
 *                            no salawat; delete when inactive for ZERO_SCORE_DAYS
 *                            days or more (default 3).
 *   - Has scored before (allTimeTotal > 0) → delete when inactive for MORE THAN
 *                            SCORED_DAYS days (default 7; users who contributed
 *                            get a longer grace period before they are pruned).
 *
 * "Inactive" is measured in Cairo calendar days since the user's last activity:
 * `lastOpenDate` when present, otherwise `installDate`. A user with neither date
 * recorded has no evidence of activity and is treated as eligible for deletion.
 *
 * Deletion removes the whole `mohamed_lovers/users/{uid}` node (which includes
 * that user's `achievements` child) in a single batched multi-path RTDB update,
 * and mirrors the delete to Firestore (`mohamed_lovers_users/{uid}`) per the
 * Phase-1 dual-write convention.
 *
 * Current-round players are also cleaned up in the same run:
 *   - Every deleted user is removed from `mohamed_lovers/{roundKey}/players/{uid}`.
 *   - Any player sitting at 0 score for two days (current `totalCount` <= 0 AND
 *     the daily-snapshot `yesterdayTotalScore` <= 0) is pruned from the round.
 *     A player with no `yesterdayTotalScore` yet (joined today) is left alone —
 *     they have not been at 0 for two days.
 * Round player docs are mirrored-deleted from Firestore too.
 *
 * ## Environment Variables
 *
 * | Variable                 | Type   | Default | Description                                            |
 * |--------------------------|--------|---------|--------------------------------------------------------|
 * | FIREBASE_SERVICE_ACCOUNT | string | —       | JSON service account key (required)                    |
 * | FIREBASE_DATABASE_URL    | string | —       | RTDB URL (required)                                    |
 * | ZERO_SCORE_DAYS          | number | 3       | Inactivity threshold (>=) for users who never scored   |
 * | SCORED_DAYS              | number | 7       | Inactivity threshold (>) for users who scored before   |
 * | DRY_RUN                  | string | "false" | When "true", logs what would be deleted without writing|
 */

const admin = require('firebase-admin');
const { mirrorUsersDelete, mirrorRoundPlayersDelete } = require('./firestore-utils');

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
 * Returns the round key (YYYY-MM-DD) for the upcoming Friday at 19:00 Cairo time.
 * Mirrors cairoRoundKey() in the other cron scripts.
 * @returns {string}
 */
function cairoRoundKey() {
  const now = new Date();
  const zone = 'Africa/Cairo';
  const weekdayStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(now);
  const dayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const jsDow = dayMap[weekdayStr];
  const hourStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(now);
  const cairoHour = parseInt(hourStr, 10);
  let daysToFriday = (5 - jsDow + 7) % 7;
  if (daysToFriday === 0 && cairoHour >= 19) daysToFriday = 7;
  const fridayDate = new Date(now.getTime() + daysToFriday * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
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
 * True when the user has ever scored — a positive lifetime `allTimeTotal`
 * (accumulated at each round close). Used to pick the inactivity threshold.
 * @param {object} user - the value of mohamed_lovers/users/{uid}
 * @returns {boolean}
 */
function hasScored(user) {
  return !!user && typeof user.allTimeTotal === 'number' && user.allTimeTotal > 0;
}

async function main() {
  console.log('[delete-inactive-users] ===== run start =====');
  const db = admin.database();
  const today = cairoToday();
  const roundKey = cairoRoundKey();

  const zeroScoreDays = parseInt(process.env.ZERO_SCORE_DAYS || '3', 10);
  const scoredDays = parseInt(process.env.SCORED_DAYS || '7', 10);
  const dryRun = process.env.DRY_RUN === 'true';

  console.log(`[delete-inactive-users] today=${today} round=${roundKey} zeroScoreDays>=${zeroScoreDays} scoredDays>${scoredDays} dryRun=${dryRun}`);

  const usersSnap = await db.ref('mohamed_lovers/users').get();
  if (!usersSnap.exists()) {
    console.log('[delete-inactive-users] no users found — exiting');
    process.exit(0);
  }

  const totalUsers = usersSnap.size;
  console.log(`[delete-inactive-users] loaded ${totalUsers} user(s)`);

  const deletions = {}; // RTDB multi-path update: node path → null
  const deletedUids = [];
  const counts = { deleted_zero_score: 0, deleted_scored: 0, kept: 0, no_dates: 0 };

  usersSnap.forEach(userSnap => {
    const uid = userSnap.key;
    const user = userSnap.val() || {};

    const lastActive = user.lastOpenDate || user.installDate || null;
    const scored = hasScored(user);
    const daysInactive = lastActive ? daysBetween(lastActive, today) : null;

    // No date recorded → no evidence of activity → eligible for deletion.
    if (daysInactive === null) {
      counts.no_dates++;
      console.log(`[delete-inactive-users] uid=${uid} no lastOpenDate/installDate → delete (scored=${scored})`);
      deletions[`mohamed_lovers/users/${uid}`] = null;
      deletedUids.push(uid);
      if (scored) counts.deleted_scored++; else counts.deleted_zero_score++;
      return;
    }

    // Never scored: delete when inactive N days "or more" (>=).
    // Scored before: delete when inactive "more than" M days (>).
    const eligible = scored ? daysInactive > scoredDays : daysInactive >= zeroScoreDays;

    if (eligible) {
      console.log(`[delete-inactive-users] uid=${uid} daysInactive=${daysInactive} scored=${scored} → delete`);
      deletions[`mohamed_lovers/users/${uid}`] = null;
      deletedUids.push(uid);
      if (scored) counts.deleted_scored++; else counts.deleted_zero_score++;
    } else {
      counts.kept++;
      console.log(`[delete-inactive-users] uid=${uid} daysInactive=${daysInactive} scored=${scored} → keep`);
    }
  });

  console.log(`[delete-inactive-users] counts: ${JSON.stringify(counts)}`);
  console.log(`[delete-inactive-users] ${deletedUids.length}/${totalUsers} user(s) selected for deletion`);

  // --- Current-round player cleanup ---------------------------------------
  // (a) cascade every deleted user out of the current round's players, and
  // (b) prune players sitting at 0 score for two days.
  const playersSnap = await db.ref(`mohamed_lovers/${roundKey}/players`).get();
  const deletedUidSet = new Set(deletedUids);
  const deletedPlayerUids = [];
  const playerCounts = { cascade: 0, zero_two_days: 0 };

  const markPlayerDeleted = (uid, reason) => {
    if (deletions[`mohamed_lovers/${roundKey}/players/${uid}`] === null) return; // already queued
    deletions[`mohamed_lovers/${roundKey}/players/${uid}`] = null;
    deletedPlayerUids.push(uid);
    console.log(`[delete-inactive-users] round-player uid=${uid} → delete (${reason})`);
  };

  if (playersSnap.exists()) {
    playersSnap.forEach(playerSnap => {
      const uid = playerSnap.key;
      const player = playerSnap.val() || {};

      // (a) User was deleted → drop their round entry too.
      if (deletedUidSet.has(uid)) {
        playerCounts.cascade++;
        markPlayerDeleted(uid, 'user deleted');
        return;
      }

      // (b) Zero score for two days: current total is 0 AND the previous daily
      // snapshot (yesterdayTotalScore, written by generate-stats.js) is also 0.
      // A player with no snapshot yet (joined today) is skipped — not two days.
      const totalCount = player.totalCount || 0;
      const yesterday = player.yesterdayTotalScore;
      if (totalCount <= 0 && typeof yesterday === 'number' && yesterday <= 0) {
        playerCounts.zero_two_days++;
        markPlayerDeleted(uid, 'zero score for two days');
      }
    });
  }

  console.log(`[delete-inactive-users] round-player counts: ${JSON.stringify(playerCounts)}`);
  console.log(`[delete-inactive-users] ${deletedPlayerUids.length} round-player(s) selected for deletion`);

  if (deletedUids.length === 0 && deletedPlayerUids.length === 0) {
    console.log('[delete-inactive-users] nothing to delete — done');
    process.exit(0);
  }

  if (dryRun) {
    console.log('[delete-inactive-users] DRY_RUN — no writes performed');
    if (deletedUids.length) console.log(`[delete-inactive-users] would delete users: ${deletedUids.join(', ')}`);
    if (deletedPlayerUids.length) console.log(`[delete-inactive-users] would delete round-players: ${deletedPlayerUids.join(', ')}`);
    process.exit(0);
  }

  // Single batched multi-path update; each path set to null removes that node.
  await db.ref('/').update(deletions);
  console.log(`[delete-inactive-users] deleted ${deletedUids.length} user node(s) and ${deletedPlayerUids.length} round-player(s) from RTDB`);

  // Phase 1: mirror the deletes to Firestore (no-op while the kill-switch is off).
  if (deletedUids.length) await mirrorUsersDelete(admin.firestore(), deletedUids);
  if (deletedPlayerUids.length) await mirrorRoundPlayersDelete(admin.firestore(), roundKey, deletedPlayerUids);

  console.log('[delete-inactive-users] ===== run end =====');
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
