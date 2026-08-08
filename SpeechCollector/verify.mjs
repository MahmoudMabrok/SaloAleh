import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const projectDirectory = path.dirname(fileURLToPath(import.meta.url));
const read = (filename) => fs.readFileSync(path.join(projectDirectory, filename), 'utf8');

// Parse every JavaScript source without running browser-only code.
new vm.Script(read('app.js'), { filename: 'app.js' });
new vm.Script(read('dist/Code.gs'), { filename: 'dist/Code.gs' });

const html = read('dist/Index.html');
assert.ok(!html.includes('/*__STYLES__*/'), 'CSS placeholder remains in dist/Index.html.');
assert.ok(!html.includes('/*__APP_JS__*/'), 'JavaScript placeholder remains in dist/Index.html.');
assert.ok(html.includes('<?!= bootstrapJson ?>'), 'Apps Script bootstrap template is missing.');

// The standalone page must carry a resolved bootstrap payload: it is served
// outside Apps Script, so an unresolved <?!= ?> template would ship as text.
const standalone = read('dist/voice.html');
assert.ok(!standalone.includes('<?!= bootstrapJson ?>'), 'dist/voice.html still contains the Apps Script template tag.');
assert.ok(!standalone.includes('/*__APP_JS__*/'), 'JavaScript placeholder remains in dist/voice.html.');

const bootstrapMatch = standalone.match(/<script id="bootstrap-data" type="application\/json">([\s\S]*?)<\/script>/);
assert.ok(bootstrapMatch, 'dist/voice.html has no bootstrap payload.');
const bootstrap = JSON.parse(bootstrapMatch[1].replace(/\\u003c/g, '<').replace(/\\u003e/g, '>').replace(/\\u0026/g, '&'));
assert.match(bootstrap.endpoint, /^https:\/\/script\.google\.com\/macros\/s\/[\w-]+\/exec$/, 'The standalone upload endpoint is not an Apps Script /exec URL.');
assert.ok(bootstrap.phrases.length > 0, 'The standalone page has no phrases.');
assert.ok(bootstrap.ui.microphoneBlocked, 'The permissions-policy message is missing from the UI strings.');

// The negative-class card. It is optional, but a page that ships it must ship
// everything the card needs, and its id must not shadow a phrase.
if (bootstrap.unknownPrompt) {
  assert.ok(bootstrap.unknownPrompt.text, 'unknownPrompt has no text for the card to show.');
  assert.ok(
    !bootstrap.phrases.some((phrase) => phrase.id === bootstrap.unknownPrompt.id),
    'unknownPrompt.id collides with a phrase id, so its uploads would be filed as that phrase.'
  );
  assert.ok(bootstrap.ui.unknownBadge, 'The unknownBadge UI string is missing from the bootstrap payload.');
}

// The background-noise card. Same contract as the unknown card, plus the
// wording that replaces "say the phrase once" — the one card where saying
// anything at all ruins the sample.
if (bootstrap.noisePrompt) {
  assert.ok(bootstrap.noisePrompt.text, 'noisePrompt has no text for the card to show.');
  assert.ok(bootstrap.noisePrompt.note, 'noisePrompt has no note; "record noise" is not actionable on its own.');
  assert.ok(
    !bootstrap.phrases.some((phrase) => phrase.id === bootstrap.noisePrompt.id),
    'noisePrompt.id collides with a phrase id, so its uploads would be filed as that phrase.'
  );
  assert.notEqual(
    bootstrap.noisePrompt.id,
    bootstrap.unknownPrompt?.id,
    'noisePrompt.id collides with unknownPrompt.id.'
  );
  for (const key of ['noiseBadge', 'noiseRecording', 'noiseRecordingReady', 'noiseUploadSuccessBody']) {
    assert.ok(bootstrap.ui[key], `The "${key}" UI string is missing from the bootstrap payload.`);
  }
  for (const key of ['noiseRecording', 'noiseRecordingReady']) {
    assert.match(
      bootstrap.ui[key],
      /لا تتكلم|لا يحتوي على كلام/,
      `The "${key}" string must tell the volunteer not to speak.`
    );
  }
}

// A hidden phrase is parked, not deleted: no card, but its id and label live on
// so the recordings already in its folder stay readable to the trainer.
assert.ok(
  !bootstrap.phrases.some((phrase) => phrase.hidden),
  'A hidden phrase reached the page; hidden phrases must not get a card.'
);
assert.ok(bootstrap.phrases.length > 0, 'Every phrase is hidden, so the page has no phrase cards.');

// The shell app.js builds the per-phrase recorders into. The cards themselves
// are created at runtime, so these containers are the only markup contract:
// app.js reads them by id and asserts nothing, and a renamed element would
// surface as a blank page in front of a volunteer.
for (const elementId of [
  'phrase-list', 'summary-phrases', 'summary-samples', 'progress-fill',
  'page-status', 'upload-all-bar', 'upload-all-button', 'upload-all-label', 'audio-player'
]) {
  assert.ok(standalone.includes(`id="${elementId}"`), `dist/voice.html is missing #${elementId}.`);
}
for (const key of [
  'record', 'reRecord', 'stop', 'play', 'pause', 'upload', 'uploading', 'uploadQueued', 'uploadAll',
  'retry', 'recordedCount', 'listHint', 'summaryPhrases', 'summarySamples', 'tooLarge', 'singleTakeRule'
]) {
  assert.ok(bootstrap.ui[key], `The "${key}" UI string is missing from the bootstrap payload.`);
}

// A clip holding the phrase more than once is unusable to the trainer, so the
// single-utterance rule must reach the page: the box that carries it, and the
// wording of every string that tells a volunteer how long a take is.
assert.ok(
  standalone.includes('data-i18n="singleTakeRule"'),
  'dist/voice.html does not render the single-utterance rule.'
);
for (const key of ['singleTakeRule', 'listHint', 'microphoneHint', 'recording', 'recordingReady']) {
  assert.match(
    bootstrap.ui[key],
    /مرة واحدة|نطق واحد|ذكر واحد/,
    `The "${key}" string must say that a recording holds one utterance.`
  );
}
assert.ok(bootstrap.ui.recordedCount.includes('{count}'), 'recordedCount must contain the {count} placeholder.');
assert.ok(bootstrap.ui.uploadAll.includes('{count}'), 'uploadAll must contain the {count} placeholder.');
assert.ok(bootstrap.ui.summarySamples.includes('{count}'), 'summarySamples must contain the {count} placeholder.');
for (const token of ['{recorded}', '{total}']) {
  assert.ok(bootstrap.ui.summaryPhrases.includes(token), `summaryPhrases must contain the ${token} placeholder.`);
}

const manifest = JSON.parse(read('dist/appsscript.json'));
assert.equal(manifest.runtimeVersion, 'V8');
assert.equal(manifest.webapp.executeAs, 'USER_DEPLOYING');
assert.equal(manifest.webapp.access, 'ANYONE_ANONYMOUS');

// Run backend validation in a sandbox. Apps Script services are referenced only
// inside functions and are not needed for these pure validation checks.
const backendContext = vm.createContext({ console });
new vm.Script(read('dist/Code.gs'), { filename: 'dist/Code.gs' }).runInContext(backendContext);

const validPayload = {
  sample_id: 's_0123456789abcdef0123456789abcdef',
  phrase_id: 1,
  duration_ms: 1500,
  sample_rate: 16000,
  mime_type: 'audio/webm;codecs=opus',
  audio_base64: Buffer.alloc(64, 1).toString('base64'),
  browser: 'Chrome 100',
  platform: 'Android',
  language: 'ar-EG'
};

backendContext.payload = validPayload;
const validated = vm.runInContext('validateRequest_(payload)', backendContext);
assert.equal(validated.phrase.id, 1);
assert.equal(validated.mimeType, 'audio/webm');
assert.equal(vm.runInContext('padPhraseId_(1)', backendContext), '001');

// The auto-written phrases.json body is generated from CONFIG and must be a
// valid [{id, text}] list that the DhikrSpeech pipeline's load_phrases accepts.
const generatedPhrases = JSON.parse(vm.runInContext('phrasesJsonContent_()', backendContext));
assert.ok(Array.isArray(generatedPhrases) && generatedPhrases.length > 0, 'phrasesJsonContent_ produced an empty list.');
assert.ok(
  generatedPhrases.every((phrase) => Number.isInteger(phrase.id) && typeof phrase.text === 'string' && phrase.text.trim()),
  'phrasesJsonContent_ entries must each be {id: integer, text: non-empty string}.'
);

// A parked phrase keeps its label. Dropping it from phrases.json would leave
// the trainer scanning a dataset/{id}/ folder it has no text for.
const hiddenIds = vm.runInContext('CONFIG.phrases.filter(function(p) { return p.hidden; }).map(function(p) { return p.id; })', backendContext);
for (const hiddenId of hiddenIds) {
  assert.ok(
    generatedPhrases.some((phrase) => phrase.id === hiddenId),
    `phrases.json dropped hidden phrase ${hiddenId}; its recordings would lose their label.`
  );
  backendContext.payload = { ...validPayload, phrase_id: hiddenId };
  assert.doesNotThrow(
    () => vm.runInContext('validateRequest_(payload)', backendContext),
    `A hidden phrase must still be an accepted phrase_id, so a take waiting across a redeploy is not lost.`
  );
}

// Apps Script services are unavailable here, so stub only what the filename
// helper touches; the naming rules are checkable without a deployment.
backendContext.Utilities = {
  formatDate: () => '20260101_000000',
  getUuid: () => 'abcdef00-0000-0000-0000-000000000000'
};

// The device token that lets the trainer keep one voice on one side of the
// train/val split. A client that cannot supply one still gets a valid filename.
assert.equal(
  vm.runInContext('speakerToken_("3f9a2c41-1111-2222-3333-444455556666")', backendContext),
  'sp3f9a2c41',
  'The speaker token must be "sp" plus the first 8 hex characters of the uuid.'
);
assert.equal(vm.runInContext('speakerToken_("")', backendContext), '', 'A missing speaker id must produce no token.');
assert.equal(vm.runInContext('speakerToken_("zzz")', backendContext), '', 'A short or non-hex speaker id must produce no token.');
assert.match(
  vm.runInContext('createFilename_("001", speakerToken_("3f9a2c41-1111-2222-3333-444455556666"), "audio/webm")', backendContext),
  /^001_sp3f9a2c41_\d{8}_\d{6}_[0-9a-f]{6}\.webm$/,
  'A filename must carry its class, then its speaker token, then the timestamp.'
);
assert.match(
  vm.runInContext('createFilename_("001", "", "audio/webm")', backendContext),
  /^001_\d{8}_\d{6}_[0-9a-f]{6}\.webm$/,
  'Without a speaker token the filename must keep its original shape, not gain an empty field.'
);

// ---------------------------------------------------------------------------
// The speaker-token backfill: recordings collected before the page minted a
// speaker id, grouped after the fact by the browser/platform pair the sheet
// kept. Only the pure naming and hashing rules are exercised here; the Drive
// and Sheets walk needs a live deployment.
// ---------------------------------------------------------------------------

// Apps Script returns SIGNED bytes from computeDigest, so a stub that hands
// back unsigned ones would hide a missing mask and produce tokens that are not
// 8 hex characters. Sign-extend to reproduce the real service.
backendContext.Utilities.DigestAlgorithm = { SHA_256: 'SHA_256' };
backendContext.Utilities.Charset = { UTF_8: 'UTF_8' };
backendContext.Utilities.computeDigest = (_algorithm, value) =>
  Array.from(createHash('sha256').update(value, 'utf8').digest()).map((byte) => (byte > 127 ? byte - 256 : byte));

const derivedToken = (browser, platform) => {
  backendContext.probe = { browser, platform };
  return vm.runInContext('derivedSpeakerToken_(probe.browser, probe.platform)', backendContext);
};

// The pipeline's `split.group_regex` is "sp[0-9a-f]{8}", so a derived token is
// only useful if it is the same shape as a real one — no pipeline change, and
// no half-tagged dataset where only some files group.
assert.match(
  derivedToken('Chrome 120', 'Android'),
  /^sp[0-9a-f]{8}$/,
  'A derived speaker token must have the same sp+8-hex shape the trainer matches.'
);
assert.equal(
  derivedToken('Chrome 120', 'Android'),
  derivedToken('  chrome 120 ', 'ANDROID'),
  'Case and stray whitespace must not split one device into two groups.'
);
assert.notEqual(
  derivedToken('Chrome 120', 'Android'),
  derivedToken('Safari 17', 'iPhone'),
  'Different browser/platform pairs must derive different groups.'
);
// Joined with a separator so a shift of characters across the two fields is not
// the same fingerprint; without it ("ab"+"c") and ("a"+"bc") would collide.
assert.notEqual(
  derivedToken('ab', 'c'),
  derivedToken('a', 'bc'),
  'The two fields must be joined with a separator, not concatenated blindly.'
);
// Nothing to derive from means no token: the trainer then treats the file as
// its own group, which is today's behaviour. Inventing a shared token here
// would merge every unknown device into one bucket.
assert.equal(derivedToken('', ''), '', 'A row with neither browser nor platform must derive no token.');
assert.equal(derivedToken(null, undefined), '', 'Blank sheet cells must derive no token.');
assert.match(derivedToken('', 'Android'), /^sp[0-9a-f]{8}$/, 'One known field is still a usable group.');

const insertToken = (filename, token) => {
  backendContext.probe = { filename, token };
  return vm.runInContext('filenameWithSpeakerToken_(probe.filename, probe.token)', backendContext);
};

// The renamed file must be indistinguishable from one uploaded today, class
// prefix first: that prefix is how the pipeline labels the clip.
assert.equal(
  insertToken('001_20260101_000000_abcdef.webm', 'sp3f9a2c41'),
  '001_sp3f9a2c41_20260101_000000_abcdef.webm',
  'The token must be inserted between the class prefix and the timestamp.'
);
assert.equal(
  insertToken('unknown_20260101_000000_abcdef.webm', 'sp3f9a2c41'),
  'unknown_sp3f9a2c41_20260101_000000_abcdef.webm',
  'Non-numeric class folders (unknown, noise) must rename the same way.'
);
// A name that is not the shape createFilename_ produces is left alone rather
// than guessed at: a mangled class prefix relabels the recording.
assert.equal(
  insertToken('001_sp3f9a2c41_20260101_000000_abcdef.webm', 'spdeadbeef'.slice(0, 10)),
  '',
  'An already-tagged filename must not gain a second token.'
);
assert.equal(insertToken('holiday-photo.jpg', 'sp3f9a2c41'), '', 'An unrelated filename must be left untouched.');
assert.equal(insertToken('001_20260101_000000_abcdef.webm', 'nope'), '', 'A malformed token must never be written into a name.');
assert.equal(insertToken('', 'sp3f9a2c41'), '', 'An empty filename must produce no rename.');

const alreadyTagged = (filename) => {
  backendContext.probe = { filename };
  return vm.runInContext('hasSpeakerToken_(probe.filename)', backendContext);
};
assert.equal(alreadyTagged('001_sp3f9a2c41_20260101_000000_abcdef.webm'), true, 'A tagged filename must be recognised so the backfill skips it.');
assert.equal(alreadyTagged('001_20260101_000000_abcdef.webm'), false, 'An untagged filename must be recognised as work to do.');
// The suffix is hex too, so the check has to be anchored to the token slot or
// every file would look tagged and the backfill would do nothing.
assert.equal(alreadyTagged('001_20260101_000000_5ba1e5.webm'), false, 'The hex suffix must not be mistaken for a speaker token.');

// The row cursor makes the walk resumable across the 6-minute execution limit,
// so it must be a real Script Property rather than an in-memory counter.
assert.ok(
  vm.runInContext('RUNTIME_KEYS.BACKFILL_ROW', backendContext),
  'The backfill needs a Script Property key to resume from.'
);

// The negative class is a folder, not a phrase: the pipeline labels it from the
// folder name, so listing it in phrases.json would invent a phrase id for it.
if (bootstrap.unknownPrompt) {
  const unknownId = bootstrap.unknownPrompt.id;
  assert.ok(
    !generatedPhrases.some((phrase) => phrase.id === unknownId),
    'phrases.json must not list the unknown prompt; it is a class folder, not a phrase.'
  );

  // An unknown-card upload is accepted and lands in the filler folder, under a
  // filename that names it — never in a zero-padded phrase folder.
  backendContext.payload = { ...validPayload, phrase_id: unknownId };
  const unknownRequest = vm.runInContext('validateRequest_(payload)', backendContext);
  assert.equal(unknownRequest.phrase.id, unknownId, 'The unknown prompt must be an accepted phrase_id.');
  assert.equal(
    vm.runInContext('classFolderName_(CONFIG.unknownPrompt)', backendContext),
    'unknown',
    'Unknown recordings must be filed under the dataset\'s unknown/ folder.'
  );
  assert.equal(
    unknownRequest.phraseText,
    'unknown',
    'The sheet must record the class for an unknown sample, not the card\'s instruction text.'
  );
  assert.match(
    vm.runInContext('createFilename_(classFolderName_(CONFIG.unknownPrompt), "", "audio/webm")', backendContext),
    /^unknown_/,
    'An unknown recording\'s filename must carry its class, like a phrase folder\'s does.'
  );
  // Derived from the phrase's own id, not a literal: the phrase list is ordered
  // for the page, so the first card is not necessarily id 1.
  assert.equal(
    vm.runInContext('classFolderName_(CONFIG.phrases[0])', backendContext),
    String(bootstrap.phrases[0].id).padStart(3, '0'),
    'A phrase must still be filed under its zero-padded id.'
  );
}

// Noise is the one prompt whose audio lives OUTSIDE dataset/. The trainer scans
// dataset/ for its classes and reads noise from a sibling folder, so getting
// this parent wrong would teach the model that background hiss is a dhikr.
if (bootstrap.noisePrompt) {
  const noiseId = bootstrap.noisePrompt.id;
  backendContext.payload = { ...validPayload, phrase_id: noiseId };
  const noiseRequest = vm.runInContext('validateRequest_(payload)', backendContext);
  assert.equal(noiseRequest.phrase.id, noiseId, 'The noise prompt must be an accepted phrase_id.');
  assert.equal(
    noiseRequest.phraseText,
    'noise',
    'The sheet must record the class for a noise sample, not the card\'s instruction text.'
  );
  assert.equal(
    vm.runInContext('classFolderName_(CONFIG.noisePrompt)', backendContext),
    'noise',
    'Noise recordings must be filed under the noise folder.'
  );
  assert.match(
    vm.runInContext('createFilename_(classFolderName_(CONFIG.noisePrompt), "", "audio/webm")', backendContext),
    /^noise_/,
    'A noise recording\'s filename must name its folder, like every other class does.'
  );
  assert.ok(
    !generatedPhrases.some((phrase) => phrase.id === noiseId),
    'phrases.json must not list the noise prompt; it is a folder, not a phrase.'
  );

  // Drive is unavailable here, so the folder tree is a stub that records only
  // what was created where — which is the whole claim under test.
  backendContext.fakeFolder = fakeFolder;
  const parents = vm.runInContext(
    '[classParentFolder_(fakeFolder("root"), CONFIG.noisePrompt).folderName,' +
    ' classParentFolder_(fakeFolder("root"), CONFIG.phrases[0]).folderName]',
    backendContext
  );
  assert.equal(parents[0], 'root', 'Noise must hang off the collector root, beside dataset/.');
  assert.equal(parents[1], 'dataset', 'A phrase must still hang off the dataset folder.');
}

function fakeFolder(folderName) {
  const children = new Map();
  return {
    folderName,
    getFoldersByName: (name) => ({ hasNext: () => children.has(name), next: () => children.get(name) }),
    createFolder: (name) => {
      const child = fakeFolder(name);
      children.set(name, child);
      return child;
    }
  };
}

backendContext.payload = { ...validPayload, phrase_id: 999 };
assert.throws(
  () => vm.runInContext('validateRequest_(payload)', backendContext),
  (error) => error.publicCode === 'INVALID_PHRASE'
);

backendContext.payload = { ...validPayload, duration_ms: 200 };
assert.throws(
  () => vm.runInContext('validateRequest_(payload)', backendContext),
  (error) => error.publicCode === 'INVALID_DURATION'
);

backendContext.payload = { ...validPayload, mime_type: 'application/octet-stream' };
assert.throws(
  () => vm.runInContext('validateRequest_(payload)', backendContext),
  (error) => error.publicCode === 'INVALID_MIME_TYPE'
);

const requiredFunctions = [
  'doGet', 'doPost', 'saveAudio', 'createFolderIfMissing', 'appendSpreadsheetRow', 'jsonResponse',
  'phrasesJsonContent_', 'ensurePhrasesFile_', 'allPrompts_', 'classFolderName_',
  'visiblePhrases_', 'classParentFolder_', 'speakerToken_',
  'backfillSpeakerTokens', 'previewSpeakerTokenBackfill', 'resetSpeakerTokenBackfill',
  'backfillRow_', 'columnIndexes_', 'derivedSpeakerToken_', 'deviceFingerprint_',
  'shortHex_', 'hasSpeakerToken_', 'filenameWithSpeakerToken_'
];
for (const functionName of requiredFunctions) {
  assert.equal(
    vm.runInContext(`typeof ${functionName}`, backendContext),
    'function',
    `${functionName} is missing from the backend.`
  );
}

console.log('Verification passed: build output, manifest, syntax, and request validation are valid.');

