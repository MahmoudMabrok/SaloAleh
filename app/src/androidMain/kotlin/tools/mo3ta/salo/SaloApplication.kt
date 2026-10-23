package tools.mo3ta.salo

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import tools.mo3ta.salo.billing.SaloBillingClient
import tools.mo3ta.salo.di.androidModule
import tools.mo3ta.salo.di.appModule
import tools.mo3ta.salo.notification.NotificationChannels

class SaloApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withStoredAppLocale())
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure Firebase is initialized before App Check, and guard the whole block.
        try {
            FirebaseApp.initializeApp(this)
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            } else {
                appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            }
        } catch (e: Exception) {
            // App Check / Firebase init failure must not crash the app on launch.
        }
        AndroidAppContext.init(this)
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@SaloApplication)
                modules(appModule, androidModule)
            }
        }
        NotificationChannels.createAll(this)
        val billing: SaloBillingClient by inject()
        billing.connect()
    }
}
