# عشر ذي الحجة — "10 of Life" Feature Design

## Overview

A time-limited screen (9 days) for tracking daily worship actions during the first days of Dhul Hijjah. Each day has 4 action categories with a points system and a dedicated leaderboard.

## Entry Point

- New tab icon in the top bar, positioned before the settings icon
- Icon: enlightening/glowing style (matches the screen's radiant theme)
- Visible only during the active 9-day period

## Screen Layout

### Visual Theme
- Dark gradient background with radial glow effects (enlightening/نوراني feel)
- Gold (#fbbf24) as accent color for points and highlights
- Green (#22c55e) for completed items

### Structure (top to bottom)
1. **Header**: "عشر ذي الحجة" title with glow + "اليوم X من ٩"
2. **Score banner**: Today's total points (gold gradient card)
3. **Day selector**: 9 circles showing progress (green = completed day, gold = current, gray = future)
4. **Actions section**: 4 action cards
5. **Mini leaderboard**: Top 3 + user's rank

## Actions

### 1. الباقيات الصالحات (Adhkar)
- **UI**: Grid of 5 dhikr items (2x2 + 1 centered below)
- **Items**:
  - سبحان الله
  - الحمد لله
  - الله أكبر
  - لا إله إلا الله
  - لا حول ولا قوة إلا بالله
- **Interaction**: Tap each item to increment its counter
- **No upper limit** — user can tap freely
- **Points**: +1 per tap (per individual dhikr)

### 2. الصيام (Fasting)
- **UI**: Toggle row (on/off)
- **Interaction**: Toggle once per day — "did you fast today?"
- **Points**: +100 when toggled on
- **Constraint**: Can only toggle on, not off (or confirm dialog to undo)

### 3. التكبير (Takbeer)
- **UI**: Counter with tap button
- **Interaction**: Tap to increment, no limit
- **Points**: +5 per tap

### 4. الصدقة (Charity)
- **UI**: Toggle row (on/off)
- **Interaction**: Toggle once per day — "did you give charity today?"
- **Points**: +150 when toggled on

## Points System

| Action | Points |
|--------|--------|
| Each dhikr tap (any of 5) | +1 |
| Fasting toggle | +100 |
| Each takbeer tap | +5 |
| Charity toggle | +150 |

**Total score** = sum of all points across all 9 days.

## Data Model

### Local Persistence (Settings/SharedPreferences)
Per-day state keyed by day number (1-9):
```
tenDays_day{N}_subhanallah: Int
tenDays_day{N}_alhamdulillah: Int
tenDays_day{N}_allahuakbar: Int
tenDays_day{N}_lailaha: Int
tenDays_day{N}_lahawla: Int
tenDays_day{N}_fasting: Boolean
tenDays_day{N}_takbeer: Int
tenDays_day{N}_sadaqah: Boolean
```

### Firebase RTDB Structure
```
ten_days_dhul_hijjah/
├── {periodKey}/                    # e.g. "2026-06-05" (start date)
│   ├── players/{uid}/
│   │   ├── uid: String
│   │   ├── totalScore: Int
│   │   ├── updatedAt: Long
│   │   └── countryCode: String
│   └── leaderboard/               # server-populated top-N
│       └── {rank}/
│           ├── uid, totalScore, countryCode
```

**Period key**: The date of day 1 of the 9-day period (YYYY-MM-DD, Cairo timezone).

### Sync Strategy
- Sync total score to Firebase on each action (debounced, same pattern as main salawat screen)
- Only `totalScore` synced — individual action breakdowns are local only
- Leaderboard populated by server script (same pattern as `populate-leaderboard.js`)

## Navigation

- `App.kt` gets new state: `showTenDays: Boolean`
- New composable: `TenDaysScreen.kt`
- New ViewModel: `TenDaysViewModel.kt`
- New store: `TenDaysStore.kt`
- New Firebase client method for ten_days node

## Auto-Play Takbeer Audio

- **Toggle** in the screen (near takbeer section or in a settings area at top)
- When enabled: plays takbeer audio clip every 10 minutes in background
- Uses platform notification/alarm mechanism to trigger playback
- Respects device silent mode
- Toggle persisted locally (`tenDays_autoPlayTakbeer: Boolean`)
- Stops automatically when the 9-day period ends

## Time Boundaries

- Screen accessible only during the 9-day active period
- Day transitions at midnight Cairo time
- After period ends: tab icon hidden, data preserved for history
- Determining active period: hardcoded start date for this year (configurable for next year)

## Leaderboard

- Completely separate from main salawat leaderboard
- Same visual style and server-side population logic
- Shows on the ten days screen itself (mini version at bottom)
- Tapping leaderboard could expand to full view (stretch goal)

## Files to Create/Modify

### New files:
- `ui/tendays/TenDaysScreen.kt` — screen composable
- `presentation/TenDaysViewModel.kt` — state + logic
- `presentation/TenDaysUiState.kt` — state data class
- `data/tendays/TenDaysStore.kt` — local persistence
- `data/tendays/TenDaysFirebaseClient.kt` — Firebase sync

### Modified files:
- `App.kt` — add navigation state + tab icon
- `di/AppModule.kt` — register new DI bindings
- `ui/MohamedLoversScreen.kt` — add ten days tab icon to top bar
