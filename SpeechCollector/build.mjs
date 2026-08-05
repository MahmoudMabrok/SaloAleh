import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const projectDirectory = path.dirname(fileURLToPath(import.meta.url));
const outputDirectory = path.join(projectDirectory, 'dist');

function read(filename) {
  return fs.readFileSync(path.join(projectDirectory, filename), 'utf8');
}

function loadConfiguration() {
  const sandbox = { globalThis: {} };
  vm.runInNewContext(read('config.ts'), sandbox, { filename: 'config.ts' });
  const config = sandbox.globalThis.SPEECH_COLLECTOR_CONFIG;
  validateConfiguration(config);
  return JSON.parse(JSON.stringify(config));
}

function validateConfiguration(config) {
  if (!config || typeof config !== 'object') throw new Error('config.ts did not define SPEECH_COLLECTOR_CONFIG.');
  if (!config.deployment || typeof config.deployment.webAppUrl !== 'string' || !config.deployment.webAppUrl.trim()) {
    throw new Error('deployment.webAppUrl is required so the standalone page knows where to upload.');
  }
  if (!Array.isArray(config.phrases) || config.phrases.length === 0) throw new Error('At least one phrase is required.');
  if (!Array.isArray(config.spreadsheetColumns) || config.spreadsheetColumns.length === 0) throw new Error('Spreadsheet columns are required.');

  const requiredColumns = [
    'sample_id', 'phrase_id', 'phrase_text', 'filename', 'duration_ms', 'sample_rate',
    'browser', 'platform', 'language', 'created_at', 'drive_file_id', 'drive_url'
  ];
  if (requiredColumns.some((column) => !config.spreadsheetColumns.includes(column))) {
    throw new Error('spreadsheetColumns is missing one or more required columns.');
  }

  const ids = new Set();
  for (const phrase of config.phrases) {
    if (!Number.isInteger(phrase.id) || phrase.id < 1 || ids.has(phrase.id)) {
      throw new Error(`Invalid or duplicate phrase id: ${phrase.id}`);
    }
    if (typeof phrase.text !== 'string' || !phrase.text.trim()) throw new Error(`Phrase ${phrase.id} has no text.`);
    ids.add(phrase.id);
  }

  if (config.recording.minimumDurationMs < 1 ||
      config.recording.maximumDurationMs < config.recording.minimumDurationMs) {
    throw new Error('Recording duration limits are invalid.');
  }
  if (!Number.isInteger(config.storage.phraseFolderDigits) || config.storage.phraseFolderDigits < 1) {
    throw new Error('phraseFolderDigits must be a positive integer.');
  }
  if (typeof config.storage.datasetSubfolder !== 'string' || !config.storage.datasetSubfolder.trim()) {
    throw new Error('storage.datasetSubfolder must be a non-empty string (the folder the trainer scans, e.g. "dataset").');
  }
}

function write(filename, contents) {
  fs.writeFileSync(path.join(outputDirectory, filename), contents, 'utf8');
}

const config = loadConfiguration();
fs.mkdirSync(outputDirectory, { recursive: true });

const backend = read('Code.gs').replace('__CONFIG_JSON__', escapeForSingleQuotedString(JSON.stringify(config)));
if (backend.includes('__CONFIG_JSON__')) throw new Error('Backend configuration placeholder was not replaced.');

let html = read('Index.html')
  .replace('/*__STYLES__*/', read('styles.css'))
  .replace('/*__APP_JS__*/', read('app.js'));
if (html.includes('/*__STYLES__*/') || html.includes('/*__APP_JS__*/')) {
  throw new Error('An HTML asset placeholder was not replaced.');
}

// Standalone copy for static hosting (GitHub Pages). Identical page, except the
// Apps Script bootstrap template is resolved at build time and the upload
// endpoint is the deployment URL. Recording only works here: an Apps Script web
// app is always framed by a sandbox that denies the microphone permission, so
// getUserMedia is rejected there before the browser can prompt.
const standalone = html.replace('<?!= bootstrapJson ?>', escapeForInlineScript(JSON.stringify(bootstrapData(config))));
if (standalone.includes('<?!= bootstrapJson ?>')) {
  throw new Error('The bootstrap template placeholder was not replaced in the standalone page.');
}

write('Code.gs', backend);
write('Index.html', html);
write('voice.html', standalone);
const manifest = JSON.parse(read('appsscript.json'));
manifest.timeZone = config.app.timezone;
write('appsscript.json', `${JSON.stringify(manifest, null, 2)}\n`);

console.log(`Built Apps Script project in ${outputDirectory}`);

/** Mirrors the payload doGet() builds in Code.gs. */
function bootstrapData(source) {
  return {
    endpoint: source.deployment.webAppUrl,
    standaloneUrl: source.deployment.standaloneUrl || '',
    app: source.app,
    recording: {
      minimumDurationMs: source.recording.minimumDurationMs,
      maximumDurationMs: source.recording.maximumDurationMs,
      maximumUploadBytes: source.recording.maximumUploadBytes,
      preferredSampleRate: source.recording.preferredSampleRate,
      preferredChannelCount: source.recording.preferredChannelCount,
      acceptedMimeTypes: source.recording.acceptedMimeTypes
    },
    theme: source.theme,
    ui: source.ui,
    phrases: source.phrases
  };
}

function escapeForInlineScript(value) {
  return value
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}

function escapeForSingleQuotedString(value) {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}
