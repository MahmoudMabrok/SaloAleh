# أهل الذكر — Issue #250 implementation checklist

Related issue: [#250](https://github.com/MahmoudMabrok/SaloAleh/issues/250)

This checklist is intentionally kept in the feature PR so the implementation can be reviewed and checked off incrementally.

## External-entry allowance

- [ ] Confirm whether the 10,000 allowance applies per challenge, per user, and per Cairo day.
- [ ] Define whether eligibility is based on the current streak or the best streak.
- [ ] Add a reusable eligibility policy for current streaks of at least 30 days.
- [ ] Raise the eligible challenge external cap to 10,000 without changing in-app tap limits.
- [ ] Preserve the server/day ledger so multiple external submissions cannot exceed the daily cap.
- [ ] Cover 0, 29, 30, 60, 90, and broken-streak cases with tests.

## Daily أهل الذكر snapshot

- [ ] Identify the authoritative Firebase activity/streak data for every challenge.
- [ ] Include all current challenges: dhikr, baqiyat, istighfar, zabad, quran, ghars, alf_hasana, kalimat, hawqala, and salawat.
- [ ] Define the JSON schema with `generatedAt`, `date`, `source`, and per-challenge `30_days`, `60_days`, and `90_days` lists.
- [ ] Restrict published identity fields to safe public fields; never publish tokens, backup codes, or private profile data.
- [ ] Implement an idempotent Node.js snapshot generator.
- [ ] Validate the generated JSON before publication and keep the last valid snapshot on failure.
- [ ] Add generator tests for thresholds, empty lists, duplicate users, missing data, and repeated runs.

## Workflow and publication

- [ ] Add a daily scheduled GitHub Actions workflow.
- [ ] Add manual dispatch with an optional Cairo date and dry-run mode.
- [ ] Log counts for each challenge and milestone.
- [ ] Commit/publish the JSON atomically so the app never reads a partial file.
- [ ] Add retry/error handling for Firebase reads and repository updates.
- [ ] Add the app-side reader with cache, timeout, malformed-file handling, and an empty-state fallback.
- [ ] Add app UI for browsing أهل الذكر by challenge and milestone.
- [ ] Run Node tests, JSON validation, Firebase rules tests, and the relevant Android/Kotlin tests.

## Review and rollout

- [ ] Review privacy and public-identity requirements before enabling publication.
- [ ] Run one manual dry-run and compare the snapshot with Firebase data.
- [ ] Verify the daily workflow after merge.
- [ ] Update Issue #250 and this checklist as each milestone is completed.
