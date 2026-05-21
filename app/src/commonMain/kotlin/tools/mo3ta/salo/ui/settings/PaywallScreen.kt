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
import tools.mo3ta.salo.data.billing.SupportTier
import tools.mo3ta.salo.domain.MohamedLoversRepository
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onBack: () -> Unit) {
    val billingManager: BillingManager = koinInject()
    val premiumStore: PremiumStore = koinInject()
    val analyticsManager: AnalyticsManager = koinInject()
    val repository: MohamedLoversRepository = koinInject()
    val scope = rememberCoroutineScope()

    val isPremium = premiumStore.hasFeature(PremiumFeature.SCORE_MASK)
    var scoreMasked by remember { mutableStateOf(premiumStore.isScoreMasked) }
    var selectedTier by remember { mutableStateOf(ProductRegistry.tiers[1]) }

    LaunchedEffect(Unit) {
        analyticsManager.logAction(BillingAnalytics.PAYWALL_VIEWED)
    }

    Scaffold(
        containerColor = Color(0xFF0f0f1a),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16213e)),
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
            Spacer(Modifier.height(16.dp))
            Text(text = "🌟", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isPremium) stringResource(Res.string.paywall_you_are_supporter) else stringResource(Res.string.paywall_support_app_title),
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isPremium) stringResource(Res.string.paywall_thanks_subtitle) else stringResource(Res.string.paywall_contribute_subtitle),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(28.dp))

            FeatureCard(icon = "🔒", title = stringResource(Res.string.paywall_hide_score_title), subtitle = stringResource(Res.string.paywall_hide_score_subtitle))
            Spacer(Modifier.height(10.dp))
            FeatureCard(icon = "⭐", title = stringResource(Res.string.paywall_supporter_badge_title), subtitle = stringResource(Res.string.paywall_supporter_badge_subtitle))
            Spacer(Modifier.height(28.dp))

            if (isPremium) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1a1a2e))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
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
                ProductRegistry.tiers.forEach { tier ->
                    SupportTierCard(
                        tier = tier,
                        price = billingManager.getProductPrice(tier.productId) ?: tier.defaultPrice,
                        isSelected = selectedTier == tier,
                        onClick = { selectedTier = tier },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "كلما زاد دعمك، زاد أجرك في الآخرة بإذن الله 🤲",
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                val tierPrice = billingManager.getProductPrice(selectedTier.productId) ?: selectedTier.defaultPrice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))))
                        .clickable {
                            analyticsManager.logAction(
                                BillingAnalytics.PURCHASE_STARTED,
                                mapOf(BillingAnalytics.PARAM_PRODUCT_ID to selectedTier.productId),
                            )
                            billingManager.purchaseProduct(selectedTier.productId)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.paywall_support_now_price, tierPrice),
                        color = Color(0xFF0f0f1a),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
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
private fun SupportTierCard(
    tier: SupportTier,
    price: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MohamedLoversPalette.GoldGlow else Color(0xFF333333)
    val bgColor = if (isSelected) Color(0xFF1a1a2e) else Color(0xFF13132a)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = tier.emoji, fontSize = 24.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tier.label,
                color = if (isSelected) MohamedLoversPalette.GoldGlow else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            if (tier.features.contains(PremiumFeature.SCORE_MASK)) {
                Text(
                    text = stringResource(Res.string.paywall_hide_score_plus_badge),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            } else {
                Text(
                    text = stringResource(Res.string.paywall_supporter_badge_title),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
        }
        Text(
            text = price,
            color = if (isSelected) MohamedLoversPalette.GoldGlow else Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun FeatureCard(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1a1a2e))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = icon, fontSize = 24.sp)
        Column {
            Text(
                text = title,
                color = MohamedLoversPalette.GoldGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
    }
}
