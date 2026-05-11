package tools.mo3ta.salo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import tools.mo3ta.salo.AndroidAppContext

actual fun showPlatformToast(message: String) {
    Toast.makeText(AndroidAppContext.get(), message, Toast.LENGTH_SHORT).show()
}

actual fun copyToClipboard(text: String) {
    val cm = AndroidAppContext.get().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("winner_code", text))
}

actual fun shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    AndroidAppContext.get().startActivity(Intent.createChooser(intent, null).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
}

actual fun getStoreUrl(): String {
    val packageName = AndroidAppContext.get().packageName
    return "https://play.google.com/store/apps/details?id=$packageName"
}

actual fun canScheduleExactAlarms(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = AndroidAppContext.get().getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

actual fun areNotificationsEnabled(): Boolean =
    NotificationManagerCompat.from(AndroidAppContext.get()).areNotificationsEnabled()

actual fun getAppVersion(): String {
    val ctx = AndroidAppContext.get()
    return ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "—"
}

actual fun openNotificationSettings() {
    val ctx = AndroidAppContext.get()
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    ctx.startActivity(intent)
}

actual fun requestExactAlarmPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()) return
    val ctx = AndroidAppContext.get()
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${ctx.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { ctx.startActivity(intent) }
        .onFailure { openNotificationSettings() }
}

actual fun openStorePage() {
    val ctx = AndroidAppContext.get()
    val packageName = ctx.packageName
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName"),
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(getStoreUrl()),
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    runCatching { ctx.startActivity(marketIntent) }
        .onFailure { ctx.startActivity(webIntent) }
}

actual fun shareBitmap(imageBitmap: ImageBitmap) {
    val context = AndroidAppContext.get()
    val androidBitmap = imageBitmap.asAndroidBitmap()

    val shareDir = java.io.File(context.cacheDir, "share").also { it.mkdirs() }
    val file = java.io.File(shareDir, "share_card.png")
    file.outputStream().use { androidBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun FloatingBubbleButton(roundKey: String?) {
    // TODO: replaced in next task
}
