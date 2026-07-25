package tools.mo3ta.salo.analytics

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide, fire-and-forget signal raised when a Firebase operation is rejected with a
 * permission-denied error. Such a rejection almost always means the running build predates a
 * security-rules change, so the UI reacts by prompting the user to update the app.
 *
 * Implemented as a process-wide singleton rather than a Koin dependency because the only detection
 * point — [logFirebaseError] — is a plain extension function with no injection scope. A singleton
 * lets that data-layer hook and the Compose root share one event stream without threading the
 * notifier through every Firebase client's failure funnel.
 */
object PermissionDeniedNotifier {
    // extraBufferCapacity = 1 with the default drop-on-overflow keeps a single event queued while
    // the snackbar is showing; a storm of failing writes therefore can't stack up dozens of events.
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyPermissionDenied() {
        _events.tryEmit(Unit)
    }
}

/** True when [error] is a Firebase permission-denied rejection (RTDB security-rule denial). */
internal fun isPermissionDeniedError(error: Throwable): Boolean {
    val message = error.message ?: return false
    return message.contains("permission denied", ignoreCase = true) ||
        message.contains("permission_denied", ignoreCase = true)
}
