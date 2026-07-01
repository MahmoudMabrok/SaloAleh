---
name: challenge-leaderboard
description: Builds and reuses the SaloAleh server-populated leaderboard pattern for Firebase daily count challenges. Use when adding a new challenge leaderboard, moving ranking out of the app client, or touching Dhikr/Baqiyat leaderboard scripts, rules, or client readers.
---

# Challenge Leaderboard

## Quick Start

For daily count challenges, keep ranking server-owned:

1. Client writes only its own player count and metadata under `<root>/<dateKey>/<playersPath>/<uid>`.
2. `scripts/populate-leaderboard.js` reads the player nodes and writes `rank`, `leaderboard`, `participantCount`, `lastRankedAt`, and any total summary.
3. App clients read the prebuilt `leaderboard` and the current user's server-owned `rank`.
4. Firebase rules allow public reads of leaderboard summaries and deny client writes to server-owned fields.

## Existing Pattern

- Pure ranking logic lives in `scripts/leaderboard-utils.js`.
- Scheduled Firebase writes live in `scripts/populate-leaderboard.js`.
- Dhikr root: `100_challenge/<dateKey>/users`.
- Baqiyat root: `baqiyat_saliha/<dateKey>/players`.
- Leaderboard entries use zero-based Firebase keys for standalone challenges, with a one-based `rank` field inside each entry.
- Rank movement uses `buildOldRankMap()` plus `computeRankChange()` and stores `rankChange` as `new`, `same`, `up`, or `down`.

## Workflow

When adding another challenge:

1. Add a root constant and wrapper helper in `scripts/leaderboard-utils.js` if the generic `buildDailyCountChallengeRanking()` needs a named domain wrapper.
2. Add tests in `scripts/rank-change.test.js` for the root path, player path, count normalization, tie behavior, participant count, and total summary.
3. Add a `populate<Challenge>Today(db)` function in `scripts/populate-leaderboard.js`.
4. Call the population function from `main()` so the existing scheduled workflow runs it.
5. Update `database.rules.json` with readable summary nodes and protected server-owned fields.
6. Update the KMP Firebase client to read `<root>/<dateKey>/leaderboard` instead of ranking all players on device.
7. Fetch the current user's rank from the server-owned player node so users outside top 10 still see their rank.

## Verification

Run:

```bash
node --test scripts/rank-change.test.js
./gradlew :app:compileDebugKotlin
```

For production Firebase changes, also confirm the scheduled `leaderboard-populate.yml` workflow uses `scripts/populate-leaderboard.js`.
