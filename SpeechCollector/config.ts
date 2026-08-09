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
    webAppUrl: "https://script.google.com/macros/s/AKfycbwidP0GUJ3VnqSnPa4fS7YZv0QZdjI5uIfvC3UVRXb4IXzak7vW3idqdL3JVyiv_PgZtw/exec",

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

    // Folder for the background-noise pool (see `noisePrompt` below). Unlike
    // `unknown`, noise is NOT a class the model learns — it is the audio the
    // training augmentation mixes underneath real clips — so it is written at
    // `{root}/noise/`, a SIBLING of datasetSubfolder rather than a folder
    // inside it. That is the path the pipeline resolves for `paths.noise_dir`;
    // filing it under `dataset/` would make the trainer scan it as a class.
    noiseFolderName: "noise",

    // Folder for the repetition takes (see `recording.streaming` below): one
    // long clip holding the same dhikr N times in a row. Like `noise`, this is a
    // SIBLING of datasetSubfolder — these clips are not single training
    // examples, so a trainer scanning `dataset/` must never see them. They are
    // the raw material for DhikrSpeech's streaming evaluation, whose
    // `paths.streaming_dir` reads this folder directly — per-phrase subfolders
    // and all. The expected repetition count travels in the filename (`_x10_`),
    // so a clip is never separated from the number of events it is meant to
    // hold, and the pipeline derives `annotations.json` from the two of them
    // (`streaming_eval.collector_annotations`) rather than anyone writing it.
    // Timings are still not derivable, and nor is the false-activation rate:
    // every take here contains the dhikr, so no detection in one is known to be
    // wrong. That number needs audio with no dhikr in it, which this collector
    // does not gather.
    // Layout: `{root}/streaming/{paddedPhraseId}/`.
    streamingSubfolder: "streaming",

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
    ],

    // The second recorder on every phrase card: ONE long clip holding the same
    // dhikr `repetitions` times, with a short pause between each. It answers a
    // question the clip dataset cannot — a clip is already cut to one utterance,
    // so it can never show whether the counter fires once per dhikr while
    // listening continuously, which is the number that decides the feature.
    //
    // It is therefore a different recording in every respect that matters: its
    // own duration bounds (a five-second cap could not hold ten repetitions),
    // its own size ceiling, and its own destination folder. The limits below are
    // enforced on the server per mode, so a long take can never be filed as a
    // training clip and a clip can never claim the repetition folder.
    //
    // The maximum is sized for the longest phrase on the page (the four Baqiyat
    // as one utterance) said ten times with pauses; the minimum only rejects a
    // take that is obviously too short to hold them. Set to null to drop the
    // second button from every card.
    streaming: {
      repetitions: 10,
      minimumDurationMs: 8000,
      maximumDurationMs: 90000,
      // Opus at the configured bitrate needs ~1.5 MB for a 90 s take, so this is
      // headroom rather than a limit anyone reaches. It exists because the body
      // Apps Script has to base64-decode is ~1.38x this number.
      maximumUploadBytes: 8 * 1024 * 1024
    }
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
    // The one rule a volunteer can get wrong in a way that costs us the sample:
    // the model learns one utterance per clip, so a clip holding the phrase twice
    // is unusable. Stated on its own line, before the cards, and repeated at the
    // moments it matters (while recording, and after a take is ready).
    singleTakeRule: "قاعدة واحدة مهمة: في زر «تسجيل» العادي كل تسجيل يحتوي على ذكر واحد يُقال مرة واحدة فقط. لا تكرّر العبارة داخل التسجيل نفسه — التكرار له زره الخاص وحده.",
    listHint: "لكل عبارة مسجّلها الخاص. سجّل وارفع كل عبارة على حدة، ويمكنك رفع عدة عينات لنفس العبارة — كل عينة إضافية تفيدنا، بشرط أن يكون في كل تسجيل نطق واحد فقط.",
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
    microphoneHint: "اقرأ العبارة مرة واحدة بصوت واضح في مكان هادئ، ثم اضغط «إيقاف».",
    privacy: "لا نجمع الاسم أو البريد أو الهاتف أو الموقع. يُحفظ التسجيل والبيانات التقنية الأساسية فقط، مع رمز عشوائي يُنشأ في متصفحك ليُعرف أن تسجيلاتك من جهاز واحد — وهو غير مرتبط بك بأي شكل.",
    ready: "اضغط «تسجيل» عند أي عبارة واسمح باستخدام الميكروفون، ثم قل العبارة مرة واحدة.",
    recording: "جارٍ التسجيل… قل العبارة مرة واحدة فقط ثم اضغط «إيقاف».",
    recordingReady: "التسجيل جاهز. استمع إليه للتأكد أنه يحتوي على العبارة مرة واحدة فقط، ثم ارفعه.",
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
    uploadSuccessBody: "تم رفع العينة. يمكنك تسجيل عينة جديدة لنفس العبارة — نطق واحد في كل تسجيل.",
    uploadFailedTitle: "فشل رفع التسجيل",
    uploadFailedBody: "احتفظنا بالتسجيل. تحقق من الإنترنت ثم حاول مجددًا.",
    retry: "إعادة المحاولة",
    unknownBadge: "ليست ذكرًا",
    // The noise card's own wording. Every other card tells the volunteer to say
    // the phrase once; on this one that instruction is wrong, so the three
    // strings that carry it are overridden rather than reworded for everyone.
    noiseBadge: "ضجيج فقط",
    noiseRecording: "جارٍ التسجيل… لا تتكلم، اترك أصوات المكان تُسجَّل وحدها.",
    noiseRecordingReady: "التسجيل جاهز. استمع إليه للتأكد أنه لا يحتوي على كلام، ثم ارفعه.",
    noiseUploadSuccessBody: "تم رفع العينة. سجّل ضجيجًا من مكان آخر أو وقت آخر — التنوّع هنا هو المفيد.",
    // The repetition take. Its whole point is that the single-utterance rule
    // does NOT apply to it, so every string it shows says the opposite of the
    // card's other strings and none of them may be reworded to match.
    // `{reps}` is replaced with recording.streaming.repetitions, so changing the
    // number in one place does not leave the wording behind.
    streamingRecord: "تسجيل {reps} مرات",
    streamingReRecord: "إعادة تسجيل الـ{reps} مرات",
    streamingHint: "زر «تسجيل {reps} مرات» مختلف تمامًا: مقطع واحد طويل تقول فيه العبارة {reps} مرات، مع وقفة قصيرة بين كل مرة والتي تليها. هذه التسجيلات وحدها تقيس هل يَعُدّ التطبيق كل ذكر مرة واحدة أثناء الاستماع المتواصل.",
    streamingRecording: "جارٍ التسجيل… قل العبارة {reps} مرات مع وقفة قصيرة بين كل مرة، ثم اضغط «إيقاف».",
    streamingRecordingReady: "التسجيل الطويل جاهز. استمع إليه للتأكد أنه يحتوي على العبارة {reps} مرات، ثم ارفعه.",
    streamingUploadSuccessBody: "تم رفع التسجيل الطويل. يمكنك تسجيل مقطع آخر لنفس العبارة — {reps} مرات في كل مقطع.",
    streamingTooShort: "التسجيل أقصر من أن يحتوي على {reps} مرات. أعد التسجيل وقل العبارة {reps} مرات كاملة.",
    streamingCount: "🔁 {count} تسجيل متكرر"
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
  //
  // `hidden: true` takes a phrase off the page without deleting it. The id stays
  // reserved, the phrase keeps its entry in phrases.json, and the recordings
  // already sitting in its Drive folder keep their label — only the card is
  // gone. That is the difference between parking a phrase and removing it:
  // removing it would orphan `dataset/{id}/`, and the next phrase added would
  // inherit the freed id and inherit those recordings with it.
  phrases: [
    // ---- Shown on the page, in this order. ----
    // The bare Tasbeeh. It leads because 6 and 7 below are it plus a tail, and
    // 11 opens with it: showing the three nested forms next to each other is
    // what stops a volunteer skimming the page and reciting the wrong one.
    { id: 1, text: "سبحان الله" },
    // Zabad challenge.
    { id: 6, text: "سبحان الله وبحمده" },
    // Original short Tasbeeh prompt.
    { id: 7, text: "سبحان الله العظيم وبحمده" },
    // Baqiyat challenge.
    { id: 8, text: "لا حول ولا قوة إلا بالله" },
    // Original short Istighfar prompt.
    { id: 5, text: "أستغفر الله" },
    // Original Salawat prompt.
    { id: 9, text: "اللهم صل على محمد" },
    // The four Baqiyat spoken as one utterance, the way the Baqiyat and Ten
    // Days challenges are actually recited — one tap per full cycle, not per
    // word. Its four parts are also phrases 1-4 on their own, so the model sees
    // each of them as a prefix of this one; that is exactly the nested-prefix
    // case softmax handles and a per-phrase binary detector does not.
    { id: 11, text: "سبحان الله، الحمد لله، الله أكبر، لا إله إلا الله" },

    // ---- Parked: no card, id and dataset folder preserved. ----
    // The rest of the Baqiyat as separate words. Superseded on the page by
    // phrase 11, which is how they are said in the app. (سبحان الله is the
    // exception: it is a counted dhikr in its own right, so it keeps a card.)
    { id: 2, text: "الحمد لله", hidden: true },
    { id: 3, text: "الله أكبر", hidden: true },
    { id: 4, text: "لا إله إلا الله", hidden: true },
    // The long Salawat. Phrase 9 is the one the counter needs.
    { id: 10, text: "اللهم صل وسلم على نبينا محمد", hidden: true },
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
    note: "مثل «صباح الخير» أو «كيف حالك» أو أي كلمة تخطر ببالك. قلها مرة واحدة فقط في التسجيل بلا تكرار، وغيّر الكلمة في كل تسجيل جديد — هذه العينات تعلّم النموذج ما ليس ذكرًا حتى لا يَعُدّ كلامك العادي."
  },

  // Background noise. Not a class and not speech: these clips are mixed
  // *underneath* real recordings during training, so a model learned in a quiet
  // room still hears the dhikr in a street, a car or a mosque. It is the one
  // card where the volunteer is asked to say nothing at all, and the one whose
  // audio lands outside `dataset/` — in `{root}/noiseFolderName/`, which is
  // where the pipeline's `paths.noise_dir` looks.
  // Set to null to drop the card. Non-phrase prompt ids are <= 0 by convention
  // (phrase ids count up from 1), so they can never collide with one.
  noisePrompt: {
    id: -1,
    text: "سجّل صوت المكان من حولك بدون أي كلام",
    note: "اترك الميكروفون يلتقط ما حولك: ضجيج الشارع، أصوات البيت، مروحة، سيارة، أو حتى غرفة هادئة. لا تتكلم ولا تقل ذكرًا في هذا التسجيل — هذه الأصوات تُخلط تحت تسجيلات الذكر أثناء التدريب حتى يعمل العدّاد في الأماكن الصاخبة."
  }
});
