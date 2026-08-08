'use strict';

(() => {
  const config = JSON.parse(document.getElementById('bootstrap-data').textContent);
  const $ = (id) => document.getElementById(id);

  // Per-device tally of successful uploads, shown on every card so a volunteer
  // can see at a glance which phrases they have already covered.
  const UPLOAD_COUNTS_KEY = 'speech_collector_upload_counts';

  // A random UUID minted once per browser and kept. It is sent with every
  // upload so the backend can stamp a short token of it into the filename,
  // which lets the trainer keep one voice on one side of the train/val split.
  // It is not an account and not a person: nothing else is stored against it,
  // and it never leaves this browser except as those 8 characters.
  const SPEAKER_ID_KEY = 'speech_collector_speaker_id';

  // The microphone is held between takes so recording a run of cards prompts
  // once instead of once per card, and released again when the volunteer stops.
  const MICROPHONE_IDLE_RELEASE_MS = 30000;

  // Below this the bar would only duplicate a card's own upload button, which
  // is already on screen. It earns its place once several takes are waiting.
  const UPLOAD_ALL_MINIMUM = 2;

  const IDLE = 'idle';
  const RECORDING = 'recording';
  const PROCESSING = 'processing';
  const READY = 'ready';
  const QUEUED = 'queued';
  const UPLOADING = 'uploading';
  const FAILED = 'failed';

  const elements = {
    list: $('phrase-list'),
    summaryPhrases: $('summary-phrases'),
    summarySamples: $('summary-samples'),
    progressFill: $('progress-fill'),
    status: $('page-status'),
    player: $('audio-player'),
    standalone: $('standalone-banner'),
    standaloneLink: $('standalone-link'),
    uploadAllBar: $('upload-all-bar'),
    uploadAll: $('upload-all-button'),
    uploadAllLabel: $('upload-all-label')
  };

  // The phrases plus the out-of-vocabulary card, which asks for any ordinary
  // word instead of a dhikr. It is a card like any other; only its destination
  // folder differs, and the backend decides that from its id.
  const prompts = collectPrompts();

  const state = {
    cards: [],
    // The card that owns the microphone. Only one take can run at a time, so
    // this is what locks every other card's record button.
    activeIndex: -1,
    playingIndex: -1,
    recorder: null,
    stream: null,
    chunks: [],
    startedAt: 0,
    pendingDurationMs: 0,
    stoppedAutomatically: false,
    timerId: 0,
    automaticStopId: 0,
    microphoneReleaseId: 0,
    audioContext: null,
    analyser: null,
    analyserSource: null,
    animationId: 0,
    // Uploads run one after another: the backend appends a spreadsheet row per
    // sample, and a batch firing at once would race on it.
    uploadChain: Promise.resolve(),
    uploadCounts: {},
    speakerId: '',
    microphoneUnavailable: false
  };

  initialize();

  function initialize() {
    document.documentElement.lang = config.app.language;
    document.documentElement.dir = config.app.direction;
    document.title = config.app.htmlTitle;
    applyTheme();
    applyTranslations();
    state.uploadCounts = loadUploadCounts();
    state.speakerId = loadSpeakerId();
    buildCards();
    bindEvents();
    render();

    if (!window.isSecureContext) {
      setPageStatus(config.ui.insecureContext, 'error');
      disableRecording();
      return;
    }

    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setPageStatus(config.ui.unsupported, 'error');
      disableRecording();
      return;
    }

    // Apps Script serves every web app inside a googleusercontent.com sandbox
    // iframe that does not delegate the "microphone" permission. getUserMedia is
    // then rejected by permissions policy *without* showing a prompt, so offer
    // the standalone page whenever this copy is framed.
    if (isEmbedded()) showStandaloneBanner();

    if (policyDeniesMicrophone()) {
      setPageStatus(config.ui.microphoneBlocked, 'error');
      disableRecording();
      return;
    }

    setPageStatus(config.ui.ready, 'info');
  }

  /**
   * Marks recording as impossible on this page (no microphone, insecure origin,
   * or a frame that withholds the permission). Kept as state rather than a bare
   * disabled flag so later control updates cannot re-enable the buttons.
   */
  function disableRecording() {
    state.microphoneUnavailable = true;
    render();
  }

  /**
   * True only when the embedder explicitly withholds the microphone permission.
   * Treated as a one-way signal: browsers disagree on what allowsFeature()
   * reports for a frame that was never granted the feature, so "true" disables
   * recording while "false" only means "no reason to give up before trying".
   */
  function policyDeniesMicrophone() {
    const policy = document.featurePolicy || document.permissionsPolicy;
    return policy?.allowsFeature ? policy.allowsFeature('microphone') === false : false;
  }

  function isEmbedded() {
    try {
      return window.self !== window.top;
    } catch (error) {
      // A cross-origin parent throws on access, which itself means we are framed.
      return true;
    }
  }

  function showStandaloneBanner() {
    const url = config.standaloneUrl;
    if (!url || !elements.standalone || url === window.location.href) return;
    elements.standaloneLink.href = url;
    elements.standalone.hidden = false;
  }

  function applyTheme() {
    const root = document.documentElement.style;
    const variables = {
      '--primary': config.theme.primary,
      '--primary-dark': config.theme.primaryDark,
      '--primary-soft': config.theme.primarySoft,
      '--accent': config.theme.accent,
      '--page-bg': config.theme.pageBackground,
      '--card-bg': config.theme.cardBackground,
      '--text': config.theme.text,
      '--muted': config.theme.mutedText,
      '--danger': config.theme.danger
    };
    Object.entries(variables).forEach(([name, value]) => root.setProperty(name, value));
    document.querySelector('meta[name="theme-color"]').content = config.theme.primary;
  }

  function applyTranslations() {
    document.querySelectorAll('[data-i18n]').forEach((element) => {
      element.textContent = config.ui[element.dataset.i18n] || element.dataset.i18n;
    });
  }

  function bindEvents() {
    elements.uploadAll.addEventListener('click', uploadEveryWaitingTake);
    elements.player.addEventListener('ended', render);
    elements.player.addEventListener('pause', render);
    window.addEventListener('beforeunload', (event) => {
      if (!waitingCards().length) return;
      event.preventDefault();
      event.returnValue = '';
    });
  }

  // ---------------------------------------------------------------------------
  // Building the list
  // ---------------------------------------------------------------------------

  /**
   * The two non-phrase cards come last: the phrases are what a volunteer came
   * for, and these read as the odd ones out at the end rather than mixed in.
   * Noise sits after unknown because it is the one card with nothing to say.
   *
   * `kind` is what the rest of the page reads — it picks the card's styling, its
   * badge, and the wording of its status lines. The backend decides where the
   * audio is filed from the prompt's id alone.
   */
  function collectPrompts() {
    const prompts = config.phrases.map((phrase) => ({ ...phrase, kind: 'phrase' }));
    if (config.unknownPrompt) prompts.push({ ...config.unknownPrompt, kind: 'unknown' });
    if (config.noisePrompt) prompts.push({ ...config.noisePrompt, kind: 'noise' });
    return prompts;
  }

  /**
   * The noise card is told to stay silent, so every string that would tell it to
   * "say the phrase once" is swapped for its noise wording. Falls back to the
   * shared string, so a missing override reads as the old copy rather than blank.
   */
  function promptText(card, key) {
    if (card.prompt.kind !== 'noise') return config.ui[key];
    const override = `noise${key.charAt(0).toUpperCase()}${key.slice(1)}`;
    return config.ui[override] || config.ui[key];
  }

  /**
   * One recorder per prompt. Every element a card needs to update is kept on the
   * card itself, so redrawing never has to search the document for it.
   */
  function buildCards() {
    const fragment = document.createDocumentFragment();
    prompts.forEach((prompt, index) => {
      const card = createCard(prompt, index);
      state.cards.push(card);
      fragment.appendChild(card.dom.root);
    });
    elements.list.replaceChildren(fragment);
    state.cards.forEach((card) => drawIdleWaveform(card));
  }

  function createCard(prompt, index) {
    const root = make('li', cardClassName(prompt, IDLE), { id: `card-${index}` });

    const head = make('div', 'card-head');
    head.appendChild(make('span', 'card-number', { text: String(index + 1) }));
    const badge = badgeText(prompt);
    if (badge) head.appendChild(make('span', 'card-badge', { id: `badge-${index}`, text: badge }));
    const tally = make('span', 'card-tally', { id: `tally-${index}` });
    head.appendChild(tally);
    root.appendChild(head);

    const panel = make('div', 'phrase-panel');
    const text = make('p', 'phrase-text', { id: `phrase-${index}`, text: prompt.text });
    text.lang = 'ar';
    panel.appendChild(text);
    // Only the two non-phrase cards carry a note: "any word" and "no words at
    // all" each need an example to act on, while a dhikr is its own instruction.
    if (prompt.note) {
      const note = make('p', 'phrase-note', { id: `note-${index}`, text: prompt.note });
      note.lang = 'ar';
      panel.appendChild(note);
    }
    root.appendChild(panel);

    const recorder = make('div', 'recorder-panel');
    const wave = make('canvas', 'waveform', { id: `wave-${index}` });
    wave.width = 720;
    wave.height = 96;
    wave.setAttribute('aria-hidden', 'true');
    recorder.appendChild(wave);
    const timer = make('output', 'timer', { id: `timer-${index}`, text: config.ui.timerReady });
    timer.setAttribute('aria-live', 'off');
    recorder.appendChild(timer);
    root.appendChild(recorder);

    const status = make('div', 'status status-empty', { id: `status-${index}` });
    status.setAttribute('role', 'status');
    status.setAttribute('aria-live', 'polite');
    root.appendChild(status);

    const buttons = make('div', 'button-grid');
    const record = makeButton(`record-${index}`, 'button-primary', '●', `record-label-${index}`, config.ui.record);
    const stop = makeButton(`stop-${index}`, 'button-danger', '■', `stop-label-${index}`, config.ui.stop);
    const play = makeButton(`play-${index}`, 'button-secondary', '▶', `play-label-${index}`, config.ui.play);
    const upload = makeButton(`upload-${index}`, 'button-accent', '⬆', `upload-label-${index}`, config.ui.upload);
    [record, stop, play, upload].forEach((button) => buttons.appendChild(button.root));
    root.appendChild(buttons);

    const card = {
      index,
      prompt,
      status: IDLE,
      recording: null,
      message: '',
      messageType: 'info',
      messageHtml: false,
      dom: {
        root,
        tally,
        wave,
        timer,
        status,
        record: record.root,
        recordLabel: record.label,
        stop: stop.root,
        play: play.root,
        playLabel: play.label,
        upload: upload.root,
        uploadLabel: upload.label
      }
    };

    record.root.addEventListener('click', () => startRecording(card));
    stop.root.addEventListener('click', () => stopRecording(card, false));
    play.root.addEventListener('click', () => togglePlayback(card));
    upload.root.addEventListener('click', () => requestUpload(card));
    return card;
  }

  function make(tagName, className, options = {}) {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (options.id) node.id = options.id;
    if (options.text !== undefined) node.textContent = options.text;
    return node;
  }

  function makeButton(id, variant, glyph, labelId, labelText) {
    const root = make('button', `button ${variant}`, { id });
    root.type = 'button';
    const icon = make('span', '', { text: glyph });
    icon.setAttribute('aria-hidden', 'true');
    root.appendChild(icon);
    const label = make('span', '', { id: labelId, text: labelText });
    root.appendChild(label);
    return { root, label };
  }

  // ---------------------------------------------------------------------------
  // Recording
  // ---------------------------------------------------------------------------

  async function startRecording(card) {
    if (state.microphoneUnavailable) return;
    // One microphone means one take at a time; every other card is locked while
    // this one runs, and a card waiting on its upload cannot be recorded over.
    if (state.activeIndex !== -1) return;
    if (card.status === QUEUED || card.status === UPLOADING) return;

    // Pressing the button while a take is waiting means "re-record": drop the
    // previous take rather than refusing until it is uploaded.
    if (card.recording) clearTake(card);

    try {
      const stream = await ensureMicrophone();

      const mimeType = selectSupportedMimeType();
      state.chunks = [];
      state.recorder = mimeType
        ? new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 128000 })
        : new MediaRecorder(stream);

      state.recorder.addEventListener('dataavailable', (event) => {
        if (event.data?.size) state.chunks.push(event.data);
      });
      state.recorder.addEventListener('stop', () => finalizeRecording(card), { once: true });
      state.recorder.addEventListener('error', (error) => handleRecorderError(card, error), { once: true });

      state.activeIndex = card.index;
      state.startedAt = performance.now();
      state.recorder.start(200);
      setCardState(card, RECORDING, promptText(card, 'recording'), 'info');
      startTimer(card);
      startWaveform(card, stream);
      state.automaticStopId = window.setTimeout(
        () => stopRecording(card, true),
        config.recording.maximumDurationMs
      );
    } catch (error) {
      console.error(error);
      state.activeIndex = -1;
      releaseMicrophone();
      reportMicrophoneError(card, error);
    }
  }

  /**
   * Returns the open microphone when there is one, otherwise asks for it from
   * inside the click handler so the browser treats it as a user gesture. A
   * device that cannot honour the preferred sample rate or channel count rejects
   * with OverconstrainedError, so retry once with the plain constraint rather
   * than reporting a permission problem.
   */
  async function ensureMicrophone() {
    window.clearTimeout(state.microphoneReleaseId);
    state.microphoneReleaseId = 0;
    if (state.stream?.getAudioTracks().some((track) => track.readyState !== 'ended')) return state.stream;

    try {
      state.stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: { ideal: config.recording.preferredChannelCount },
          sampleRate: { ideal: config.recording.preferredSampleRate },
          echoCancellation: false,
          noiseSuppression: false,
          autoGainControl: false
        }
      });
    } catch (error) {
      if (error?.name !== 'OverconstrainedError' && error?.name !== 'ConstraintNotSatisfiedError') throw error;
      console.warn('Preferred audio constraints were rejected; retrying with defaults.', error);
      state.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    }
    return state.stream;
  }

  /**
   * Distinguishes the failures a volunteer can actually fix. The permissions
   * policy case is the important one: it never shows a prompt, so telling the
   * user to "allow access" would be advice they cannot follow.
   */
  function reportMicrophoneError(card, error) {
    const name = error?.name || '';
    const message = String(error?.message || '');

    if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
      setCardState(card, IDLE, config.ui.microphoneMissing, 'error');
      return;
    }
    if (name === 'NotReadableError' || name === 'TrackStartError' || name === 'AbortError') {
      setCardState(card, IDLE, config.ui.microphoneBusy, 'error');
      return;
    }
    if (name === 'SecurityError' || !window.isSecureContext) {
      setCardState(card, IDLE, config.ui.insecureContext, 'error');
      return;
    }
    // A rejection inside a frame is a permissions-policy block in practice: the
    // browser never prompted, so "allow access and retry" is advice the
    // volunteer cannot act on. Only a top-level page can mean a real denial.
    if (isEmbedded() || policyDeniesMicrophone() || /permissions policy|feature policy|disallowed by/i.test(message)) {
      showStandaloneBanner();
      setCardState(card, IDLE, config.ui.microphoneBlocked, 'error');
      return;
    }
    setCardState(card, IDLE, config.ui.microphoneDenied, 'error');
  }

  function stopRecording(card, automatic) {
    if (state.activeIndex !== card.index) return;
    if (!state.recorder || state.recorder.state !== 'recording') return;
    window.clearTimeout(state.automaticStopId);
    const elapsed = Math.min(performance.now() - state.startedAt, config.recording.maximumDurationMs);
    state.pendingDurationMs = Math.round(elapsed);
    state.stoppedAutomatically = automatic;
    state.recorder.stop();
    setCardState(card, PROCESSING, card.message, card.messageType);
  }

  function finalizeRecording(card) {
    stopTimer(card);
    stopWaveform();

    const mimeType = normalizeMimeType(state.recorder.mimeType || state.chunks[0]?.type);
    const blob = new Blob(state.chunks, { type: mimeType });
    const trackSettings = state.stream?.getAudioTracks()[0]?.getSettings?.() || {};
    state.recorder = null;
    state.chunks = [];
    state.activeIndex = -1;
    scheduleMicrophoneRelease();

    if (state.pendingDurationMs < config.recording.minimumDurationMs) {
      resetRecorderPanel(card);
      setCardState(card, IDLE, config.ui.tooShort, 'error');
      return;
    }

    if (!config.recording.acceptedMimeTypes.includes(mimeType)) {
      resetRecorderPanel(card);
      setCardState(card, IDLE, config.ui.unsupported, 'error');
      return;
    }

    if (blob.size > config.recording.maximumUploadBytes) {
      resetRecorderPanel(card);
      setCardState(card, IDLE, config.ui.tooLarge, 'error');
      return;
    }

    card.recording = {
      blob,
      mimeType,
      durationMs: state.pendingDurationMs,
      sampleRate: Number(trackSettings.sampleRate) || 0,
      sampleId: createSampleId(),
      url: URL.createObjectURL(blob)
    };
    setCardState(card, READY, promptText(card, 'recordingReady'), 'success');
  }

  function handleRecorderError(card, error) {
    console.error(error);
    stopTimer(card);
    stopWaveform();
    releaseMicrophone();
    state.recorder = null;
    state.chunks = [];
    state.activeIndex = -1;
    resetRecorderPanel(card);
    setCardState(card, IDLE, config.ui.unsupported, 'error');
  }

  // ---------------------------------------------------------------------------
  // Playback
  // ---------------------------------------------------------------------------

  async function togglePlayback(card) {
    if (!card.recording) return;

    if (state.playingIndex === card.index && !elements.player.paused) {
      elements.player.pause();
      render();
      return;
    }

    elements.player.pause();
    elements.player.src = card.recording.url;
    state.playingIndex = card.index;
    try {
      await elements.player.play();
    } catch (error) {
      console.error(error);
    }
    render();
  }

  function isPlaying(card) {
    return state.playingIndex === card.index && !elements.player.paused;
  }

  // ---------------------------------------------------------------------------
  // Uploading
  // ---------------------------------------------------------------------------

  /**
   * Queues one card's take. Uploads are serialized because the backend appends a
   * spreadsheet row per sample; a batch fired at once would race on it. A card
   * waits its turn visibly rather than looking idle.
   */
  function requestUpload(card) {
    if (!card.recording || card.status === QUEUED || card.status === UPLOADING) return false;
    setCardState(card, QUEUED, config.ui.uploadQueued, 'info');
    state.uploadChain = state.uploadChain.then(() => runUpload(card)).catch(() => {});
    return true;
  }

  /** The bottom bar: everything with a take that is not already on its way. */
  function uploadEveryWaitingTake() {
    waitingCards().forEach(requestUpload);
  }

  function waitingCards() {
    return state.cards.filter(
      (card) => card.recording && card.status !== QUEUED && card.status !== UPLOADING
    );
  }

  async function runUpload(card) {
    if (!card.recording) return;

    if (state.playingIndex === card.index) elements.player.pause();
    setCardState(card, UPLOADING, config.ui.uploading, 'info');

    try {
      const payload = {
        sample_id: card.recording.sampleId,
        speaker_id: state.speakerId,
        phrase_id: card.prompt.id,
        duration_ms: card.recording.durationMs,
        sample_rate: card.recording.sampleRate,
        mime_type: card.recording.mimeType,
        audio_base64: await blobToBase64(card.recording.blob),
        browser: detectBrowser(),
        platform: detectPlatform(),
        language: navigator.language || config.app.language,
        client_timestamp: new Date().toISOString()
      };

      const result = await postPayload(payload);
      if (!result.ok) throw new Error(result.error?.message || config.ui.uploadFailedTitle);

      recordUploadedPhrase(card);
      clearTake(card);
      resetRecorderPanel(card);
      // Back to an empty card on the same phrase: another sample of it is worth
      // more to the dataset than moving the volunteer somewhere else.
      setCardState(
        card,
        IDLE,
        `<strong>${escapeHtml(config.ui.uploadSuccessTitle)}</strong><br>${escapeHtml(promptText(card, 'uploadSuccessBody'))}`,
        'success',
        true
      );
    } catch (error) {
      console.error(error);
      setCardState(
        card,
        FAILED,
        `<strong>${escapeHtml(config.ui.uploadFailedTitle)}</strong><br>${escapeHtml(config.ui.uploadFailedBody)}`,
        'error',
        true
      );
    }
  }

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------

  function setCardState(card, status, message, messageType, messageHtml = false) {
    card.status = status;
    card.message = message || '';
    card.messageType = messageType || 'info';
    card.messageHtml = Boolean(messageHtml);
    render();
  }

  function render() {
    state.cards.forEach(renderCard);
    renderSummary();
    renderUploadAllBar();
  }

  function renderCard(card) {
    const recording = card.status === RECORDING;
    const processing = card.status === PROCESSING;
    const inFlight = card.status === QUEUED || card.status === UPLOADING;
    const hasTake = Boolean(card.recording);
    const busyElsewhere = state.activeIndex !== -1 && state.activeIndex !== card.index;

    card.dom.root.className = cardClassName(card.prompt, card.status);
    card.dom.tally.textContent = tallyLabel(card);

    card.dom.status.className = card.message ? `status status-${card.messageType}` : 'status status-empty';
    if (card.messageHtml) card.dom.status.innerHTML = card.message;
    else card.dom.status.textContent = card.message;

    card.dom.record.disabled =
      state.microphoneUnavailable || busyElsewhere || recording || processing || inFlight;
    card.dom.recordLabel.textContent = hasTake ? config.ui.reRecord : config.ui.record;
    card.dom.stop.disabled = !recording;
    card.dom.play.disabled = !hasTake || inFlight;
    card.dom.playLabel.textContent = isPlaying(card) ? config.ui.pause : config.ui.play;
    card.dom.upload.disabled = !hasTake || inFlight;
    card.dom.uploadLabel.textContent = uploadLabel(card);
  }

  function uploadLabel(card) {
    if (card.status === UPLOADING) return config.ui.uploading;
    if (card.status === QUEUED) return config.ui.uploadQueued;
    if (card.status === FAILED) return config.ui.retry;
    return config.ui.upload;
  }

  function cardClassName(prompt, status) {
    const kind = prompt.kind === 'phrase' ? '' : ` phrase-card-${prompt.kind}`;
    return `phrase-card${kind} card-${status}`;
  }

  function badgeText(prompt) {
    if (prompt.kind === 'unknown') return config.ui.unknownBadge || '';
    if (prompt.kind === 'noise') return config.ui.noiseBadge || '';
    return '';
  }

  function tallyLabel(card) {
    const count = uploadCount(card.prompt.id);
    return count ? config.ui.recordedCount.replace('{count}', String(count)) : '';
  }

  /** The unknown and noise cards count towards coverage: the dataset needs them as much as a phrase. */
  function renderSummary() {
    const covered = prompts.filter((prompt) => uploadCount(prompt.id) > 0).length;
    const samples = prompts.reduce((total, prompt) => total + uploadCount(prompt.id), 0);
    elements.summaryPhrases.textContent = config.ui.summaryPhrases
      .replace('{recorded}', String(covered))
      .replace('{total}', String(prompts.length));
    elements.summarySamples.textContent = config.ui.summarySamples.replace('{count}', String(samples));
    elements.progressFill.style.width = `${(covered / prompts.length) * 100}%`;
  }

  function renderUploadAllBar() {
    const waiting = waitingCards().length;
    elements.uploadAllBar.hidden = waiting < UPLOAD_ALL_MINIMUM;
    elements.uploadAllLabel.textContent = config.ui.uploadAll.replace('{count}', String(waiting));
  }

  function setPageStatus(message, type) {
    elements.status.className = `status status-${type}`;
    elements.status.textContent = message;
  }

  // ---------------------------------------------------------------------------
  // Per-phrase tally
  // ---------------------------------------------------------------------------

  function uploadCount(phraseId) {
    return Number(state.uploadCounts[phraseId]) || 0;
  }

  function recordUploadedPhrase(card) {
    state.uploadCounts[card.prompt.id] = uploadCount(card.prompt.id) + 1;
    saveUploadCounts();
  }

  /** Storage can be unavailable (private mode, sandboxed frame); the tally is optional. */
  function loadUploadCounts() {
    try {
      const stored = JSON.parse(window.localStorage.getItem(UPLOAD_COUNTS_KEY) || '{}');
      return stored && typeof stored === 'object' ? stored : {};
    } catch (error) {
      return {};
    }
  }

  function saveUploadCounts() {
    try {
      window.localStorage.setItem(UPLOAD_COUNTS_KEY, JSON.stringify(state.uploadCounts));
    } catch (error) {
      /* Ignored: the tally is a convenience, never a requirement. */
    }
  }

  // ---------------------------------------------------------------------------
  // Speaker id
  // ---------------------------------------------------------------------------

  /**
   * The browser's own random UUID, minted on first visit and reused after that
   * so every clip one volunteer records carries the same token.
   *
   * When storage is unavailable (private mode, sandboxed frame) a fresh id is
   * still generated and used for this session: grouping the clips of one sitting
   * is worth having, and a session is the most the page can honestly promise.
   */
  function loadSpeakerId() {
    try {
      const stored = String(window.localStorage.getItem(SPEAKER_ID_KEY) || '');
      if (/^[0-9a-f-]{16,}$/i.test(stored)) return stored;
    } catch (error) {
      return createSpeakerId();
    }

    const created = createSpeakerId();
    try {
      window.localStorage.setItem(SPEAKER_ID_KEY, created);
    } catch (error) {
      /* Ignored: an id that lasts one session still groups that session. */
    }
    return created;
  }

  function createSpeakerId() {
    if (crypto.randomUUID) return crypto.randomUUID();
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  }

  // ---------------------------------------------------------------------------
  // Timer and waveform
  // ---------------------------------------------------------------------------

  function startTimer(card) {
    window.clearInterval(state.timerId);
    updateTimer(card);
    state.timerId = window.setInterval(() => updateTimer(card), 100);
  }

  function updateTimer(card) {
    const elapsed = Math.min(performance.now() - state.startedAt, config.recording.maximumDurationMs);
    const seconds = Math.floor(elapsed / 1000);
    const tenths = Math.floor((elapsed % 1000) / 100);
    card.dom.timer.textContent = `00:${String(seconds).padStart(2, '0')}.${tenths}`;
  }

  function stopTimer(card) {
    window.clearInterval(state.timerId);
    state.timerId = 0;
    updateTimer(card);
  }

  function resetRecorderPanel(card) {
    card.dom.timer.textContent = config.ui.timerReady;
    drawIdleWaveform(card);
  }

  function startWaveform(card, stream) {
    stopWaveform();
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    if (!state.audioContext) state.audioContext = new AudioContextClass();
    // The stream is held across takes, so its source node is too: connecting the
    // same stream twice would stack nodes on every record.
    if (!state.analyserSource) {
      state.analyserSource = state.audioContext.createMediaStreamSource(stream);
      state.analyser = state.audioContext.createAnalyser();
      state.analyser.fftSize = 256;
      state.analyserSource.connect(state.analyser);
    }
    drawLiveWaveform(card);
  }

  function drawLiveWaveform(card) {
    // The analyser outlives a take (the microphone is held), so the loop is tied
    // to the card that owns the microphone rather than to the analyser existing.
    if (!state.analyser || state.activeIndex !== card.index) return;
    const canvas = card.dom.wave;
    const context = canvas.getContext('2d');
    const samples = new Uint8Array(state.analyser.frequencyBinCount);
    state.analyser.getByteTimeDomainData(samples);
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.strokeStyle = config.theme.danger;
    context.lineWidth = 4;
    context.beginPath();
    samples.forEach((sample, index) => {
      const x = (index / (samples.length - 1)) * canvas.width;
      const y = (sample / 255) * canvas.height;
      if (index === 0) context.moveTo(x, y); else context.lineTo(x, y);
    });
    context.stroke();
    state.animationId = requestAnimationFrame(() => drawLiveWaveform(card));
  }

  function drawIdleWaveform(card) {
    const canvas = card.dom.wave;
    const context = canvas.getContext('2d');
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.strokeStyle = '#b9d8c7';
    context.lineWidth = 3;
    context.setLineDash([8, 9]);
    context.beginPath();
    context.moveTo(0, canvas.height / 2);
    context.lineTo(canvas.width, canvas.height / 2);
    context.stroke();
    context.setLineDash([]);
  }

  function stopWaveform() {
    cancelAnimationFrame(state.animationId);
    state.animationId = 0;
  }

  // ---------------------------------------------------------------------------
  // Microphone lifetime
  // ---------------------------------------------------------------------------

  function scheduleMicrophoneRelease() {
    window.clearTimeout(state.microphoneReleaseId);
    state.microphoneReleaseId = window.setTimeout(releaseMicrophone, MICROPHONE_IDLE_RELEASE_MS);
  }

  function releaseMicrophone() {
    window.clearTimeout(state.microphoneReleaseId);
    state.microphoneReleaseId = 0;
    state.stream?.getTracks().forEach((track) => track.stop());
    state.stream = null;
    state.analyserSource?.disconnect();
    state.analyserSource = null;
    state.analyser = null;
    if (state.audioContext) state.audioContext.close().catch(() => {});
    state.audioContext = null;
  }

  // ---------------------------------------------------------------------------
  // Takes
  // ---------------------------------------------------------------------------

  function clearTake(card) {
    if (card.recording?.url) URL.revokeObjectURL(card.recording.url);
    card.recording = null;
    if (state.playingIndex === card.index) {
      elements.player.pause();
      elements.player.removeAttribute('src');
      elements.player.load();
      state.playingIndex = -1;
    }
  }

  function selectSupportedMimeType() {
    const candidates = [
      'audio/wav',
      'audio/webm;codecs=opus',
      'audio/ogg;codecs=opus',
      'audio/mp4;codecs=mp4a.40.2',
      'audio/mp4',
      'audio/webm'
    ];
    return candidates.find((type) => MediaRecorder.isTypeSupported(type)) || '';
  }

  function normalizeMimeType(mimeType) {
    return String(mimeType || '').split(';')[0].trim().toLowerCase();
  }

  function blobToBase64(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(reader.error || new Error('Could not read audio.'));
      reader.onload = () => resolve(String(reader.result).split(',')[1]);
      reader.readAsDataURL(blob);
    });
  }

  /**
   * Posts to doPost first. Some Apps Script deployments block reading a fetch
   * response across Google's redirect domains, so the HtmlService bridge is a
   * safe fallback. sample_id makes that fallback idempotent if the POST saved
   * successfully but its response was unreadable.
   */
  async function postPayload(payload) {
    try {
      if (!config.endpoint) throw new Error('The deployment URL is unavailable.');
      const response = await fetch(config.endpoint, {
        method: 'POST',
        redirect: 'follow',
        credentials: 'omit',
        headers: { 'Content-Type': 'text/plain;charset=utf-8' },
        body: JSON.stringify(payload)
      });
      return await response.json();
    } catch (fetchError) {
      console.warn('Direct POST response unavailable; using Apps Script bridge.', fetchError);
      return callAppsScript('saveAudio', payload);
    }
  }

  function callAppsScript(functionName, argument) {
    return new Promise((resolve, reject) => {
      if (!globalThis.google?.script?.run) {
        reject(new Error('Apps Script bridge is unavailable.'));
        return;
      }
      google.script.run
        .withSuccessHandler(resolve)
        .withFailureHandler(reject)
        [functionName](argument);
    });
  }

  function createSampleId() {
    if (crypto.randomUUID) return `s_${crypto.randomUUID().replaceAll('-', '')}`;
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    return `s_${Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')}`;
  }

  function detectBrowser() {
    const ua = navigator.userAgent;
    const matchers = [
      ['Edge', /Edg\/([\d.]+)/],
      ['Chrome', /(?:Chrome|CriOS)\/([\d.]+)/],
      ['Firefox', /(?:Firefox|FxiOS)\/([\d.]+)/],
      ['Safari', /Version\/([\d.]+).*Safari/]
    ];
    const match = matchers.map(([name, pattern]) => [name, ua.match(pattern)]).find(([, value]) => value);
    return match ? `${match[0]} ${match[1][1]}` : 'Unknown browser';
  }

  function detectPlatform() {
    return navigator.userAgentData?.platform || navigator.platform || 'Unknown platform';
  }

  function escapeHtml(value) {
    const node = document.createElement('span');
    node.textContent = String(value);
    return node.innerHTML;
  }
})();
