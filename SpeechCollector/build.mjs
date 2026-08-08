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
  // A page with nothing to record is a deployment nobody would notice was
  // broken: every phrase can be parked, but not all of them at once.
  if (visiblePhrases(config).length === 0) {
    throw new Error('Every phrase is hidden; the page would show no phrase cards.');
  }

  validateUnknownPrompt(config, ids);
  validateNoisePrompt(config, ids);
  validateStreamingRecording(config);

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
  if (typeof config.storage.phrasesFile !== 'string' || !config.storage.phrasesFile.trim()) {
    throw new Error('storage.phrasesFile must be a non-empty string (the labels file the collector writes, e.g. "phrases.json").');
  }
}

/**
 * The negative class is optional (set `unknownPrompt` to null to drop the card),
 * but a half-configured one would silently file ordinary speech under a phrase,
 * so every field it needs is checked here rather than at upload time.
 */
function validateUnknownPrompt(config, phraseIds) {
  const prompt = config.unknownPrompt;
  if (prompt === null || prompt === undefined) return;

  if (typeof prompt !== 'object') throw new Error('unknownPrompt must be an object or null.');
  if (!Number.isInteger(prompt.id) || prompt.id < 0) {
    throw new Error(`unknownPrompt.id must be a non-negative integer: ${prompt.id}`);
  }
  if (phraseIds.has(prompt.id)) throw new Error(`unknownPrompt.id ${prompt.id} collides with a phrase id.`);
  if (typeof prompt.text !== 'string' || !prompt.text.trim()) throw new Error('unknownPrompt has no text.');

  const folder = config.storage.unknownFolderName;
  if (typeof folder !== 'string' || !folder.trim()) {
    throw new Error('storage.unknownFolderName is required when unknownPrompt is set (e.g. "unknown").');
  }
  // A digits-only name would collide with a zero-padded phrase folder, and the
  // pipeline would read it as a phrase id instead of the filler class.
  if (/^\d+$/.test(folder.trim())) {
    throw new Error('storage.unknownFolderName must not be numeric; it would clash with a phrase folder.');
  }
}

/**
 * The noise card. Its folder sits beside the dataset rather than inside it, so
 * the checks here are about keeping those two levels apart: a noise folder that
 * shadowed the dataset folder, or a class folder inside it, would feed the
 * trainer background hiss as if it were someone reciting.
 */
function validateNoisePrompt(config, phraseIds) {
  const prompt = config.noisePrompt;
  if (prompt === null || prompt === undefined) return;

  if (typeof prompt !== 'object') throw new Error('noisePrompt must be an object or null.');
  if (!Number.isInteger(prompt.id)) throw new Error(`noisePrompt.id must be an integer: ${prompt.id}`);
  if (phraseIds.has(prompt.id)) throw new Error(`noisePrompt.id ${prompt.id} collides with a phrase id.`);
  if (config.unknownPrompt && config.unknownPrompt.id === prompt.id) {
    throw new Error(`noisePrompt.id ${prompt.id} collides with unknownPrompt.id.`);
  }
  if (typeof prompt.text !== 'string' || !prompt.text.trim()) throw new Error('noisePrompt has no text.');

  const folder = String(config.storage.noiseFolderName || '').trim();
  if (!folder) {
    throw new Error('storage.noiseFolderName is required when noisePrompt is set (e.g. "noise").');
  }
  if (folder === String(config.storage.datasetSubfolder).trim()) {
    throw new Error('storage.noiseFolderName must differ from datasetSubfolder; noise is not a dataset class.');
  }
}

/**
 * The repetition take: one long clip holding the same dhikr N times, filed
 * outside `dataset/`. It is optional (`recording.streaming: null` drops the
 * second button), but a half-configured one is the dangerous state — a
 * streaming folder equal to the dataset folder would put N-utterance clips
 * where the trainer scans for single ones, and a maximum that does not exceed
 * the clip maximum would mean the second button records exactly what the first
 * one does while claiming otherwise.
 */
function validateStreamingRecording(config) {
  const streaming = config.recording.streaming;
  if (streaming === null || streaming === undefined) return;

  if (typeof streaming !== 'object') throw new Error('recording.streaming must be an object or null.');
  if (!Number.isInteger(streaming.repetitions) || streaming.repetitions < 2) {
    throw new Error(`recording.streaming.repetitions must be an integer of at least 2: ${streaming.repetitions}`);
  }
  for (const key of ['minimumDurationMs', 'maximumDurationMs', 'maximumUploadBytes']) {
    if (!Number.isFinite(streaming[key]) || streaming[key] <= 0) {
      throw new Error(`recording.streaming.${key} must be a positive number.`);
    }
  }
  if (streaming.maximumDurationMs < streaming.minimumDurationMs) {
    throw new Error('recording.streaming duration limits are invalid.');
  }
  // A repetition take that fits inside a clip's five seconds is not a different
  // recording, and its own limits would be doing nothing.
  if (streaming.maximumDurationMs <= config.recording.maximumDurationMs) {
    throw new Error('recording.streaming.maximumDurationMs must exceed recording.maximumDurationMs; it holds several utterances.');
  }

  const folder = String(config.storage.streamingSubfolder || '').trim();
  if (!folder) {
    throw new Error('storage.streamingSubfolder is required when recording.streaming is set (e.g. "streaming").');
  }
  if (folder === String(config.storage.datasetSubfolder).trim()) {
    throw new Error('storage.streamingSubfolder must differ from datasetSubfolder; a repetition take is not a training clip.');
  }
  if (folder === String(config.storage.noiseFolderName || '').trim()) {
    throw new Error('storage.streamingSubfolder must differ from noiseFolderName.');
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

/**
 * The phrases that get a card. A hidden phrase keeps its id, its folder and its
 * phrases.json entry; it just never reaches the page, so the page never has to
 * know the concept exists. Mirrors visiblePhrases_() in Code.gs.
 */
function visiblePhrases(source) {
  return source.phrases.filter((phrase) => !phrase.hidden);
}

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
      acceptedMimeTypes: source.recording.acceptedMimeTypes,
      streaming: source.recording.streaming || null
    },
    theme: source.theme,
    ui: source.ui,
    phrases: visiblePhrases(source),
    unknownPrompt: source.unknownPrompt || null,
    noisePrompt: source.noisePrompt || null
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
