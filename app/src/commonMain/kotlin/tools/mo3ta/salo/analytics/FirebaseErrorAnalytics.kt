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
            "surface" to surface,
            "operation" to operation,
            "access" to access,
            "error" to (error.message ?: error::class.simpleName ?: "unknown"),
        ),
    )
}
