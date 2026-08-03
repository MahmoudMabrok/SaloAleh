package tools.mo3ta.salo.domain

/**
 * Remote-config payload describing the latest published app release.
 *
 * Read from RTDB `mohamed_lovers/app_config`. The client compares [latestVersion]
 * (a versionName string) to the running app's version name at startup and offers a
 * *soft* update when it is newer.
 *
 * [minSupportedVersionCode], when present, is the lowest still-allowed integer version
 * code (Android `versionCode` / iOS `CFBundleVersion`): any running build whose code is
 * lower is *forced* to update and blocked from using the app. A code is used rather than
 * a versionName so internal builds that share the same name can still be gated precisely,
 * and so the comparison is an unambiguous integer check.
 */
data class AppUpdateConfig(
    val latestVersion: String,
    val minSupportedVersionCode: Int? = null,
)

/**
 * Compares two dotted version-name strings (e.g. "3.9.1") component-by-component
 * numerically and returns true when [candidate] is a strictly newer release than
 * [current].
 *
 * - Missing trailing components count as `0`, so "3.9" equals "3.9.0".
 * - Only the leading digits of each component are read, so build suffixes like
 *   "3.9.1-beta" compare as "3.9.1"; a component with no leading digits counts as `0`.
 * - Blank input compares as "0", so an empty [candidate] never looks newer.
 */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val a = parseVersion(candidate)
    val b = parseVersion(current)
    val size = maxOf(a.size, b.size)
    for (i in 0 until size) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun parseVersion(version: String): List<Int> =
    version.trim()
        .split('.')
        .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
