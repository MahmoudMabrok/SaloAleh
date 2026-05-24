package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.data.billing.BillingManager
import tools.mo3ta.salo.data.language.LanguageStore
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.billing.SupportTier
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.notification.NotificationScheduler
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.ui.areNotificationsEnabled
import tools.mo3ta.salo.ui.canScheduleExactAlarms
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.getAppVersion
import tools.mo3ta.salo.ui.getStoreUrl
import tools.mo3ta.salo.ui.openNotificationSettings
import tools.mo3ta.salo.ui.openStorePage
import tools.mo3ta.salo.ui.requestExactAlarmPermission
import tools.mo3ta.salo.ui.setAppLocale
import tools.mo3ta.salo.ui.shareText
import tools.mo3ta.salo.ui.showPlatformToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenOnboarding: () -> Unit = {}, onOpenExtensionQr: () -> Unit = {}, onOpenPaywall: () -> Unit = {}) {
    val store: NotificationSettingsStore = koinInject()
    val hadithStore: DailyHadithStore = koinInject()
    val viewModel: MohamedLoversViewModel = koinViewModel()
    var dailyEnabled by remember { mutableStateOf(store.dailyEnabled) }
    var fridayEnabled by remember { mutableStateOf(store.fridayEnabled) }
    var hadithOnStartup by remember { mutableStateOf(hadithStore.showOnStartup) }
    var showRankChip by remember { mutableStateOf(store.showRankChip) }

    var notifPermGranted by remember { mutableStateOf(areNotificationsEnabled()) }
    var exactAlarmGranted by remember { mutableStateOf(canScheduleExactAlarms()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val languageStore: LanguageStore = koinInject()
    var selectedLanguage by remember { mutableStateOf(languageStore.language) }
    val analyticsManager: AnalyticsManager = koinInject()
    val billingManager: BillingManager = koinInject()
    val premiumStore: PremiumStore = koinInject()
    val supporterTier = premiumStore.highestTier
    val enableNotifToast = stringResource(Res.string.settings_enable_notifications_toast)
    val enableExactAlarmToast = stringResource(Res.string.settings_enable_exact_alarm_toast)

    LaunchedEffect(Unit){
        analyticsManager.logView("SettingsScreen")
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifPermGranted = areNotificationsEnabled()
                exactAlarmGranted = canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color(0xFF0f0f1a),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        color = MohamedLoversPalette.GoldGlow,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.mohamed_lovers_back_cd),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_language_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            LanguageChipRow(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { lang ->
                    if (lang != selectedLanguage) {
                        selectedLanguage = lang
                        languageStore.language = lang
                        analyticsManager.logAction(AppAnalytics.LANGUAGE_CHANGED, mapOf(AppAnalytics.PARAM_LANG to lang))
                        setAppLocale(lang)
                    }
                },
            )

            Text(
                text = stringResource(Res.string.settings_notifications_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (!notifPermGranted) {
                SettingLinkRow(
                    label = stringResource(Res.string.settings_notifications_disabled),
                    labelColor = Color(0xFFFF6B6B),
                    onClick = { openNotificationSettings() },
                )
            }

            if (!exactAlarmGranted) {
                SettingLinkRow(
                    label = stringResource(Res.string.settings_exact_alarm_disabled),
                    labelColor = Color(0xFFFFC857),
                    onClick = { requestExactAlarmPermission() },
                )
            }

            SettingToggleRow(
                label = stringResource(Res.string.settings_daily_reminder),
                subtitle = stringResource(Res.string.settings_daily_reminder_subtitle),
                checked = dailyEnabled,
                onToggle = { checked ->
                    if (checked && !notifPermGranted) {
                        showPlatformToast(enableNotifToast)
                        openNotificationSettings()
                    } else {
                        dailyEnabled = checked
                        store.dailyEnabled = checked
                        NotificationScheduler.apply(checked, store.fridayEnabled)
                        if (checked && !exactAlarmGranted) {
                            showPlatformToast(enableExactAlarmToast)
                            requestExactAlarmPermission()
                        }
                    }
                },
            )

            SettingToggleRow(
                label = stringResource(Res.string.settings_friday_notifications),
                subtitle = stringResource(Res.string.settings_friday_notifications_subtitle),
                checked = fridayEnabled,
                onToggle = { checked ->
                    if (checked && !notifPermGranted) {
                        showPlatformToast(enableNotifToast)
                        openNotificationSettings()
                    } else {
                        fridayEnabled = checked
                        store.fridayEnabled = checked
                        NotificationScheduler.apply(store.dailyEnabled, checked)
                        if (checked && !exactAlarmGranted) {
                            showPlatformToast(enableExactAlarmToast)
                            requestExactAlarmPermission()
                        }
                    }
                },
            )

            Text(
                text = stringResource(Res.string.settings_content_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            SettingToggleRow(
                label = stringResource(Res.string.settings_hadith_on_open),
                subtitle = stringResource(Res.string.settings_hadith_on_open_subtitle),
                checked = hadithOnStartup,
                onToggle = { checked ->
                    hadithOnStartup = checked
                    hadithStore.showOnStartup = checked
                },
            )

            SettingToggleRow(
                label = stringResource(Res.string.settings_show_rank),
                subtitle = stringResource(Res.string.settings_show_rank_subtitle),
                checked = showRankChip,
                onToggle = { checked ->
                    showRankChip = checked
                    store.showRankChip = checked
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
                text = stringResource(Res.string.settings_sync_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            val uriHandler = LocalUriHandler.current

            SettingLinkRow(
                label = stringResource(Res.string.settings_sync_from_extension),
                onClick = onOpenExtensionQr,
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_download_chrome_extension),
                onClick = { uriHandler.openUri("https://mahmoudmabrok.github.io/SaloAleh/landing.html#extension") },
            )

            if (billingManager.isEnabled) {
                Text(
                    text = stringResource(Res.string.settings_support_header),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                if (supporterTier != null) {
                    SupporterStatusCard(tier = supporterTier, onClick = onOpenPaywall)
                } else {
                    SettingLinkRow(
                        label = stringResource(Res.string.settings_support_app),
                        labelColor = MohamedLoversPalette.GoldGlow,
                        onClick = onOpenPaywall,
                    )
                }
            }

            Text(
                text = stringResource(Res.string.settings_about_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_app_guide),
                onClick = onOpenOnboarding,
            )
            SettingLinkRow(
                label = stringResource(Res.string.settings_privacy_policy),
                onClick = { uriHandler.openUri("https://mahmoudmabrok.github.io/MyDataCenter/policy/salo.html") },
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_share_app),
                onClick = { shareText(getStoreUrl()) },
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_open_store_page),
                onClick = { openStorePage() },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.settings_app_version),
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
private fun SupporterStatusCard(tier: SupportTier, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A2E))
            .border(1.dp, MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = tier.emoji, fontSize = 24.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_supporter_status, tier.label),
                color = MohamedLoversPalette.GoldGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = stringResource(Res.string.settings_manage_features),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }
        Text(
            text = "✅",
            fontSize = 18.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LanguageChipRow(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
) {
    val languages = listOf(
        "ar" to stringResource(Res.string.settings_language_ar),
        "en" to stringResource(Res.string.settings_language_en),
        "ur" to stringResource(Res.string.settings_language_ur),
        "zh" to stringResource(Res.string.settings_language_zh),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        languages.forEach { (tag, label) ->
            FilterChip(
                selected = selectedLanguage == tag,
                onClick = { onLanguageSelected(tag) },
                label = { Text(label, fontSize = 14.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MohamedLoversPalette.GoldGlow.copy(alpha = 0.2f),
                    selectedLabelColor = MohamedLoversPalette.GoldGlow,
                    labelColor = Color.White.copy(alpha = 0.7f),
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = Color.White.copy(alpha = 0.2f),
                    selectedBorderColor = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
                    enabled = true,
                    selected = selectedLanguage == tag,
                ),
            )
        }
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
