# Crediting Al-Baqara readings to the Quran challenge

**Date:** 2026-07-19
**Status:** Approved, ready for implementation planning

## Problem

Surah Al-Baqara is 48 pages of the Mushaf. A user who records a completed
Al-Baqara reading has, by definition, read 48 Quran pages — but the Quran
challenge knows nothing about it. Today they must open the Quran screen and
enter 48 pages by hand through the manual-entry sheet.

Auto-crediting without asking would be wrong: many users already log those
pages manually, and a silent credit would double-count their day.

## Solution

After each recorded Al-Baqara reading, prompt the user to credit 48 pages to
the Quran challenge. Confirming credits the pages; dismissing does nothing.

## Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Trigger | Auto-prompt on every `onReadTap()` | Discoverable without a new button; nobody has to find a secondary action |
| Repeat use | Repeatable — dialog on every reading | A genuine second reading of Al-Baqara earns its 48 pages |
| Guard against double-count | The dialog's before→after row | A user who already logged 48 sees `48 → 96` and dismisses on sight |
| "Don't ask again" | Not offered | The confirmation *is* the safeguard; suppressing it defeats the feature |
| Undo | Decoupled | `onUndoTap` reverses the Baqara reading only. Credited pages stay credited |
| Offline | Same as every other challenge | Credit lands in the local pending ledger and syncs later |

## The dialog

Fires immediately after the Baqara count increments. Contents:

- Title: "نضيف صفحات البقرة إلى تحدّي القرآن؟"
- Body: states that Al-Baqara is 48 pages, and to dismiss if already logged
- **Before→after row**: today's Quran count and where it would land (`3 ← 51`)
- Primary: "نعم، أضِف 48 صفحة"
- Secondary: "سجّلتها بالفعل"

The before→after row is the load-bearing element. It is the only thing standing
between a user and an accidental double-count, so it must show a *fresh* Quran
count — see below.

## Architecture

`AlBaqaraChallengeViewModel` gains three constructor dependencies —
`QuranChallengeStore`, `QuranChallengeFirebaseClient`, `ChallengeBadgeStore` —
and one method, `creditQuranPages()`.

**Rejected:** resolving a second `QuranChallengeViewModel` from the Baqara
screen. It would write from a Quran remote baseline that this screen never
fetched, so `onSyncSuccess` could clobber a higher server count. Its celebration
state would also be set but never rendered.

### Credit path (order matters)

1. **Fetch the Quran remote baseline** via `fetchUserCount`, then
   `updateRemoteBaseline`. This is the correctness constraint: without it, a
   stale local count overwrites a higher remote total.
2. `quranStore.addToday(today, ALBAQARA_PAGE_COUNT)` — 48 as a named constant,
   well under `CHALLENGE_MANUAL_DAILY_CAP` (10,000).
3. `challengeBadgeStore.recordActivity(ChallengeType.QURAN, today)` and
   `recordWin(...)` — 48 clears `QURAN_CHALLENGE_DAILY_GOAL` (1), so this earns
   the badge and keeps the Quran streak alive.
4. `writeUserDay(...)` with the current streak; `onSyncSuccess` on success.

### Keeping the before→after row fresh

`onScreenEntered` reads `quranStore.todayCount` into UI state and refreshes it
from remote in the background, so the dialog opens against an accurate number
rather than a stale one.

## State

`AlBaqaraChallengeUiState` gains:

- `showQuranCreditDialog: Boolean` — dialog visibility
- `quranTodayCount: Int` — drives the before→after row
- `isCreditingQuran: Boolean` — disables the confirm button mid-write

## Touch list

| File | Change |
|------|--------|
| `presentation/AlBaqaraChallengeViewModel.kt` | 3 deps, `creditQuranPages()`, dialog show/dismiss, Quran count refresh |
| `presentation/AlBaqaraChallengeUiState.kt` | 3 new fields |
| `ui/AlBaqaraChallengeScreen.kt` | Render the dialog |
| `ui/albaqara/AlBaqaraQuranCreditDialog.kt` | New composable |
| `domain/AlBaqaraChallengeModels.kt` | `ALBAQARA_PAGE_COUNT = 48` |
| `di/AppModule.kt` | 3 extra `get()`s (all already `single`s) |
| `composeResources/values{,-en,-ur,-zh}/strings.xml` | New keys ×4 locales |
| `commonTest/.../AlBaqaraChallengeViewModelQuranCreditTest.kt` | New test |

## Testing

The discriminating test: **credit fetches the remote baseline before writing.**
Given a local Quran count of 0 and a remote count of 30, crediting 48 must write
78 — not 48. This is the failure mode that silently destroys user data, and it
is the reason the second-ViewModel approach was rejected.

Supporting cases:

- Credit adds exactly 48 to today's Quran total
- Credit records a Quran win and keeps the streak alive
- Dismissing leaves the Quran count untouched
- `onUndoTap` after a credit reduces Baqara but not the Quran count
- Two readings, both credited, yield 96 pages

## Out of scope

- Crediting other surahs by page count
- Retroactively crediting past Al-Baqara readings
- Any Firebase schema or `database.rules.json` change — this writes through the
  existing Quran challenge node with existing validated fields
