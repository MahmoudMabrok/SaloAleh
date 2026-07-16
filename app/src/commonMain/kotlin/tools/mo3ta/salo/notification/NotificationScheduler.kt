package tools.mo3ta.salo.notification

expect object NotificationScheduler {
    fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean, eveningEnabled: Boolean)
    fun scheduleTest(afterSeconds: Double)
}
