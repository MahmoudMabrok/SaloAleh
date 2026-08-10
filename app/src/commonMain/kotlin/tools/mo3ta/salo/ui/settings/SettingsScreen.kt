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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
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
import tools.mo3ta.salo.data.salawat.SalawatVariantStore
import tools.mo3ta.salo.data.salawat.SalawatVariants
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.billing.SupportTier
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
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
fun SettingsScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onOpenOnboarding: () -> Unit = {},
    onOpenExtensionQr: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    onOpenReferral: () -> Unit = {},
    onOpenVoiceDhikr: () -> Unit = {},
    onOpenDhikrModelTest: () -> Unit = {},
    onOpenAccountBackup: () -> Unit = {},
) {
    val store: NotificationSettingsStore = koinInject()
    val hadithStore: DailyHadithStore = koinInject()
    val sessionStore: MohamedLoversSessionStore = koinInject()
    val viewModel: MohamedLoversViewModel = koinViewModel()
    var dailyEnabled by remember { mutableStateOf(store.dailyEnabled) }
    var fridayEnabled by remember { mutableStateOf(store.fridayEnabled) }
    var eveningEnabled by remember { mutableStateOf(store.eveningEnabled) }
    var hadithOnStartup by remember { mutableStateOf(hadithStore.showOnStartup) }
    var showRankChip by remember { mutableStateOf(store.showRankChip) }
    var serverRemindersEnabled by remember { mutableStateOf(store.serverRemindersEnabled) }
    var leaderboardNotifsEnabled by remember { mutableStateOf(store.leaderboardNotifsEnabled) }
    var nicknameText by remember { mutableStateOf(sessionStore.getNickname().orEmpty()) }
    var nicknameEnabled by remember { mutableStateOf(sessionStore.isNicknameEnabled) }

    var notifPermGranted by remember { mutableStateOf(areNotificationsEnabled()) }
    var exactAlarmGranted by remember { mutableStateOf(canScheduleExactAlarms()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val languageStore: LanguageStore = koinInject()
    var selectedLanguage by remember { mutableStateOf(languageStore.language) }
    val salawatVariantStore: SalawatVariantStore = koinInject()
    var selectedSalawatVariant by remember { mutableStateOf(salawatVariantStore.variantIndex) }
    var showSalawatVariantSheet by remember { mutableStateOf(false) }
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
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.mohamed_lovers_back_cd),
                                tint = MohamedLoversPalette.GoldGlow,
                            )
                        }
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
            val uriHandler = LocalUriHandler.current

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
                text = stringResource(Res.string.settings_referral_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_invite_friends),
                labelColor = MohamedLoversPalette.GoldGlow,
                onClick = onOpenReferral,
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_contact_us),
                onClick = { uriHandler.openUri("mailto:mahmoudmabrok3579@gmail.com") },
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_open_store_page),
                onClick = { openStorePage() },
            )

            Text(
                text = stringResource(Res.string.settings_voice_dhikr_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_voice_dhikr_link),
                labelColor = MohamedLoversPalette.GoldGlow,
                onClick = {
                    analyticsManager.logAction(AppAnalytics.OPEN_VOICE_DHIKR)
                    onOpenVoiceDhikr()
                },
            )

            if (dhikrModelTestAvailable) {
                SettingLinkRow(
                    label = stringResource(Res.string.settings_dhikr_model_test),
                    labelColor = MohamedLoversPalette.GoldGlow,
                    onClick = onOpenDhikrModelTest,
                )
            }

            Text(
                text = stringResource(Res.string.settings_account_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            SettingLinkRow(
                label = stringResource(Res.string.settings_account_backup),
                labelColor = MohamedLoversPalette.GoldGlow,
                onClick = {
                    analyticsManager.logAction(AppAnalytics.OPEN_ACCOUNT_BACKUP)
                    onOpenAccountBackup()
                },
            )

            Text(
                text = stringResource(Res.string.settings_notifications_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
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
                        NotificationScheduler.apply(checked, store.fridayEnabled, store.eveningEnabled)
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
                        NotificationScheduler.apply(store.dailyEnabled, checked, store.eveningEnabled)
                        if (checked && !exactAlarmGranted) {
                            showPlatformToast(enableExactAlarmToast)
                            requestExactAlarmPermission()
                        }
                    }
                },
            )

            SettingToggleRow(
                label = stringResource(Res.string.settings_evening_reminder),
                subtitle = stringResource(Res.string.settings_evening_reminder_subtitle),
                checked = eveningEnabled,
                onToggle = { checked ->
                    if (checked && !notifPermGranted) {
                        showPlatformToast(enableNotifToast)
                        openNotificationSettings()
                    } else {
                        eveningEnabled = checked
                        store.eveningEnabled = checked
                        NotificationScheduler.apply(store.dailyEnabled, store.fridayEnabled, checked)
                        if (checked && !exactAlarmGranted) {
                            showPlatformToast(enableExactAlarmToast)
                            requestExactAlarmPermission()
                        }
                    }
                },
            )

            SettingToggleRow(
                label = stringResource(Res.string.settings_server_reminders),
                subtitle = stringResource(Res.string.settings_server_reminders_subtitle),
                checked = serverRemindersEnabled,
                onToggle = { checked ->
                    serverRemindersEnabled = checked
                    viewModel.setServerRemindersEnabled(checked)
                },
            )

            SettingToggleRow(
                label = stringResource(Res.string.settings_leaderboard_notifs),
                subtitle = stringResource(Res.string.settings_leaderboard_notifs_subtitle),
                checked = leaderboardNotifsEnabled,
                onToggle = { checked ->
                    leaderboardNotifsEnabled = checked
                    viewModel.setLeaderboardNotifsEnabled(checked)
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

            Text(
                text = stringResource(Res.string.settings_nickname_header),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )

            OutlinedTextField(
                value = nicknameText,
                onValueChange = { value ->
                    if (value.length <= 20) {
                        nicknameText = value
                        viewModel.updateNicknameLocal(value.ifBlank { null })
                    }
                },
                label = { Text(stringResource(Res.string.settings_nickname_label)) },
                placeholder = { Text(stringResource(Res.string.settings_nickname_placeholder)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MohamedLoversPalette.GoldGlow,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = MohamedLoversPalette.GoldGlow,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = MohamedLoversPalette.GoldGlow,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                    if (!state.isFocused) viewModel.commitNickname()
                },
            )

            Spacer(Modifier.height(8.dp))

            SettingToggleRow(
                label = stringResource(Res.string.settings_nickname_show),
                subtitle = stringResource(Res.string.settings_nickname_show_subtitle),
                checked = nicknameEnabled,
                onToggle = { checked ->
                    nicknameEnabled = checked
                    viewModel.setNicknameEnabled(checked)
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

            SalawatVariantButton(
                selectedIndex = selectedSalawatVariant,
                onClick = { showSalawatVariantSheet = true },
            )

            if (showSalawatVariantSheet) {
                SalawatVariantSelectorSheet(
                    selectedIndex = selectedSalawatVariant,
                    onDismiss = { showSalawatVariantSheet = false },
                    onSelected = { index ->
                        if (index != selectedSalawatVariant) {
                            selectedSalawatVariant = index
                            salawatVariantStore.variantIndex = index
                            analyticsManager.logAction(
                                AppAnalytics.SALAWAT_VARIANT_CHANGED,
                                mapOf(AppAnalytics.PARAM_VARIANT to (index + 1).toString()),
                            )
                        }
                        showSalawatVariantSheet = false
                    },
                )
            }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalawatVariantButton(
    selectedIndex: Int,
    onClick: () -> Unit,
) {
    val safeSelectedIndex = selectedIndex.coerceIn(0, SalawatVariants.COUNT - 1)
    val selectedText = stringResource(SalawatVariants.textResIds[safeSelectedIndex])

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_salawat_variant_header),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = selectedText,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MohamedLoversPalette.GoldGlow.copy(alpha = 0.8f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalawatVariantSelectorSheet(
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val safeSelectedIndex = selectedIndex.coerceIn(0, SalawatVariants.COUNT - 1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF16213e),
        contentColor = Color.White,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_salawat_variant_header),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            SalawatVariants.textResIds.forEachIndexed { index, resId ->
                val selected = index == safeSelectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MohamedLoversPalette.GoldGlow.copy(alpha = 0.12f)
                            else Color.White.copy(alpha = 0.04f),
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelected(index) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MohamedLoversPalette.GoldGlow,
                            unselectedColor = Color.White.copy(alpha = 0.5f),
                        ),
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = stringResource(resId),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
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
