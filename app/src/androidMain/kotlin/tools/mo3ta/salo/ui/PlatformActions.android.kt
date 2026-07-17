package tools.mo3ta.salo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import android.app.AlarmManager
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.challenge_bubble_start_cd
import tools.mo3ta.salo.generated.resources.challenge_bubble_stop_cd
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tools.mo3ta.salo.ui.tendays.TenDaysPalette
import androidx.core.content.FileProvider
import tools.mo3ta.salo.AndroidAppContext
import tools.mo3ta.salo.notification.ProtectionNotificationService
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

actual fun launchQrScanner(onResult: (String?) -> Unit) {
    val ctx = AndroidAppContext.get()
    val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
        .build()
    com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx, options)
        .startScan()
        .addOnSuccessListener { barcode -> onResult(barcode.rawValue) }
        .addOnFailureListener { onResult(null) }
        .addOnCanceledListener { onResult(null) }
}

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

actual fun startProtectionNotificationService() {
    val ctx = AndroidAppContext.get()
    ContextCompat.startForegroundService(
        ctx,
        Intent(ctx, ProtectionNotificationService::class.java),
    )
}

actual fun setLeaderboardTopicSubscription(enabled: Boolean) {
    val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
    if (enabled) messaging.subscribeToTopic("leaderboard_notifs")
    else messaging.unsubscribeFromTopic("leaderboard_notifs")
}

actual fun setAppLocale(languageTag: String) {
    val ctx = AndroidAppContext.get()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val localeManager = ctx.getSystemService(android.app.LocaleManager::class.java)
        localeManager.applicationLocales = android.os.LocaleList.forLanguageTags(languageTag)
    } else {
        AndroidAppContext.getActivity()?.recreate()
    }
}

actual fun shareBitmap(imageBitmap: ImageBitmap) {
    val context = AndroidAppContext.get()
    val androidBitmap = imageBitmap.asAndroidBitmap()
    GlobalScope.launch(Dispatchers.IO) {
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
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun FloatingBubbleButton(roundKey: String?) {
    val context = LocalContext.current
    val activeType by FloatingBubbleService.activeType.collectAsState()
    val isActive = activeType == FloatingBubbleService.BubbleType.SALAWAT.id
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        OverlayPermissionRationaleDialog(
            onAllow = {
                showPermissionDialog = false
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    val analyticsManager: tools.mo3ta.salo.analytics.AnalyticsManager = org.koin.compose.koinInject()

    val onClick: () -> Unit = {
        if (isActive) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
            analyticsManager.logAction("bubble_deactivate")
        } else if (!Settings.canDrawOverlays(context)) {
            showPermissionDialog = true
        } else if (roundKey.isNullOrBlank()) {
            Toast.makeText(context, "لا يوجد جولة نشطة", Toast.LENGTH_SHORT).show()
        } else {
            analyticsManager.logAction("bubble_activate")
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingBubbleService::class.java)
                    .putExtra(FloatingBubbleService.EXTRA_ROUND_KEY, roundKey)
                    .putExtra(
                        FloatingBubbleService.EXTRA_BUBBLE_TYPE,
                        FloatingBubbleService.BubbleType.SALAWAT.id,
                    )
            )
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val orbColor = if (isActive) Color(0xFF4CAF50) else MohamedLoversPalette.GoldHighlight
    val label = if (isActive) "إيقاف" else "فقاعة الصلوات"

    Box(contentAlignment = Alignment.Center) {
        if (!isActive) {
            Canvas(
                modifier = Modifier
                    .size(120.dp, 44.dp)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale; alpha = glowAlpha },
            ) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MohamedLoversPalette.GoldBase.copy(alpha = 0.5f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.maxDimension / 1.5f,
                    ),
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = if (isActive)
                Color(0xFF4CAF50).copy(alpha = 0.18f)
            else
                MohamedLoversPalette.GoldHighlight.copy(alpha = 0.12f),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    if (isActive) {
                        drawRoundRect(
                            color = Color(0xFFE53935),
                            topLeft = Offset(size.width * 0.25f, size.height * 0.25f),
                            size = Size(size.width * 0.5f, size.height * 0.5f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                    } else {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MohamedLoversPalette.GoldHighlight,
                                    MohamedLoversPalette.GoldBase.copy(alpha = 0.6f),
                                ),
                                center = Offset(size.width * 0.4f, size.height * 0.38f),
                                radius = size.minDimension / 2,
                            ),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f),
                            radius = size.minDimension * 0.15f,
                            center = Offset(size.width * 0.35f, size.height * 0.32f),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    color = orbColor.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
actual fun BubbleFeaturePromo(roundKey: String?) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("ml_session", android.content.Context.MODE_PRIVATE)
    }
    var showPromo by remember {
        mutableStateOf(!prefs.getBoolean("bubble_promo_shown", false))
    }

    if (showPromo && !roundKey.isNullOrBlank()) {
        BubbleFeaturePromoDialog(
            onTryIt = {
                prefs.edit().putBoolean("bubble_promo_shown", true).apply()
                showPromo = false
                if (!Settings.canDrawOverlays(context)) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } else {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, FloatingBubbleService::class.java)
                            .putExtra(FloatingBubbleService.EXTRA_ROUND_KEY, roundKey)
                    )
                    (context as? Activity)?.moveTaskToBack(true)
                }
            },
            onDismiss = {
                prefs.edit().putBoolean("bubble_promo_shown", true).apply()
                showPromo = false
            },
        )
    }
}

@Composable
actual fun ChallengeBubbleButton(challengeType: String) {
    val context = LocalContext.current
    val activeType by FloatingBubbleService.activeType.collectAsState()
    val isActive = activeType == challengeType
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        OverlayPermissionRationaleDialog(
            onAllow = {
                showPermissionDialog = false
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    val analyticsManager: tools.mo3ta.salo.analytics.AnalyticsManager = org.koin.compose.koinInject()

    val accent = when (challengeType) {
        FloatingBubbleService.BubbleType.DHIKR.id -> Color(0xFF6FCF9E)
        FloatingBubbleService.BubbleType.ISTIGHFAR.id -> Color(0xFFC08A3E)
        FloatingBubbleService.BubbleType.ZABAD.id -> Color(0xFF2ED3C4)
        FloatingBubbleService.BubbleType.GHARS.id -> Color(0xFFC4762A)
        else -> MohamedLoversPalette.GoldHighlight
    }

    val onClick: () -> Unit = {
        if (isActive) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
            analyticsManager.logAction("bubble_deactivate", mapOf("type" to challengeType))
        } else if (!Settings.canDrawOverlays(context)) {
            showPermissionDialog = true
        } else {
            analyticsManager.logAction("bubble_activate", mapOf("type" to challengeType))
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingBubbleService::class.java)
                    .putExtra(FloatingBubbleService.EXTRA_BUBBLE_TYPE, challengeType)
            )
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val contentDesc = if (isActive) {
        stringResource(Res.string.challenge_bubble_stop_cd)
    } else {
        stringResource(Res.string.challenge_bubble_start_cd)
    }

    Box(contentAlignment = Alignment.Center) {
        if (!isActive) {
            Canvas(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { alpha = glowAlpha },
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.6f), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 1.6f,
                    ),
                )
            }
        }

        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = if (isActive) Color(0xFFE53935).copy(alpha = 0.18f) else accent.copy(alpha = 0.14f),
            modifier = Modifier
                .size(40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(18.dp).semantics { contentDescription = contentDesc }) {
                    if (isActive) {
                        drawRoundRect(
                            color = Color(0xFFE53935),
                            topLeft = Offset(size.width * 0.25f, size.height * 0.25f),
                            size = Size(size.width * 0.5f, size.height * 0.5f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                    } else {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accent, accent.copy(alpha = 0.55f)),
                                center = Offset(size.width * 0.4f, size.height * 0.38f),
                                radius = size.minDimension / 2,
                            ),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f),
                            radius = size.minDimension * 0.15f,
                            center = Offset(size.width * 0.35f, size.height * 0.32f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
actual fun TakbeerOverlayButton(autoRemind: Boolean, intervalMinutes: Int, repeatCount: Int) {
    val context = LocalContext.current
    val isActive by TakbeerOverlayService.isRunning.collectAsState()
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        OverlayPermissionRationaleDialog(
            onAllow = {
                showPermissionDialog = false
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    val label = if (isActive) "إيقاف التكبير" else "تشغيل التكبير"
    val containerColor = if (isActive) Color(0xFFE53935) else TenDaysPalette.Gold

    Button(
        onClick = {
            if (isActive) {
                context.stopService(Intent(context, TakbeerOverlayService::class.java))
            } else if (!Settings.canDrawOverlays(context)) {
                showPermissionDialog = true
            } else {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TakbeerOverlayService::class.java)
                        .putExtra(TakbeerOverlayService.EXTRA_AUTO_REMIND, autoRemind)
                        .putExtra(TakbeerOverlayService.EXTRA_INTERVAL_MINUTES, intervalMinutes)
                        .putExtra(TakbeerOverlayService.EXTRA_REPEAT_COUNT, repeatCount)
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}
