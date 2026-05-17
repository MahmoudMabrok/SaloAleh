# SaloAleh — Chrome extension

Companion to the SaloAleh KMP mobile app. Counts salawat taps from the
browser, scoped to the current weekly round, and hands the total to the
mobile app via a QR code.

## Features

- **Toolbar popup** — shows the auto-detected country code, the current
  round key, the count for this round, and a big +1 button. Highlights
  Friday's bonus window (before 18:00 Cairo).
- **Floating window** — "نافذة عائمة" opens the popup as a detached
  Chrome window (`chrome.windows.create`, type `popup`) so the counter
  stays available while you browse. Chrome does not expose a true
  always-on-top API to MV3 extensions, so this is the closest portable
  approximation. A content-script overlay on the page itself was avoided
  because it would require `<all_urls>` host permission.
- **Per-round storage** — round key is computed locally with the same
  algorithm as the mobile app (`CompetitionWindowUtils.kt`): the ISO
  date of the next Friday 18:00 in `Africa/Cairo`, accounting for DST.
- **QR handoff with auto-reset** — the options page renders a QR with payload:
  ```json
  {"v":2,"type":"saloaleh-submit","round":"2026-05-22","count":1234,
   "country":"EG","nonce":"<32 hex>","src":"chrome-ext","ts":1747...}
  ```
  The extension polls Firebase RTDB at `mohamed_lovers/handoffs/<nonce>`
  (4s while the options page is open, plus a 1-minute `chrome.alarms`
  job in the service worker) and decrements the local round count by
  `record.count` once the mobile app has written its confirmation. Taps
  made after the QR was generated are preserved (subtraction, not
  zeroing). A manual fallback button is available for offline cases.
  The extension's UID is never sent in the payload — the mobile app
  attributes the score to its own UID when writing.

## Install (dev)

1. Open `chrome://extensions`, enable Developer mode.
2. Click "Load unpacked" and pick this folder.
3. Pin the extension to the toolbar.

## Layout

```
chrome-extension/
├── manifest.json              MV3 manifest
├── background.js              service worker — opens floating window
├── popup.{html,css,js}        toolbar popup UI
├── settings.{html,css,js}     options page with QR + reset
├── lib/
│   ├── round.js               next-Friday-18:00-Cairo round key
│   ├── country.js             locale → ISO country
│   ├── state.js               chrome.storage.local wrapper + QR payload
│   └── qrcode.js              vendored qrcode-generator 1.4.4 (MIT)
└── icons/
    ├── icon48.png             reused from Android mipmap-mdpi
    └── icon128.png            reused from Android mipmap-xxxhdpi
```

## Storage shape

`chrome.storage.local.saloAleh`:

```json
{
  "schemaVersion": 2,
  "uid": "<sha256 hex of a per-profile uuid>",
  "countryCode": "EG",
  "countryAuto": true,
  "rounds": { "2026-05-22": { "count": 1234 } },
  "pendingHandoff": {
    "nonce": "<32 hex>",
    "roundKey": "2026-05-22",
    "count": 1234,
    "createdAt": 1747000000000
  },
  "lastApplied": {
    "nonce": "<32 hex>",
    "roundKey": "2026-05-22",
    "count": 1234,
    "byUid": "<mobile sha256 hash>",
    "at": 1747000050000,
    "manual": false
  }
}
```

The UID exists to keep parity with the mobile identity model (SHA-256 of
a persisted UUID) but is intentionally **not** included in the QR payload
— the mobile app must attribute the score to its own UID, otherwise it
would write to a row no real user owns.

## Mobile-side contract (to implement)

For auto-reset to work end-to-end, the mobile app needs to:

1. Open a QR scanner and parse the JSON payload above.
2. Validate `type === "saloaleh-submit"` and `v === 2`.
3. Refuse stale payloads — reject if `round` is not the current round,
   or if `nonce` was already consumed locally.
4. Call the existing `MohamedLoversRepository.incrementSession` (or
   equivalent) with `delta = payload.count`, attributing it to the
   mobile's own UID.
5. **After** that write succeeds, write a one-shot handoff confirmation
   to RTDB at `mohamed_lovers/handoffs/<nonce>`:
   ```json
   {
     "consumedAt": {".sv": "timestamp"},
     "byUid": "<mobile sha256 hash, 64 chars>",
     "round": "<roundKey from payload>",
     "count": <count from payload>
   }
   ```
6. Treat the write as fire-and-forget; if the chrome extension never
   sees it, the user can use the manual-confirm button.

The security rules in `database.rules.json` accept this write only when
the path doesn't already exist (write-once), the nonce is 16–64 chars,
and the schema validates — so a bad QR can't poison another nonce.

## Not included

- No direct Firebase RTDB writes from the extension to player rows. Only
  the handoff path is polled (REST GET, no auth). All score persistence
  goes through the mobile app.
- No QR scanning on the mobile side — that's a separate change in
  `commonMain` / platform code (CameraX + ML Kit on Android,
  AVFoundation on iOS). See the contract above for the exact payload.
- No periodic cleanup of stale handoff records. Each entry is ~80 bytes;
  a cron in `scripts/` can be added later to prune handoffs older than
  72h if storage cost ever becomes a concern.

## Deploying the security rule change

The `handoffs` rule was added to `database.rules.json`. Deploy with:

```bash
firebase deploy --only database
```

Without that deploy, the mobile app's handoff write will be rejected and
auto-reset won't work (the manual-confirm button will still function).
