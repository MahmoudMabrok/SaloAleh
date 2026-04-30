package tools.mo3ta.salo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import tools.mo3ta.salo.ui.MohamedLoversScreen

@Composable
fun App() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MohamedLoversScreen()
    }
}
