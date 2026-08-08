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
      acceptedMimeTypes: CONFIG.recording.acceptedMimeTypes
    },
    theme: CONFIG.theme,
    ui: CONFIG.ui,
    phrases: visiblePhrases_(),
    unknownPrompt: CONFIG.unknownPrompt || null,
    noisePrompt: CONFIG.noisePrompt || null
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
    // parsing to avoid unnecessary memory use.
    const maximumBodyLength = Math.ceil(CONFIG.recording.maximumUploadBytes * 1.38) + 20000;
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

  if (audioBytes.length > CONFIG.recording.maximumUploadBytes) {
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
      classParentFolder_(rootFolder, validated.phrase),
      classFolderName
    );
    const filename = createFilename_(classFolderName, validated.speakerToken, validated.mimeType);
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
 * Every prompt an upload may name: the phrases plus the two non-phrase cards
 * (out-of-vocabulary speech, and background noise).
 *
 * Hidden phrases are still accepted. A volunteer who kept a tab open across a
 * redeploy would otherwise lose the take waiting on a card that has since been
 * parked, and the id still has a folder and a label either way.
 */
function allPrompts_() {
  var prompts = CONFIG.phrases.slice();
  if (CONFIG.unknownPrompt) prompts.push(CONFIG.unknownPrompt);
  if (CONFIG.noisePrompt) prompts.push(CONFIG.noisePrompt);
  return prompts;
}

function isUnknownPrompt_(prompt) {
  return Boolean(CONFIG.unknownPrompt) && prompt.id === CONFIG.unknownPrompt.id;
}

function isNoisePrompt_(prompt) {
  return Boolean(CONFIG.noisePrompt) && prompt.id === CONFIG.noisePrompt.id;
}

/**
 * The folder a prompt writes into: the configured `unknown` folder for the
 * negative class, the `noise` folder for background noise, a zero-padded phrase
 * id for everything else. The first two are names the pipeline knows
 * (`classes.unknown_class`, `paths.noise_dir`); the rest are its class folders.
 */
function classFolderName_(prompt) {
  if (isNoisePrompt_(prompt)) return CONFIG.storage.noiseFolderName;
  if (isUnknownPrompt_(prompt)) return CONFIG.storage.unknownFolderName;
  return padPhraseId_(prompt.id);
}

/**
 * Where that folder hangs. Everything the model learns lives under
 * `dataset/`, which is the tree the trainer scans as its classes. Noise is not
 * learned — it is mixed underneath training clips — so it hangs off the root
 * beside `dataset/`, exactly where `paths.noise_dir` resolves. Putting it
 * inside would turn background hiss into a class of its own.
 */
function classParentFolder_(rootFolder, prompt) {
  return isNoisePrompt_(prompt)
    ? rootFolder
    : createFolderIfMissing(rootFolder, CONFIG.storage.datasetSubfolder);
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

  const sampleId = String(request.sample_id || '');
  if (!/^[A-Za-z0-9_-]{16,80}$/.test(sampleId)) {
    throw publicError_('INVALID_SAMPLE_ID', 'sample_id is not valid.');
  }

  const durationMs = Math.round(Number(request.duration_ms));
  if (!Number.isFinite(durationMs) ||
      durationMs < CONFIG.recording.minimumDurationMs ||
      durationMs > CONFIG.recording.maximumDurationMs + 300) {
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
  if (estimatedBytes > CONFIG.recording.maximumUploadBytes + 2) {
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

function createFilename_(classFolderName, speakerToken, mimeType) {
  const timestamp = Utilities.formatDate(new Date(), CONFIG.app.timezone, 'yyyyMMdd_HHmmss');
  const suffix = Utilities.getUuid().replace(/-/g, '').slice(0, 6).toLowerCase();
  const speaker = speakerToken ? speakerToken + '_' : '';
  return classFolderName + '_' + speaker + timestamp + '_' + suffix + extensionForMime_(mimeType);
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
