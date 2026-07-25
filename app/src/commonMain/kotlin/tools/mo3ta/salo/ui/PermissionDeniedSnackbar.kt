package tools.mo3ta.salo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.analytics.PermissionDeniedNotifier
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.permission_denied_update_message

/**
 * App-wide snackbar host that surfaces a "something went wrong, please update the app" message
 * whenever a Firebase write/read is rejected with a permission-denied error (see
 * [PermissionDeniedNotifier]). Mounted once at the Compose root as an overlay so the message reaches
 * the user on any screen, including the challenge full-screen overlays and onboarding.
 */
@Composable
fun PermissionDeniedSnackbar(modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    val message = stringResource(Res.string.permission_denied_update_message)
    LaunchedEffect(hostState, message) {
        PermissionDeniedNotifier.events.collect {
            // Skip re-showing while one is already visible so repeated failing writes don't queue
            // a chain of identical snackbars.
            if (hostState.currentSnackbarData == null) {
                hostState.showSnackbar(message)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
