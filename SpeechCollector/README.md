# Arabic Speech Dataset Collector

A production-ready, mobile-first Google Apps Script Web App for collecting short Arabic Dhikr recordings. Audio is stored in Google Drive, metadata is written to Google Sheets, and no separate backend or paid service is required.

The app collects only the recording, basic technical metadata (browser, platform, language, duration, sample rate, and server timestamp), and a random code the browser generates for itself so the recordings of one device can be told apart from another's. It does **not** request or store a name, email address, phone number, location, or IP address.

## What is in this folder

```text
SpeechCollector/
├── Code.gs             Apps Script backend source
├── Index.html          HTML shell
├── app.js              Browser recording and upload logic
├── styles.css          Mobile-first visual design
├── config.ts           Single editable configuration source
├── appsscript.json     Apps Script manifest source
├── build.mjs           Dependency-free build script
├── verify.mjs          Dependency-free static and validation checks
├── ui.test.mjs         Recorder UI behaviour tests (DOM stub, no dependencies)
├── dist/               Files ready to upload to Apps Script, plus voice.html
└── README.md
```

Google Apps Script projects accept only `.gs`, `.html`, and manifest files. They cannot directly deploy `config.ts`, `app.js`, or `styles.css`. The build script reads `config.ts`, injects configuration into the backend, embeds JavaScript and CSS into `Index.html`, and writes a deployable three-file project to `dist/`.

It also writes `dist/voice.html`, a self-contained copy for static hosting. **This is the page volunteers must use.**

## Why recording does not work on the `/exec` URL

Apps Script never serves a web app as a top-level document. `/exec` returns a wrapper page that embeds the project's HTML in a `googleusercontent.com` sandbox iframe, and that iframe is not granted the `microphone` permission. A browser refuses `getUserMedia()` in a frame without the delegated permission **before** it would prompt, so the page reports a microphone failure and no permission dialog is ever shown. Nothing in the Apps Script project can grant the permission to its own frame.

The fix is to serve the recorder from a normal page and keep Apps Script as the upload backend only:

- `dist/voice.html` is deployed to GitHub Pages by `.github/workflows/deploy-pages.yml` and is published at `deployment.standaloneUrl`. It is a top-level page, so the microphone prompt appears normally.
- It uploads to `deployment.webAppUrl` (the `/exec` URL) with a `text/plain` body, which is a CORS-simple request; `doPost` handles it exactly as before.
- The `/exec` page still works as a landing page: when it detects that the microphone is blocked by permissions policy, it disables recording and shows an **فتح صفحة التسجيل** button that opens the standalone page. That keeps already-shipped app builds that link to `/exec` usable.

Set both URLs in `deployment` in `config.ts` whenever the deployment or the Pages site moves.

No npm packages, frameworks, APIs, or external assets are used. The build requires only Node.js 18 or newer.

## 1. Configure the collector

Open `config.ts` in a text editor. This is the single source for:

- The phrase list and phrase IDs
- Drive folder name or folder ID
- Spreadsheet name or spreadsheet ID
- Sheet name and column order
- Minimum/maximum recording duration and upload-size limit
- Accepted audio MIME types and preferred sample rate
- Arabic UI text
- Theme colors, page language, direction, and timezone

The phrase list is the short spoken Dhikr used by the repository's active Dhikr,
Baqiyat, Istighfar, Zabad, and Salawat challenges, sourced from the Arabic
resources in `app/src/commonMain/composeResources/values/strings.xml`. Quran and
Al-Baqara reading prompts are not included because they are recitation challenges
rather than Dhikr counter phrases. Keep this list in step with
`DhikrSpeech/phrases.json`, which the training pipeline reads.

Eleven phrases are defined; seven get a card. The rest are parked with
`hidden: true` — see "Park a phrase" below.

### One utterance per recording

A clip is a single training example, so it must hold the phrase **once**. A
volunteer who repeats the dhikr inside one take produces a clip the pipeline
cannot label, and more samples come from recording again, not from saying the
phrase twice. The rule is therefore stated in `ui.singleTakeRule` (its own
highlighted box above the cards) and repeated in the wording of `listHint`,
`microphoneHint`, `ready`, `recording`, `recordingReady`, `uploadSuccessBody`
and `unknownPrompt.note`. `verify.mjs` asserts the box ships and that those
strings still carry the rule, so a copy edit cannot quietly drop it.

### Recommended: use an existing Drive folder

1. In Google Drive, create a folder named `Dhikr Speech Dataset`.
2. Open it.
3. Copy the ID from its URL. For example, in `https://drive.google.com/drive/folders/ABC123`, the ID is `ABC123`.
4. Paste it into `storage.rootFolderId` in `config.ts`.

If `rootFolderId` is blank, the app finds a folder with `rootFolderName` in the deploying account's My Drive or creates one on the first upload. It remembers the chosen folder ID in Apps Script Properties. Providing an explicit ID avoids ambiguity if Drive contains folders with identical names.

### Recommended: use an existing spreadsheet

1. Create a blank Google Sheet.
2. Copy the ID between `/d/` and `/edit` in its URL.
3. Paste it into `storage.spreadsheetId` in `config.ts`.

You do not need to create the header. On the first upload the app creates the configured sheet tab and writes:

```text
sample_id | phrase_id | phrase_text | filename | duration_ms | sample_rate | browser | platform | language | created_at | drive_file_id | drive_url
```

If `spreadsheetId` is blank, the app creates a spreadsheet with `spreadsheetName` on the first upload and remembers its ID in Apps Script Properties.

### Add or change phrases

Edit only `phrases` in `config.ts`:

```js
phrases: [
  { id: 1, text: "سبحان الله" },
  { id: 2, text: "الحمد لله" }
]
```

The order of the list is the order the cards appear on the page and nothing
else — put the phrases you most need samples of at the top. An `id` is the
phrase's identity everywhere else (its Drive folder, its class in the trained
model, its local upload tally), so reorder freely but never renumber an id:
existing recordings would then belong to the wrong class.

IDs must be unique positive integers. Recordings are stored under a `dataset/` subfolder of the root folder (`{root}/dataset/{id}/`, set by `storage.datasetSubfolder`), one numeric zero-padded folder per phrase (`001`, `002`, and so on); Arabic text is never used in folder names. The `dataset/` level matches what the DhikrSpeech training pipeline scans.

### Park a phrase

To take a phrase off the page without deleting it, add `hidden: true`:

```js
{ id: 10, text: "اللهم صل وسلم على نبينا محمد", hidden: true }
```

A parked phrase gets no card, and that is the only thing that changes. Its id
stays reserved, its `dataset/{id}/` folder keeps every recording it already has,
and it keeps its entry in `phrases.json` so the trainer still has a label for
that folder. The backend also still accepts uploads for it, so a volunteer whose
tab was open across a redeploy does not lose the take waiting on a card that has
since disappeared.

**Deleting the entry instead is what you want to avoid.** The label goes with it,
the folder is orphaned, and the next phrase added inherits the freed id — and
with it the old recordings, now filed under someone else's words. Park; do not
delete.

At least one phrase must stay visible; the build refuses a page with no phrase
cards.

### The unknown card (words that are not a dhikr)

The last card on the page asks the volunteer for **any ordinary word that is not a dhikr** — "صباح الخير", "كيف حالك", anything. These are the model's negative examples: a classifier trained on phrases alone has nowhere to put ordinary speech, so it hands every word it hears to the nearest dhikr and over-counts. The card is configured by `unknownPrompt` in `config.ts`:

```js
unknownPrompt: {
  id: 0,
  text: "قل أي كلمة عادية ليست ذكرًا",
  note: "مثل «صباح الخير» أو …"
}
```

- Its recordings go to `{root}/dataset/unknown/` (`storage.unknownFolderName`), not to a numeric phrase folder, and their filenames start with `unknown_`. That is exactly the folder DhikrSpeech's `classes.unknown_class` scans, so no manual sorting is needed.
- Its `id` must not collide with a phrase id, and `unknownFolderName` must not be numeric — the build refuses both, because either would file ordinary speech as a phrase.
- It is deliberately **left out of `phrases.json`**: `unknown` is a class folder, not a phrase, and the pipeline labels it from the folder name. The spreadsheet row records `phrase_text` as `unknown` for the same reason — the card's own text is an instruction, not something anyone said.
- Set `unknownPrompt: null` to drop the card entirely.
- The `note` is shown under the prompt as an example. Only the two non-phrase cards have one; a dhikr is its own instruction.

### The noise card (no words at all)

The final card asks for **background noise with no speech in it** — a street, a
room, a fan, a car. These are not a class the model learns. They are mixed
*underneath* real recordings during training (`augmentation.background_noise`),
so a model learned in a quiet room still hears the dhikr in a noisy one.
Configured by `noisePrompt`:

```js
noisePrompt: {
  id: -1,
  text: "سجّل صوت المكان من حولك بدون أي كلام",
  note: "اترك الميكروفون يلتقط ما حولك…"
}
```

- **Its audio does not go under `dataset/`.** It is written to
  `{root}/noise/` (`storage.noiseFolderName`), a **sibling** of `dataset/`,
  because that is where the pipeline's `paths.noise_dir` resolves. Filing it
  inside `dataset/` would make the trainer scan background hiss as a class of its
  own. This is the one structural difference from the unknown card, and the build
  refuses a `noiseFolderName` equal to `datasetSubfolder`.
- Filenames start with `noise_`, and the spreadsheet records `phrase_text` as
  `noise` — the card's text is an instruction, not something anyone said.
- Like `unknown`, it is left out of `phrases.json`: it is a folder, not a phrase.
- It is the one card where speaking ruins the sample, so the three strings that
  otherwise say "say the phrase once" are overridden for it: `ui.noiseRecording`,
  `ui.noiseRecordingReady`, `ui.noiseUploadSuccessBody`. `verify.mjs` asserts they
  ship and still tell the volunteer not to speak.
- Non-phrase prompt ids are `<= 0` by convention (`0` unknown, `-1` noise), so
  they can never collide with a phrase id, which counts up from 1.
- Set `noisePrompt: null` to drop the card entirely.

### The speaker id in every filename

The page mints a random UUID on a volunteer's first visit and keeps it in
`localStorage` (`speech_collector_speaker_id`). Every upload sends it, and the
backend stamps the first 8 hex characters into the filename as a `sp…` token:

```text
006_sp3f9a2c41_20260803_183015_ab12cd.webm
```

This exists for one reason: the trainer can then keep one voice on one side of
the train/val split. `DhikrSpeech`'s `split.group_regex` is `null` today, which
means the same speaker can sit in both train and validation and the reported
validation accuracy is optimistic. Once enough recordings carry the token, set:

```yaml
split:
  group_regex: "sp[0-9a-f]{8}"
```

Files without a token fall back to being their own group, which is exactly the
current behaviour, so turning it on is safe on a mixed dataset.

Recordings collected before the token shipped can be given one after the fact.
The sheet still records the `browser` and `platform` of every upload, and that
pair — normalised and hashed — is a usable stand-in for a device. Section **4b**
of `DhikrSpeech/notebooks/DhikrSpeech.ipynb` reads the sheet and renames those
files on the mounted Drive; it lives in the notebook rather than here because
Drive is mounted as a filesystem there, so the rename is not a per-file Drive API
call bounded by Apps Script's 6-minute execution limit. It never writes back to
this spreadsheet, so the `filename` column keeps the pre-rename name — which is
harmless, because the matching tolerates it. See "Backfill speaker ids" in
`DhikrSpeech/README.md`.

It identifies a browser profile, not a person: it is generated locally, is never
stored against anything else, and reaches the server only as those 8 characters.
It is deliberately **not** a spreadsheet column — adding one would break the
header check on the existing sheet. When a browser cannot store it (private mode,
sandboxed frame) a session-lived id is used instead, and a client that sends none
simply gets the original filename shape.

The collector also writes `phrases.json` (set by `storage.phrasesFile`) at the **root** of the dataset folder — a sibling of `dataset/` — regenerated from this `phrases` list whenever it changes. It is written sorted by id, so changing the on-page order alone leaves it (and the training pipeline) untouched. That is the id→text label file the DhikrSpeech pipeline reads, so there is no separate step to create or upload it: change `phrases` here, redeploy, and the next upload refreshes the file. (If you ever delete it in Drive, edit a phrase or clear the `SPEECH_COLLECTOR_PHRASES_SIGNATURE` script property to force a rewrite.)

## 2. Build the Apps Script files

From a terminal:

```bash
cd SpeechCollector
node build.mjs
node verify.mjs
node ui.test.mjs
```

Successful output looks like:

```text
Built Apps Script project in .../SpeechCollector/dist
Verification passed: build output, manifest, syntax, and request validation are valid.
UI behaviour tests passed: per-prompt recorders, the unknown card, single-take locking, uploads, and tallies.
```

Re-run these commands after every change to `config.ts`, `Code.gs`, `Index.html`, `app.js`, `styles.css`, or `appsscript.json`. `ui.test.mjs` runs `app.js` against a minimal DOM stub and drives the buttons on the generated cards, so it catches a broken recorder, a card that fails to lock while another records, or a mis-queued upload before the page reaches a volunteer. The Pages workflow runs all three before publishing `voice.html`.

## 3. Create the Apps Script project

These steps require no Apps Script experience:

1. Visit [script.google.com](https://script.google.com/) while signed in to the Google account that owns or can edit the chosen Drive folder and spreadsheet.
2. Click **New project**.
3. Rename the project, for example `Dhikr Speech Collector`.
4. In the left file list, open `Code.gs`. Replace everything in it with the complete contents of `dist/Code.gs`.
5. Click **+** beside Files, choose **HTML**, name it exactly `Index`, and replace its contents with `dist/Index.html`.
6. Click **Project Settings** (the gear icon).
7. Enable **Show "appsscript.json" manifest file in editor**.
8. Return to Editor, open `appsscript.json`, and replace it with `dist/appsscript.json`.
9. Click **Save project**.

`DriveApp` and `SpreadsheetApp` are built-in Apps Script services. **Do not enable the Advanced Drive API**; this project does not need it.

## 4. Authorize and deploy as a public Web App

1. In Apps Script, click **Deploy → New deployment**.
2. Beside **Select type**, click the gear and select **Web app**.
3. Enter a description such as `Initial production deployment`.
4. Set **Execute as** to **Me**. This makes all anonymous uploads use the Drive and Sheet access of the deploying account.
5. Set **Who has access** to **Anyone**. Depending on the Google Workspace account, the label can appear as **Anyone, even anonymous**.
6. Click **Deploy**.
7. Google asks for authorization. Click **Authorize access**, choose the deploying account, and approve access to Google Drive and Google Sheets.
8. If Google shows an unverified-app warning for your own script, click **Advanced**, open the project, review the permissions, and continue. Do this only for the project you created yourself.
9. Copy the Web App URL ending in `/exec` and open it in a new browser tab.

If a managed Google Workspace account does not offer public/anonymous access, its administrator has disabled that deployment option. Use a permitted Google account or ask the Workspace administrator to allow public Apps Script Web Apps.

### Deploy updates

After code or configuration changes:

1. Run `node build.mjs` again.
2. Copy the rebuilt `dist/Code.gs`, `dist/Index.html`, and `dist/appsscript.json` into the existing Apps Script project. (`dist/voice.html` is not uploaded to Apps Script; committing it publishes it through the Pages workflow.)
3. Save.
4. Choose **Deploy → Manage deployments**.
5. Click the pencil icon, choose **New version**, and click **Deploy**.

Saving code alone does not update the public `/exec` deployment. A new deployment version is required.

## 5. Test on Android and iPhone

Both hosts use HTTPS, which is required for microphone access. Test the standalone page — the `/exec` URL cannot record.

### Android

1. Open the standalone page (`deployment.standaloneUrl`) in a current version of Chrome.
2. Tap **تسجيل** (Record) on the first phrase card.
3. When asked, allow microphone access.
4. Speak for at least one second. Recording stops automatically at five seconds.
5. Tap **استماع** (Play), then **رفع التسجيل** (Upload).
6. Confirm that the success message appears on that card, its `✓` tally reads 1, and the card is empty and ready for another sample.
7. Record two more cards without uploading, confirm the bottom bar appears with a count of 2, and tap it. Both cards should upload one after the other, and the bar should disappear.

### iPhone or iPad

1. Open the standalone page (`deployment.standaloneUrl`) in a current version of Safari.
2. Allow microphone access when prompted.
3. Record, play, and upload as above.
4. If microphone access was previously denied, open **Settings → Safari → Microphone** (or the website settings from Safari's address bar), allow access, and reload the page.

Safari commonly records AAC audio in an M4A/MP4 container; Chrome commonly records WebM/Opus. Both are accepted by default. WAV is selected only when the browser's `MediaRecorder` implementation supports it. The app requests mono audio at 16 kHz, but the browser/device may choose another hardware or codec sample rate; the actual reported rate is saved when the browser exposes it.

### Verify Drive and Sheets

After a successful upload:

1. Open the root Drive folder, then its `dataset/` subfolder.
2. Confirm that a phrase subfolder such as `dataset/006` exists — and, after a take on the unknown card, `dataset/unknown`.
3. Confirm that it contains a file like `006_sp3f9a2c41_20260803_183015_ab12cd.webm` (or `unknown_sp3f9a2c41_…`) or `.m4a`/`.wav`.
4. Go back up to the root folder and confirm that a take on the noise card produced `noise/noise_sp3f9a2c41_…` — **beside** `dataset/`, not inside it.
5. Confirm that the `sp…` token is the same across every file you just uploaded.
6. Open the spreadsheet.
7. Confirm that exactly one metadata row was appended and its Drive URL opens the file for the owner.

Drive files remain private unless the owner separately changes their sharing settings. The public collector does not expose a file listing or make uploaded recordings public.

## Recording and upload behavior

- Requests mono, 16 kHz audio when the browser supports those constraints.
- Uses WAV where `MediaRecorder` supports it, then WebM/Opus, Ogg/Opus, or MP4/AAC fallbacks.
- Rejects recordings shorter than one second.
- Stops automatically at five seconds.
- Shows a live waveform and timer.
- Keeps the recording in browser memory after every upload error.
- Uses a stable random `sample_id` for retries. If the direct POST succeeds but its response is interrupted, retrying returns the existing row rather than creating a duplicate.
- Tallies a sample only after the server confirms success.

### One recorder per prompt

There is no navigation. Every visible phrase is a card on the same page — plus the unknown and noise cards at the end — each carrying its own waveform, timer, status line, and four buttons (**تسجيل**, **إيقاف**, **استماع**, **رفع التسجيل**). A volunteer records and uploads whichever cards they like, in any order, as many times as they like.

- **Record any card.** Tapping **تسجيل** starts that card and locks every other card's record button — there is one microphone, so there is one take at a time. The 1s minimum and 5s auto-stop are unchanged.
- **Re-record.** Once a take is waiting, that card's record button becomes **إعادة التسجيل**: pressing it drops the take and starts a new one. No confirmation — pressing it already says so.
- **Upload one card.** **رفع التسجيل** sends that card's take on its own. On success the card's `✓` tally ticks up and the card empties, ready for another sample of the same phrase; several samples of one phrase are worth more to the dataset than one sample each.
- **Upload several.** Once two or more takes are waiting, a bar pins itself to the bottom of the screen: **رفع كل التسجيلات الجاهزة ({count})**. Below two it stays hidden, because the card's own upload button is already on screen. It never appears for a card that is mid-upload.
- **Uploads are serialized.** The backend appends one spreadsheet row per sample, so takes are sent one after another. A queued card shows **في الانتظار…** and cannot be recorded over until its turn passes.
- **A failure is local to its card.** The take is kept, the button becomes **إعادة المحاولة**, and the rest of the batch still goes through. The `sample_id` is stable, so a retry cannot create a duplicate row.
- **The microphone is held between takes** and released after 30 seconds of not recording, so a run of cards costs one permission prompt rather than one per card.
- **A card says nothing when it has nothing to report.** Status lines appear only while recording, when a take is ready, during upload, and on success or failure — ten identical idle hints would be noise.
- The per-phrase upload tally is stored in this browser's `localStorage` (`speech_collector_upload_counts`). It is a convenience only — it never reaches the server, and recording works normally when storage is unavailable (private mode, sandboxed frame).
- The speaker id lives in the same storage (`speech_collector_speaker_id`) and, unlike the tally, is sent with every upload — see "The speaker id in every filename".

## Backend validation and security

The Apps Script backend:

- Accepts only phrase IDs present in `config.ts` (including parked ones), plus the `unknownPrompt` and `noisePrompt` ids when they are configured.
- Replaces client-supplied phrase text with the trusted configured phrase text, and decides the destination folder **and its parent** from the id — a client cannot choose where its audio is filed, nor push a phrase recording into the noise pool.
- Reduces the client-supplied speaker id to 8 hex characters before it reaches a filename, and drops it entirely when it is not hex.
- Enforces duration, MIME type, base64 syntax, sample-rate range, and maximum upload size.
- Generates the filename and Drive folder name on the server.
- Limits metadata lengths and prevents spreadsheet formula injection.
- Serializes writes with `LockService`.
- Trashes a newly created Drive file if the corresponding spreadsheet write fails.
- Returns generic internal errors rather than exposing private exception details.

Because the app intentionally allows anonymous uploads, anyone with the Web App URL can submit valid audio. Keep the URL limited to the intended volunteer group, monitor Drive storage, and reduce `maximumUploadBytes` if appropriate. Apps Script and Google Drive account quotas still apply.

## Troubleshooting

### The page says microphone access failed

- **No permission dialog appeared at all:** the page is running inside a frame that withholds the microphone, which is always the case on the Apps Script `/exec` URL. Open `deployment.standaloneUrl` (`voice.html`) instead — see "Why recording does not work on the `/exec` URL".
- Confirm the page URL begins with `https://`.
- Check the browser's site permissions and reload; a previously denied site never prompts again until the permission is reset.
- Close other apps that may hold exclusive microphone access.
- Test with current Chrome on Android or current Safari on iPhone.

### Upload failed but the recording still plays

This is expected safe behavior. The app deliberately keeps the Blob and stable `sample_id` in memory. Check connectivity and press **إعادة المحاولة** (Retry). Do not reload or close the tab until the upload succeeds.

### `INVALID_FOLDER_ID` or `INVALID_SPREADSHEET_ID`

The configured ID is wrong, or the deploying Google account does not have edit access. Correct the ID in `config.ts`, rebuild, copy the generated files, and deploy a new version.

### `INVALID_SHEET_HEADER`

The existing first row differs from `spreadsheetColumns`. Use a blank sheet/tab or make the first row exactly match the configured columns. Do not reorder columns without changing `config.ts` and rebuilding.

### Find automatically created resources

Search the deploying account's Drive for the configured `rootFolderName` and `spreadsheetName`. Their generated IDs are also visible in Apps Script under **Project Settings → Script Properties** as:

- `SPEECH_COLLECTOR_ROOT_FOLDER_ID`
- `SPEECH_COLLECTOR_SPREADSHEET_ID`

### Inspect backend failures

In Apps Script, open **Executions** in the left sidebar. Select a failed `doPost` or `saveAudio` execution to see its server log and stack trace. Volunteers receive only a safe retry message.

## Optional command-line deployment with clasp

Manual deployment above is the simplest path for beginners. Experienced Apps Script developers can also initialize `clasp` in this folder, configure its root directory as `dist`, and push the generated project. `.claspignore` is included so source-only files are not uploaded. Never place OAuth credentials or private keys in `config.ts` or commit them to source control.
