package tools.mo3ta.salo.analytics

fun AnalyticsManager.logFirebaseError(
    surface: String,
    operation: String,
    access: String,
    error: Throwable,
) {
    logAction(
        "firebase_error",
        mapOf(
            "salo_surface" to surface,
            "salo_operation" to operation,
            "salo_access" to access,
            "salo_error" to (error.message ?: error::class.simpleName ?: "unknown"),
        ),
    )
    // A permission-denied rejection means the client is out of step with the deployed security
    // rules — almost always an outdated build — so nudge the user to update the app.
    if (isPermissionDeniedError(error)) {
        PermissionDeniedNotifier.notifyPermissionDenied()
    }
}
