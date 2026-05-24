# Motivation Features Design Spec

Adds three in-session motivation features to make tapping feel more rewarding: overtake alerts, daily salawat milestones with badges, and rank movement summary.

## Feature 1: Overtake Alerts

When a player's projected rank improves (local taps + leaderboard snapshot push them past another lover), show a subtle overlay.

### UX

- Overlay text: "⬆ تجاوزت المحب #N!" (You passed lover #N!)
- Rank chip flashes green with ⬆ arrow
- Duration: 2 seconds, fade out
- Cooldown: max 1 alert per 5 seconds
- No sound
- Tapping not blocked during overlay

### Data Source

Local rank projection — already computed in `MohamedLoversViewModel` using leaderboard snapshot + pending local taps. No new Firebase reads.

### Implementation Notes

- Track `lastProjectedRank` in ViewModel state
- On each leaderboard update or local tap flush, compare new projected rank to previous
- If improved, emit overtake event with old rank number
- UI layer consumes event, shows overlay with `AnimatedVisibility` + fade

---

## Feature 2: Daily Salawat Milestones with Badges

When daily salawat count (raw taps, not score) crosses milestone thresholds, show celebration overlay and save badge to Firebase player node.

### Tiers

| Threshold | Badge SVG | Arabic Title | Key |
|-----------|-----------|-------------|-----|
| 100 | `rank_100_sprout.svg` | مبتدئ الذكر | `sprout` |
| 200 | `rank_200_heart.svg` | محب النبي ﷺ | `heart` |
| 500 | `rank_500_tasbih.svg` | كثير الصلاة | `tasbih` |
| 1,000 | `rank_1000_dome.svg` | رفيق الذكر | `dome` |
| 2,000 | `rank_2000_crescent.svg` | من المكثرين | `crescent` |
| 5,000 | `rank_5000_crown.svg` | فارس الصلاة | `crown` |

Badge SVGs from `salou_alayh_leaderboard_badges_svg` asset pack. Must be added to Compose Multiplatform resources.

### UX

- Celebration overlay: centered card with badge icon + "N صلاة اليوم!" + Arabic title
- Duration: 3 seconds, auto-dismiss (tap to dismiss early)
- Tapping not blocked during overlay
- Subtle golden pulse on counter text at milestone moment
- Once per day per tier — resets at midnight Cairo time

### Counter Basis

Raw salawat (taps), not score. Friday 2x multiplier does NOT inflate milestones. Uses `DailyGoalStore` daily tap tracking.

### Firebase: `dailyBadge` on Player Node

```
mohamed_lovers/{roundKey}/players/{uid}/
├── totalCount
├── updatedAt
├── countryCode
└── dailyBadge: "dome"    ← highest milestone badge key hit today
```

- **Client writes** `dailyBadge` to own player node when milestone crossed
- **`populate-leaderboard.js`** copies `dailyBadge` into leaderboard entries
- **`generate-stats.js`** clears all `dailyBadge` fields at 23:45 Cairo (daily reset)

### Leaderboard Display

Badge SVG icon (22dp) shown next to player name in leaderboard. No badge = no icon (dash placeholder). Legend bar at bottom of leaderboard shows all tiers.

### Reset Logic in `generate-stats.js`

After existing stats logic, iterate all players in current round and set `dailyBadge` to null. Also clear `dailyBadge` from all leaderboard entries.

```
// For each player in current round:
players/{uid}/dailyBadge → null

// For each leaderboard entry:
leaderboard/{idx}/dailyBadge → null
```

### Security Rules

Allow client to write `dailyBadge` string to own player node. Validate it's one of the known badge keys or null.

---

## Feature 3: Rank Movement Summary

On app open, show brief summary of rank changes since last visit.

### UX

- **Rank improved**: green banner — "⬆ صعدت N مراكز" with #old → #new
- **Rank dropped**: red banner — "⬇ نزلت N مراكز" with #old → #new
- **No change**: no banner shown
- Duration: 4 seconds, fade out. Tap to dismiss early.
- Rank chip tinted green (⬆) or red (⬇) to match

### Trigger

First leaderboard load after app open. Compare current rank vs. stored last-known rank.

### Storage

Save last-known rank to `MohamedLoversSessionStore` on each leaderboard update. On next app open, read stored rank, compare with fresh rank.

### Implementation Notes

- Add `lastKnownRank: Int?` to session store
- ViewModel computes delta on first leaderboard observation
- Emit rank movement event (direction + delta + old/new ranks)
- UI shows banner with `AnimatedVisibility` + slide-in

---

## Architecture Summary

### New State Fields in `MohamedLoversUiState`

```kotlin
// Overtake alerts
val overtakeEvent: OvertakeEvent?  // rank number passed, auto-clears

// Daily milestones
val milestoneEvent: MilestoneEvent?  // badge tier hit, auto-clears
val currentDailyBadge: String?  // highest badge key earned today

// Rank movement
val rankMovementEvent: RankMovementEvent?  // direction, delta, old/new rank, auto-clears
```

### New Files

| File | Purpose |
|------|---------|
| `domain/DailyBadgeModels.kt` | Badge tier enum, milestone thresholds, badge keys |
| `ui/components/OvertakeOverlay.kt` | Overtake alert overlay composable |
| `ui/components/MilestoneCelebration.kt` | Milestone celebration overlay composable |
| `ui/components/RankMovementBanner.kt` | Rank movement summary banner composable |
| `ui/components/DailyBadgeIcon.kt` | Badge SVG icon composable for leaderboard |

### Modified Files

| File | Changes |
|------|---------|
| `MohamedLoversViewModel.kt` | Overtake detection, milestone detection, rank movement logic |
| `MohamedLoversUiState.kt` | New event fields |
| `MohamedLoversSessionStore.kt` | `lastKnownRank`, daily milestone tracking |
| `MohamedLoversScreen.kt` | Render overlays/banners |
| `MohamedLoversInfoSheet.kt` | Show badge icons in leaderboard entries |
| `MohamedLoversRepository.kt` | Write `dailyBadge` to Firebase player node |
| `MohamedLoversModels.kt` | Add `dailyBadge` to `LeaderboardEntry` model |
| `DailyGoalStore.kt` | Expose raw daily tap count for milestone checks |
| `scripts/populate-leaderboard.js` | Copy `dailyBadge` from player to leaderboard entry |
| `scripts/generate-stats.js` | Clear all `dailyBadge` fields at 23:45 Cairo |
| `database.rules.json` | Allow `dailyBadge` write on own player node |

### Compose Resources

Add 6 SVG badge files to `app/src/commonMain/composeResources/drawable/`:
- `badge_sprout.svg`
- `badge_heart.svg`
- `badge_tasbih.svg`
- `badge_dome.svg`
- `badge_crescent.svg`
- `badge_crown.svg`

### Strings (Arabic)

```
overtake_alert = "⬆ تجاوزت المحب #%d!"
milestone_today = "%d صلاة اليوم!"
rank_climbed = "⬆ صعدت %d مراكز"
rank_dropped = "⬇ نزلت %d مراكز"
since_last_visit = "منذ آخر زيارة"
badge_sprout_title = "مبتدئ الذكر"
badge_heart_title = "محب النبي ﷺ"
badge_tasbih_title = "كثير الصلاة"
badge_dome_title = "رفيق الذكر"
badge_crescent_title = "من المكثرين"
badge_crown_title = "فارس الصلاة"
```
