package tools.mo3ta.salo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

@SuppressLint("StaticFieldLeak")
object AndroidAppContext {
    private lateinit var context: Context
    private var activityRef: WeakReference<Activity>? = null
    fun init(context: Context) { this.context = context.applicationContext }
    fun get(): Context = context
    fun setActivity(activity: Activity) { activityRef = WeakReference(activity) }
    fun getActivity(): Activity? = activityRef?.get()
}
