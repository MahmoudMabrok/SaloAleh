package tools.mo3ta.salo.appcheck

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.debug.internal.StorageHelper

/**
 * Pins the App Check debug secret to the build-time token from
 * `BuildConfig.APP_CHECK_DEBUG_TOKEN` (system env `FIREBASE_APPCHECK_DEBUG_TOKEN`,
 * see app/build.gradle.kts) by writing it into the preferences file
 * `DebugAppCheckProvider` reads its secret from — the SDK then exchanges that same
 * secret for every debug install, so the value registered once in the Firebase
 * Console keeps working across reinstalls instead of a fresh random one each time.
 *
 * The stock factory still generates and saves its own random secret when this is
 * skipped, so a build without the env var behaves exactly as before.
 *
 * @return true when a token was seeded, false when [token] was blank.
 */
internal fun seedFixedDebugSecret(app: FirebaseApp, token: String): Boolean {
    val secret = token.trim()
    if (secret.isEmpty()) return false
    StorageHelper(app.applicationContext, app.persistenceKey).saveDebugSecret(secret)
    return true
}
