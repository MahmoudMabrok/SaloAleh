# round-results

Written automatically by `scripts/aggregate-all-time.js` (workflow
`.github/workflows/aggregate-all-time.yml`) right after each weekly Mohamed Lovers
round closes, and committed back to `main` with `[skip ci]`.

- `<round>.json` — one file per closed round (round key = the Friday the round ended).
- `latest.json` — the newest round's summary (what the poster is built from).
- `latest.md` — the same, as an Arabic table.

Shape:

```json
{
  "round": "2026-10-30",
  "closedAt": "2026-10-30T17:10:00+02:00",
  "roundTotal": 123456,
  "playerCount": 321,
  "top10Sum": 45678,
  "top10": [
    { "rank": 1, "name": "...", "countryCode": "EG", "total": 9876 }
  ]
}
```

Public repo: no uids, winner codes, emails or phone numbers are ever written here.
A player who hid their score in the app (`scoreMasked`) is listed without `total`
(`"scoreMasked": true`) and is excluded from `top10Sum` (`top10MaskedCount` says how
many were excluded). A missing nickname is shown as `مجهول`.
