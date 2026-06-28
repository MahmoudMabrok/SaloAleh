# Floating Bubble Revamp — Design Spec

**Date:** 2026-06-28  
**Scope:** Android-only (`androidMain`) — `FloatingBubbleView.kt` + `FloatingBubbleService.kt`

---

## Goal

Revamp the Mohamed Lovers floating bubble with a richer Islamic aesthetic, remove the X button, add a long-press drag-to-action UX, a click pulse animation, and proper screen-edge clamping (32 dp margin).

---

## Visual Design

- **Bubble size:** 72 dp circle
- **Background:** radial gradient — deep green center (`#1B5E20`) to navy outer (`#0D1B4B`)
- **Ring:** gold stroke border (~3 dp, `#FFD700`) via `GradientDrawable` stroke
- **Glow:** elevation ~8 dp + optional gold `ColorFilter` shadow layer
- **Count text:** 24 sp, bold, gold (`#FFD700`)
- **"صلوات" label:** 9 sp, soft white with 70% alpha, above count
- **No ✕ button** anywhere on the bubble
- **Tooltip** (existing behavior retained): Arabic prayer text card, shows on timer

---

## Interaction Model

### Tap → count + pulse animation
- Increment salawat count (existing `handleTap()` logic unchanged)
- Animate bubble: `scaleX`/`scaleY` 1.0 → 1.25 → 1.0, duration ~200 ms, interpolator `OvershootInterpolator`

### Long press → reveal action targets
- Threshold: **400 ms** held without exceeding drag threshold (10 px)
- Two circular targets (48 dp each) slide out below/flanking the bubble with alpha + translation animation (~250 ms)
  - **Left target:** close — dark red (`#B71C1C`), ✕ icon text
  - **Right target:** open app — gold (`#FFD700`), ↗ icon text
- Haptic feedback on reveal (`HapticFeedbackConstants.LONG_PRESS`)

### Drag-into-target → fire action
- While targets are visible, on `ACTION_MOVE` compute bubble center vs target center
- If distance < **40 dp**: highlight target (scale to 1.2), fire action on `ACTION_UP`
  - Close target → `stopSelf()`
  - Open app target → launch `MainActivity` with `Intent.FLAG_ACTIVITY_NEW_TASK`
- If `ACTION_UP` without entering any target → slide targets back out, bubble stays

### Movement / clamping
- Free drag anywhere; on `ACTION_UP` (non-tap, non-action) clamp position so bubble stays **≥ 32 dp** from all four screen edges
- Uses `WindowManager.defaultDisplay.getRealMetrics()` for screen bounds

---

## Implementation Plan (files)

| File | Changes |
|------|---------|
| `FloatingBubbleView.kt` | Full rewrite: gold-ring gradient, remove `closeBtn`, add `showActionTargets()` / `hideActionTargets()`, expose `onOpenApp` callback, add `animateTap()` |
| `FloatingBubbleService.kt` | Replace close-button hit-test with long-press detector; drag-into-target logic; 32 dp edge clamping; wire `onOpenApp` |

No new files needed. No changes to `commonMain`, `iosMain`, or any other layer.

---

## Out of Scope

- iOS changes
- Tooltip content or timing changes
- Any backend / Firebase changes
