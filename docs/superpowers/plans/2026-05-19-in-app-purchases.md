# In-App Purchases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-time $1.99 "Support the App" purchase with score masking and supporter badge on the leaderboard.

**Architecture:** Feature-gated premium system: `PremiumFeature` enum → `ProductRegistry` maps products to features → `PremiumStore` resolves purchases into feature access. Score masking writes a `scoreMasked` field to Firebase player node; badge is local-only. Paywall is a full-screen Composable accessible from Settings.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin DI, Firebase RTDB, Google Play Billing (Android), multiplatform-settings

---

## File Map

### New Files (commonMain)
- `app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/PremiumFeature.kt` — feature enum
- `app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/ProductRegistry.kt` — product→feature mapping
- `app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/PremiumStore.kt` — local premium state + feature resolution
- `app/src/commonMain/kotlin/tools/mo3ta/salo/analytics/BillingAnalytics.kt` — analytics event constants
- `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/PaywallScreen.kt` — paywall UI

### Modified Files
- `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt` — register PremiumStore
- `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversModels.kt:28-34` — add `scoreMasked` to `FirebaseLeaderboardEntry`
- `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt:5-13` — add `scoreMasked` to `MohamedLoversLeaderboardEntry`
- `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt:227-234` — parse `scoreMasked` from leaderboard snapshot
- `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt:127-149` — add `setScoreMasked` method
- `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseApi.kt` — add `setScoreMasked` to interface
- `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt:343-360` — propagate `scoreMasked` in `applyLeaderboard`
- `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversInfoSheet.kt:444-495` — render masked score + badge
- `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/SettingsScreen.kt` — add paywall entry row
- `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt` — add `showPaywall` navigation state
- `scripts/populate-leaderboard.js:76-88,103-110` — propagate `scoreMasked` into leaderboard entries
- `database.rules.json:101-131` — allow `scoreMasked` Boolean in player writes

---

### Task 1: Premium Feature Enum + Product Registry

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/PremiumFeature.kt`
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/ProductRegistry.kt`

- [ ] **Step 1: Create PremiumFeature enum**

```kotlin
// app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/PremiumFeature.kt
package tools.mo3ta.salo.data.billing

enum class PremiumFeature {
    SCORE_MASK,
    SUPPORTER_BADGE,
}
```

- [ ] **Step 2: Create ProductRegistry**

```kotlin
// app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/ProductRegistry.kt
package tools.mo3ta.salo.data.billing

object ProductRegistry {
    const val SUPPORT_APP_PREMIUM = "support_app_premium"

    private val productFeatures: Map<String, Set<PremiumFeature>> = mapOf(
        SUPPORT_APP_PREMIUM to setOf(PremiumFeature.SCORE_MASK, PremiumFeature.SUPPORTER_BADGE),
    )

    fun featuresFor(productId: String): Set<PremiumFeature> =
        productFeatures[productId] ?: emptySet()

    val allProductIds: List<String> get() = productFeatures.keys.toList()
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/PremiumFeature.kt app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/ProductRegistry.kt
git commit -m "feat: add PremiumFeature enum and ProductRegistry"
```

---

### Task 2: PremiumStore + DI Wiring

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/PremiumStore.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt`

- [ ] **Step 1: Create PremiumStore**

```kotlin
// app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/PremiumStore.kt
package tools.mo3ta.salo.data.billing

import com.russhwolf.settings.Settings

class PremiumStore(private val settings: Settings) {
    fun markPurchased(productId: String) {
        settings.putBoolean("purchased_$productId", true)
    }

    fun isPurchased(productId: String): Boolean =
        settings.getBoolean("purchased_$productId", false)

    fun hasFeature(feature: PremiumFeature): Boolean =
        ProductRegistry.allProductIds.any { productId ->
            isPurchased(productId) && feature in ProductRegistry.featuresFor(productId)
        }

    var isScoreMasked: Boolean
        get() = settings.getBoolean("score_masked", false)
        set(value) { settings.putBoolean("score_masked", value) }
}
```

- [ ] **Step 2: Register PremiumStore in AppModule**

In `app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt`, add import and singleton:

Add import:
```kotlin
import tools.mo3ta.salo.data.billing.PremiumStore
```

Add after `single { NotificationSettingsStore(get()) }` (line 29):
```kotlin
    single { PremiumStore(get()) }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/billing/PremiumStore.kt app/src/commonMain/kotlin/tools/mo3ta/salo/di/AppModule.kt
git commit -m "feat: add PremiumStore with feature-based gating and wire into Koin"
```

---

### Task 3: Billing Analytics Events

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/analytics/BillingAnalytics.kt`

- [ ] **Step 1: Create BillingAnalytics constants**

```kotlin
// app/src/commonMain/kotlin/tools/mo3ta/salo/analytics/BillingAnalytics.kt
package tools.mo3ta.salo.analytics

object BillingAnalytics {
    const val PAYWALL_VIEWED = "paywall_viewed"
    const val PURCHASE_STARTED = "purchase_started"
    const val PURCHASE_COMPLETED = "purchase_completed"
    const val PURCHASE_FAILED = "purchase_failed"
    const val PURCHASE_RESTORED = "purchase_restored"
    const val SCORE_MASK_TOGGLED = "score_mask_toggled"

    const val PARAM_PRODUCT_ID = "product_id"
    const val PARAM_ERROR = "error"
    const val PARAM_ENABLED = "enabled"
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/analytics/BillingAnalytics.kt
git commit -m "feat: add billing analytics event constants"
```

---

### Task 4: Score Masking in Data Models

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversModels.kt:28-34`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt:5-13`

- [ ] **Step 1: Add `scoreMasked` to `FirebaseLeaderboardEntry`**

In `MohamedLoversModels.kt`, change:

```kotlin
data class FirebaseLeaderboardEntry(
    val rank: Int,
    val uid: String,
    val score: Int,
    val countryCode: String = "",
    val rankChange: String = "",
)
```

To:

```kotlin
data class FirebaseLeaderboardEntry(
    val rank: Int,
    val uid: String,
    val score: Int,
    val countryCode: String = "",
    val rankChange: String = "",
    val scoreMasked: Boolean = false,
)
```

- [ ] **Step 2: Add `scoreMasked` to `MohamedLoversLeaderboardEntry`**

In `MohamedLoversUiState.kt`, change:

```kotlin
data class MohamedLoversLeaderboardEntry(
    val rank: Int,
    val displayTag: String,
    val totalCount: Int,
    val isCurrentUser: Boolean,
    val rankChange: String = "",
){
    val displayedRank = if (rank > 0) "#$rank " else ""
}
```

To:

```kotlin
data class MohamedLoversLeaderboardEntry(
    val rank: Int,
    val displayTag: String,
    val totalCount: Int,
    val isCurrentUser: Boolean,
    val rankChange: String = "",
    val scoreMasked: Boolean = false,
){
    val displayedRank = if (rank > 0) "#$rank " else ""
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/domain/MohamedLoversModels.kt app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversUiState.kt
git commit -m "feat: add scoreMasked field to leaderboard data models"
```

---

### Task 5: Firebase Client — Parse + Write `scoreMasked`

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseApi.kt`
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt`

- [ ] **Step 1: Add `setScoreMasked` to FirebaseApi interface**

In `MohamedLoversFirebaseApi.kt`, add before the closing `}`:

```kotlin
    suspend fun setScoreMasked(roundKey: String, uid: String, masked: Boolean): Result<Unit>
```

- [ ] **Step 2: Parse `scoreMasked` in `toLeaderboardEntry`**

In `MohamedLoversFirebaseClient.kt`, the `toLeaderboardEntry` extension function (line 227-234), change:

```kotlin
        val rankChange = map[RANK_CHANGE_KEY] as? String ?: ""
        return FirebaseLeaderboardEntry(rank = rank, uid = uid, score = score, countryCode = countryCode, rankChange = rankChange)
```

To:

```kotlin
        val rankChange = map[RANK_CHANGE_KEY] as? String ?: ""
        val scoreMasked = map[SCORE_MASKED_KEY] as? Boolean ?: false
        return FirebaseLeaderboardEntry(rank = rank, uid = uid, score = score, countryCode = countryCode, rankChange = rankChange, scoreMasked = scoreMasked)
```

- [ ] **Step 3: Add `SCORE_MASKED_KEY` constant**

In `MohamedLoversFirebaseClient.kt`, inside the `companion object` (after line 265 `const val UPDATED_AT_KEY`), add:

```kotlin
        const val SCORE_MASKED_KEY = "scoreMasked"
```

- [ ] **Step 4: Implement `setScoreMasked`**

In `MohamedLoversFirebaseClient.kt`, add before `private fun playersPath` (line 221):

```kotlin
    override suspend fun setScoreMasked(roundKey: String, uid: String, masked: Boolean): Result<Unit> {
        log.d { "setScoreMasked[$roundKey/$uid] masked=$masked" }
        return runCatching {
            Firebase.database.reference(playersPath(roundKey)).child(uid).updateChildren(
                mapOf(SCORE_MASKED_KEY to masked)
            )
        }.also { result ->
            result.fold(
                onSuccess = { log.d { "setScoreMasked[$roundKey/$uid] ok" } },
                onFailure = { log.e(it) { "setScoreMasked[$roundKey/$uid] failed" } },
            )
        }
    }
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseApi.kt app/src/commonMain/kotlin/tools/mo3ta/salo/data/firebase/MohamedLoversFirebaseClient.kt
git commit -m "feat: parse and write scoreMasked in Firebase client"
```

---

### Task 6: ViewModel — Propagate `scoreMasked` to UI

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt:343-390`

- [ ] **Step 1: Propagate `scoreMasked` in `applyLeaderboard`**

In `MohamedLoversViewModel.kt`, the `applyLeaderboard` method builds `topEntries` (around line 349). Change:

```kotlin
        val topEntries = remoteLeaderboard.entries.map { entry ->
            val isCurrentUser = entry.uid == uid
            MohamedLoversLeaderboardEntry(
                rank = 0,
                displayTag = buildMohamedLoversDisplayTag(entry.uid, entry.countryCode),
                totalCount = if (isCurrentUser) selfProjectedTotal else entry.score,
                isCurrentUser = isCurrentUser,
                rankChange = entry.rankChange,
            )
        }.sortedByDescending { it.totalCount }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
```

To:

```kotlin
        val topEntries = remoteLeaderboard.entries.map { entry ->
            val isCurrentUser = entry.uid == uid
            MohamedLoversLeaderboardEntry(
                rank = 0,
                displayTag = buildMohamedLoversDisplayTag(entry.uid, entry.countryCode),
                totalCount = if (isCurrentUser) selfProjectedTotal else entry.score,
                isCurrentUser = isCurrentUser,
                rankChange = entry.rankChange,
                scoreMasked = entry.scoreMasked,
            )
        }.sortedByDescending { it.totalCount }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/presentation/MohamedLoversViewModel.kt
git commit -m "feat: propagate scoreMasked from Firebase to leaderboard UI entries"
```

---

### Task 7: Leaderboard UI — Render Score Masking + Badge

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversInfoSheet.kt:444-495`

- [ ] **Step 1: Update `LeaderboardRow` to show masked score and badge**

In `MohamedLoversInfoSheet.kt`, replace the `LeaderboardRow` composable (lines 444-495):

```kotlin
@Composable
private fun LeaderboardRow(entry: MohamedLoversLeaderboardEntry, pinned: Boolean) {
    val rankColor = when (entry.rank) {
        1 -> MohamedLoversPalette.GoldHighlight
        2 -> MohamedLoversPalette.RankSilver
        3 -> MohamedLoversPalette.RankBronze
        else -> MohamedLoversPalette.GoldGlow.copy(alpha = 0.45f)
    }
    val backgroundColor = when {
        pinned -> MohamedLoversPalette.GoldBase.copy(alpha = 0.2f)
        entry.isCurrentUser -> MohamedLoversPalette.GoldBase.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RankChangeIndicator(entry.rankChange)
            if (entry.rank > 0) {
                Text(
                    text = stringResource(Res.string.mohamed_lovers_rank_number, entry.rank),
                    style = bodyStyle().copy(
                        fontWeight = FontWeight.W700,
                        fontSize = if (entry.rank <= 3) 15.sp else 13.sp,
                    ),
                    color = rankColor,
                )
            }
            Text(
                text = entry.displayTag,
                style = bodyStyle().copy(
                    fontWeight = if (entry.isCurrentUser) FontWeight.W700 else FontWeight.W400,
                ),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.92f),
            )
        }
        if (entry.scoreMasked && !entry.isCurrentUser) {
            Text(
                text = "مخفي 🔒",
                style = bodyStyle().copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.4f),
            )
        } else {
            Text(
                text = entry.totalCount.toString(),
                style = bodyStyle(),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
            )
        }
    }
}
```

- [ ] **Step 2: Add badge to self entry in `LeaderboardCard`**

In `MohamedLoversInfoSheet.kt`, the `LeaderboardCard` composable receives `topPlayers`. Add a `isPremium` parameter and show badge on current user's display tag.

First, update `LeaderboardCard` signature (line 378):

```kotlin
@Composable
private fun LeaderboardCard(
    topPlayers: List<MohamedLoversLeaderboardEntry>,
    selfEntry: MohamedLoversLeaderboardEntry?,
    selfInTop: Boolean,
    isDaily: Boolean,
    isPremium: Boolean = false,
) {
```

In `LeaderboardCard`, where self entry (not in top) is displayed (line 416), change:

```kotlin
                Text(
                    text = "${selfEntry.displayedRank}${selfEntry.displayTag}",
```

To:

```kotlin
                Text(
                    text = "${selfEntry.displayedRank}${if (isPremium) "⭐ " else ""}${selfEntry.displayTag}",
```

In `LeaderboardCard`, where `topPlayers.forEach` renders rows (line 439), change:

```kotlin
            topPlayers.forEach { entry -> LeaderboardRow(entry = entry, pinned = false) }
```

To:

```kotlin
            topPlayers.forEach { entry ->
                LeaderboardRow(
                    entry = if (entry.isCurrentUser && isPremium) entry.copy(displayTag = "⭐ ${entry.displayTag}") else entry,
                    pinned = false,
                )
            }
```

- [ ] **Step 3: Pass `isPremium` from `MohamedLoversInfoSheet`**

In `MohamedLoversInfoSheet.kt`, update the `MohamedLoversInfoSheet` composable signature (line 88) to accept `isPremium`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MohamedLoversInfoSheet(
    isOpen: Boolean,
    state: MohamedLoversUiState,
    onDismiss: () -> Unit,
    onCopyWinnerCode: (String) -> Unit,
    isPremium: Boolean = false,
) {
```

Update the `LeaderboardCard` call inside the sheet (line 134):

```kotlin
            LeaderboardCard(
                topPlayers = state.topPlayers,
                selfEntry = state.selfEntry,
                selfInTop = state.selfInTop,
                isDaily = state.isUsingDailyLeaderboard,
                isPremium = isPremium,
            )
```

- [ ] **Step 4: Pass `isPremium` from the caller of `MohamedLoversInfoSheet`**

Find where `MohamedLoversInfoSheet` is called. It will be in `MohamedLoversScreen.kt`. Add `isPremium` parameter there, injecting `PremiumStore` via Koin:

```kotlin
val premiumStore: PremiumStore = koinInject()
```

Then pass to the sheet call:

```kotlin
isPremium = premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE),
```

(Add the necessary imports: `tools.mo3ta.salo.data.billing.PremiumStore` and `tools.mo3ta.salo.data.billing.PremiumFeature`)

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/components/MohamedLoversInfoSheet.kt app/src/commonMain/kotlin/tools/mo3ta/salo/ui/MohamedLoversScreen.kt
git commit -m "feat: render score masking and supporter badge in leaderboard UI"
```

---

### Task 8: Paywall Screen

**Files:**
- Create: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/PaywallScreen.kt`

- [ ] **Step 1: Create PaywallScreen composable**

```kotlin
// app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/PaywallScreen.kt
package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.BillingAnalytics
import tools.mo3ta.salo.data.billing.BillingManager
import tools.mo3ta.salo.data.billing.PremiumFeature
import tools.mo3ta.salo.data.billing.PremiumStore
import tools.mo3ta.salo.data.billing.ProductRegistry
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(onBack: () -> Unit) {
    val billingManager: BillingManager = koinInject()
    val premiumStore: PremiumStore = koinInject()
    val analyticsManager: AnalyticsManager = koinInject()

    val isPremium = premiumStore.hasFeature(PremiumFeature.SCORE_MASK)
    var scoreMasked by remember { mutableStateOf(premiumStore.isScoreMasked) }
    val price = billingManager.getProductPrice(ProductRegistry.SUPPORT_APP_PREMIUM) ?: "$1.99"

    LaunchedEffect(Unit) {
        analyticsManager.logAction(BillingAnalytics.PAYWALL_VIEWED)
    }

    Scaffold(
        containerColor = Color(0xFF0f0f1a),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isPremium) "إعدادات الداعم" else "ادعم التطبيق",
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
                text = if (isPremium) "أنت داعم! ✅" else "ادعم التطبيق",
                color = MohamedLoversPalette.GoldGlow,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isPremium) "شكرًا لدعمك — إليك إعدادات المزايا الحصرية" else "ساهم في تطوير التطبيق واحصل على مزايا حصرية",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(28.dp))

            FeatureCard(icon = "🔒", title = "إخفاء النتيجة", subtitle = "اخفِ نتيجتك من الآخرين في المتصدرين")
            Spacer(Modifier.height(10.dp))
            FeatureCard(icon = "⭐", title = "شارة الداعم", subtitle = "شارة مميزة بجانب اسمك في المتصدرين")
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
                            text = "إخفاء النتيجة",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "نتيجتك مخفية عن المتسابقين الآخرين",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = scoreMasked,
                        onCheckedChange = { checked ->
                            scoreMasked = checked
                            premiumStore.isScoreMasked = checked
                            analyticsManager.logAction(
                                BillingAnalytics.SCORE_MASK_TOGGLED,
                                mapOf(BillingAnalytics.PARAM_ENABLED to checked.toString()),
                            )
                        },
                    )
                }
            } else {
                Button(
                    onClick = {
                        analyticsManager.logAction(
                            BillingAnalytics.PURCHASE_STARTED,
                            mapOf(BillingAnalytics.PARAM_PRODUCT_ID to ProductRegistry.SUPPORT_APP_PREMIUM),
                        )
                        billingManager.purchaseProduct(ProductRegistry.SUPPORT_APP_PREMIUM)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "ادعم الآن — $price",
                        color = Color(0xFF0f0f1a),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFD700), Color(0xFFFF8C00)),
                                ),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "استعادة المشتريات",
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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/PaywallScreen.kt
git commit -m "feat: add PaywallScreen composable with purchase flow and score mask toggle"
```

---

### Task 9: Settings Screen — Add Paywall Entry Row

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add paywall row to Settings**

In `SettingsScreen.kt`, add imports at the top:

```kotlin
import tools.mo3ta.salo.data.billing.BillingManager
```

Inside `SettingsScreen` composable, after `val analyticsManager` (line 73), add:

```kotlin
    val billingManager: BillingManager = koinInject()
```

Before the "عن التطبيق" section header (line 252), add:

```kotlin
            if (billingManager.isEnabled) {
                Text(
                    text = "الدعم",
                    color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                SettingLinkRow(
                    label = "🌟 ادعم التطبيق",
                    labelColor = MohamedLoversPalette.GoldGlow,
                    onClick = onOpenPaywall,
                )
            }
```

- [ ] **Step 2: Add `onOpenPaywall` callback to SettingsScreen signature**

Change the `SettingsScreen` signature (line 60):

```kotlin
fun SettingsScreen(onBack: () -> Unit, onOpenOnboarding: () -> Unit = {}, onOpenExtensionQr: () -> Unit = {})
```

To:

```kotlin
fun SettingsScreen(onBack: () -> Unit, onOpenOnboarding: () -> Unit = {}, onOpenExtensionQr: () -> Unit = {}, onOpenPaywall: () -> Unit = {})
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/ui/settings/SettingsScreen.kt
git commit -m "feat: add paywall entry row in Settings screen"
```

---

### Task 10: App Navigation — Wire PaywallScreen

**Files:**
- Modify: `app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt`

- [ ] **Step 1: Add paywall navigation state and screen**

In `App.kt`, add import:

```kotlin
import tools.mo3ta.salo.ui.settings.PaywallScreen
```

After `var showExtensionQr` (line 57), add:

```kotlin
        var showPaywall by remember { mutableStateOf(false) }
```

Update `PlatformBackHandler` enabled condition (line 59) — add `showPaywall`:

```kotlin
        PlatformBackHandler(enabled = showPaywall || showTenDays || showExtensionQr || showHadithList || showAchievements || showSettings || showOnboarding) {
            when {
                showPaywall -> showPaywall = false
                showTenDays -> showTenDays = false
```

In the `when` block (line 70), add before `showExtensionQr`:

```kotlin
            showPaywall -> PaywallScreen(onBack = { showPaywall = false })
```

Update the `SettingsScreen` call (line 73) to pass `onOpenPaywall`:

```kotlin
            showSettings -> SettingsScreen(
                onBack = { showSettings = false },
                onOpenOnboarding = { showOnboarding = true },
                onOpenExtensionQr = { showExtensionQr = true },
                onOpenPaywall = { showPaywall = true },
            )
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/tools/mo3ta/salo/App.kt
git commit -m "feat: wire PaywallScreen into app navigation"
```

---

### Task 11: Firebase Security Rules — Allow `scoreMasked`

**Files:**
- Modify: `database.rules.json`

- [ ] **Step 1: Add `scoreMasked` field to player validation**

In `database.rules.json`, inside the `mohamed_lovers.$round.players.$uid` node, after the `winnerCode` rule (line 128-130), add before `"$other": { ".validate": false }`:

```json
            "scoreMasked": {
              ".validate": "newData.isBoolean()"
            },
```

Also update the `".validate"` rule for `$uid` (line 106) to make `scoreMasked` optional. The current rule requires `['uid','totalCount','updatedAt','countryCode']` — that's fine since `scoreMasked` is optional (not in `hasChildren`).

The existing `"$other": { ".validate": false }` (line 131) currently blocks any field not explicitly defined. We need the `scoreMasked` rule added before `$other` to allow it.

- [ ] **Step 2: Commit**

```bash
git add database.rules.json
git commit -m "feat: allow scoreMasked boolean in Firebase player write rules"
```

---

### Task 12: Server Script — Propagate `scoreMasked` to Leaderboard

**Files:**
- Modify: `scripts/populate-leaderboard.js`

- [ ] **Step 1: Read `scoreMasked` from player data**

In `populate-leaderboard.js`, inside the `allPlayersSnapshot.forEach` callback (lines 76-88), where player data is pushed, change:

```javascript
      allPlayers.push({
        uid: data.uid,
        score: data.totalCount,
        updatedAt: data.updatedAt || 0,
        countryCode: typeof data.countryCode === 'string' ? data.countryCode : 'NA',
        yesterdayTotalScore: typeof data.yesterdayTotalScore === 'number' ? data.yesterdayTotalScore : 0,
      });
```

To:

```javascript
      allPlayers.push({
        uid: data.uid,
        score: data.totalCount,
        updatedAt: data.updatedAt || 0,
        countryCode: typeof data.countryCode === 'string' ? data.countryCode : 'NA',
        yesterdayTotalScore: typeof data.yesterdayTotalScore === 'number' ? data.yesterdayTotalScore : 0,
        scoreMasked: data.scoreMasked === true,
      });
```

- [ ] **Step 2: Write `scoreMasked` to weekly leaderboard entries**

In `populate-leaderboard.js`, where leaderboard entries are built (lines 103-110), change:

```javascript
  top10.forEach((player, i) => {
    leaderboard[String(i + 1)] = {
      rank: i + 1,
      uid: player.uid,
      score: player.score,
      countryCode: player.countryCode,
    };
  });
```

To:

```javascript
  top10.forEach((player, i) => {
    const entry = {
      rank: i + 1,
      uid: player.uid,
      score: player.score,
      countryCode: player.countryCode,
    };
    if (player.scoreMasked) entry.scoreMasked = true;
    leaderboard[String(i + 1)] = entry;
  });
```

- [ ] **Step 3: Write `scoreMasked` to daily leaderboard entries**

In `populate-leaderboard.js`, where daily leaderboard entries are built (lines 119-127), change:

```javascript
  dailyTop10.forEach((player, i) => {
    dailyLeaderboard[String(i + 1)] = {
      rank: i + 1,
      uid: player.uid,
      score: player.dailyScore,
      countryCode: player.countryCode,
    };
  });
```

To:

```javascript
  dailyTop10.forEach((player, i) => {
    const entry = {
      rank: i + 1,
      uid: player.uid,
      score: player.dailyScore,
      countryCode: player.countryCode,
    };
    if (player.scoreMasked) entry.scoreMasked = true;
    dailyLeaderboard[String(i + 1)] = entry;
  });
```

- [ ] **Step 4: Commit**

```bash
git add scripts/populate-leaderboard.js
git commit -m "feat: propagate scoreMasked flag into weekly and daily leaderboard entries"
```

---

### Task 13: Integration Verification

- [ ] **Step 1: Compile all targets**

Run: `./gradlew :app:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :app:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL

(Android compile will fail due to missing `google-services.json` — expected in dev, works in CI.)

- [ ] **Step 2: Verify no regressions in existing tests**

Run: `./gradlew :app:iosSimulatorArm64Test`
Expected: All existing tests pass

- [ ] **Step 3: Final commit with all files**

If any files were missed in previous commits:

```bash
git status
git add <any missed files>
git commit -m "chore: integration verification pass"
```
