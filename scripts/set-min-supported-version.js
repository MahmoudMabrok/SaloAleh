// Sets the force-update floor `mohamed_lovers/app_config/minSupportedVersion`.
//
// Builds whose versionName is strictly older than this value are force-blocked
// to update (non-dismissable prompt) and denied on the main competition write
// path by the schemaVersion rule. Run this deliberately to retire old builds —
// it is NOT auto-published alongside a release like `latestVersion`.
//
// Rollout order matters: ship + publish the new build and let the store
// propagate BEFORE raising this floor, otherwise not-yet-updated clients are
// blocked immediately.

const admin = require('firebase-admin');
const { publishMinSupportedVersion } = require('./app-config-utils');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const version = process.env.MIN_SUPPORTED_VERSION || '4.2.5';

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

async function main() {
  console.log(`[set-min-supported-version] ===== run start ===== version=${version}`);
  await publishMinSupportedVersion(admin.database(), version);
  console.log('[set-min-supported-version] ===== run end =====');
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
