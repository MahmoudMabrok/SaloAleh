# ADR-0001: Segmented Push Notifications via Server-Side Script

**Date:** 2026-05-08  
**Status:** Accepted  
**Deciders:** mahmoudmabrok

---

## Context

Three compounding retention gaps were identified:

1. **Day-1 drop-off** — new users install, open once, never return.
2. **Weekly churn** — users active 1–2 rounds then disappear.
3. **Daily disengagement** — users miss streaks and Friday bonus taps.

The app already stores per-user activity in Firebase RTDB (`installDate`, `lastOpenDate`, `fcmToken`) written by the client on each launch. FCM tokens are available. The existing infrastructure — RTDB, FCM Admin SDK, and GitHub Actions — can support server-side targeting without adding new services.

Two approaches were considered:

| Approach | Description |
|---|---|
| **A — FCM topic broadcasts** | All users in a single topic. Simple but no per-user personalization. |
| **B — Server-side segment evaluation (chosen)** | Script reads RTDB, evaluates per-user conditions, sends targeted FCM messages. |

---

## Decision

Use a Node.js script (`scripts/notify-users.js`) that runs every 6 hours via GitHub Actions. The script:

1. Reads all user records from `mohamed_lovers/users/`.
2. Resolves round context (active round key, finality) using Cairo-timezone logic that mirrors the client's `CompetitionWindowUtils.kt`.
3. Evaluates each user against five segments in priority order (first match wins).
4. Sends FCM messages using Firebase Admin SDK (`admin.messaging().send()`).
5. Writes a debounce flag (`lastRivalNotifDate`) back to RTDB for the rival-alert segment.

### Notification Segments (priority order)

| # | Segment | Condition | Kill switch |
|---|---|---|---|
| 1 | Day-1 lapsed | `daysInstalled == 1 && daysInactive >= 1` | — |
| 2 | Mid-week inactive | `!isFinal && daysInactive >= 3` | `NOTIF_MIDWEEK_ENABLED` |
| 3 | Round-end recap | `isFinal && lastOpenDate < today` | — |
| 4 | Streak at risk | `!isFinal && lastOpenDate == yesterday` | — |
| 5 | Rival alert | `!isFinal && gap to 10th place <= threshold` | `NOTIF_RIVAL_ENABLED` |

Segments 1–4 are mutually exclusive (early return). Segment 5 fires only when no earlier segment matched and is debounced to once per user per day.

### Key Design Choices

**Round key mirrors client logic.** `cairoRoundKey()` in the script reproduces `CompetitionWindowUtils.kt`'s next-Friday-18:00 calculation so server and client agree on which round is active.

**Rival alert reads leaderboard once.** The 10th-place score is fetched a single time before the user loop to avoid N+1 RTDB reads. Per-user scores are fetched only for users that pass the coarse filter (`rivalEnabled && !isFinal && tenthPlaceScore != null`).

**Kill switches via env vars.** `NOTIF_RIVAL_ENABLED` and `NOTIF_MIDWEEK_ENABLED` allow disabling noisy segments without a deploy. `NOTIF_RIVAL_THRESHOLD` controls the gap sensitivity.

**Per-user debounce in RTDB.** Rival alerts write `lastRivalNotifDate` (Cairo ISO date) back to the user node. This is checked at the start of each 6h run, preventing multiple rival alerts on the same calendar day.

**Errors are non-fatal.** Each `messaging().send()` call is `.catch()`-ed individually. A bad token or revoked FCM registration does not abort the whole run.

---

## Consequences

**Positive:**
- Personalized messages without a dedicated backend service — no new infrastructure cost.
- Five distinct retention use cases addressed in one script.
- Kill switches allow rapid response to over-notification without a code deploy.
- Cairo-timezone awareness matches the app's competition clock exactly.

**Negative / Trade-offs:**
- GitHub Actions cron has ±15–30 min non-determinism; the 6h cadence absorbs this.
- Script reads all user records on every run — will need pagination or indexing if `users/` grows beyond ~10k entries.
- Rival-alert debounce relies on a RTDB write; if that write fails silently, a user could receive duplicate rival alerts within the same day.
- No analytics on notification open rates from this script — requires separate Firebase Analytics or FCM delivery reporting setup.

---

## RTDB Paths Touched

| Path | Access |
|---|---|
| `mohamed_lovers/users/` | Read (all users) |
| `mohamed_lovers/{roundKey}/leaderboard/10` | Read (10th-place score) |
| `mohamed_lovers/{roundKey}/players/{uid}/totalCount` | Read (user score for rival check) |
| `mohamed_lovers/users/{uid}/lastRivalNotifDate` | Write (debounce flag) |
