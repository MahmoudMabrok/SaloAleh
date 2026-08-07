/**
 * Google Apps Script backend for the Arabic speech dataset collector.
 * Configuration is injected from config.ts by build.mjs.
 */

const CONFIG = Object.freeze(JSON.parse('{"app":{"title":"Dhikr Speech Dataset","htmlTitle":"Arabic Speech Dataset Collector","language":"ar","direction":"rtl","timezone":"Africa/Cairo"},"deployment":{"webAppUrl":"https://script.google.com/macros/s/AKfycbyf_55s33KUlWU8UWF4pjaO_7NY5tYjmt2ssGphxvXJSyM6hTUsMPtz5_Mv6ojvKm4G7A/exec","standaloneUrl":"https://mahmoudmabrok.github.io/SaloAleh/voice.html"},"storage":{"rootFolderId":"1v8qS5-8NBiQOstqHSBapEpGb0O5eQyRy","rootFolderName":"Dhikr Speech Dataset","datasetSubfolder":"dataset","unknownFolderName":"unknown","phrasesFile":"phrases.json","spreadsheetId":"17nkSzNoyBB4PvCkaelLdyW82wFgcRPYoPAVDoEE5NoI","spreadsheetName":"Dhikr Speech Dataset Metadata","sheetName":"samples","phraseFolderDigits":3},"recording":{"minimumDurationMs":1000,"maximumDurationMs":5000,"maximumUploadBytes":5242880,"preferredSampleRate":16000,"preferredChannelCount":1,"acceptedMimeTypes":["audio/wav","audio/x-wav","audio/webm","audio/ogg","audio/mp4","audio/mpeg"]},"theme":{"primary":"#176b45","primaryDark":"#0d4f32","primarySoft":"#e9f6ef","accent":"#d6a53a","pageBackground":"#f4f8f5","cardBackground":"#ffffff","text":"#17362a","mutedText":"#61746b","danger":"#b3261e"},"ui":{"hero":"❤️ ساعدنا في بناء ميزة الذكر بالصوت","singleTakeRule":"قاعدة واحدة مهمة: كل تسجيل يحتوي على ذكر واحد يُقال مرة واحدة فقط. لا تكرّر العبارة داخل التسجيل نفسه.","listHint":"لكل عبارة مسجّلها الخاص. سجّل وارفع كل عبارة على حدة، ويمكنك رفع عدة عينات لنفس العبارة — كل عينة إضافية تفيدنا، بشرط أن يكون في كل تسجيل نطق واحد فقط.","summaryPhrases":"{recorded} من {total} عبارات لها تسجيل","summarySamples":"{count} عينة مرفوعة","recordedCount":"✓ {count} عينة مرفوعة","record":"تسجيل","reRecord":"إعادة التسجيل","stop":"إيقاف","play":"استماع","pause":"إيقاف الاستماع","upload":"رفع التسجيل","uploading":"جارٍ الرفع…","uploadQueued":"في الانتظار…","uploadAll":"رفع كل التسجيلات الجاهزة ({count})","timerReady":"00:00.0","microphoneHint":"اقرأ العبارة مرة واحدة بصوت واضح في مكان هادئ، ثم اضغط «إيقاف».","privacy":"لا نجمع الاسم أو البريد أو الهاتف أو الموقع. يُحفظ التسجيل والبيانات التقنية الأساسية فقط.","ready":"اضغط «تسجيل» عند أي عبارة واسمح باستخدام الميكروفون، ثم قل العبارة مرة واحدة.","recording":"جارٍ التسجيل… قل العبارة مرة واحدة فقط ثم اضغط «إيقاف».","recordingReady":"التسجيل جاهز. استمع إليه للتأكد أنه يحتوي على العبارة مرة واحدة فقط، ثم ارفعه.","microphoneDenied":"تم رفض إذن الميكروفون. افتح إعدادات الموقع في المتصفح، فعّل الميكروفون، ثم أعد تحميل الصفحة.","microphoneBlocked":"المتصفح لا يعرض طلب الإذن لأن الصفحة معروضة داخل إطار لا يسمح بالميكروفون. افتح صفحة التسجيل في نافذة مستقلة ثم اضغط «تسجيل».","microphoneMissing":"لم يُعثر على ميكروفون متاح. وصّل ميكروفونًا أو تحقق من إعدادات الصوت ثم حاول مجددًا.","microphoneBusy":"الميكروفون مشغول بتطبيق آخر. أغلق التطبيقات التي تستخدمه ثم حاول مجددًا.","insecureContext":"التسجيل يتطلب فتح الصفحة عبر رابط https. افتح الرابط الرسمي للصفحة ثم حاول مجددًا.","openStandalone":"فتح صفحة التسجيل","unsupported":"هذا المتصفح لا يدعم تسجيل الصوت. جرّب إصدارًا حديثًا من Chrome أو Safari.","tooShort":"التسجيل قصير جدًا. سجّل لمدة ثانية واحدة على الأقل.","tooLarge":"حجم التسجيل كبير جدًا. سجّل مقطعًا أقصر ثم حاول مجددًا.","uploadSuccessTitle":"✅ شكرًا لك!","uploadSuccessBody":"تم رفع العينة. يمكنك تسجيل عينة جديدة لنفس العبارة — نطق واحد في كل تسجيل.","uploadFailedTitle":"فشل رفع التسجيل","uploadFailedBody":"احتفظنا بالتسجيل. تحقق من الإنترنت ثم حاول مجددًا.","retry":"إعادة المحاولة","unknownBadge":"ليست ذكرًا"},"spreadsheetColumns":["sample_id","phrase_id","phrase_text","filename","duration_ms","sample_rate","browser","platform","language","created_at","drive_file_id","drive_url"],"phrases":[{"id":7,"text":"سبحان الله العظيم وبحمده"},{"id":6,"text":"سبحان الله وبحمده"},{"id":1,"text":"سبحان الله"},{"id":2,"text":"الحمد لله"},{"id":3,"text":"الله أكبر"},{"id":4,"text":"لا إله إلا الله"},{"id":5,"text":"أستغفر الله"},{"id":8,"text":"لا حول ولا قوة إلا بالله"},{"id":9,"text":"اللهم صل على محمد"},{"id":10,"text":"اللهم صل وسلم على نبينا محمد"}],"unknownPrompt":{"id":0,"text":"قل أي كلمة عادية ليست ذكرًا","note":"مثل «صباح الخير» أو «كيف حالك» أو أي كلمة تخطر ببالك. قلها مرة واحدة فقط في التسجيل بلا تكرار، وغيّر الكلمة في كل تسجيل جديد — هذه العينات تعلّم النموذج ما ليس ذكرًا حتى لا يَعُدّ كلامك العادي."}}'));
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
    phrases: CONFIG.phrases,
    unknownPrompt: CONFIG.unknownPrompt || null
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
    const datasetFolder = createFolderIfMissing(rootFolder, CONFIG.storage.datasetSubfolder);
    const classFolderName = classFolderName_(validated.phrase);
    const classFolder = createFolderIfMissing(datasetFolder, classFolderName);
    const filename = createFilename_(classFolderName, validated.mimeType);
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
 * Every prompt the page offers: the phrases plus the out-of-vocabulary card,
 * which is a class of its own rather than a phrase.
 */
function allPrompts_() {
  return CONFIG.unknownPrompt ? CONFIG.phrases.concat([CONFIG.unknownPrompt]) : CONFIG.phrases;
}

function isUnknownPrompt_(prompt) {
  return Boolean(CONFIG.unknownPrompt) && prompt.id === CONFIG.unknownPrompt.id;
}

/**
 * The dataset class folder a prompt writes into: the configured `unknown`
 * folder for the negative class, a zero-padded phrase id for everything else.
 * These are the folder names the DhikrSpeech pipeline scans as its classes.
 */
function classFolderName_(prompt) {
  return isUnknownPrompt_(prompt) ? CONFIG.storage.unknownFolderName : padPhraseId_(prompt.id);
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
 * reordered. The unknown prompt is intentionally absent — it is a class folder,
 * not a phrase id, and the pipeline labels it from the folder name.
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
    // What the volunteer actually said. The unknown card's own text is an
    // instruction, not a spoken phrase, so the sheet records the class name.
    phraseText: isUnknownPrompt_(phrase) ? CONFIG.storage.unknownFolderName : phrase.text,
    sampleId: sampleId,
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

function createFilename_(classFolderName, mimeType) {
  const timestamp = Utilities.formatDate(new Date(), CONFIG.app.timezone, 'yyyyMMdd_HHmmss');
  const suffix = Utilities.getUuid().replace(/-/g, '').slice(0, 6).toLowerCase();
  return classFolderName + '_' + timestamp + '_' + suffix + extensionForMime_(mimeType);
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
