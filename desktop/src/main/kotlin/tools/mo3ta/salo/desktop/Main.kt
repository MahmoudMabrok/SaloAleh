package tools.mo3ta.salo.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "صلِّ عليه — سطح المكتب",
        state = rememberWindowState(size = DpSize(480.dp, 760.dp)),
    ) {
        DesktopApp()
    }
}
