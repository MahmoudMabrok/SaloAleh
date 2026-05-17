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
- **QR handoff with smart deduct** — the options page renders a QR with payload:
  ```json
  {"v":2,"type":"saloaleh-submit","round":"2026-05-22","count":1234,
   "country":"EG","src":"chrome-ext","ts":1747...}
  ```
  The extension snapshots the count at QR-render time. After scanning
  on the phone, the user presses "تم الإرسال — خصم العدد" and the
  extension subtracts the **snapshotted** count from the current round
  total — so any taps made between QR render and the click are kept.
  The extension does **not** call Firebase: the mobile app is the only
  party that writes to RTDB.

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
  "schemaVersion": 3,
  "uid": "<sha256 hex of a per-profile uuid>",
  "countryCode": "EG",
  "countryAuto": true,
  "rounds": { "2026-05-22": { "count": 1234 } },
  "pendingSubmission": {
    "roundKey": "2026-05-22",
    "count": 1234,
    "createdAt": 1747000000000
  },
  "lastSubmittedAt": 1747000050000
}
```

The UID exists to keep parity with the mobile identity model (SHA-256 of
a persisted UUID) but is intentionally **not** included in the QR payload
— the mobile app must attribute the score to its own UID, otherwise it
would write to a row no real user owns.

## Mobile-side contract (to implement)

The mobile app needs to:

1. Open a QR scanner and parse the JSON payload.
2. Validate `type === "saloaleh-submit"` and `v === 2`.
3. Reject if `round` is not the current round, or if the same payload
   was already consumed (suggest hashing `(round, count, ts)` and
   keeping a local consumed-set).
4. Call the existing `MohamedLoversRepository.incrementSession` with
   `delta = payload.count`, attributing the write to the mobile's own
   UID. The extension's UID is intentionally not in the payload.

After the user sees the score appear on the phone, they switch back to
the browser and click "تم الإرسال — خصم العدد" — the extension does the
local deduct on its own. Because there's no back-channel from Firebase
to the extension, an automatic reset would have required the extension
to read RTDB; that's explicitly out of scope.

## Not included

- No Firebase access of any kind from the extension. The mobile app is
  the only writer/reader.
- No QR scanning on the mobile side — that's a separate change in
  `commonMain` / platform code (CameraX + ML Kit on Android,
  AVFoundation on iOS).
