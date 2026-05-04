package tools.mo3ta.salo.notification

import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithName
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

actual object NotificationScheduler {

    private const val TAG = "[NotifScheduler]"
    private const val ID_DAILY = "notif_daily"
    private val FRIDAY_IDS = (9..17).map { "notif_friday_$it" }

    private val foregroundDelegate = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
        ) {
            println("$TAG willPresentNotification fired — id=${willPresentNotification.request.identifier}")
            withCompletionHandler(
                UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound,
            )
        }
    }

    actual fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean) {
        println("$TAG apply() called — daily=$dailyEnabled friday=$fridayEnabled")
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.delegate = foregroundDelegate
        println("$TAG delegate set")

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
            center.addNotificationRequest(request) { error ->
                if (error != null) println("$TAG daily add error: ${error.localizedDescription}")
                else println("$TAG daily notification scheduled OK (9 AM Cairo)")
            }
        } else {
            center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_DAILY))
            println("$TAG daily notification removed")
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
                center.addNotificationRequest(request) { error ->
                    if (error != null) println("$TAG friday $hour add error: ${error.localizedDescription}")
                    else println("$TAG friday $hour notification scheduled OK")
                }
            }
        } else {
            center.removePendingNotificationRequestsWithIdentifiers(FRIDAY_IDS)
            println("$TAG friday notifications removed")
        }
    }

    actual fun scheduleTest(afterSeconds: Double) {
        println("$TAG scheduleTest() called — afterSeconds=$afterSeconds")
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.delegate = foregroundDelegate

        // First check current authorization status
        center.getNotificationSettingsWithCompletionHandler { settings ->
            println("$TAG auth status = ${settings?.authorizationStatus}")
        }

        val content = UNMutableNotificationContent().apply {
            setTitle("اللهم صلِّ على محمد ﷺ")
            setBody("تذكيرك اليومي — اضغط لتشارك الصلاة على النبي")
            setSound(UNNotificationSound.defaultSound())
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(afterSeconds, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier("notif_test", content, trigger)
        center.addNotificationRequest(request) { error ->
            if (error != null) println("$TAG test add error: ${error.localizedDescription}")
            else println("$TAG test notification scheduled — fires in ${afterSeconds}s")
        }
    }
}
