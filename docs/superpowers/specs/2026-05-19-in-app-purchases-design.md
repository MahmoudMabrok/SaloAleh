# In-App Purchases — Support the App

## Overview

One-time purchase ($1.99) positioned as "support the app" with two premium perks: score masking on the leaderboard and a supporter badge. Android-only at launch; iOS uses a no-op stub (`isEnabled = false`).

## Premium Features

### Score Masking

- Premium users can hide their score from other players on the leaderboard.
- When enabled, the player's score column shows "مخفي 🔒" to everyone else.
- The user's own view always shows their real score.
- Implemented by writing `scoreMasked: true` to the player's Firebase RTDB node under the current round (`{roundKey}/players/{uid}/scoreMasked`).
- The leaderboard population script (`populate-leaderboard.js`) propagates this flag into leaderboard entries so clients render it without extra reads.
- Score masking is toggleable from Settings after purchase.

### Supporter Badge

- Premium users get a ⭐ emoji displayed next to their name on the leaderboard.
- Local-only decoration — derived from local purchase state, not stored in Firebase.
- Visible only on the premium user's own device (other users do not see the badge).

## Purchase Model

### Current: One-Time Support

| Property | Value |
|----------|-------|
| Type | One-time (non-consumable) |
| Price | $1.99 |
| Product ID | `support_app_premium` |
| Platform | Android (Google Play Billing) |
| iOS | Disabled (`NoOpBillingManager`, `isEnabled = false`) |
| Features granted | `score_mask`, `supporter_badge` |

### Future-Ready: Product → Feature Mapping

Products map to feature sets, not a single boolean. Adding a subscription later means defining a new product with its own feature set.

```
Product                  → Features
─────────────────────────────────────
support_app_premium      → score_mask, supporter_badge
(future) monthly_sub     → score_mask, supporter_badge, custom_tag, ...
```

Each feature is an enum value in `PremiumFeature`. The `PremiumStore` resolves which features a user has based on their purchased products. UI checks `hasFeature(PremiumFeature.SCORE_MASK)` — never checks product IDs directly.

## Architecture

### Purchase State Storage

- **Local**: `Settings` stores purchased product IDs (e.g. `purchased_support_app_premium = true`). `PremiumStore` resolves these into feature access.
- **Firebase**: `scoreMasked` field on player node. Written by client when user toggles score masking. Read by leaderboard population script and other clients.
- **Restore**: Google Play `queryPurchasesAsync` on app start to recover state after reinstall.

### BillingManager (already created)

```
interface BillingManager {
    val isEnabled: Boolean
    fun initialize()
    fun purchaseProduct(productId: String)
    fun restorePurchases()
    fun isPurchased(productId: String): Boolean
    fun getProductPrice(productId: String): String?
}
```

- `AndroidBillingManager`: Google Play BillingClient integration. `isEnabled = true`.
- `NoOpBillingManager`: All no-ops. `isEnabled = false`. Used on iOS.
- Injected via Koin platform modules (already wired).

### PremiumFeature Enum (new, commonMain)

```kotlin
enum class PremiumFeature {
    SCORE_MASK,
    SUPPORTER_BADGE,
}
```

### Product Registry (new, commonMain)

Maps product IDs to feature sets. Adding a subscription later = one new entry here.

```kotlin
object ProductRegistry {
    private val productFeatures: Map<String, Set<PremiumFeature>> = mapOf(
        "support_app_premium" to setOf(PremiumFeature.SCORE_MASK, PremiumFeature.SUPPORTER_BADGE),
        // future: "monthly_sub" to setOf(SCORE_MASK, SUPPORTER_BADGE, CUSTOM_TAG, ...)
    )

    fun featuresFor(productId: String): Set<PremiumFeature> =
        productFeatures[productId] ?: emptySet()

    val allProductIds: List<String> get() = productFeatures.keys.toList()
}
```

### PremiumStore (new, commonMain)

Resolves purchased products into feature access:

```kotlin
class PremiumStore(private val settings: Settings) {
    fun markPurchased(productId: String) { settings.putBoolean("purchased_$productId", true) }
    fun isPurchased(productId: String): Boolean = settings.getBoolean("purchased_$productId", false)

    fun hasFeature(feature: PremiumFeature): Boolean =
        ProductRegistry.allProductIds.any { productId ->
            isPurchased(productId) && feature in ProductRegistry.featuresFor(productId)
        }

    val isScoreMasked: Boolean get() = settings.getBoolean("score_masked", false)
    fun setScoreMasked(value: Boolean) { settings.putBoolean("score_masked", value) }
}
```

UI calls `premiumStore.hasFeature(PremiumFeature.SCORE_MASK)` — never references product IDs. Adding a subscription later requires no UI changes, only a new `ProductRegistry` entry and `BillingManager` subscription support.

## UI

### Paywall Screen (full-screen, new Composable)

- Entry point: "ادعم التطبيق" row in Settings screen (only shown when `billingManager.isEnabled`).
- Layout (RTL, dark theme):
  1. Hero icon (🌟)
  2. Title: "ادعم التطبيق"
  3. Subtitle: "ساهم في تطوير التطبيق واحصل على مزايا حصرية"
  4. Feature cards (2): score masking (🔒) + supporter badge (⭐)
  5. CTA button: "ادعم الآن — $1.99" (price from `getProductPrice` with $1.99 fallback)
  6. "استعادة المشتريات" text link below CTA
- If already premium: show "أنت داعم! ✅" state with score masking toggle instead of purchase button.

### Leaderboard Changes

- `LeaderboardRow`: if entry has `scoreMasked = true` and `!isCurrentUser`, show "مخفي 🔒" instead of score.
- `LeaderboardRow`: if `isCurrentUser` and premium, prepend ⭐ to display tag.

### Settings Screen Changes

- New row: "ادعم التطبيق" (gold accent, 🌟 icon) — navigates to paywall screen.
- Only visible when `billingManager.isEnabled == true`.
- After purchase: row changes to "إعدادات الداعم" and navigates to premium settings (score mask toggle).

## Analytics Events

All fired through existing `AnalyticsManager.logAction()`:

| Event | Params | When |
|-------|--------|------|
| `paywall_viewed` | — | Paywall screen opened |
| `purchase_started` | `product_id` | User tapped buy button |
| `purchase_completed` | `product_id` | Successful purchase |
| `purchase_failed` | `product_id`, `error` | Purchase error |
| `purchase_restored` | `product_id` | Restored previous purchase |
| `score_mask_toggled` | `enabled` | Premium user toggles score masking |

## Firebase RTDB Changes

### Player node addition

```
{roundKey}/players/{uid}/scoreMasked: Boolean (default: absent/false)
```

### Leaderboard entry addition

```
{roundKey}/leaderboard/{index}/scoreMasked: Boolean
```

### Security rules update

Add `scoreMasked` as optional Boolean field in player write validation.

### populate-leaderboard.js update

Copy `scoreMasked` field from player node into leaderboard entry when populating.

## Data Flow

1. User opens paywall → `paywall_viewed` event
2. User taps buy → `purchase_started` event → Google Play billing flow
3. Purchase succeeds → `purchase_completed` event → `PremiumStore.markPurchased("support_app_premium")`
4. User toggles score masking in Settings → `score_mask_toggled` event → `PremiumStore.setScoreMasked(true)` → write `scoreMasked: true` to Firebase player node
5. `populate-leaderboard.js` copies `scoreMasked` into leaderboard entries
6. Other clients read leaderboard → hide score where `scoreMasked == true`
7. On reinstall → `restorePurchases()` at init → restore `PremiumStore` state

## Files to Create

| File | Location | Purpose |
|------|----------|---------|
| `PremiumFeature.kt` | `commonMain/data/billing/` | Feature enum |
| `ProductRegistry.kt` | `commonMain/data/billing/` | Product → feature mapping |
| `PremiumStore.kt` | `commonMain/data/billing/` | Local premium state persistence + feature resolution |
| `PaywallScreen.kt` | `commonMain/ui/settings/` | Paywall UI |
| `BillingAnalytics.kt` | `commonMain/analytics/` | Analytics event constants and helper |

## Files to Modify

| File | Change |
|------|--------|
| `AndroidBillingManager.kt` | Google Play BillingClient implementation |
| `AppModule.kt` | Register `PremiumStore` |
| `SettingsScreen.kt` | Add "ادعم التطبيق" row + score mask toggle for premium users |
| `MohamedLoversInfoSheet.kt` | Score masking + badge in `LeaderboardRow` |
| `MohamedLoversUiState.kt` | Add `scoreMasked` to `MohamedLoversLeaderboardEntry` |
| `MohamedLoversModels.kt` | Add `scoreMasked` to `FirebaseLeaderboardEntry` |
| `MohamedLoversFirebaseClient.kt` | Write `scoreMasked` field on player node |
| `populate-leaderboard.js` | Copy `scoreMasked` into leaderboard entries |
| `database.rules.json` | Allow `scoreMasked` Boolean in player writes |

## Subscription Extensibility

The architecture is subscription-ready without UI changes:

1. Add new enum values to `PremiumFeature` (e.g. `CUSTOM_TAG`)
2. Add new product entry in `ProductRegistry` with its feature set
3. Add subscription support to `BillingManager` (`purchaseSubscription`, `isSubscriptionActive`)
4. `PremiumStore.hasFeature()` automatically resolves features from all purchased products
5. Paywall screen can show multiple product options, each listing its features
6. Feature-split between user types is just different `ProductRegistry` entries

No UI code checks product IDs — only `hasFeature()`. So splitting features across tiers is a registry change, not a UI rewrite.

## Out of Scope

- iOS billing implementation (NoOp for now)
- Server-side purchase verification
- Subscription implementation (architecture ready, not built)
- Badge visible to other users (local-only for now)
