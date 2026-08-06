/**
 * Single source of configuration for the speech collector.
 *
 * This file intentionally uses JavaScript-compatible TypeScript so the
 * dependency-free build script can load it directly. Edit values here, then
 * run `node build.mjs` before copying the files in dist/ to Apps Script.
 */
globalThis.SPEECH_COLLECTOR_CONFIG = Object.freeze({
  app: {
    title: "Dhikr Speech Dataset",
    htmlTitle: "Arabic Speech Dataset Collector",
    language: "ar",
    direction: "rtl",
    timezone: "Africa/Cairo"
  },

  deployment: {
    // The Apps Script /exec URL of this project. The standalone page is not
    // served by Apps Script, so it cannot call ScriptApp.getService().getUrl()
    // and needs the deployment URL as its upload endpoint.
    webAppUrl: "https://script.google.com/macros/s/AKfycbyf_55s33KUlWU8UWF4pjaO_7NY5tYjmt2ssGphxvXJSyM6hTUsMPtz5_Mv6ojvKm4G7A/exec",

    // Public URL of dist/voice.html. Apps Script renders every web app inside a
    // googleusercontent.com sandbox iframe that does not delegate the
    // "microphone" permission, so getUserMedia there is rejected by permissions
    // policy and the browser never shows a prompt. Recording only works on a
    // top-level page, so the app links volunteers to this copy instead.
    standaloneUrl: "https://mahmoudmabrok.github.io/SaloAleh/voice.html"
  },

  storage: {
    // Leave blank to find/create this folder in the deploying user's My Drive.
    // Setting an ID is recommended for production deployments.
    rootFolderId: "1v8qS5-8NBiQOstqHSBapEpGb0O5eQyRy",
    rootFolderName: "Dhikr Speech Dataset",

    // Recordings are stored under `{root}/{datasetSubfolder}/{phraseId}/`.
    // This mirrors the DhikrSpeech training pipeline's `paths.dataset_dir`
    // (configs/config.yaml), which scans `dataset/` for the class folders, so
    // the collector and the trainer agree on the layout.
    datasetSubfolder: "dataset",

    // Folder for the negative class (see `unknownPrompt` below): anything that
    // is not one of the phrases lands in `{root}/{datasetSubfolder}/unknown/`
    // instead of a numeric phrase folder. Must match the pipeline's
    // `classes.unknown_class`, and must not look like a padded phrase id.
    unknownFolderName: "unknown",

    // The collector writes this file at the ROOT of the dataset folder (a
    // sibling of datasetSubfolder), regenerated from `phrases` below whenever
    // that list changes, so the DhikrSpeech pipeline finds its id->text labels
    // with no manual upload. Matches the pipeline's `paths.phrases_file`.
    phrasesFile: "phrases.json",

    // Leave blank to create a spreadsheet automatically on first upload.
    // Its ID is saved in Apps Script Properties for future uploads.
    spreadsheetId: "17nkSzNoyBB4PvCkaelLdyW82wFgcRPYoPAVDoEE5NoI",
    spreadsheetName: "Dhikr Speech Dataset Metadata",
    sheetName: "samples",
    phraseFolderDigits: 3
  },

  recording: {
    minimumDurationMs: 1000,
    maximumDurationMs: 5000,
    maximumUploadBytes: 5 * 1024 * 1024,
    preferredSampleRate: 16000,
    preferredChannelCount: 1,
    acceptedMimeTypes: [
      "audio/wav",
      "audio/x-wav",
      "audio/webm",
      "audio/ogg",
      "audio/mp4",
      "audio/mpeg"
    ]
  },

  theme: {
    primary: "#176b45",
    primaryDark: "#0d4f32",
    primarySoft: "#e9f6ef",
    accent: "#d6a53a",
    pageBackground: "#f4f8f5",
    cardBackground: "#ffffff",
    text: "#17362a",
    mutedText: "#61746b",
    danger: "#b3261e"
  },

  ui: {
    hero: "❤️ ساعدنا في بناء ميزة الذكر بالصوت",
    listHint: "لكل عبارة مسجّلها الخاص. سجّل وارفع أي عبارة على حدة، وكرّرها كما تشاء — كل عينة إضافية تفيدنا.",
    summaryPhrases: "{recorded} من {total} عبارات لها تسجيل",
    summarySamples: "{count} عينة مرفوعة",
    recordedCount: "✓ {count} عينة مرفوعة",
    record: "تسجيل",
    reRecord: "إعادة التسجيل",
    stop: "إيقاف",
    play: "استماع",
    pause: "إيقاف الاستماع",
    upload: "رفع التسجيل",
    uploading: "جارٍ الرفع…",
    uploadQueued: "في الانتظار…",
    uploadAll: "رفع كل التسجيلات الجاهزة ({count})",
    timerReady: "00:00.0",
    microphoneHint: "اقرأ العبارة بصوت واضح في مكان هادئ.",
    privacy: "لا نجمع الاسم أو البريد أو الهاتف أو الموقع. يُحفظ التسجيل والبيانات التقنية الأساسية فقط.",
    ready: "اضغط «تسجيل» عند أي عبارة واسمح باستخدام الميكروفون.",
    recording: "جارٍ التسجيل…",
    recordingReady: "التسجيل جاهز. استمع إليه أو ارفعه.",
    microphoneDenied: "تم رفض إذن الميكروفون. افتح إعدادات الموقع في المتصفح، فعّل الميكروفون، ثم أعد تحميل الصفحة.",
    microphoneBlocked: "المتصفح لا يعرض طلب الإذن لأن الصفحة معروضة داخل إطار لا يسمح بالميكروفون. افتح صفحة التسجيل في نافذة مستقلة ثم اضغط «تسجيل».",
    microphoneMissing: "لم يُعثر على ميكروفون متاح. وصّل ميكروفونًا أو تحقق من إعدادات الصوت ثم حاول مجددًا.",
    microphoneBusy: "الميكروفون مشغول بتطبيق آخر. أغلق التطبيقات التي تستخدمه ثم حاول مجددًا.",
    insecureContext: "التسجيل يتطلب فتح الصفحة عبر رابط https. افتح الرابط الرسمي للصفحة ثم حاول مجددًا.",
    openStandalone: "فتح صفحة التسجيل",
    unsupported: "هذا المتصفح لا يدعم تسجيل الصوت. جرّب إصدارًا حديثًا من Chrome أو Safari.",
    tooShort: "التسجيل قصير جدًا. سجّل لمدة ثانية واحدة على الأقل.",
    tooLarge: "حجم التسجيل كبير جدًا. سجّل مقطعًا أقصر ثم حاول مجددًا.",
    uploadSuccessTitle: "✅ شكرًا لك!",
    uploadSuccessBody: "تم رفع العينة. يمكنك تسجيل عينة أخرى لنفس العبارة.",
    uploadFailedTitle: "فشل رفع التسجيل",
    uploadFailedBody: "احتفظنا بالتسجيل. تحقق من الإنترنت ثم حاول مجددًا.",
    retry: "إعادة المحاولة",
    unknownBadge: "ليست ذكرًا"
  },

  spreadsheetColumns: [
    "sample_id",
    "phrase_id",
    "phrase_text",
    "filename",
    "duration_ms",
    "sample_rate",
    "browser",
    "platform",
    "language",
    "created_at",
    "drive_file_id",
    "drive_url"
  ],

  // The list order is the order the cards appear on the page — nothing more.
  // `id` is the phrase's identity everywhere else: the Drive folder it is
  // stored in, the class the model learns, and the key of the local upload
  // tally. Reorder freely to change what volunteers see first; never renumber
  // an id, or already-collected recordings would land in the wrong class.
  // phrases.json is written sorted by id, so this order does not reach the
  // training pipeline.
  phrases: [
    // Original short Tasbeeh prompt — most needed, so it leads the page.
    { id: 7, text: "سبحان الله العظيم وبحمده" },
    // Zabad challenge.
    { id: 6, text: "سبحان الله وبحمده" },
    // Baqiyat challenge (also reused by the Ten Days challenge).
    { id: 1, text: "سبحان الله" },
    { id: 2, text: "الحمد لله" },
    { id: 3, text: "الله أكبر" },
    { id: 4, text: "لا إله إلا الله" },
    // Original short Istighfar prompt.
    { id: 5, text: "أستغفر الله" },
    // Baqiyat challenge.
    { id: 8, text: "لا حول ولا قوة إلا بالله" },
    // Original Salawat prompts.
    { id: 9, text: "اللهم صل على محمد" },
    { id: 10, text: "اللهم صل وسلم على نبينا محمد" },
  ],

  // The negative class. A model trained on phrases alone has nowhere to put
  // ordinary speech, so it hands every word it hears to the nearest dhikr; this
  // card asks for exactly that ordinary speech. Its recordings go to
  // `{dataset}/unknownFolderName/` rather than a numeric phrase folder, and it
  // is deliberately left out of phrases.json — `unknown` is a class, not a
  // phrase, and the pipeline labels it from the folder name.
  // Set to null to drop the card. Its id must not collide with a phrase id.
  unknownPrompt: {
    id: 0,
    text: "قل أي كلمة عادية ليست ذكرًا",
    note: "مثل «صباح الخير» أو «كيف حالك» أو أي كلمة تخطر ببالك. غيّر الكلمة في كل تسجيل — هذه العينات تعلّم النموذج ما ليس ذكرًا حتى لا يَعُدّ كلامك العادي."
  }
});
