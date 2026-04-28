package tools.mo3ta.salo

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AndroidAppContext {
    private lateinit var context: Context
    fun init(context: Context) { this.context = context.applicationContext }
    fun get(): Context = context
}
