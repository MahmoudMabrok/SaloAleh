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
- **QR handoff** — the options page renders a QR with payload:
  ```json
  {"v":1,"type":"saloaleh-submit","round":"2026-05-22","count":1234,
   "country":"EG","src":"chrome-ext","ts":1747...}
  ```
  Once the mobile app has scanned and submitted, click
  "تأكيد الإرسال والتصفير" to reset the local count for this round. The
  mobile app uses its own UID when writing to Firebase — the extension's
  UID is never sent.

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
  "schemaVersion": 1,
  "uid": "<sha256 hex of a per-profile uuid>",
  "countryCode": "EG",
  "countryAuto": true,
  "rounds": { "2026-05-22": { "count": 1234 } },
  "lastSubmittedRound": null
}
```

The UID exists to keep parity with the mobile identity model (SHA-256 of
a persisted UUID) but is intentionally **not** included in the QR payload
— the mobile app must attribute the score to its own UID, otherwise it
would write to a row no real user owns.

## Not included

- No direct Firebase RTDB writes from the extension (would need bundled
  credentials and lax security rules). All persistence to the leaderboard
  goes through the mobile app's existing `incrementSession` flow.
- No QR scanning on the mobile side — that is a separate change to
  `commonMain` / platform code (CameraX + ML Kit on Android, AVFoundation
  on iOS).
