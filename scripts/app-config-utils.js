// Shared helper for the remote app-update config the client reads at startup.
//
// The app reads `mohamed_lovers/app_config/latestVersion` (a versionName string
// like "3.9.2") and, when it is newer than the running build, shows the in-app
// update prompt. We publish it at the same moment the "new version available"
// FCM broadcast is sent, so the prompt lines up with when users are told the
// update is live (after the store has had time to propagate the release).

const APP_CONFIG_PATH = 'mohamed_lovers/app_config';

/**
 * Writes the latest published version name to remote config so clients on older
 * builds start prompting to update. Idempotent — writing the same value twice is
 * harmless. Never throws: a config-write failure must not abort the surrounding
 * notification job, so failures are logged and swallowed.
 *
 * @param {import('firebase-admin').database.Database} db
 * @param {string} version versionName string, e.g. "3.9.2"
 */
async function publishLatestVersion(db, version) {
  if (!version || typeof version !== 'string') {
    console.warn(`[app-config] skipping latestVersion write — invalid version: ${version}`);
    return;
  }
  try {
    await db.ref(`${APP_CONFIG_PATH}/latestVersion`).set(version);
    console.log(`[app-config] latestVersion set to ${version}`);
  } catch (e) {
    console.error(`[app-config] failed to set latestVersion=${version}: ${e.message}`);
  }
}

/**
 * Writes the minimum supported version *code* to remote config. Builds whose
 * integer version code (Android `versionCode`) is lower than this are
 * force-blocked to update (non-dismissable prompt). A code is used rather than a
 * versionName so internal builds sharing a name can still be gated precisely and
 * the client comparison is an unambiguous integer check. Unlike `latestVersion`,
 * this is never auto-published alongside a build — it is set deliberately to
 * retire old builds. Idempotent — writing the same value twice is harmless. Never
 * throws.
 *
 * @param {import('firebase-admin').database.Database} db
 * @param {number} versionCode integer version code, e.g. 125
 */
async function publishMinSupportedVersionCode(db, versionCode) {
  const code = Number(versionCode);
  if (!Number.isInteger(code) || code <= 0) {
    console.warn(`[app-config] skipping minSupportedVersionCode write — invalid code: ${versionCode}`);
    return;
  }
  try {
    await db.ref(`${APP_CONFIG_PATH}/minSupportedVersionCode`).set(code);
    console.log(`[app-config] minSupportedVersionCode set to ${code}`);
  } catch (e) {
    console.error(`[app-config] failed to set minSupportedVersionCode=${code}: ${e.message}`);
  }
}

module.exports = { publishLatestVersion, publishMinSupportedVersionCode, APP_CONFIG_PATH };
