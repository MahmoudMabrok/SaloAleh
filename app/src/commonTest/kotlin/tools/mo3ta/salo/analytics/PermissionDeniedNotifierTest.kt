package tools.mo3ta.salo.analytics

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionDeniedNotifierTest {

    @Test
    fun detects_gitlive_permission_denied_message() {
        assertTrue(isPermissionDeniedError(RuntimeException("Firebase Database error: Permission denied")))
    }

    @Test
    fun detects_uppercase_permission_denied_code() {
        assertTrue(isPermissionDeniedError(RuntimeException("PERMISSION_DENIED: rule rejected write")))
    }

    @Test
    fun ignores_unrelated_failures() {
        assertFalse(isPermissionDeniedError(RuntimeException("Network is unreachable")))
    }

    @Test
    fun ignores_error_without_message() {
        assertFalse(isPermissionDeniedError(RuntimeException()))
    }

    @Test
    fun notify_emits_event_to_active_collector() = runTest {
        val collected = async { PermissionDeniedNotifier.events.first() }
        // Let the collector subscribe before emitting; SharedFlow has replay = 0.
        runCurrent()
        PermissionDeniedNotifier.notifyPermissionDenied()
        assertEquals(Unit, collected.await())
    }
}
