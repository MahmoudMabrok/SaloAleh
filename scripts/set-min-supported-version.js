// Sets the force-update floor `mohamed_lovers/app_config/minSupportedVersionCode`.
//
// Builds whose integer version code (Android `versionCode`) is lower than this
// value are force-blocked to update (non-dismissable prompt). Using a version
// code rather than a versionName lets internal builds that share the same name
// be gated precisely, and makes the client check an unambiguous integer compare.
// Run this deliberately to retire old builds — it is NOT auto-published
// alongside a release like `latestVersion`.
//
// Rollout order matters: ship + publish the new build and let the store
// propagate BEFORE raising this floor, otherwise not-yet-updated clients are
// blocked immediately.

const admin = require('firebase-admin');
const { publishMinSupportedVersionCode } = require('./app-config-utils');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const versionCode = Number(process.env.MIN_SUPPORTED_VERSION_CODE || '125');

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

async function main() {
  console.log(`[set-min-supported-version] ===== run start ===== versionCode=${versionCode}`);
  await publishMinSupportedVersionCode(admin.database(), versionCode);
  console.log('[set-min-supported-version] ===== run end =====');
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
