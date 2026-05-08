package tools.mo3ta.salo

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.messaging.FirebaseMessaging
import tools.mo3ta.salo.notification.NotificationChannels

class SaloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
        AndroidAppContext.init(this)
        NotificationChannels.createAll(this)
        FirebaseMessaging.getInstance().subscribeToTopic("general")
    }
}
