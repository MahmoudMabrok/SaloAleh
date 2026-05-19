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

| Property | Value |
|----------|-------|
| Type | One-time (non-consumable) |
| Price | $1.99 |
| Product ID | `support_app_premium` |
| Platform | Android (Google Play Billing) |
| iOS | Disabled (`NoOpBillingManager`, `isEnabled = false`) |

## Architecture

### Purchase State Storage

- **Local**: `Settings` key `is_premium_user` (Boolean). Checked by UI to show/hide premium features and badge.
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

### PremiumStore (new, commonMain)

Thin wrapper over `Settings` for premium state:

```kotlin
class PremiumStore(private val settings: Settings) {
    val isPremium: Boolean get() = settings.getBoolean("is_premium_user", false)
    fun setPremium(value: Boolean) { settings.putBoolean("is_premium_user", value) }
    val isScoreMasked: Boolean get() = settings.getBoolean("score_masked", false)
    fun setScoreMasked(value: Boolean) { settings.putBoolean("score_masked", value) }
}
```

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
3. Purchase succeeds → `purchase_completed` event → `PremiumStore.setPremium(true)`
4. User toggles score masking in Settings → `score_mask_toggled` event → `PremiumStore.setScoreMasked(true)` → write `scoreMasked: true` to Firebase player node
5. `populate-leaderboard.js` copies `scoreMasked` into leaderboard entries
6. Other clients read leaderboard → hide score where `scoreMasked == true`
7. On reinstall → `restorePurchases()` at init → restore `PremiumStore` state

## Files to Create

| File | Location | Purpose |
|------|----------|---------|
| `PremiumStore.kt` | `commonMain/data/billing/` | Local premium state persistence |
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

## Out of Scope

- iOS billing implementation (NoOp for now)
- Server-side purchase verification
- Multiple product tiers
- Subscription model
- Badge visible to other users (local-only for now)
