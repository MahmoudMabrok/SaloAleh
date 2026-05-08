package tools.mo3ta.salo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationScheduler.onExactAlarmTriggered(context, intent.action)
    }
}
