# Subscription Encouragement System

**Date**: 2026-05-25
**Goal**: Encourage users to support the app via subscription with a hybrid approach — milestone celebrations + leaderboard visual hints + settings discovery dot.
**Tone**: Sadaqah jariyah framing — supporting the app shares thawab with every user who prays through it.

## Current State

- 3 support tiers: Basic ($0.99), Support ($1.99), Race ($4.99)
- 3 premium features: Score Mask, Supporter Badge, Friday Scores
- Paywall screen exists in settings
- One-shot `PremiumPromoDialog` shown once on first launch, never again
- Analytics track paywall views and purchases
- iOS billing not implemented (NoOp stub)

## Design

### 1. Milestone Celebration Dialog

**Triggers** (non-supporter users only):
- Score milestones per round: 1000, 5000, 10000, 25000, 50000 taps
- New best rank achieved (rank improved vs previous round achievement)
- Max once per session per trigger type

**Dialog content**:
- Header: "ما شاء الله! 🎉"
- Body: "بارك الله فيك! وصلت إلى [milestone] صلاة على الحبيب ﷺ"
- Support pitch: "ادعم التطبيق كصدقة جارية، وشارك الثواب مع كل من يصلي على النبي ﷺ من خلاله"
- Subtext: "كل صلاة تُرفع من خلال التطبيق لك نصيب من أجرها"
- CTA button: "أريد الأجر 💚" → navigates to PaywallScreen
- Dismiss button: "لاحقًا" → closes dialog

**Storage**:
- Track shown milestones in SharedPreferences: `milestone_shown_{value}` boolean
- Track last shown session to enforce once-per-session limit

**Strings**: All text as string resources in 4 locales (ar, en, ur, zh).

### 2. Leaderboard Supporter Hints

Visual-only hints for non-supporter users on the leaderboard:

#### 2a. Supporter Badge Visibility
- Existing supporters already show ⭐ badge on leaderboard entries
- No change needed — this naturally creates curiosity

#### 2b. Friday Score Blur
- On Fridays, non-premium users see other players' scores as "---"
- Below leaderboard: hint text "ادعم التطبيق لرؤية نتائج المتسابقين 🔓"
- Tapping hint text opens PaywallScreen
- Already partially implemented via `isPremium` check in `MohamedLoversScreen`

#### 2c. "Join Supporters" Row
- At bottom of leaderboard list: a styled row "انضم للداعمين ⭐"
- Golden/warm accent color, distinct from regular leaderboard rows
- Tapping opens PaywallScreen
- Only visible to non-supporters

### 3. Settings Badge Dot

**Persistent discovery for non-supporters**:
- Small gold dot overlay on settings icon in main screen toolbar
- Disappears permanently once any tier is purchased
- Inside settings: "ادعم التطبيق" row gets subtle golden background tint
- Implementation: check `premiumStore.highestTier == null` to show/hide

### 4. Analytics Events

New events for conversion funnel tracking:

| Event | Parameters | When |
|-------|-----------|------|
| `MILESTONE_SUPPORT_SHOWN` | `milestone_value: Int` | Milestone dialog displayed |
| `MILESTONE_SUPPORT_CTA_TAPPED` | `milestone_value: Int` | User tapped "أريد الأجر" |
| `MILESTONE_SUPPORT_DISMISSED` | `milestone_value: Int` | User tapped "لاحقًا" |
| `LEADERBOARD_SUPPORT_HINT_TAPPED` | `hint_type: String` | Tapped any leaderboard hint |
| `SETTINGS_DOT_VISIBLE` | — | Settings dot shown (once per session) |

## Architecture

### New Files
- `ui/support/MilestoneSupportDialog.kt` — milestone celebration composable
- `data/MilestoneTracker.kt` — tracks which milestones have been shown

### Modified Files
- `ui/MohamedLoversScreen.kt` — add milestone detection logic, leaderboard hint row
- `ui/settings/SettingsScreen.kt` — add golden tint to support row
- `ui/components/` — settings icon with badge dot overlay
- `analytics/BillingAnalytics.kt` — new event constants
- String resource files (4 locales)

### Data Flow
1. User taps → score increments → `MilestoneTracker.checkMilestone(score)` returns milestone if hit
2. If milestone hit + user not supporter + not shown this session → show `MilestoneSupportDialog`
3. CTA tap → navigate to PaywallScreen + log analytics
4. Leaderboard renders → check supporter status → show/hide hints
5. Settings icon renders → check `premiumStore.highestTier` → show/hide dot

## Out of Scope
- iOS billing implementation (stays NoOp)
- Push notification nudges
- A/B testing of different messages
- Server-side supporter status validation
