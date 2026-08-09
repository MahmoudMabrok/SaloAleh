import assert from 'node:assert/strict';
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

// The repetition take: one long clip holding the same dhikr N times, for
// measuring the counter rather than training it. Everything it needs to be a
// *different* recording from a clip has to reach the page, because the page is
// what stops a volunteer producing a five-second take under a ten-times prompt.
if (bootstrap.recording.streaming) {
  const streaming = bootstrap.recording.streaming;
  assert.ok(
    Number.isInteger(streaming.repetitions) && streaming.repetitions >= 2,
    'recording.streaming.repetitions must be an integer of at least 2.'
  );
  assert.ok(
    streaming.maximumDurationMs > bootstrap.recording.maximumDurationMs,
    'A repetition take must be allowed to run longer than a clip; otherwise it holds one utterance too.'
  );
  assert.ok(
    streaming.minimumDurationMs > bootstrap.recording.minimumDurationMs,
    'A repetition take must demand more than a clip\'s minimum, or a one-second take passes as ten dhikr.'
  );
  for (const key of [
    'streamingRecord', 'streamingReRecord', 'streamingHint', 'streamingRecording',
    'streamingRecordingReady', 'streamingUploadSuccessBody', 'streamingTooShort', 'streamingCount'
  ]) {
    assert.ok(bootstrap.ui[key], `The "${key}" UI string is missing from the bootstrap payload.`);
  }
  // The count lives in config, not in six Arabic sentences: a string that spells
  // the number out would keep saying "ten" after the config said eleven.
  for (const key of [
    'streamingRecord', 'streamingReRecord', 'streamingHint', 'streamingRecording',
    'streamingRecordingReady', 'streamingUploadSuccessBody', 'streamingTooShort'
  ]) {
    assert.ok(
      bootstrap.ui[key].includes('{reps}'),
      `The "${key}" string must carry the {reps} placeholder rather than a written-out number.`
    );
  }
  assert.ok(bootstrap.ui.streamingCount.includes('{count}'), 'streamingCount must contain the {count} placeholder.');
  assert.ok(
    standalone.includes('id="streaming-hint"'),
    'dist/voice.html does not explain the repetition button, whose instruction contradicts the single-take rule.'
  );
}

// The long negative take: minutes of ordinary sound with no dhikr in it. It is
// the only recording the collector gathers that can measure false activations
// per hour, so the page has to say the one thing that would waste it — that a
// single dhikr anywhere in those minutes makes the whole take unusable.
if (bootstrap.recording.negative) {
  const negative = bootstrap.recording.negative;
  assert.ok(
    negative.maximumDurationMs > bootstrap.recording.maximumDurationMs,
    'A negative take must run longer than a clip; false activations are counted per hour.'
  );
  assert.ok(
    negative.minimumDurationMs > bootstrap.recording.minimumDurationMs,
    'A negative take must demand more than a clip\'s minimum.'
  );
  assert.ok(bootstrap.longNoisePrompt, 'recording.negative is configured but no card asks for one.');
  assert.ok(bootstrap.longNoisePrompt.text, 'longNoisePrompt has no text for the card to show.');
  assert.ok(
    bootstrap.longNoisePrompt.note,
    'longNoisePrompt has no note; "record several minutes" is not actionable without the rule.'
  );
  for (const other of [bootstrap.unknownPrompt, bootstrap.noisePrompt]) {
    if (other) assert.notEqual(bootstrap.longNoisePrompt.id, other.id, 'longNoisePrompt.id collides with another card.');
  }
  assert.ok(
    !bootstrap.phrases.some((phrase) => phrase.id === bootstrap.longNoisePrompt.id),
    'longNoisePrompt.id collides with a phrase id, so its uploads would be filed as that phrase.'
  );
  for (const key of [
    'longNoiseBadge', 'longNoiseRecord', 'longNoiseReRecord', 'longNoiseRecording',
    'longNoiseRecordingReady', 'longNoiseTooShort', 'longNoiseUploadSuccessBody', 'longNoiseCount'
  ]) {
    assert.ok(bootstrap.ui[key], `The "${key}" UI string is missing from the bootstrap payload.`);
  }
  // The limits live in config, not in the Arabic: a string spelling out "five
  // minutes" would keep saying it after the config said three.
  assert.ok(bootstrap.ui.longNoiseRecording.includes('{minutes}'), 'longNoiseRecording must carry the {minutes} placeholder.');
  assert.ok(bootstrap.ui.longNoiseTooShort.includes('{seconds}'), 'longNoiseTooShort must carry the {seconds} placeholder.');
  assert.ok(bootstrap.ui.longNoiseCount.includes('{count}'), 'longNoiseCount must contain the {count} placeholder.');
  // The one rule that decides whether the recording is worth anything.
  assert.match(
    bootstrap.longNoisePrompt.note,
    /بدون أي ذكر|لا تُقال|بلا ذكر/,
    'The long-negative note must say that no dhikr may be spoken in the take.'
  );
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

// The repetition take, server side. The mode decides both the folder and the
// limits, so it is the one field a client must not be trusted on: a long take
// filed as a training clip would put ten utterances where the trainer expects
// one, and a clip filed as a repetition take would claim to hold ten.
if (bootstrap.recording.streaming) {
  const streaming = bootstrap.recording.streaming;
  const streamingPayload = {
    ...validPayload,
    mode: 'streaming',
    duration_ms: streaming.minimumDurationMs + 1000
  };

  backendContext.payload = streamingPayload;
  const streamingRequest = vm.runInContext('validateRequest_(payload)', backendContext);
  assert.equal(streamingRequest.mode, 'streaming');
  assert.equal(
    streamingRequest.limits.maximumDurationMs,
    streaming.maximumDurationMs,
    'A repetition take must be held to the streaming duration limit, not the clip one.'
  );

  // The same audio, without the mode, is a clip — and far too long to be one.
  backendContext.payload = { ...streamingPayload, mode: undefined };
  assert.throws(
    () => vm.runInContext('validateRequest_(payload)', backendContext),
    (error) => error.publicCode === 'INVALID_DURATION',
    'A long take with no mode must be rejected as an over-long clip, never quietly accepted.'
  );

  // ...and a clip-length take cannot claim to hold ten repetitions.
  backendContext.payload = { ...validPayload, mode: 'streaming' };
  assert.throws(
    () => vm.runInContext('validateRequest_(payload)', backendContext),
    (error) => error.publicCode === 'INVALID_DURATION',
    'A clip-length take must not be accepted as a repetition take.'
  );

  // "Say it ten times" needs something to say: the unknown card asks for a
  // different word every time and the noise card asks for none at all, so
  // neither may put audio in a class folder under the repetition mode.
  for (const nonPhrase of [bootstrap.unknownPrompt, bootstrap.noisePrompt]) {
    if (!nonPhrase) continue;
    backendContext.payload = { ...streamingPayload, phrase_id: nonPhrase.id };
    assert.throws(
      () => vm.runInContext('validateRequest_(payload)', backendContext),
      (error) => error.publicCode === 'INVALID_MODE',
      `Prompt ${nonPhrase.id} is not a phrase and must not accept a repetition take.`
    );
  }

  // The two trees, from the same class-folder name. A repetition take carries
  // the phrase's own folder, one level over from where the trainer scans.
  backendContext.fakeFolder = fakeFolder;
  const streamingParents = vm.runInContext(
    '[classParentFolder_(fakeFolder("root"), CONFIG.phrases[0], "streaming").folderName,' +
    ' classParentFolder_(fakeFolder("root"), CONFIG.phrases[0], "clip").folderName]',
    backendContext
  );
  assert.equal(streamingParents[0], 'streaming', 'A repetition take must hang off streaming/, beside dataset/.');
  assert.equal(streamingParents[1], 'dataset', 'A clip must still hang off dataset/.');
  assert.equal(
    vm.runInContext('classFolderName_(CONFIG.phrases[0])', backendContext),
    String(bootstrap.phrases[0].id).padStart(3, '0'),
    'A repetition take keeps the phrase\'s own class folder name; only its parent differs.'
  );

  // The count the clip is meant to hold, written where it cannot be separated
  // from the audio — annotations.json is built from these files by hand later.
  assert.equal(
    vm.runInContext('repetitionTag_("streaming")', backendContext),
    `x${streaming.repetitions}`,
    'A repetition take must carry its expected event count.'
  );
  assert.equal(vm.runInContext('repetitionTag_("clip")', backendContext), '', 'A clip carries no repetition tag.');
  assert.match(
    vm.runInContext(
      'createFilename_("001", speakerToken_("3f9a2c41-1111-2222-3333-444455556666"), "audio/webm", repetitionTag_("streaming"))',
      backendContext
    ),
    new RegExp(`^001_x${streaming.repetitions}_sp3f9a2c41_\\d{8}_\\d{6}_[0-9a-f]{6}\\.webm$`),
    'A repetition filename must read class, then count, then speaker token.'
  );
  assert.match(
    vm.runInContext('createFilename_("001", "", "audio/webm", "")', backendContext),
    /^001_\d{8}_\d{6}_[0-9a-f]{6}\.webm$/,
    'A clip filename must be unchanged by the repetition feature.'
  );

  // An unrecognised mode is read as a clip, the strictest of the three: that is
  // where every recording went before the evaluation takes existed.
  for (const [input, expected] of [
    ['streaming', 'streaming'], ['STREAMING', 'streaming'], ['negative', 'negative'],
    ['NEGATIVE', 'negative'], ['clip', 'clip'], ['', 'clip'], ['nonsense', 'clip']
  ]) {
    backendContext.mode = input;
    assert.equal(vm.runInContext('normalizeMode_(mode)', backendContext), expected, `normalizeMode_(${JSON.stringify(input)})`);
  }
}

// The negative take, server side. Its mode is the only one that files audio
// under a folder meaning "no dhikr in here", so it is checked in both
// directions: no other card may claim it, and this card may claim nothing else.
if (bootstrap.recording.negative) {
  const negative = bootstrap.recording.negative;
  const negativeId = bootstrap.longNoisePrompt.id;
  const negativePayload = {
    ...validPayload,
    phrase_id: negativeId,
    mode: 'negative',
    duration_ms: negative.minimumDurationMs + 1000
  };

  backendContext.payload = negativePayload;
  const negativeRequest = vm.runInContext('validateRequest_(payload)', backendContext);
  assert.equal(negativeRequest.mode, 'negative');
  assert.equal(
    negativeRequest.limits.maximumDurationMs,
    negative.maximumDurationMs,
    'A negative take must be held to the negative duration limit, not the clip or streaming one.'
  );
  assert.equal(
    negativeRequest.phraseText,
    'negative',
    'The sheet must record the folder for a negative take, not the card\'s instruction text.'
  );

  // A phrase claiming the negative mode would mark its own recitation as proof
  // the counter should have stayed silent — the most damaging mislabel here.
  backendContext.payload = { ...negativePayload, phrase_id: bootstrap.phrases[0].id };
  assert.throws(
    () => vm.runInContext('validateRequest_(payload)', backendContext),
    (error) => error.publicCode === 'INVALID_MODE',
    'Only the long-negative card may record a negative take.'
  );

  // ...and the reverse: this card in clip mode would land in dataset/negative/,
  // inventing a training class out of television.
  backendContext.payload = { ...validPayload, phrase_id: negativeId };
  assert.throws(
    () => vm.runInContext('validateRequest_(payload)', backendContext),
    (error) => error.publicCode === 'INVALID_MODE',
    'The long-negative card must not be recordable as a training clip.'
  );
  backendContext.payload = { ...negativePayload, mode: 'streaming' };
  assert.throws(
    () => vm.runInContext('validateRequest_(payload)', backendContext),
    (error) => error.publicCode === 'INVALID_MODE',
    'The long-negative card holds no dhikr, so it cannot hold ten of them.'
  );

  // A non-numeric folder inside streaming/, so the pipeline reads it as shared
  // material rather than as one phrase's repetition takes.
  assert.ok(
    !generatedPhrases.some((phrase) => phrase.id === negativeId),
    'phrases.json must not list the long-negative prompt; it is a folder, not a phrase.'
  );

  const folderName = vm.runInContext('classFolderName_(CONFIG.longNoisePrompt)', backendContext);
  assert.equal(folderName, 'negative', 'A negative take must be filed under the streaming tree\'s negative folder.');
  assert.ok(!/^\d+$/.test(folderName), 'The negative folder must not look like a zero-padded phrase id.');
  backendContext.fakeFolder = fakeFolder;
  assert.equal(
    vm.runInContext('classParentFolder_(fakeFolder("root"), CONFIG.longNoisePrompt, "negative").folderName', backendContext),
    'streaming',
    'A negative take must hang off streaming/, beside dataset/ — it is evaluation material, not a class.'
  );

  // x0 is not the absence of a count. It is the count, and it is the one that
  // says every detection in this audio is a false activation.
  assert.equal(
    vm.runInContext('repetitionTag_("negative")', backendContext),
    'x0',
    'A negative take must carry x0: zero events expected in it.'
  );
  assert.match(
    vm.runInContext(
      'createFilename_(classFolderName_(CONFIG.longNoisePrompt), speakerToken_("3f9a2c41-1111-2222-3333-444455556666"), "audio/webm", repetitionTag_("negative"))',
      backendContext
    ),
    /^negative_x0_sp3f9a2c41_\d{8}_\d{6}_[0-9a-f]{6}\.webm$/,
    'A negative filename must read folder, then x0, then speaker token — the shape the pipeline parses.'
  );
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
  'normalizeMode_', 'recordingLimits_', 'repetitionTag_', 'largestUploadBytes_',
  'negativeSettings_', 'isLongNoisePrompt_'
];
for (const functionName of requiredFunctions) {
  assert.equal(
    vm.runInContext(`typeof ${functionName}`, backendContext),
    'function',
    `${functionName} is missing from the backend.`
  );
}

console.log('Verification passed: build output, manifest, syntax, and request validation are valid.');

