package tools.mo3ta.salo

import android.app.Application

class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.init(this)
        val clazz = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        val companion = clazz.getDeclaredField("Companion").get(null)
        val setter = companion.javaClass.getDeclaredMethod("setANDROID_CONTEXT", android.content.Context::class.java)
        setter.isAccessible = true
        setter.invoke(companion, this)
    }
}
