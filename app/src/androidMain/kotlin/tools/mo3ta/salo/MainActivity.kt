package tools.mo3ta.salo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import tools.mo3ta.salo.di.androidModule
import tools.mo3ta.salo.di.appModule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.init(this)
        startKoin {
            androidContext(this@MainActivity)
            modules(appModule, androidModule)
        }
        enableEdgeToEdge()
        setContent { App() }
    }
}
