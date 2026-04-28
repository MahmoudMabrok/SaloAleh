package tools.mo3ta.salo

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.di.iosModule

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin { modules(appModule, iosModule) }
    },
) { App() }
