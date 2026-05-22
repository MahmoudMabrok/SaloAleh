package tools.mo3ta.salo.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIImage
import platform.UIKit.UIPasteboard
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import tools.mo3ta.salo.data.tendays.TenDaysStore
import tools.mo3ta.salo.ui.tendays.TenDaysPalette

actual fun launchQrScanner(onResult: (String?) -> Unit) {
    showPlatformToast("ماسح QR متاح على أندرويد فقط حاليًا")
    onResult(null)
}

actual fun showPlatformToast(message: String) {
    val alert = UIAlertController.alertControllerWithTitle(null, message, 0)
    alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault, null))
    UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(alert, true, null)
}

actual fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun shareText(text: String) {
    val vc = UIActivityViewController(listOf(text), null)
    UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(vc, true, null)
}

actual fun getStoreUrl(): String =
    "https://apps.apple.com/us/app/%D8%B5%D9%84%D9%88%D8%A7-%D8%B9%D9%84%D9%8A%D9%87/id6749495873"

actual fun canScheduleExactAlarms(): Boolean = true

private var iosNotifGranted: Boolean = true

actual fun areNotificationsEnabled(): Boolean {
    UNUserNotificationCenter.currentNotificationCenter()
        .getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            iosNotifGranted = status == UNAuthorizationStatusAuthorized ||
                status == UNAuthorizationStatusProvisional ||
                status == UNAuthorizationStatusEphemeral
        }
    return iosNotifGranted
}

actual fun getAppVersion(): String =
    platform.Foundation.NSBundle.mainBundle.infoDictionary
        ?.get("CFBundleShortVersionString") as? String ?: "—"

actual fun openNotificationSettings() {
    val url = NSURL(string = UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url)
}

actual fun requestExactAlarmPermission() = Unit

actual fun setAppLocale(languageTag: String) {
    platform.Foundation.NSUserDefaults.standardUserDefaults.setObject(listOf(languageTag), forKey = "AppleLanguages")
    platform.Foundation.NSUserDefaults.standardUserDefaults.synchronize()
    platform.posix.exit(0)
}

actual fun openStorePage() {
    val url = NSURL(string = getStoreUrl()) ?: return
    UIApplication.sharedApplication.openURL(url)
}

@OptIn(ExperimentalForeignApi::class)
actual fun shareBitmap(imageBitmap: ImageBitmap) {
    val pixelMap = imageBitmap.toPixelMap()
    val width = pixelMap.width
    val height = pixelMap.height
    val bytesPerRow = width * 4

    val rawBytes = ByteArray(height * bytesPerRow)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = pixelMap[x, y]
            val i = (y * width + x) * 4
            rawBytes[i] = (color.red * 255).toInt().and(0xFF).toByte()
            rawBytes[i + 1] = (color.green * 255).toInt().and(0xFF).toByte()
            rawBytes[i + 2] = (color.blue * 255).toInt().and(0xFF).toByte()
            rawBytes[i + 3] = (color.alpha * 255).toInt().and(0xFF).toByte()
        }
    }

    val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return
    val uiImage: UIImage = rawBytes.usePinned { pinned ->
        val cfData = CFDataCreate(
            allocator = kCFAllocatorDefault,
            bytes = pinned.addressOf(0).reinterpret(),
            length = rawBytes.size.toLong(),
        ) ?: return@usePinned null
        val provider = CGDataProviderCreateWithCFData(cfData) ?: return@usePinned null
        val cgImage = CGImageCreate(
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bitsPerPixel = 32u,
            bytesPerRow = bytesPerRow.toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            provider = provider,
            decode = null,
            shouldInterpolate = false,
            intent = CGColorRenderingIntent.kCGRenderingIntentDefault,
        ) ?: return@usePinned null
        UIImage.imageWithCGImage(cgImage)
    } ?: return

    val activityVC = UIActivityViewController(
        activityItems = listOf(uiImage),
        applicationActivities = null,
    )
    UIApplication.sharedApplication.keyWindow
        ?.rootViewController
        ?.presentViewController(activityVC, animated = true, completion = null)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@Composable
actual fun FloatingBubbleButton(roundKey: String?) {}

@Composable
actual fun BubbleFeaturePromo(roundKey: String?) {}

@Composable
actual fun TakbeerOverlayButton(autoRemind: Boolean, intervalMinutes: Int, repeatCount: Int) {
    val isActive by remember { IosTakbeerScheduler.isRunning }
    val label = if (isActive) "إيقاف التكبير" else "تشغيل التكبير"
    val containerColor = if (isActive) Color(0xFFE53935) else TenDaysPalette.Gold

    Button(
        onClick = {
            if (isActive) {
                IosTakbeerScheduler.stop()
            } else {
                IosTakbeerScheduler.start(autoRemind, intervalMinutes, repeatCount)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalForeignApi::class)
private object IosTakbeerScheduler : KoinComponent {
    private val store: TenDaysStore by inject()
    var isRunning = mutableStateOf(false)
    private var scope: CoroutineScope? = null
    private var player: AVAudioPlayer? = null

    private fun currentDay(): Int {
        val cairoTz = TimeZone.of("Africa/Cairo")
        val today = Clock.System.todayIn(cairoTz)
        val startDate = LocalDate.parse("2026-05-18")
        return (startDate.daysUntil(today) + 1).coerceIn(1, 9)
    }

    private fun configureAudioSession() {
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
        } catch (_: Exception) {}
    }

    private suspend fun playOnce() {
        val url = NSBundle.mainBundle.URLForResource("takbeer", withExtension = "mp3") ?: return
        val p = AVAudioPlayer(contentsOfURL = url, error = null)
        player = p
        p.play()
        while (p.isPlaying()) {
            delay(200)
        }
        store.incrementTakbeerSound(currentDay())
        player = null
    }

    fun start(autoRemind: Boolean, intervalMinutes: Int, repeatCount: Int) {
        stop()
        configureAudioSession()
        isRunning.value = true
        val intervalMs = intervalMinutes * 60 * 1000L
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch {
                do {
                    repeat(repeatCount) { playOnce() }
                    if (autoRemind) delay(intervalMs)
                } while (autoRemind && isRunning.value)
                stop()
            }
        }
    }

    fun stop() {
        player?.stop()
        player = null
        scope?.cancel()
        scope = null
        isRunning.value = false
    }
}

