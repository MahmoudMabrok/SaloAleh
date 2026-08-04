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
    webAppUrl: "https://script.google.com/macros/s/AKfycby0iRCm_qYASYLppPhF9FUTHyEuiIsqxV-Zm_Rm0r7NLQ3DuVUshT9ZRV5vc8zgplbnKQ/exec",

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
    hero: "❤️ ساهم في تحسين التعرّف على الكلام العربي",
    currentPhrase: "العبارة الحالية",
    phraseProgress: "العبارة {current} من {total}",
    record: "تسجيل",
    stop: "إيقاف",
    play: "استماع",
    pause: "إيقاف الاستماع",
    upload: "رفع التسجيل",
    uploading: "جارٍ الرفع…",
    next: "التالي",
    timerReady: "00:00.0",
    microphoneHint: "اقرأ العبارة بصوت واضح في مكان هادئ.",
    privacy: "لا نجمع الاسم أو البريد أو الهاتف أو الموقع. يُحفظ التسجيل والبيانات التقنية الأساسية فقط.",
    ready: "اضغط «تسجيل» واسمح باستخدام الميكروفون.",
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
    uploadSuccessTitle: "✅ شكرًا لك!",
    uploadSuccessBody: "تم رفع العينة بنجاح.",
    uploadFailedTitle: "فشل رفع التسجيل",
    uploadFailedBody: "احتفظنا بالتسجيل. تحقق من الإنترنت ثم حاول مجددًا.",
    retry: "إعادة المحاولة",
    nextBlocked: "ارفع التسجيل الحالي قبل الانتقال حتى لا تفقده.",
    completed: "شكرًا! أكملت جميع العبارات. يمكنك البدء من جديد وجمع عينات إضافية.",
    restart: "البدء من جديد"
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

  phrases: [
    // Baqiyat challenge (also reused by the Ten Days challenge).
    { id: 1, text: "سبحان الله" },
    { id: 2, text: "الحمد لله" },
    { id: 3, text: "الله أكبر" },
    { id: 4, text: "لا إله إلا الله" },
    // Original short Istighfar prompt.
    { id: 5, text: "أستغفر الله" },
    // Zabad challenge.
    { id: 6, text: "سبحان الله وبحمده" },
    // Original short Tasbeeh prompt.
    { id: 7, text: "سبحان الله العظيم وبحمده" },
    // Baqiyat challenge.
    { id: 8, text: "لا حول ولا قوة إلا بالله" },
    // Original Salawat prompts.
    { id: 9, text: "اللهم صل على محمد" },
    { id: 10, text: "اللهم صل وسلم على نبينا محمد" },
  ]
});
