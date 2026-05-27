package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.BillingAnalytics
import tools.mo3ta.salo.data.billing.BillingManager
import tools.mo3ta.salo.data.billing.PremiumFeature
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.billing.ProductRegistry
import tools.mo3ta.salo.data.billing.SubscriptionPeriod
import tools.mo3ta.salo.data.billing.SupportTier
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

private val ScreenBg = Color(0xFF0f0f1a)
private val CardBg = Color(0xFF1a1a2e)
private val CardBorder = Color(0xFF333333)
private val VerseCardBg = Color(0xFF1a1a2e)
private val HadithCardBg = Color(0xFF16213e)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onBack: () -> Unit) {
    val billingManager: BillingManager = koinInject()
    val premiumStore: PremiumStore = koinInject()
    val analyticsManager: AnalyticsManager = koinInject()
    val repository: MohamedLoversRepository = koinInject()
    val scope = rememberCoroutineScope()

    val prices by billingManager.productPrices.collectAsState()

    val isPremium = premiumStore.hasFeature(PremiumFeature.SCORE_MASK)
    var scoreMasked by remember { mutableStateOf(premiumStore.isScoreMasked) }
    var selectedPeriod by remember { mutableStateOf(SubscriptionPeriod.MONTHLY) }

    val visibleTiers = remember(selectedPeriod) {
        ProductRegistry.subscriptionTiers.filter { it.period == selectedPeriod }
    }
    var selectedTier by remember(selectedPeriod) { mutableStateOf(visibleTiers.lastOrNull() ?: visibleTiers.first()) }

    LaunchedEffect(Unit) {
        analyticsManager.logAction(BillingAnalytics.PAYWALL_VIEWED)
    }

    Scaffold(
        containerColor = ScreenBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isPremium) stringResource(Res.string.paywall_supporter_settings_title) else stringResource(Res.string.paywall_support_app_title),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HadithCardBg),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Bismillah + header
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.paywall_bismillah),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isPremium) stringResource(Res.string.paywall_you_are_supporter) else stringResource(Res.string.paywall_spiritual_title),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isPremium) stringResource(Res.string.paywall_thanks_subtitle) else stringResource(Res.string.paywall_spiritual_subtitle),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            // Quranic verse card
            QuranVerseCard()
            Spacer(Modifier.height(12.dp))

            // Hadith card
            HadithCard()
            Spacer(Modifier.height(24.dp))

            // Spiritual rewards section
            Text(
                text = stringResource(Res.string.paywall_rewards_header),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(12.dp))

            RewardItem(
                icon = "🤲",
                title = stringResource(Res.string.paywall_reward_salawat_title),
                subtitle = stringResource(Res.string.paywall_reward_salawat_subtitle),
            )
            Spacer(Modifier.height(8.dp))
            RewardItem(
                icon = "📿",
                title = stringResource(Res.string.paywall_reward_sadaqa_title),
                subtitle = stringResource(Res.string.paywall_reward_sadaqa_subtitle),
            )
            Spacer(Modifier.height(8.dp))
            RewardItem(
                icon = "🕌",
                title = stringResource(Res.string.paywall_reward_spread_title),
                subtitle = stringResource(Res.string.paywall_reward_spread_subtitle),
            )
            Spacer(Modifier.height(28.dp))

            if (isPremium) {
                // Score mask toggle for existing supporters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.paywall_hide_score_title),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(Res.string.paywall_score_hidden_description),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = scoreMasked,
                        onCheckedChange = { checked ->
                            scoreMasked = checked
                            premiumStore.isScoreMasked = checked
                            scope.launch { repository.setScoreMasked(checked) }
                            analyticsManager.logAction(
                                BillingAnalytics.SCORE_MASK_TOGGLED,
                                mapOf(BillingAnalytics.PARAM_ENABLED to checked.toString()),
                            )
                        },
                    )
                }
            } else {
                // Tier selection
                Text(
                    text = stringResource(Res.string.paywall_choose_label),
                    color = MohamedLoversPalette.GoldGlow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(12.dp))

                // Basic tier card
                SpiritualTierCard(
                    emoji = "🌙",
                    tierName = stringResource(Res.string.paywall_basic_tier_label),
                    price = prices[ProductRegistry.SUB_MONTHLY_BASIC]
                        ?: ProductRegistry.subscriptionTiers.first().defaultPrice,
                    features = listOf(
                        "⭐" to stringResource(Res.string.paywall_supporter_badge_title),
                        "👁️" to stringResource(Res.string.paywall_friday_scores_title),
                        "🏆" to stringResource(Res.string.paywall_others_achievements_title),
                    ),
                    isSelected = selectedTier.features == ProductRegistry.subscriptionTiers.first().features,
                    onClick = {
                        selectedTier = visibleTiers.firstOrNull { !it.features.contains(PremiumFeature.SCORE_MASK) }
                            ?: visibleTiers.first()
                    },
                )
                Spacer(Modifier.height(10.dp))

                // Premium tier card
                SpiritualTierCard(
                    emoji = "⭐",
                    tierName = stringResource(Res.string.paywall_premium_tier_label),
                    price = prices[
                        if (selectedPeriod == SubscriptionPeriod.MONTHLY) ProductRegistry.SUB_MONTHLY_PREMIUM
                        else ProductRegistry.SUB_YEARLY_PREMIUM
                    ] ?: visibleTiers.last().defaultPrice,
                    features = listOf(
                        "📡" to stringResource(Res.string.paywall_live_leaderboard_title),
                        "🔒" to stringResource(Res.string.paywall_hide_score_title),
                        "⭐" to stringResource(Res.string.paywall_supporter_badge_title),
                        "👁️" to stringResource(Res.string.paywall_friday_scores_title),
                        "🏆" to stringResource(Res.string.paywall_others_achievements_title),
                    ),
                    isSelected = selectedTier.features.contains(PremiumFeature.SCORE_MASK),
                    onClick = {
                        selectedTier = visibleTiers.lastOrNull { it.features.contains(PremiumFeature.SCORE_MASK) }
                            ?: visibleTiers.last()
                    },
                )
                Spacer(Modifier.height(16.dp))

                PeriodToggle(
                    selected = selectedPeriod,
                    onSelect = { selectedPeriod = it },
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.paywall_recurring_note),
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                // CTA button
                val selectedPrice = prices[selectedTier.productId] ?: selectedTier.defaultPrice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))))
                        .clickable {
                            analyticsManager.logAction(
                                BillingAnalytics.SUBSCRIPTION_STARTED,
                                mapOf(
                                    BillingAnalytics.PARAM_PRODUCT_ID to selectedTier.productId,
                                    BillingAnalytics.PARAM_PERIOD to (selectedTier.period?.name ?: ""),
                                ),
                            )
                            billingManager.purchaseProduct(selectedTier.productId)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.paywall_support_now_price, selectedPrice),
                        color = ScreenBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))

                // Bottom sadaqa note
                Text(
                    text = stringResource(Res.string.paywall_sadaqa_bottom_note),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.paywall_restore_purchases),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        analyticsManager.logAction(BillingAnalytics.PURCHASE_RESTORED)
                        billingManager.restorePurchases()
                    },
                )
            }
        }
    }
}

@Composable
private fun QuranVerseCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VerseCardBg)
            .border(1.dp, MohamedLoversPalette.GoldGlow.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.paywall_quran_verse),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.paywall_quran_ref),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HadithCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HadithCardBg)
            .border(1.dp, MohamedLoversPalette.GoldGlow.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.paywall_hadith_text),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.paywall_hadith_ref),
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun RewardItem(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = icon, fontSize = 22.sp)
        Column {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SpiritualTierCard(
    emoji: String,
    tierName: String,
    price: String,
    features: List<Pair<String, String>>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MohamedLoversPalette.GoldGlow else CardBorder
    val bgColor = if (isSelected) CardBg else Color(0xFF13132a)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = emoji, fontSize = 24.sp)
            Text(
                text = tierName,
                color = if (isSelected) MohamedLoversPalette.GoldGlow else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = price,
                color = if (isSelected) MohamedLoversPalette.GoldGlow else Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        features.forEach { (icon, label) ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = icon, fontSize = 14.sp)
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PeriodToggle(
    selected: SubscriptionPeriod,
    onSelect: (SubscriptionPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PeriodTab(
            label = stringResource(Res.string.paywall_period_monthly),
            isSelected = selected == SubscriptionPeriod.MONTHLY,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(SubscriptionPeriod.MONTHLY) },
        )
        PeriodTab(
            label = stringResource(Res.string.paywall_period_yearly),
            isSelected = selected == SubscriptionPeriod.YEARLY,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(SubscriptionPeriod.YEARLY) },
        )
    }
}

@Composable
private fun PeriodTab(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MohamedLoversPalette.GoldGlow.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) MohamedLoversPalette.GoldGlow else Color.White.copy(alpha = 0.5f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
        )
    }
}
