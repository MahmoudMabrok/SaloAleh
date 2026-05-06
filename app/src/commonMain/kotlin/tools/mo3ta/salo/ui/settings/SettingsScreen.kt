package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.notification.NotificationScheduler
import tools.mo3ta.salo.ui.areNotificationsEnabled
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.getAppVersion
import tools.mo3ta.salo.ui.openNotificationSettings
import tools.mo3ta.salo.ui.showPlatformToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store: NotificationSettingsStore = koinInject()
    val hadithStore: DailyHadithStore = koinInject()
    var dailyEnabled by remember { mutableStateOf(store.dailyEnabled) }
    var fridayEnabled by remember { mutableStateOf(store.fridayEnabled) }
    var hadithOnStartup by remember { mutableStateOf(hadithStore.showOnStartup) }
    val notifPermGranted = remember { areNotificationsEnabled() }

    val analyticsManager: AnalyticsManager = koinInject()
    LaunchedEffect(Unit){
        analyticsManager.logView("SettingsScreen")
    }

    Scaffold(
        containerColor = Color(0xFF0f0f1a),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات",
                        color = MohamedLoversPalette.GoldGlow,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MohamedLoversPalette.GoldGlow,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF16213e),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "الإشعارات",
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (!notifPermGranted) {
                SettingLinkRow(
                    label = "الإشعارات معطّلة — اضغط للتفعيل",
                    labelColor = Color(0xFFFF6B6B),
                    onClick = { openNotificationSettings() },
                )
            }

            SettingToggleRow(
                label = "تذكير يومي",
                subtitle = "مرة واحدة يوميًا",
                checked = dailyEnabled,
                onToggle = { checked ->
                    dailyEnabled = checked
                    store.dailyEnabled = checked
                    NotificationScheduler.apply(checked, store.fridayEnabled)
                },
            )

            SettingToggleRow(
                label = "إشعارات الجمعة",
                subtitle = "كل ساعة من 9ص – 5م",
                checked = fridayEnabled,
                onToggle = { checked ->
                    fridayEnabled = checked
                    store.fridayEnabled = checked
                    NotificationScheduler.apply(store.dailyEnabled, checked)
                },
            )

            Text(
                text = "المحتوى",
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            SettingToggleRow(
                label = "حديث عند الفتح",
                subtitle = "عرض حديث في فضل الصلاة على النبي ﷺ",
                checked = hadithOnStartup,
                onToggle = { checked ->
                    hadithOnStartup = checked
                    hadithStore.showOnStartup = checked
                },
            )

//            SettingLinkRow(
//                label = "اختبار الإشعار (10 ثوانٍ)",
//                onClick = {
//                    NotificationScheduler.scheduleTest(10.0)
//                    showPlatformToast("سيصلك إشعار بعد 10 ثوانٍ")
//                },
//            )

            Text(
                text = "عن التطبيق",
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            val uriHandler = LocalUriHandler.current
            SettingLinkRow(
                label = "سياسة الخصوصية",
                onClick = { uriHandler.openUri("https://mahmoudmabrok.github.io/MyDataCenter/policy/salo.html") },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "إصدار التطبيق",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = remember { getAppVersion() },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingLinkRow(
    label: String,
    onClick: () -> Unit,
    labelColor: Color = Color.White,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.padding(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
        )
    }
}
