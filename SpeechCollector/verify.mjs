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
  'retry', 'recordedCount', 'listHint', 'summaryPhrases', 'summarySamples', 'tooLarge'
]) {
  assert.ok(bootstrap.ui[key], `The "${key}" UI string is missing from the bootstrap payload.`);
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
  'phrasesJsonContent_', 'ensurePhrasesFile_'
];
for (const functionName of requiredFunctions) {
  assert.equal(
    vm.runInContext(`typeof ${functionName}`, backendContext),
    'function',
    `${functionName} is missing from the backend.`
  );
}

console.log('Verification passed: build output, manifest, syntax, and request validation are valid.');

