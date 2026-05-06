package tools.mo3ta.salo

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
// For Play Integrity - imported conditionally at build time for release builds

class SaloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.init(this)
        val appCheck = FirebaseAppCheck.getInstance()
        val isDebugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebugBuild) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            // For release builds, use Play Integrity
            try {
                val playIntegrityFactoryClass = Class.forName("com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory")
                val getInstanceMethod = playIntegrityFactoryClass.getMethod("getInstance")
                val factory = getInstanceMethod.invoke(null)
                appCheck.installAppCheckProviderFactory(factory as com.google.firebase.appcheck.AppCheckProviderFactory)
            } catch (e: Exception) {
                // Fallback if Play Integrity is not available
                appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            }
        }
    }
}
