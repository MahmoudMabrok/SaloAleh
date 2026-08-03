'use strict';

(() => {
  const config = JSON.parse(document.getElementById('bootstrap-data').textContent);
  const $ = (id) => document.getElementById(id);

  const elements = {
    phraseText: $('phrase-text'),
    phraseProgress: $('phrase-progress'),
    progressFill: $('progress-fill'),
    timer: $('timer'),
    waveform: $('waveform'),
    status: $('status'),
    record: $('record-button'),
    stop: $('stop-button'),
    play: $('play-button'),
    playLabel: $('play-label'),
    upload: $('upload-button'),
    uploadLabel: $('upload-label'),
    next: $('next-button'),
    player: $('audio-player')
  };

  const state = {
    phraseIndex: 0,
    recorder: null,
    stream: null,
    chunks: [],
    recording: null,
    startedAt: 0,
    timerId: 0,
    automaticStopId: 0,
    audioContext: null,
    analyser: null,
    animationId: 0,
    uploading: false,
    completed: false
  };

  initialize();

  function initialize() {
    document.documentElement.lang = config.app.language;
    document.documentElement.dir = config.app.direction;
    document.title = config.app.htmlTitle;
    applyTheme();
    applyTranslations();
    bindEvents();
    drawIdleWaveform();
    renderPhrase();

    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setStatus(config.ui.unsupported, 'error');
      elements.record.disabled = true;
    } else {
      setStatus(config.ui.ready, 'info');
    }
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
    elements.timer.textContent = config.ui.timerReady;
  }

  function bindEvents() {
    elements.record.addEventListener('click', startRecording);
    elements.stop.addEventListener('click', () => stopRecording(false));
    elements.play.addEventListener('click', togglePlayback);
    elements.upload.addEventListener('click', uploadRecording);
    elements.next.addEventListener('click', nextPhrase);
    elements.player.addEventListener('ended', updatePlaybackButton);
    elements.player.addEventListener('pause', updatePlaybackButton);
    window.addEventListener('beforeunload', (event) => {
      if (state.recording) {
        event.preventDefault();
        event.returnValue = '';
      }
    });
  }

  async function startRecording() {
    if (state.recording || state.uploading || state.recorder?.state === 'recording') return;

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

      const mimeType = selectSupportedMimeType();
      state.chunks = [];
      state.recorder = mimeType
        ? new MediaRecorder(state.stream, { mimeType, audioBitsPerSecond: 128000 })
        : new MediaRecorder(state.stream);

      state.recorder.addEventListener('dataavailable', (event) => {
        if (event.data?.size) state.chunks.push(event.data);
      });
      state.recorder.addEventListener('stop', finalizeRecording, { once: true });
      state.recorder.addEventListener('error', handleRecorderError, { once: true });

      state.startedAt = performance.now();
      state.recorder.start(200);
      startTimer();
      startWaveform(state.stream);
      updateControls('recording');
      setStatus(config.ui.recording, 'info');
      state.automaticStopId = window.setTimeout(
        () => stopRecording(true),
        config.recording.maximumDurationMs
      );
    } catch (error) {
      console.error(error);
      releaseMicrophone();
      setStatus(config.ui.microphoneDenied, 'error');
    }
  }

  function stopRecording(automatic) {
    if (!state.recorder || state.recorder.state !== 'recording') return;
    window.clearTimeout(state.automaticStopId);
    const elapsed = Math.min(performance.now() - state.startedAt, config.recording.maximumDurationMs);
    state.pendingDurationMs = Math.round(elapsed);
    state.stoppedAutomatically = automatic;
    state.recorder.stop();
    updateControls('processing');
  }

  async function finalizeRecording() {
    stopTimer();
    stopWaveform();

    const mimeType = normalizeMimeType(state.recorder.mimeType || state.chunks[0]?.type);
    const blob = new Blob(state.chunks, { type: mimeType });
    const trackSettings = state.stream?.getAudioTracks()[0]?.getSettings?.() || {};
    releaseMicrophone();

    if (state.pendingDurationMs < config.recording.minimumDurationMs) {
      state.chunks = [];
      state.recorder = null;
      elements.timer.textContent = config.ui.timerReady;
      drawIdleWaveform();
      setStatus(config.ui.tooShort, 'error');
      updateControls('idle');
      return;
    }

    if (!config.recording.acceptedMimeTypes.includes(mimeType)) {
      state.recorder = null;
      setStatus(config.ui.unsupported, 'error');
      updateControls('idle');
      return;
    }

    if (blob.size > config.recording.maximumUploadBytes) {
      state.recorder = null;
      setStatus(config.ui.uploadFailedTitle + ': file is too large.', 'error');
      updateControls('idle');
      return;
    }

    state.recording = {
      blob,
      mimeType,
      durationMs: state.pendingDurationMs,
      sampleRate: Number(trackSettings.sampleRate) || 0,
      sampleId: createSampleId(),
      url: URL.createObjectURL(blob)
    };
    elements.player.src = state.recording.url;
    state.recorder = null;
    setStatus(config.ui.recordingReady, 'success');
    updateControls('ready');
  }

  async function togglePlayback() {
    if (!state.recording) return;
    if (elements.player.paused) {
      try {
        await elements.player.play();
      } catch (error) {
        console.error(error);
      }
    } else {
      elements.player.pause();
    }
    updatePlaybackButton();
  }

  function updatePlaybackButton() {
    elements.playLabel.textContent = elements.player.paused ? config.ui.play : config.ui.pause;
  }

  async function uploadRecording() {
    if (!state.recording || state.uploading) return;

    state.uploading = true;
    elements.player.pause();
    updateControls('uploading');
    setStatus(config.ui.uploading, 'info');

    try {
      const payload = {
        sample_id: state.recording.sampleId,
        phrase_id: config.phrases[state.phraseIndex].id,
        duration_ms: state.recording.durationMs,
        sample_rate: state.recording.sampleRate,
        mime_type: state.recording.mimeType,
        audio_base64: await blobToBase64(state.recording.blob),
        browser: detectBrowser(),
        platform: detectPlatform(),
        language: navigator.language || config.app.language,
        client_timestamp: new Date().toISOString()
      };

      const result = await postPayload(payload);
      if (!result.ok) throw new Error(result.error?.message || config.ui.uploadFailedTitle);

      setStatus(`<strong>${escapeHtml(config.ui.uploadSuccessTitle)}</strong><br>${escapeHtml(config.ui.uploadSuccessBody)}`, 'success', true);
      clearRecording();
      updateControls('uploading');
      window.setTimeout(advanceAfterUpload, 1100);
    } catch (error) {
      console.error(error);
      state.uploading = false;
      setStatus(`<strong>${escapeHtml(config.ui.uploadFailedTitle)}</strong><br>${escapeHtml(config.ui.uploadFailedBody)}`, 'error', true);
      updateControls('ready');
      elements.uploadLabel.textContent = config.ui.retry;
    }
  }

  function nextPhrase() {
    if (state.recording || state.uploading || state.recorder?.state === 'recording') {
      setStatus(config.ui.nextBlocked, 'error');
      return;
    }
    if (state.completed) {
      state.completed = false;
      state.phraseIndex = 0;
    } else {
      state.phraseIndex = (state.phraseIndex + 1) % config.phrases.length;
    }
    renderPhrase();
    setStatus(config.ui.ready, 'info');
  }

  function advanceAfterUpload() {
    state.uploading = false;
    if (state.phraseIndex === config.phrases.length - 1) {
      state.completed = true;
      elements.phraseText.textContent = '✓';
      elements.phraseProgress.textContent = config.ui.completed;
      elements.progressFill.style.width = '100%';
      elements.next.querySelector('[data-i18n]').textContent = config.ui.restart;
      setStatus(config.ui.completed, 'success');
      elements.record.disabled = true;
      return;
    }

    state.phraseIndex += 1;
    renderPhrase();
    setStatus(config.ui.ready, 'info');
  }

  function renderPhrase() {
    const phrase = config.phrases[state.phraseIndex];
    elements.phraseText.textContent = phrase.text;
    elements.phraseProgress.textContent = config.ui.phraseProgress
      .replace('{current}', String(state.phraseIndex + 1))
      .replace('{total}', String(config.phrases.length));
    elements.progressFill.style.width = `${((state.phraseIndex + 1) / config.phrases.length) * 100}%`;
    elements.next.querySelector('[data-i18n]').textContent = config.ui.next;
    elements.timer.textContent = config.ui.timerReady;
    drawIdleWaveform();
    updateControls('idle');
  }

  function updateControls(mode) {
    const recording = mode === 'recording';
    const ready = mode === 'ready';
    const uploading = mode === 'uploading';
    const processing = mode === 'processing';
    elements.record.disabled = recording || ready || uploading || processing || state.completed;
    elements.stop.disabled = !recording;
    elements.play.disabled = !ready || uploading;
    elements.upload.disabled = !ready || uploading;
    elements.next.disabled = recording || uploading || processing;
    if (!uploading) elements.uploadLabel.textContent = config.ui.upload;
  }

  function startTimer() {
    stopTimer();
    updateTimer();
    state.timerId = window.setInterval(updateTimer, 100);
  }

  function updateTimer() {
    const elapsed = Math.min(performance.now() - state.startedAt, config.recording.maximumDurationMs);
    const seconds = Math.floor(elapsed / 1000);
    const tenths = Math.floor((elapsed % 1000) / 100);
    elements.timer.textContent = `00:${String(seconds).padStart(2, '0')}.${tenths}`;
  }

  function stopTimer() {
    window.clearInterval(state.timerId);
    state.timerId = 0;
    updateTimer();
  }

  function startWaveform(stream) {
    stopWaveform();
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    state.audioContext = new AudioContextClass();
    const source = state.audioContext.createMediaStreamSource(stream);
    state.analyser = state.audioContext.createAnalyser();
    state.analyser.fftSize = 256;
    source.connect(state.analyser);
    drawLiveWaveform();
  }

  function drawLiveWaveform() {
    if (!state.analyser) return;
    const canvas = elements.waveform;
    const context = canvas.getContext('2d');
    const samples = new Uint8Array(state.analyser.frequencyBinCount);
    state.analyser.getByteTimeDomainData(samples);
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.strokeStyle = config.theme.primary;
    context.lineWidth = 4;
    context.beginPath();
    samples.forEach((sample, index) => {
      const x = (index / (samples.length - 1)) * canvas.width;
      const y = (sample / 255) * canvas.height;
      if (index === 0) context.moveTo(x, y); else context.lineTo(x, y);
    });
    context.stroke();
    state.animationId = requestAnimationFrame(drawLiveWaveform);
  }

  function drawIdleWaveform() {
    const canvas = elements.waveform;
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
    state.analyser = null;
    if (state.audioContext) state.audioContext.close().catch(() => {});
    state.audioContext = null;
  }

  function releaseMicrophone() {
    state.stream?.getTracks().forEach((track) => track.stop());
    state.stream = null;
  }

  function handleRecorderError(error) {
    console.error(error);
    stopTimer();
    stopWaveform();
    releaseMicrophone();
    state.recorder = null;
    setStatus(config.ui.unsupported, 'error');
    updateControls('idle');
  }

  function clearRecording() {
    if (state.recording?.url) URL.revokeObjectURL(state.recording.url);
    state.recording = null;
    state.chunks = [];
    elements.player.removeAttribute('src');
    elements.player.load();
    elements.timer.textContent = config.ui.timerReady;
    drawIdleWaveform();
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

  function setStatus(message, type, allowHtml = false) {
    elements.status.className = `status status-${type}`;
    if (allowHtml) elements.status.innerHTML = message;
    else elements.status.textContent = message;
  }

  function escapeHtml(value) {
    const node = document.createElement('span');
    node.textContent = String(value);
    return node.innerHTML;
  }
})();
