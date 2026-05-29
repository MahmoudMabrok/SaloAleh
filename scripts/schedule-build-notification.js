// Writes a "pending build notification" node to RTDB after a successful
// production release. The populate-leaderboard job (runs every ~30 min) picks
// it up once firesAt has passed, sends the FCM broadcast, and deletes the node.
//
// This replaces the earlier Deno-scheduler/KV approach: Deno Deploy doesn't
// expose Deno KV or runtime Deno.cron in our project, so we lean on the
// already-scheduled leaderboard job + Firebase as the durable store instead.
const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const version = process.env.NEW_BUILD_VERSION || '';
const track = process.env.GOOGLE_PLAY_TRACK || 'production';
const delayHours = Number(process.env.NOTIFY_DELAY_HOURS || '2');

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

async function main() {
  if (!version) {
    console.error('[schedule-build-notification] NEW_BUILD_VERSION is empty — aborting');
    process.exit(1);
  }

  const now = Date.now();
  const firesAt = now + delayHours * 60 * 60 * 1000;

  const db = admin.database();
  await db.ref('mohamed_lovers/scheduledBuildNotification').set({
    version,
    track,
    scheduledAt: now,
    firesAt,
  });

  console.log(
    `[schedule-build-notification] queued v${version} (track=${track}) — ` +
    `fires after ${delayHours}h at ${new Date(firesAt).toISOString()} ` +
    `(picked up by the next leaderboard-populate run)`
  );
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
