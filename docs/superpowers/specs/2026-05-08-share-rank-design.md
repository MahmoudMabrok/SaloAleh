# Share Rank Feature — Design Spec

**Date:** 2026-05-08  
**Status:** Approved

---

## Overview

Users can share their current rank and score as a generated image via the OS share sheet. The share card uses the C3 "Illuminated Page" (المخطوطة) design — cream/gold Islamic ornamental style with a gold header/footer band, corner rosettes, and a central rank medallion.

---

## Entry Point

A "مشاركة رتبتي" button is placed inside `MohamedLoversInfoSheet`, below the `TotalsCard`. Tapping it generates the share image and triggers the native OS share sheet.

---

## Share Card Design (C3 — المخطوطة)

Fixed dimensions: **400×620 dp** (2:1 portrait ratio, renders at 2× density for crisp export).

Layout top to bottom:

| Zone | Content |
|---|---|
| **Header band** (gold gradient) | `✦ ✦ ✦` · `صلى الله عليه وسلم` |
| **Rank medallion** | `#N` rank in circular bordered medallion with dashed outer ring |
| **Player count** | `من أصل {roundPlayerCount} مشارك` |
| **Divider** | Gold gradient horizontal rule |
| **Display name** | `displayTag` |
| **Score row** | Left: user score (`صلاتي هذا الأسبوع`) · Right: community total (`مجموع الجولة`) |
| **Footer band** (gold gradient) | `✦ ✦ ✦` · `© SaloAleh · صلِّ عليه` |
| **Corner rosettes** | Four CSS/Compose geometric rosettes at inner corners |

---

## Data Sources

All data comes from existing `MohamedLoversUiState` — no new Firebase calls.

| Card field | UiState source |
|---|---|
| Rank | `selfEntry.rank` |
| Display name | `selfEntry.displayTag` |
| User score this round | `selfEntry.totalCount` (user's own player node count) |
| Community round total | `roundTotal` |
| Player count | `roundPlayerCount` |

---

## Architecture

### New files

**`ShareCard.kt`** (`commonMain/ui/components/`)  
Pure Compose composable. Accepts `ShareCardData`. No ViewModel dependency — just renders the C3 card layout. Used both for the exported bitmap and (optionally) for preview.

```kotlin
data class ShareCardData(
    val displayTag: String,
    val rank: Int,
    val userScore: Int,
    val roundTotal: Int,
    val roundPlayerCount: Int,
)
```

**`ShareManager`** (`expect`/`actual`)  
`expect fun shareImage(context: PlatformContext, data: ShareCardData)`

- **Android actual:** Render `ShareCard` into an off-screen `ComposeView`, capture as `Bitmap`, write to cache `FileProvider` URI, launch `Intent.ACTION_SEND` with `image/png`.
- **iOS actual:** Render `ShareCard` via `ImageRenderer` (Compose Multiplatform), produce `UIImage`, present `UIActivityViewController`.

### Modified files

**`MohamedLoversInfoSheet.kt`**  
Add share button row below `TotalsCard`. Calls `ShareManager.shareImage(...)` with data mapped from `uiState`.

---

## Share Button

- Style: gold gradient pill button (`مشاركة رتبتي` + share icon)
- Disabled state: when `selfEntry.rank == 0` (rank not yet loaded)
- Placement: below `TotalsCard`, above bottom safe area inset

---

## Error Handling

- If bitmap capture fails: show a `Snackbar` with generic error message. No crash.
- If `rank == 0`: button is disabled with reduced opacity — no share triggered.

---

## Out of Scope

- Sharing via deep link or URL
- Animated share card
- Custom share message text editing
