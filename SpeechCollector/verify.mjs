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
  'doGet', 'doPost', 'saveAudio', 'createFolderIfMissing', 'appendSpreadsheetRow', 'jsonResponse'
];
for (const functionName of requiredFunctions) {
  assert.equal(
    vm.runInContext(`typeof ${functionName}`, backendContext),
    'function',
    `${functionName} is missing from the backend.`
  );
}

console.log('Verification passed: build output, manifest, syntax, and request validation are valid.');

