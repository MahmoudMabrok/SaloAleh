package tools.mo3ta.salo.notification

import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

actual object NotificationScheduler {

    private const val ID_DAILY = "notif_daily"
    private val FRIDAY_IDS = (9..17).map { "notif_friday_$it" }

    actual fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean) {
        val center = UNUserNotificationCenter.currentNotificationCenter()

        if (dailyEnabled) {
            val content = UNMutableNotificationContent().apply {
                setTitle("اللهم صلِّ على محمد ﷺ")
                setBody("تذكيرك اليومي — اضغط لتشارك الصلاة على النبي")
                setSound(UNNotificationSound.defaultSound())
            }
            val components = NSDateComponents().apply {
                hour = 9L
                minute = 0L
                timeZone = NSTimeZone.timeZoneWithName("Africa/Cairo")
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
            val request = UNNotificationRequest.requestWithIdentifier(ID_DAILY, content, trigger)
            center.addNotificationRequest(request) { _ -> }
        } else {
            center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_DAILY))
        }

        if (fridayEnabled) {
            (9..17).forEach { hour ->
                val content = UNMutableNotificationContent().apply {
                    setTitle("اللهم صلِّ على محمد ﷺ")
                    setBody("يوم الجمعة المبارك — صلّ على النبي الكريم")
                    setSound(UNNotificationSound.defaultSound())
                }
                val components = NSDateComponents().apply {
                    weekday = 6L
                    this.hour = hour.toLong()
                    minute = 0L
                    timeZone = NSTimeZone.timeZoneWithName("Africa/Cairo")
                }
                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
                val request = UNNotificationRequest.requestWithIdentifier("notif_friday_$hour", content, trigger)
                center.addNotificationRequest(request) { _ -> }
            }
        } else {
            center.removePendingNotificationRequestsWithIdentifiers(FRIDAY_IDS)
        }
    }
}
