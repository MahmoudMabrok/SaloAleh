/**
 * Google Apps Script backend for the Arabic speech dataset collector.
 * Configuration is injected from config.ts by build.mjs.
 */

const CONFIG = Object.freeze(JSON.parse('__CONFIG_JSON__'));
const RUNTIME_KEYS = Object.freeze({
  ROOT_FOLDER_ID: 'SPEECH_COLLECTOR_ROOT_FOLDER_ID',
  SPREADSHEET_ID: 'SPEECH_COLLECTOR_SPREADSHEET_ID',
  PHRASES_SIGNATURE: 'SPEECH_COLLECTOR_PHRASES_SIGNATURE'
});

/**
 * The three kinds of recording a card can produce.
 *
 * - A **clip** is one utterance and is a training example.
 * - A **streaming** take is the same dhikr repeated N times in one long clip.
 * - A **negative** take is minutes of ordinary sound holding no dhikr at all.
 *
 * The last two are evaluation material and measure different things: a
 * repetition take can only show whether the counter reaches ten, because every
 * detection in it lands on a dhikr that really is there; only a negative take
 * can show the counter firing when nobody said anything.
 *
 * They differ in allowed duration, size and destination folder, so the mode is
 * validated rather than trusted: a client cannot file a 90-second take as a
 * training clip, nor a one-second one as a repetition take, nor put five minutes
 * of television in a phrase's folder.
 */
const MODE_CLIP = 'clip';
const MODE_STREAMING = 'streaming';
const MODE_NEGATIVE = 'negative';

/** Serves the mobile web application. */
function doGet() {
  const template = HtmlService.createTemplateFromFile('Index');
  template.bootstrapJson = safeJsonForHtml_({
    endpoint: ScriptApp.getService().getUrl(),
    // Apps Script renders this page inside a sandbox iframe that withholds the
    // microphone permission, so the page offers the standalone copy instead.
    standaloneUrl: CONFIG.deployment.standaloneUrl,
    app: CONFIG.app,
    recording: {
      minimumDurationMs: CONFIG.recording.minimumDurationMs,
      maximumDurationMs: CONFIG.recording.maximumDurationMs,
      maximumUploadBytes: CONFIG.recording.maximumUploadBytes,
      preferredSampleRate: CONFIG.recording.preferredSampleRate,
      preferredChannelCount: CONFIG.recording.preferredChannelCount,
      acceptedMimeTypes: CONFIG.recording.acceptedMimeTypes,
      streaming: CONFIG.recording.streaming || null,
      negative: CONFIG.recording.negative || null
    },
    theme: CONFIG.theme,
    ui: CONFIG.ui,
    phrases: visiblePhrases_(),
    unknownPrompt: CONFIG.unknownPrompt || null,
    noisePrompt: CONFIG.noisePrompt || null,
    longNoisePrompt: CONFIG.longNoisePrompt || null
  });

  return template.evaluate()
    .setTitle(CONFIG.app.htmlTitle)
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.DEFAULT);
}

/** Accepts a JSON POST body containing metadata and base64-encoded audio. */
function doPost(event) {
  try {
    if (!event || !event.postData || typeof event.postData.contents !== 'string') {
      throw publicError_('EMPTY_REQUEST', 'Request body is required.');
    }

    // Base64 is roughly 4/3 of the binary size. Reject oversized bodies before
    // parsing to avoid unnecessary memory use. The ceiling is the larger of the
    // two modes' limits; which one this body is actually held to is decided by
    // validateRequest_, once the mode inside it can be read.
    const maximumBodyLength = Math.ceil(largestUploadBytes_() * 1.38) + 20000;
    if (event.postData.contents.length > maximumBodyLength) {
      throw publicError_('FILE_TOO_LARGE', 'The uploaded audio exceeds the size limit.');
    }

    const request = JSON.parse(event.postData.contents);
    return jsonResponse(saveAudio(request));
  } catch (error) {
    console.error(error && error.stack ? error.stack : error);
    return jsonResponse(errorPayload_(error));
  }
}

/**
 * Validates and stores one recording, then writes its metadata row.
 * The sample_id makes retries idempotent: repeated uploads return the original
 * row instead of creating duplicate files.
 */
function saveAudio(request) {
  const validated = validateRequest_(request);
  const audioBytes = Utilities.base64Decode(validated.audioBase64);

  if (audioBytes.length > validated.limits.maximumUploadBytes) {
    throw publicError_('FILE_TOO_LARGE', 'The uploaded audio exceeds the size limit.');
  }
  if (audioBytes.length < 64) {
    throw publicError_('INVALID_AUDIO', 'The uploaded audio is empty or invalid.');
  }

  const lock = LockService.getScriptLock();
  if (!lock.tryLock(30000)) {
    throw publicError_('SERVER_BUSY', 'The service is busy. Please retry.');
  }

  try {
    const sheet = getOrCreateSheet_();
    const existing = findExistingSample_(sheet, validated.sampleId);
    if (existing) {
      return {
        ok: true,
        duplicate: true,
        sample_id: existing.sampleId,
        filename: existing.filename,
        drive_file_id: existing.driveFileId,
        drive_url: existing.driveUrl
      };
    }

    const rootFolder = getOrCreateRootFolder_();
    ensurePhrasesFile_(rootFolder);
    const classFolderName = classFolderName_(validated.phrase);
    const classFolder = createFolderIfMissing(
      classParentFolder_(rootFolder, validated.phrase, validated.mode),
      classFolderName
    );
    const filename = createFilename_(
      classFolderName,
      validated.speakerToken,
      validated.mimeType,
      repetitionTag_(validated.mode)
    );
    const blob = Utilities.newBlob(audioBytes, validated.mimeType, filename);
    const file = classFolder.createFile(blob);

    try {
      const row = {
        sample_id: validated.sampleId,
        phrase_id: validated.phrase.id,
        phrase_text: validated.phraseText,
        filename: filename,
        duration_ms: validated.durationMs,
        sample_rate: validated.sampleRate,
        browser: safeSheetText_(validated.browser),
        platform: safeSheetText_(validated.platform),
        language: safeSheetText_(validated.language),
        created_at: new Date().toISOString(),
        drive_file_id: file.getId(),
        drive_url: file.getUrl()
      };

      appendSpreadsheetRow(sheet, row);
      return {
        ok: true,
        duplicate: false,
        sample_id: row.sample_id,
        filename: row.filename,
        drive_file_id: row.drive_file_id,
        drive_url: row.drive_url
      };
    } catch (error) {
      // Keep Drive and Sheets consistent if writing metadata fails.
      file.setTrashed(true);
      throw error;
    }
  } finally {
    lock.releaseLock();
  }
}

/**
 * The phrases that get a card. A hidden phrase stays a valid, labelled id with
 * its recordings intact; it is only off the page. Mirrors visiblePhrases() in
 * build.mjs, which builds the same payload for the standalone copy.
 */
function visiblePhrases_() {
  return CONFIG.phrases.filter(function(phrase) { return !phrase.hidden; });
}

/**
 * Every prompt an upload may name: the phrases plus the three non-phrase cards
 * (out-of-vocabulary speech, background noise, and the long negative take).
 *
 * Hidden phrases are still accepted. A volunteer who kept a tab open across a
 * redeploy would otherwise lose the take waiting on a card that has since been
 * parked, and the id still has a folder and a label either way.
 */
function allPrompts_() {
  var prompts = CONFIG.phrases.slice();
  if (CONFIG.unknownPrompt) prompts.push(CONFIG.unknownPrompt);
  if (CONFIG.noisePrompt) prompts.push(CONFIG.noisePrompt);
  if (CONFIG.longNoisePrompt) prompts.push(CONFIG.longNoisePrompt);
  return prompts;
}

function isUnknownPrompt_(prompt) {
  return Boolean(CONFIG.unknownPrompt) && prompt.id === CONFIG.unknownPrompt.id;
}

function isNoisePrompt_(prompt) {
  return Boolean(CONFIG.noisePrompt) && prompt.id === CONFIG.noisePrompt.id;
}

function isLongNoisePrompt_(prompt) {
  return Boolean(CONFIG.longNoisePrompt) && prompt.id === CONFIG.longNoisePrompt.id;
}

/**
 * The folder a prompt writes into: the configured `unknown` folder for the
 * negative class, the `noise` folder for background noise, the streaming tree's
 * `negative` folder for a long take with no dhikr in it, and a zero-padded
 * phrase id for everything else. The first three are names the pipeline knows
 * (`classes.unknown_class`, `paths.noise_dir`, and a non-numeric folder under
 * `paths.streaming_dir`, which is how it tells shared negative material from a
 * phrase's own takes); the rest are its class folders.
 */
function classFolderName_(prompt) {
  if (isNoisePrompt_(prompt)) return CONFIG.storage.noiseFolderName;
  if (isLongNoisePrompt_(prompt)) return CONFIG.storage.streamingNegativeFolderName;
  if (isUnknownPrompt_(prompt)) return CONFIG.storage.unknownFolderName;
  return padPhraseId_(prompt.id);
}

/**
 * Where that folder hangs. Everything the model learns lives under
 * `dataset/`, which is the tree the trainer scans as its classes. Noise is not
 * learned — it is mixed underneath training clips — so it hangs off the root
 * beside `dataset/`, exactly where `paths.noise_dir` resolves. Putting it
 * inside would turn background hiss into a class of its own.
 *
 * A repetition take is filed the same way and for the same reason: it holds the
 * dhikr N times, so it is not a training example at all, and a trainer scanning
 * `dataset/{id}/` must never find one there. It gets `streaming/{id}/` beside
 * the dataset instead — same class folder name, different tree.
 *
 * A negative take joins it in that tree, under a folder that is deliberately not
 * a phrase id: it belongs to no phrase, and every target's evaluation reads it.
 */
function classParentFolder_(rootFolder, prompt, mode) {
  if (isNoisePrompt_(prompt)) return rootFolder;
  if (mode === MODE_STREAMING || mode === MODE_NEGATIVE) {
    return createFolderIfMissing(rootFolder, CONFIG.storage.streamingSubfolder);
  }
  return createFolderIfMissing(rootFolder, CONFIG.storage.datasetSubfolder);
}

/** The configured repetition take, or null when the second button is dropped. */
function streamingSettings_() {
  return CONFIG.recording.streaming || null;
}

/** The configured long negative take, or null when that card is dropped. */
function negativeSettings_() {
  return CONFIG.recording.negative || null;
}

/**
 * The duration and size bounds this mode is held to. Each evaluation take needs
 * limits a training clip must never be allowed (90 seconds would be a whole
 * conversation filed as one utterance; five minutes doubly so), so they are
 * separate rather than the clip's bounds widened for everyone.
 */
function recordingLimits_(mode) {
  const settings = mode === MODE_NEGATIVE
    ? negativeSettings_()
    : (mode === MODE_STREAMING ? streamingSettings_() : null);
  if (settings) {
    return {
      minimumDurationMs: settings.minimumDurationMs,
      maximumDurationMs: settings.maximumDurationMs,
      maximumUploadBytes: settings.maximumUploadBytes
    };
  }
  return {
    minimumDurationMs: CONFIG.recording.minimumDurationMs,
    maximumDurationMs: CONFIG.recording.maximumDurationMs,
    maximumUploadBytes: CONFIG.recording.maximumUploadBytes
  };
}

/** The body-size ceiling doPost applies before it knows which mode it is reading. */
function largestUploadBytes_() {
  return [streamingSettings_(), negativeSettings_()].reduce(function(largest, settings) {
    return settings ? Math.max(largest, settings.maximumUploadBytes) : largest;
  }, CONFIG.recording.maximumUploadBytes);
}

/**
 * The `x{n}` token an evaluation take carries in its filename: how many times
 * the dhikr is in the audio. `x10` for a repetition take, and **`x0` for a
 * negative take** — zero is not the absence of a count, it is the count, and it
 * is the one that measures false activations.
 *
 * The collector cannot produce event timings, so the number it asked for is
 * written where it cannot be separated from the audio: the pipeline derives
 * `annotations.json` from the filename and the folder
 * (`streaming_eval.collector_annotations`) rather than anyone writing it.
 */
function repetitionTag_(mode) {
  if (mode === MODE_NEGATIVE) return negativeSettings_() ? 'x0' : '';
  const streaming = streamingSettings_();
  return mode === MODE_STREAMING && streaming ? 'x' + streaming.repetitions : '';
}

/** Returns the existing child folder or creates it when missing. */
function createFolderIfMissing(parentFolder, folderName) {
  const matches = parentFolder.getFoldersByName(folderName);
  return matches.hasNext() ? matches.next() : parentFolder.createFolder(folderName);
}

/**
 * The phrases.json body the DhikrSpeech pipeline reads: a plain [{id, text}]
 * list, sorted by id. CONFIG.phrases is ordered for the page, so sorting here
 * keeps the label file (and its change signature) stable when the cards are
 * reordered. The unknown and noise prompts are intentionally absent — they are
 * folders, not phrase ids, and the pipeline knows them by name.
 *
 * Hidden phrases ARE listed. Their `dataset/{id}/` folders still hold
 * recordings, and a label file that dropped them would leave the trainer
 * scanning folders it has no text for.
 */
function phrasesJsonContent_() {
  const phrases = CONFIG.phrases.map(function(phrase) {
    return { id: phrase.id, text: phrase.text };
  }).sort(function(a, b) { return a.id - b.id; });
  return JSON.stringify(phrases, null, 2) + '\n';
}

/**
 * Writes phrases.json at the root of the dataset folder so the training
 * pipeline gets its id->text labels without a manual upload. Best-effort: it
 * never throws, so a failure here can never fail an audio upload. A signature
 * in Script Properties gates the Drive write to only when the phrase list
 * changes (e.g. after a redeploy), keeping the common path free of extra I/O.
 */
function ensurePhrasesFile_(rootFolder) {
  try {
    const content = phrasesJsonContent_();
    const properties = PropertiesService.getScriptProperties();
    const signature = Utilities.base64Encode(
      Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, content)
    );
    if (properties.getProperty(RUNTIME_KEYS.PHRASES_SIGNATURE) === signature) return;

    const existing = rootFolder.getFilesByName(CONFIG.storage.phrasesFile);
    if (existing.hasNext()) {
      existing.next().setContent(content);
    } else {
      rootFolder.createFile(CONFIG.storage.phrasesFile, content, 'application/json');
    }
    properties.setProperty(RUNTIME_KEYS.PHRASES_SIGNATURE, signature);
  } catch (error) {
    console.warn('Could not sync ' + CONFIG.storage.phrasesFile + ': ' + error);
  }
}

/** Appends a row in the exact order configured in config.ts. */
function appendSpreadsheetRow(sheet, rowObject) {
  const values = CONFIG.spreadsheetColumns.map(function(column) {
    return Object.prototype.hasOwnProperty.call(rowObject, column) ? rowObject[column] : '';
  });
  sheet.appendRow(values);
  SpreadsheetApp.flush();
}

/** Returns an Apps Script JSON response. */
function jsonResponse(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}

function validateRequest_(request) {
  if (!request || typeof request !== 'object') {
    throw publicError_('INVALID_REQUEST', 'A JSON object is required.');
  }

  const phraseId = Number(request.phrase_id);
  const phrase = allPrompts_().find(function(item) { return item.id === phraseId; });
  if (!Number.isInteger(phraseId) || !phrase) {
    throw publicError_('INVALID_PHRASE', 'phrase_id is not valid.');
  }

  // The mode decides the destination folder and the limits below, so it is
  // checked against the prompt before either is read. Only a phrase can be
  // repeated: "say it ten times" is meaningless for the unknown card (whose
  // point is that every take is a different word) and for noise (no speech at
  // all), and allowing it would put non-class audio in a class folder.
  const mode = normalizeMode_(request.mode);
  if (mode === MODE_STREAMING) {
    if (!streamingSettings_()) {
      throw publicError_('INVALID_MODE', 'Repetition takes are not enabled.');
    }
    if (nonPhraseFolderName_(phrase)) {
      throw publicError_('INVALID_MODE', 'Only a phrase can be recorded as a repetition take.');
    }
  }
  if (mode === MODE_NEGATIVE) {
    if (!negativeSettings_()) {
      throw publicError_('INVALID_MODE', 'Long negative takes are not enabled.');
    }
    // A negative take is filed under a folder that means "no dhikr in here", so
    // letting a phrase card claim the mode would mark that phrase's own audio as
    // proof the counter should have stayed silent.
    if (!isLongNoisePrompt_(phrase)) {
      throw publicError_('INVALID_MODE', 'Only the long negative card can be recorded as a negative take.');
    }
  } else if (isLongNoisePrompt_(phrase)) {
    // The reverse, and the more damaging of the two: that card in any other mode
    // would put its audio in `dataset/{negative}/`, inventing a training class
    // out of television — the trainer scans dataset/ and would learn it.
    throw publicError_('INVALID_MODE', 'The long negative card records negative takes only.');
  }
  const limits = recordingLimits_(mode);

  const sampleId = String(request.sample_id || '');
  if (!/^[A-Za-z0-9_-]{16,80}$/.test(sampleId)) {
    throw publicError_('INVALID_SAMPLE_ID', 'sample_id is not valid.');
  }

  const durationMs = Math.round(Number(request.duration_ms));
  if (!Number.isFinite(durationMs) ||
      durationMs < limits.minimumDurationMs ||
      durationMs > limits.maximumDurationMs + 300) {
    throw publicError_('INVALID_DURATION', 'Recording duration is outside the allowed range.');
  }

  const mimeType = normalizeMimeType_(request.mime_type);
  if (CONFIG.recording.acceptedMimeTypes.indexOf(mimeType) === -1) {
    throw publicError_('INVALID_MIME_TYPE', 'This audio format is not accepted.');
  }

  const audioBase64 = String(request.audio_base64 || '');
  if (!audioBase64 || audioBase64.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(audioBase64)) {
    throw publicError_('INVALID_AUDIO', 'audio_base64 is not valid base64.');
  }

  const estimatedBytes = Math.floor(audioBase64.length * 0.75);
  if (estimatedBytes > limits.maximumUploadBytes + 2) {
    throw publicError_('FILE_TOO_LARGE', 'The uploaded audio exceeds the size limit.');
  }

  const sampleRate = Math.round(Number(request.sample_rate) || 0);
  if (sampleRate !== 0 && (sampleRate < 8000 || sampleRate > 192000)) {
    throw publicError_('INVALID_SAMPLE_RATE', 'sample_rate is not valid.');
  }

  return {
    phrase: phrase,
    // What the volunteer actually said. The unknown and noise cards' own texts
    // are instructions, not spoken phrases, so the sheet records the folder they
    // were filed under instead.
    phraseText: nonPhraseFolderName_(phrase) || phrase.text,
    mode: mode,
    limits: limits,
    sampleId: sampleId,
    speakerToken: speakerToken_(request.speaker_id),
    durationMs: durationMs,
    mimeType: mimeType,
    audioBase64: audioBase64,
    sampleRate: sampleRate,
    browser: limitedText_(request.browser, 120),
    platform: limitedText_(request.platform, 120),
    language: limitedText_(request.language || CONFIG.app.language, 35)
  };
}

function getOrCreateRootFolder_() {
  const properties = PropertiesService.getScriptProperties();
  const configuredId = CONFIG.storage.rootFolderId;
  const rememberedId = properties.getProperty(RUNTIME_KEYS.ROOT_FOLDER_ID);
  const folderId = configuredId || rememberedId;

  if (folderId) {
    try {
      return DriveApp.getFolderById(folderId);
    } catch (error) {
      if (configuredId) {
        throw publicError_('INVALID_FOLDER_ID', 'The configured Drive folder is unavailable.');
      }
      properties.deleteProperty(RUNTIME_KEYS.ROOT_FOLDER_ID);
    }
  }

  const matches = DriveApp.getFoldersByName(CONFIG.storage.rootFolderName);
  const folder = matches.hasNext() ? matches.next() : DriveApp.createFolder(CONFIG.storage.rootFolderName);
  properties.setProperty(RUNTIME_KEYS.ROOT_FOLDER_ID, folder.getId());
  return folder;
}

function getOrCreateSheet_() {
  const properties = PropertiesService.getScriptProperties();
  const configuredId = CONFIG.storage.spreadsheetId;
  const rememberedId = properties.getProperty(RUNTIME_KEYS.SPREADSHEET_ID);
  const spreadsheetId = configuredId || rememberedId;
  let spreadsheet;
  let wasCreated = false;

  if (spreadsheetId) {
    try {
      spreadsheet = SpreadsheetApp.openById(spreadsheetId);
    } catch (error) {
      if (configuredId) {
        throw publicError_('INVALID_SPREADSHEET_ID', 'The configured spreadsheet is unavailable.');
      }
      properties.deleteProperty(RUNTIME_KEYS.SPREADSHEET_ID);
    }
  }

  if (!spreadsheet) {
    spreadsheet = SpreadsheetApp.create(CONFIG.storage.spreadsheetName);
    wasCreated = true;
    properties.setProperty(RUNTIME_KEYS.SPREADSHEET_ID, spreadsheet.getId());
  }

  let sheet = spreadsheet.getSheetByName(CONFIG.storage.sheetName);
  if (!sheet) {
    sheet = wasCreated
      ? spreadsheet.getSheets()[0].setName(CONFIG.storage.sheetName)
      : spreadsheet.insertSheet(CONFIG.storage.sheetName);
  }

  ensureHeader_(sheet);
  return sheet;
}

function ensureHeader_(sheet) {
  const columns = CONFIG.spreadsheetColumns;
  if (sheet.getLastRow() === 0) {
    sheet.getRange(1, 1, 1, columns.length).setValues([columns]);
    sheet.setFrozenRows(1);
    sheet.getRange(1, 1, 1, columns.length)
      .setFontWeight('bold')
      .setBackground(CONFIG.theme.primary)
      .setFontColor('#ffffff');
    return;
  }

  const existing = sheet.getRange(1, 1, 1, columns.length).getDisplayValues()[0];
  if (existing.join('|') !== columns.join('|')) {
    throw publicError_('INVALID_SHEET_HEADER', 'The spreadsheet header does not match config.ts.');
  }
}

function findExistingSample_(sheet, sampleId) {
  if (sheet.getLastRow() < 2) return null;

  const sampleIdColumn = CONFIG.spreadsheetColumns.indexOf('sample_id') + 1;
  const match = sheet
    .getRange(2, sampleIdColumn, sheet.getLastRow() - 1, 1)
    .createTextFinder(sampleId)
    .matchEntireCell(true)
    .findNext();

  if (!match) return null;

  const row = sheet.getRange(match.getRow(), 1, 1, CONFIG.spreadsheetColumns.length).getValues()[0];
  const valueFor = function(column) { return row[CONFIG.spreadsheetColumns.indexOf(column)]; };
  return {
    sampleId: valueFor('sample_id'),
    filename: valueFor('filename'),
    driveFileId: valueFor('drive_file_id'),
    driveUrl: valueFor('drive_url')
  };
}

/** The class-folder name for a non-phrase prompt, or '' for a real phrase. */
function nonPhraseFolderName_(prompt) {
  if (isNoisePrompt_(prompt)) return CONFIG.storage.noiseFolderName;
  if (isLongNoisePrompt_(prompt)) return CONFIG.storage.streamingNegativeFolderName;
  if (isUnknownPrompt_(prompt)) return CONFIG.storage.unknownFolderName;
  return '';
}

/**
 * The device token stamped into every filename this browser uploads: `sp` plus
 * the first 8 hex characters of a UUID the page generates once and keeps.
 *
 * It exists so the trainer can keep one voice on one side of the train/val
 * split (`split.group_regex`, e.g. `sp[0-9a-f]{8}`). Without it the same
 * speaker sits on both sides and the reported validation accuracy is optimistic.
 * It identifies a browser profile, never a person: it is generated locally,
 * never leaves the filename, and is not linked to anything else we store.
 *
 * A client that cannot produce one (storage blocked, older build) simply gets
 * the old filename shape — a naming detail must never cost us a recording.
 */
function speakerToken_(speakerId) {
  const hex = String(speakerId || '').replace(/-/g, '').toLowerCase().replace(/[^0-9a-f]/g, '');
  return hex.length >= 8 ? 'sp' + hex.slice(0, 8) : '';
}

/**
 * `{class}_[x10_][sp…_]{timestamp}_{suffix}.{ext}`.
 *
 * The repetition tag sits before the speaker token so the class and the number
 * of events the clip should hold read as one prefix, and so `split.group_regex`
 * still finds `sp[0-9a-f]{8}` wherever it lands. An omitted field is left out
 * rather than written empty — a naming detail must never cost us a recording.
 */
function createFilename_(classFolderName, speakerToken, mimeType, repetitionTag) {
  const timestamp = Utilities.formatDate(new Date(), CONFIG.app.timezone, 'yyyyMMdd_HHmmss');
  const suffix = Utilities.getUuid().replace(/-/g, '').slice(0, 6).toLowerCase();
  const repetitions = repetitionTag ? repetitionTag + '_' : '';
  const speaker = speakerToken ? speakerToken + '_' : '';
  return classFolderName + '_' + repetitions + speaker + timestamp + '_' + suffix +
    extensionForMime_(mimeType);
}

/**
 * An absent or unrecognised mode is a clip. Older clients send no mode at all,
 * and the safe reading of "unknown" is the strictest of the three: a clip is
 * bounded to five seconds and lands in the dataset, which is where every
 * recording went before the evaluation takes existed.
 */
function normalizeMode_(mode) {
  const value = String(mode || '').trim().toLowerCase();
  if (value === MODE_STREAMING) return MODE_STREAMING;
  if (value === MODE_NEGATIVE) return MODE_NEGATIVE;
  return MODE_CLIP;
}

function padPhraseId_(phraseId) {
  const digits = CONFIG.storage.phraseFolderDigits;
  return String(phraseId).padStart(digits, '0');
}

function extensionForMime_(mimeType) {
  const extensions = {
    'audio/wav': '.wav',
    'audio/x-wav': '.wav',
    'audio/webm': '.webm',
    'audio/ogg': '.ogg',
    'audio/mp4': '.m4a',
    'audio/mpeg': '.mp3'
  };
  return extensions[mimeType] || '.audio';
}

function normalizeMimeType_(mimeType) {
  return String(mimeType || '').split(';')[0].trim().toLowerCase();
}

function limitedText_(value, maximumLength) {
  return String(value || '').replace(/[\u0000-\u001f\u007f]/g, '').slice(0, maximumLength);
}

function safeSheetText_(value) {
  const text = String(value || '');
  return /^[=+\-@]/.test(text) ? "'" + text : text;
}

function safeJsonForHtml_(value) {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}

function publicError_(code, message) {
  const error = new Error(message);
  error.publicCode = code;
  return error;
}

function errorPayload_(error) {
  return {
    ok: false,
    error: {
      code: error && error.publicCode ? error.publicCode : 'INTERNAL_ERROR',
      message: error && error.publicCode ? error.message : 'The server could not save the recording.'
    }
  };
}
