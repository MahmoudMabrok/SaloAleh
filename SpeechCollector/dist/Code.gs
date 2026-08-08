/**
 * Google Apps Script backend for the Arabic speech dataset collector.
 * Configuration is injected from config.ts by build.mjs.
 */

const CONFIG = Object.freeze(JSON.parse('{"app":{"title":"Dhikr Speech Dataset","htmlTitle":"Arabic Speech Dataset Collector","language":"ar","direction":"rtl","timezone":"Africa/Cairo"},"deployment":{"webAppUrl":"https://script.google.com/macros/s/AKfycbyf_55s33KUlWU8UWF4pjaO_7NY5tYjmt2ssGphxvXJSyM6hTUsMPtz5_Mv6ojvKm4G7A/exec","standaloneUrl":"https://mahmoudmabrok.github.io/SaloAleh/voice.html"},"storage":{"rootFolderId":"1v8qS5-8NBiQOstqHSBapEpGb0O5eQyRy","rootFolderName":"Dhikr Speech Dataset","datasetSubfolder":"dataset","unknownFolderName":"unknown","noiseFolderName":"noise","phrasesFile":"phrases.json","spreadsheetId":"17nkSzNoyBB4PvCkaelLdyW82wFgcRPYoPAVDoEE5NoI","spreadsheetName":"Dhikr Speech Dataset Metadata","sheetName":"samples","phraseFolderDigits":3},"recording":{"minimumDurationMs":1000,"maximumDurationMs":5000,"maximumUploadBytes":5242880,"preferredSampleRate":16000,"preferredChannelCount":1,"acceptedMimeTypes":["audio/wav","audio/x-wav","audio/webm","audio/ogg","audio/mp4","audio/mpeg"]},"theme":{"primary":"#176b45","primaryDark":"#0d4f32","primarySoft":"#e9f6ef","accent":"#d6a53a","pageBackground":"#f4f8f5","cardBackground":"#ffffff","text":"#17362a","mutedText":"#61746b","danger":"#b3261e"},"ui":{"hero":"❤️ ساعدنا في بناء ميزة الذكر بالصوت","singleTakeRule":"قاعدة واحدة مهمة: كل تسجيل يحتوي على ذكر واحد يُقال مرة واحدة فقط. لا تكرّر العبارة داخل التسجيل نفسه.","listHint":"لكل عبارة مسجّلها الخاص. سجّل وارفع كل عبارة على حدة، ويمكنك رفع عدة عينات لنفس العبارة — كل عينة إضافية تفيدنا، بشرط أن يكون في كل تسجيل نطق واحد فقط.","summaryPhrases":"{recorded} من {total} عبارات لها تسجيل","summarySamples":"{count} عينة مرفوعة","recordedCount":"✓ {count} عينة مرفوعة","record":"تسجيل","reRecord":"إعادة التسجيل","stop":"إيقاف","play":"استماع","pause":"إيقاف الاستماع","upload":"رفع التسجيل","uploading":"جارٍ الرفع…","uploadQueued":"في الانتظار…","uploadAll":"رفع كل التسجيلات الجاهزة ({count})","timerReady":"00:00.0","microphoneHint":"اقرأ العبارة مرة واحدة بصوت واضح في مكان هادئ، ثم اضغط «إيقاف».","privacy":"لا نجمع الاسم أو البريد أو الهاتف أو الموقع. يُحفظ التسجيل والبيانات التقنية الأساسية فقط، مع رمز عشوائي يُنشأ في متصفحك ليُعرف أن تسجيلاتك من جهاز واحد — وهو غير مرتبط بك بأي شكل.","ready":"اضغط «تسجيل» عند أي عبارة واسمح باستخدام الميكروفون، ثم قل العبارة مرة واحدة.","recording":"جارٍ التسجيل… قل العبارة مرة واحدة فقط ثم اضغط «إيقاف».","recordingReady":"التسجيل جاهز. استمع إليه للتأكد أنه يحتوي على العبارة مرة واحدة فقط، ثم ارفعه.","microphoneDenied":"تم رفض إذن الميكروفون. افتح إعدادات الموقع في المتصفح، فعّل الميكروفون، ثم أعد تحميل الصفحة.","microphoneBlocked":"المتصفح لا يعرض طلب الإذن لأن الصفحة معروضة داخل إطار لا يسمح بالميكروفون. افتح صفحة التسجيل في نافذة مستقلة ثم اضغط «تسجيل».","microphoneMissing":"لم يُعثر على ميكروفون متاح. وصّل ميكروفونًا أو تحقق من إعدادات الصوت ثم حاول مجددًا.","microphoneBusy":"الميكروفون مشغول بتطبيق آخر. أغلق التطبيقات التي تستخدمه ثم حاول مجددًا.","insecureContext":"التسجيل يتطلب فتح الصفحة عبر رابط https. افتح الرابط الرسمي للصفحة ثم حاول مجددًا.","openStandalone":"فتح صفحة التسجيل","unsupported":"هذا المتصفح لا يدعم تسجيل الصوت. جرّب إصدارًا حديثًا من Chrome أو Safari.","tooShort":"التسجيل قصير جدًا. سجّل لمدة ثانية واحدة على الأقل.","tooLarge":"حجم التسجيل كبير جدًا. سجّل مقطعًا أقصر ثم حاول مجددًا.","uploadSuccessTitle":"✅ شكرًا لك!","uploadSuccessBody":"تم رفع العينة. يمكنك تسجيل عينة جديدة لنفس العبارة — نطق واحد في كل تسجيل.","uploadFailedTitle":"فشل رفع التسجيل","uploadFailedBody":"احتفظنا بالتسجيل. تحقق من الإنترنت ثم حاول مجددًا.","retry":"إعادة المحاولة","unknownBadge":"ليست ذكرًا","noiseBadge":"ضجيج فقط","noiseRecording":"جارٍ التسجيل… لا تتكلم، اترك أصوات المكان تُسجَّل وحدها.","noiseRecordingReady":"التسجيل جاهز. استمع إليه للتأكد أنه لا يحتوي على كلام، ثم ارفعه.","noiseUploadSuccessBody":"تم رفع العينة. سجّل ضجيجًا من مكان آخر أو وقت آخر — التنوّع هنا هو المفيد."},"spreadsheetColumns":["sample_id","phrase_id","phrase_text","filename","duration_ms","sample_rate","browser","platform","language","created_at","drive_file_id","drive_url"],"phrases":[{"id":1,"text":"سبحان الله"},{"id":6,"text":"سبحان الله وبحمده"},{"id":7,"text":"سبحان الله العظيم وبحمده"},{"id":8,"text":"لا حول ولا قوة إلا بالله"},{"id":5,"text":"أستغفر الله"},{"id":9,"text":"اللهم صل على محمد"},{"id":11,"text":"سبحان الله، الحمد لله، الله أكبر، لا إله إلا الله"},{"id":2,"text":"الحمد لله","hidden":true},{"id":3,"text":"الله أكبر","hidden":true},{"id":4,"text":"لا إله إلا الله","hidden":true},{"id":10,"text":"اللهم صل وسلم على نبينا محمد","hidden":true}],"unknownPrompt":{"id":0,"text":"قل أي كلمة عادية ليست ذكرًا","note":"مثل «صباح الخير» أو «كيف حالك» أو أي كلمة تخطر ببالك. قلها مرة واحدة فقط في التسجيل بلا تكرار، وغيّر الكلمة في كل تسجيل جديد — هذه العينات تعلّم النموذج ما ليس ذكرًا حتى لا يَعُدّ كلامك العادي."},"noisePrompt":{"id":-1,"text":"سجّل صوت المكان من حولك بدون أي كلام","note":"اترك الميكروفون يلتقط ما حولك: ضجيج الشارع، أصوات البيت، مروحة، سيارة، أو حتى غرفة هادئة. لا تتكلم ولا تقل ذكرًا في هذا التسجيل — هذه الأصوات تُخلط تحت تسجيلات الذكر أثناء التدريب حتى يعمل العدّاد في الأماكن الصاخبة."}}'));
const RUNTIME_KEYS = Object.freeze({
  ROOT_FOLDER_ID: 'SPEECH_COLLECTOR_ROOT_FOLDER_ID',
  SPREADSHEET_ID: 'SPEECH_COLLECTOR_SPREADSHEET_ID',
  PHRASES_SIGNATURE: 'SPEECH_COLLECTOR_PHRASES_SIGNATURE',
  // Row the speaker-token backfill should resume from. See backfillSpeakerTokens().
  BACKFILL_ROW: 'SPEECH_COLLECTOR_BACKFILL_ROW'
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

/* ===========================================================================
 * Maintenance: speaker tokens for recordings collected before the token existed
 *
 * Every filename written today carries a `sp<8 hex>` device token so the
 * trainer can keep one voice out of both sides of the train/val split
 * (`split.group_regex: "sp[0-9a-f]{8}"`). Recordings uploaded before that token
 * shipped have no such marker, so each of them is its own group and the same
 * voice can sit in train and validation — which is exactly what makes a
 * reported validation accuracy optimistic.
 *
 * Those recordings are not lost causes: the metadata sheet still holds the
 * `browser` and `platform` the upload came from, and that pair, hashed, is a
 * usable stand-in for a device. `backfillSpeakerTokens()` walks the sheet,
 * renames each untagged Drive file to the shape a fresh upload would have had,
 * and writes the new name back to the sheet.
 *
 * What this buys, and what it does not:
 *
 *  - It **over-groups**, and that is the safe direction. Two volunteers on the
 *    same Chrome/Android build collapse into one group, so they land in the
 *    same split; a single volunteer is never split across two. Leakage can only
 *    go down, never up. The cost is granularity — a large bucket lands wholly
 *    in one split — which is why previewSpeakerTokenBackfill() prints the group
 *    sizes before anything is renamed.
 *  - A derived token is deliberately the same `sp<8 hex>` shape as a real one,
 *    so the pipeline needs no change to honour it. To tell them apart later,
 *    recompute `derivedSpeakerToken_(browser, platform)` from the row: the
 *    derivation is deterministic, so a token that reproduces is a derived
 *    (coarse) one and a token that does not is a real per-browser id.
 *  - It never invents an identity out of nothing. A row with neither a browser
 *    nor a platform is left alone, keeping the current one-group-per-file
 *    behaviour rather than merging every unknown device into one bucket.
 * ======================================================================== */

const BACKFILL = Object.freeze({
  // Rows read, renamed and written back per chunk. The sheet write and the
  // cursor advance happen once per chunk, so a run that dies mid-way loses at
  // most this many sheet updates — and those are recovered on the next run by
  // the repair path below, which reads the name Drive already has.
  CHUNK_ROWS: 100,
  // Apps Script kills an execution at 6 minutes. Stop well before that and ask
  // to be run again, rather than being cut off at an arbitrary point.
  TIME_BUDGET_MS: 240000,
  // How many of the largest groups previewSpeakerTokenBackfill() lists.
  PREVIEW_GROUPS: 15
});

/** A filename that already names a speaker, whether real or derived. */
const SPEAKER_TOKEN_IN_FILENAME = /(?:^|_)sp[0-9a-f]{8}_/;

/**
 * The shape createFilename_() produces without a token:
 * `{class}_{yyyyMMdd}_{HHmmss}_{6 hex}{extension}`. Anything else is left
 * untouched rather than guessed at — a mangled name is worse than an untagged
 * one, because the class prefix is how the pipeline labels the clip.
 */
const UNTAGGED_FILENAME = /^([^_]+)_(\d{8}_\d{6}_[0-9a-f]{6}\.[A-Za-z0-9]+)$/;

/**
 * Reports what backfillSpeakerTokens() would do, without touching Drive or the
 * sheet. Reads only the spreadsheet, so it is cheap enough to run over the
 * whole history, and its group listing is the number to look at first: if one
 * bucket holds most of the dataset, grouping by it buys little.
 */
function previewSpeakerTokenBackfill() {
  const sheet = getOrCreateSheet_();
  const columns = CONFIG.spreadsheetColumns;
  const index = columnIndexes_(columns);
  const lastRow = sheet.getLastRow();
  const summary = {
    rows: 0, alreadyTagged: 0, toRename: 0, noMetadata: 0, unrecognised: 0, groups: []
  };
  const groups = {};

  if (lastRow >= 2) {
    const values = sheet.getRange(2, 1, lastRow - 1, columns.length).getValues();
    values.forEach(function(row) {
      summary.rows++;
      const filename = String(row[index.filename] || '');
      if (!filename) { summary.unrecognised++; return; }
      if (hasSpeakerToken_(filename)) { summary.alreadyTagged++; return; }

      const token = derivedSpeakerToken_(row[index.browser], row[index.platform]);
      if (!token) { summary.noMetadata++; return; }
      if (!filenameWithSpeakerToken_(filename, token)) { summary.unrecognised++; return; }

      summary.toRename++;
      if (!groups[token]) {
        groups[token] = { token: token, count: 0, device: deviceFingerprint_(row[index.browser], row[index.platform]) };
      }
      groups[token].count++;
    });
  }

  summary.groups = Object.keys(groups)
    .map(function(token) { return groups[token]; })
    .sort(function(a, b) { return b.count - a.count; });

  console.log(
    'Speaker-token backfill preview: ' + summary.rows + ' rows, ' +
    summary.alreadyTagged + ' already tagged, ' + summary.toRename + ' to rename into ' +
    summary.groups.length + ' derived groups, ' + summary.noMetadata + ' without browser/platform, ' +
    summary.unrecognised + ' with an unrecognised filename.'
  );
  summary.groups.slice(0, BACKFILL.PREVIEW_GROUPS).forEach(function(group) {
    console.log('  ' + group.token + '  ' + group.count + ' clips  ' + group.device);
  });
  return summary;
}

/**
 * Renames untagged recordings in Drive and updates the sheet to match.
 *
 * Resumable: the row to continue from lives in Script Properties, so a run that
 * hits the time budget just needs running again (resetSpeakerTokenBackfill()
 * starts over). Idempotent: a file that already carries a token is skipped, and
 * one renamed by an interrupted run has its sheet row repaired from the name
 * Drive reports rather than being renamed twice.
 *
 * Deliberately takes no script lock. It only ever writes the `filename` cell of
 * rows it has already read, and a concurrent upload appends at the bottom, so
 * the two cannot collide — and holding the lock for minutes would fail live
 * uploads with SERVER_BUSY for no gain.
 */
function backfillSpeakerTokens() {
  const properties = PropertiesService.getScriptProperties();
  const sheet = getOrCreateSheet_();
  const columns = CONFIG.spreadsheetColumns;
  const index = columnIndexes_(columns);
  const filenameColumn = index.filename + 1;
  const startedAt = Date.now();
  const totals = {
    scanned: 0, renamed: 0, repaired: 0, alreadyTagged: 0,
    noMetadata: 0, unrecognised: 0, missing: 0, failed: 0
  };

  let cursor = Math.max(2, Math.floor(Number(properties.getProperty(RUNTIME_KEYS.BACKFILL_ROW))) || 2);
  let lastRow = sheet.getLastRow();
  let complete = true;

  while (cursor <= lastRow) {
    if (Date.now() - startedAt > BACKFILL.TIME_BUDGET_MS) { complete = false; break; }

    const rowCount = Math.min(BACKFILL.CHUNK_ROWS, lastRow - cursor + 1);
    const values = sheet.getRange(cursor, 1, rowCount, columns.length).getValues();
    const names = [];
    let changed = false;

    for (let offset = 0; offset < rowCount; offset++) {
      const before = String(values[offset][index.filename] || '');
      const after = backfillRow_(values[offset], index, totals);
      if (after !== before) changed = true;
      names.push([after]);
    }

    // The sheet write comes before the cursor advance: a crash in between
    // re-reads this chunk next run, where the repair path makes it a no-op.
    if (changed) sheet.getRange(cursor, filenameColumn, rowCount, 1).setValues(names);
    cursor += rowCount;
    properties.setProperty(RUNTIME_KEYS.BACKFILL_ROW, String(cursor));
    // Uploads may have landed while this chunk ran; pick them up in this pass.
    lastRow = sheet.getLastRow();
  }

  const result = {
    complete: complete,
    nextRow: cursor,
    scanned: totals.scanned,
    renamed: totals.renamed,
    repaired: totals.repaired,
    alreadyTagged: totals.alreadyTagged,
    noMetadata: totals.noMetadata,
    unrecognised: totals.unrecognised,
    missing: totals.missing,
    failed: totals.failed
  };
  console.log(
    'Speaker-token backfill: scanned ' + result.scanned + ', renamed ' + result.renamed +
    ', repaired ' + result.repaired + ', already tagged ' + result.alreadyTagged +
    ', no browser/platform ' + result.noMetadata + ', unrecognised name ' + result.unrecognised +
    ', file missing ' + result.missing + ', rename failed ' + result.failed + '. ' +
    (complete
      ? 'Finished; run resetSpeakerTokenBackfill() before running it again.'
      : 'Time budget reached — run backfillSpeakerTokens() again to continue from row ' + result.nextRow + '.')
  );
  return result;
}

/** Forgets the resume position so the next backfill run starts from the top. */
function resetSpeakerTokenBackfill() {
  PropertiesService.getScriptProperties().deleteProperty(RUNTIME_KEYS.BACKFILL_ROW);
  console.log('Speaker-token backfill cursor cleared; the next run starts at the first row.');
}

/**
 * Brings one sheet row and its Drive file in line, returning the filename the
 * row should now hold. Every failure is counted and returns the existing name:
 * one unreadable file must not stop the pass.
 */
function backfillRow_(row, index, totals) {
  const recordedName = String(row[index.filename] || '');
  const fileId = String(row[index.drive_file_id] || '');
  totals.scanned++;

  if (!fileId) { totals.missing++; return recordedName; }

  let file;
  try {
    file = DriveApp.getFileById(fileId);
  } catch (error) {
    totals.missing++;
    console.warn('Backfill: no Drive file for ' + fileId + ' (' + recordedName + '): ' + error);
    return recordedName;
  }

  // Drive, not the sheet, is the authority on what a file is called: an
  // interrupted run leaves a renamed file next to a stale row, and that row is
  // repaired here rather than the file being renamed a second time.
  const currentName = file.getName();
  if (hasSpeakerToken_(currentName)) {
    if (currentName !== recordedName) { totals.repaired++; return currentName; }
    totals.alreadyTagged++;
    return recordedName;
  }

  const token = derivedSpeakerToken_(row[index.browser], row[index.platform]);
  if (!token) { totals.noMetadata++; return currentName; }

  const renamed = filenameWithSpeakerToken_(currentName, token);
  if (!renamed) {
    totals.unrecognised++;
    console.warn('Backfill: unrecognised filename shape, left as is: ' + currentName);
    return currentName;
  }

  try {
    file.setName(renamed);
  } catch (error) {
    totals.failed++;
    console.warn('Backfill: could not rename ' + currentName + ': ' + error);
    return currentName;
  }
  totals.renamed++;
  return renamed;
}

/** Column name -> zero-based position, so row arrays are read by name. */
function columnIndexes_(columns) {
  const indexes = {};
  columns.forEach(function(column, position) { indexes[column] = position; });
  return indexes;
}

/**
 * The stand-in device token for a recording that predates the real one: `sp`
 * plus the first 8 hex characters of the SHA-256 of the browser/platform pair.
 * Returns '' when the row names neither, so a row with no signal keeps the
 * pipeline's one-group-per-file fallback instead of joining a catch-all bucket.
 */
function derivedSpeakerToken_(browser, platform) {
  const fingerprint = deviceFingerprint_(browser, platform);
  return fingerprint ? 'sp' + shortHex_(fingerprint) : '';
}

/**
 * The string the token is derived from. Case and whitespace are normalised so
 * the same device reported two ways still hashes to one group, and the two
 * fields are joined with a separator so ('ab','c') and ('a','bc') stay apart.
 */
function deviceFingerprint_(browser, platform) {
  const parts = [browser, platform].map(function(value) {
    return String(value === null || value === undefined ? '' : value)
      .trim().toLowerCase().replace(/\s+/g, ' ');
  });
  return parts.join('') ? parts.join('|') : '';
}

/** First 4 bytes of the SHA-256 of `text`, as 8 lowercase hex characters. */
function shortHex_(text) {
  const digest = Utilities.computeDigest(
    Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8
  );
  let hex = '';
  for (let position = 0; position < 4; position++) {
    // Apps Script hands back signed bytes; mask before formatting.
    hex += ('0' + (digest[position] & 0xff).toString(16)).slice(-2);
  }
  return hex;
}

/** Whether a filename already names a speaker. */
function hasSpeakerToken_(filename) {
  return SPEAKER_TOKEN_IN_FILENAME.test(String(filename || ''));
}

/**
 * The same filename with `token` in the slot createFilename_() puts it in, or
 * '' when the name is not the untagged shape or the token is malformed.
 */
function filenameWithSpeakerToken_(filename, token) {
  const match = UNTAGGED_FILENAME.exec(String(filename || ''));
  if (!match || !/^sp[0-9a-f]{8}$/.test(String(token || ''))) return '';
  return match[1] + '_' + token + '_' + match[2];
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
