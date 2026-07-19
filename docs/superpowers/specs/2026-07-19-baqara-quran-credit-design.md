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

The whole method runs under the ViewModel's existing `syncMutex` — see
"Concurrent credits" below.

1. **Fetch the Quran remote baseline** via `fetchUserCount`, then
   `updateRemoteBaseline`. This is the correctness constraint: without it, a
   stale local count overwrites a higher remote total.
2. `quranStore.addToday(today, ALBAQARA_PAGE_COUNT)` — 48 as a named constant,
   well under `CHALLENGE_MANUAL_DAILY_CAP` (10,000).
3. `challengeBadgeStore.recordActivity(ChallengeType.QURAN, today)` and
   `recordWin(...)` — 48 clears `QURAN_CHALLENGE_DAILY_GOAL` (1), so this earns
   the badge and keeps the Quran streak alive.
4. **Only if step 1 succeeded**, `writeUserDay(...)` with the current streak;
   `onSyncSuccess` on success.
5. Refresh `quranTodayCount` from the store so a subsequent dialog is accurate.

### Baseline fetch failure must not write

`writeUserDay` is a blind overwrite — `updateChildren(COUNT_KEY to count)`, no
transaction and no server-side max. So writing on a stale baseline destroys
data: local 0, remote 30, credit 48 → writes 48, and the user loses 30 pages.

Therefore **a failed baseline fetch skips the immediate write entirely.** The 48
pages still land in the local pending ledger via `addToday`; the next visit to
the Quran screen reconciles them through the normal
`onScreenEntered` → `updateRemoteBaseline` → `onScreenLeft` path. This mirrors
how the app already treats offline taps and loses nothing.

### Concurrent credits

`creditQuranPages` writes asynchronously, so without serialization two credits
can corrupt the pending ledger: confirm dialog 1 → dismiss → tap → confirm
dialog 2 while write 1 is in flight. Both call `addToday` (pending 96), then
write 1's `onSyncSuccess` does `putInt(KEY_PENDING, 0)` and wipes write 2's 48.

Guard with the ViewModel's existing `syncMutex`. It also guards Baqara's
`onScreenLeft`, which is harmless — that write targets a different node, and
serializing the two costs nothing. This is the same reason `QuranChallengeViewModel`
wraps its own writes in a mutex.

### Keeping the before→after row fresh

`onScreenEntered` reads `quranStore.todayCount` into UI state and refreshes it
from remote in the background, so the dialog opens against an accurate number.

`creditQuranPages` must also re-read it after crediting (step 5). Without that,
the second reading's dialog still shows `30 → 78` instead of `78 → 126` —
stale precisely when the row is doing its job.

### Badge count on repeat credits

`recordWin` is idempotent per Cairo day, so two credits in one day award **+1**
Quran badge, not +2. Streak and activity are unaffected. This is intended, not a
bug — it matches how every other challenge counts a daily win.

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

The second data-loss case: **a failed baseline fetch must not write.** Given a
remote count of 30 and a `fetchUserCount` that fails, crediting 48 must issue no
`writeUserDay` at all — the pages stay pending locally. Asserting on the absence
of the write is the point.

Supporting cases:

- Credit adds exactly 48 to today's Quran total
- Credit records a Quran win and keeps the streak alive
- Dismissing leaves the Quran count untouched
- `onUndoTap` after a credit reduces Baqara but not the Quran count
- Two readings, both credited, yield 96 pages **and** `quranTodayCount` updates
  between the two dialogs (48 after the first, not still 0)
- Two credits in one day award +1 badge, not +2

## Out of scope

- Crediting other surahs by page count
- Retroactively crediting past Al-Baqara readings
- Any Firebase schema or `database.rules.json` change — this writes through the
  existing Quran challenge node with existing validated fields
