# Diff-only rank writes (RTDB + Firestore mirror)

**Date:** 2026-07-23
**Status:** Approved
**Scope:** `scripts/leaderboard-utils.js`, `scripts/firestore-utils.js`, `scripts/populate-leaderboard.js` (call sites), tests

## Problem

The Firebase project `kamapp-3b3ac` is on the **Spark (free) plan** → hard cap of
**20,000 Firestore document writes/day**. `populate-leaderboard.js` runs every 30 min
(48×/day) and re-mirrors **every participant's rank** across the main round (635 players)
plus 7 challenge boards — even when nothing changed. This blows the daily write cap
(~31.5k/day from the main board alone), producing `RESOURCE_EXHAUSTED` errors.

Two secondary problems, both fixed here as a byproduct:

- **500-op batch limit:** `mirrorMohamedLoversRound` commits all 635 players in one
  un-chunked batch (656 ops > Firestore's hard 500-op limit) → it fails every run once a
  board crosses ~480 participants. Any challenge board crossing ~500 will do the same.
- **RTDB egress:** writing every rank every run also pushes no-op updates to every
  listening client, consuming the Spark 10 GB/month download cap.

## Goal

Write **only ranks that changed since the last run**, to both stores, for all 8 boards.
Target: Firestore ~10–12k writes/day (comfortably under 20k), which unblocks Phase 2
(switching reads to Firestore).

## Design

Two independent diffs, one per store, because the stores can drift (the Firestore mirror
has been failing) — each diffs against *its own* last-written state.

### RTDB diff — baseline is free

The populate flow already reads each participant's full node, which carries its current
`rank`. Diff the newly computed rank against that stored rank and write only the changes.
No snapshot needed.

- **Challenges** (`leaderboard-utils.js` → shared `buildDailyCountChallengeRanking`):
  each user already carries `currentRank` (populated from `data.rank` in the populate
  read). Change `rankUpdates` construction to:
  - active user: include `.../rank = user.rank` **only if** `user.rank !== currentRank`;
  - inactive user (count 0): include `.../rank = null` **only if** `currentRank != null`
    (i.e. it actually has a rank to clear).
  One edit to the shared builder covers all 7 count-based challenges.
- **Main round** (`leaderboard-utils.js` → `populateMohamedLoversRound`): also capture
  `data.rank` into each `allPlayers` entry as `currentRank`, then add
  `.../players/{uid}/rank = i+1` to `rankUpdates` **only if** `i+1 !== currentRank`.

Leaderboard / dailyLeaderboard / roundTotal / notifications / drop-out detection are
unchanged (they use the full `allPlayers`/`rankedUsers` list and `oldRanks` from the
leaderboard node, not `rankUpdates`).

### Firestore diff — snapshot doc per board

Firestore is not read during populate, so reading all rank docs to diff would burn the
read quota. Instead keep one bookkeeping doc per board+period.

New shared helper in `firestore-utils.js`:

```
writeChangedRanks(userCollectionRef, snapshotDocRef, newRanks, label)
  1. read snapshotDocRef → oldRanks (JSON.parse of a string field; {} if absent)
  2. changed = uids where newRanks[uid] !== oldRanks[uid]
  3. write { rank } to userCollectionRef.doc(uid) for each changed uid, merge,
     chunked at 490 (respects the 500-op batch limit)
  4. only after all commits succeed → overwrite snapshotDocRef with
     { ranksJson: JSON.stringify(newRanks), count, updatedAt: Date.now() }
  5. log "<label> ranks: <changed>/<total> changed"
```

- Baseline stored as a **JSON string field**, not a map — a 635-key map would trigger
  Firestore per-key auto-indexing on every write. ~46 KB at 635 players; well under the
  1 MiB doc limit (headroom to ~10k players before sharding matters).
- Snapshot location: `…/{key}/_meta/rankSnapshot` (isolated `_meta` subcollection doc under
  each round/day doc). Admin SDK bypasses security rules, so no `firestore.rules` change.

Per-mirror change (all 8): replace the per-user rank loop with a `writeChangedRanks(...)`
call. The day-doc `.set()` and the ≤10/≤20 leaderboard docs stay always-written in their
own small batch (no longer carrying the 635 rank writes).

### Edge cases

- **Cold start** (first run / new challenge day): snapshot absent → all ranks are
  "changed" → all written once (chunked), snapshot created; deltas thereafter.
- **Partial failure:** if a commit throws mid-run, the snapshot is *not* updated, so the
  next run re-detects and re-writes those ranks. Redundant but correct.
- **Departed players:** drop out of the next snapshot; their stale Firestore rank docs are
  harmless (Phase-2 cleanup). RTDB inactive-clear handled by the `null` diff above.
- **Deleted day doc** (challenge aggregate+clean): its `_meta/rankSnapshot` subcollection
  doc is orphaned but harmless; each new day starts a fresh snapshot.

## Testing

`node:test` (matching the existing convention):

- `writeChangedRanks`: cold start writes all; identical second run writes zero (only the
  snapshot); single rank change writes exactly one doc; >490 changes chunk into multiple
  commits; a commit failure leaves the snapshot unchanged.
- `buildDailyCountChallengeRanking`: `rankUpdates` contains only changed ranks; a rank that
  matches `currentRank` is omitted; an inactive user with `currentRank != null` gets a
  `null` clear, one with `currentRank == null` is omitted.

## Non-goals

- Diffing the ≤10-entry leaderboard subcollections (they carry per-run `rankChange`;
  ~7.7k writes/day total, within budget).
- Upgrading to Blaze / changing cadence (separate levers, not needed once under the cap).
