/**
 * Behaviour tests for app.js.
 *
 * app.js is a browser IIFE with no exports, so it runs inside a vm against a
 * minimal DOM/MediaRecorder stub. The stub implements only what the app
 * touches, which keeps the tests honest: they drive the same buttons a
 * volunteer does and assert on what the page ends up showing.
 *
 * Covered: the phrase picker, skipping a phrase without recording it,
 * re-recording over a waiting take, and the per-phrase upload tally.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const projectDirectory = path.dirname(fileURLToPath(import.meta.url));
const read = (filename) => fs.readFileSync(path.join(projectDirectory, filename), 'utf8');
const source = new vm.Script(read('app.js'), { filename: 'app.js' });

const bootstrap = JSON.parse(
  read('dist/voice.html')
    .match(/<script id="bootstrap-data" type="application\/json">([\s\S]*?)<\/script>/)[1]
    .replace(/\\u003c/g, '<')
    .replace(/\\u003e/g, '>')
    .replace(/\\u0026/g, '&')
);

const phrases = bootstrap.phrases;
const UPLOAD_COUNTS_KEY = 'speech_collector_upload_counts';

const ELEMENT_IDS = [
  'phrase-text', 'phrase-note', 'phrase-select', 'phrase-progress', 'progress-fill', 'timer',
  'waveform', 'status', 'record-button', 'record-label', 'stop-button', 'play-button', 'play-label',
  'upload-button', 'upload-label', 'next-button', 'audio-player', 'standalone-banner', 'standalone-link'
];

class FakeElement {
  constructor(tagName = 'div', id = '') {
    this.tagName = tagName;
    this.id = id;
    this.textContent = '';
    this.className = '';
    this.innerHTML = '';
    this.value = '';
    this.disabled = false;
    this.hidden = true;
    this.href = '';
    this.paused = true;
    this.width = 720;
    this.height = 112;
    this.dataset = {};
    this.children = [];
    this.style = { setProperty() {}, width: '' };
    this.handlers = new Map();
  }

  /** The app reads select.options[index] when refreshing labels. */
  get options() {
    return this.children;
  }

  addEventListener(type, handler) {
    this.handlers.set(type, handler);
  }

  dispatch(type, event = {}) {
    const handler = this.handlers.get(type);
    assert.ok(handler, `#${this.id || this.tagName} has no "${type}" listener.`);
    return handler(event);
  }

  appendChild(node) {
    this.children.push(node);
    return node;
  }

  replaceChildren(fragment) {
    this.children = [...fragment.children];
  }

  querySelector() {
    if (!this.namedChild) this.namedChild = new FakeElement('span');
    return this.namedChild;
  }

  removeAttribute() {}
  load() {}
  pause() {
    this.paused = true;
  }
  play() {
    this.paused = false;
    return Promise.resolve();
  }

  getContext() {
    return drawingContext;
  }
}

// Every canvas call is a no-op; only the app's control flow is under test.
const drawingContext = new Proxy({}, { get: () => () => {} });

function createEnvironment({ secureContext = true, confirmResult = true, storageSeed = null, uploadOk = true } = {}) {
  const dom = new Map(ELEMENT_IDS.map((id) => [id, new FakeElement('div', id)]));
  const bootstrapNode = new FakeElement('script', 'bootstrap-data');
  bootstrapNode.textContent = JSON.stringify(bootstrap);
  dom.set('bootstrap-data', bootstrapNode);

  const confirmCalls = [];
  const timers = [];
  const clock = { now: 0 };
  const storage = new Map(storageSeed ? [[UPLOAD_COUNTS_KEY, JSON.stringify(storageSeed)]] : []);
  const recorders = [];

  const document = {
    documentElement: { lang: '', dir: '', style: { setProperty() {} } },
    title: '',
    getElementById: (id) => dom.get(id) || null,
    querySelectorAll: () => [],
    querySelector: () => ({ content: '' }),
    createElement: (tagName) => new FakeElement(tagName),
    createDocumentFragment: () => new FakeElement('fragment')
  };

  const window = {
    isSecureContext: secureContext,
    self: 1,
    top: 1,
    location: { href: 'https://example.test/voice.html' },
    document,
    addEventListener() {},
    // Deferred callbacks are queued, never fired automatically: the five-second
    // auto-stop must not go off in the middle of a test.
    setTimeout: (callback) => timers.push(callback),
    clearTimeout: (id) => {
      if (id) timers[id - 1] = null;
    },
    setInterval: () => 0,
    clearInterval() {},
    confirm: (message) => {
      confirmCalls.push(message);
      return confirmResult;
    },
    localStorage: {
      getItem: (key) => (storage.has(key) ? storage.get(key) : null),
      setItem: (key, value) => storage.set(key, String(value))
    },
    AudioContext: class {
      createMediaStreamSource() {
        return { connect() {} };
      }
      createAnalyser() {
        return { fftSize: 0, frequencyBinCount: 4, getByteTimeDomainData() {} };
      }
      close() {
        return Promise.resolve();
      }
    }
  };

  class FakeMediaRecorder {
    static isTypeSupported() {
      return true;
    }

    constructor(stream, options = {}) {
      this.stream = stream;
      this.mimeType = options.mimeType || 'audio/webm;codecs=opus';
      this.state = 'inactive';
      this.handlers = new Map();
      recorders.push(this);
    }

    addEventListener(type, handler) {
      this.handlers.set(type, handler);
    }

    start() {
      this.state = 'recording';
    }

    /** The real recorder fires "stop" in a later task, after stopRecording returns. */
    stop() {
      this.state = 'inactive';
      queueMicrotask(() => this.handlers.get('stop')?.());
    }
  }

  const context = vm.createContext({
    window,
    document,
    console: { log() {}, warn() {}, error() {} },
    navigator: {
      language: 'ar-EG',
      userAgent: 'Mozilla/5.0 (Linux; Android 13) Chrome/120.0.0.0',
      platform: 'Android',
      mediaDevices: {
        getUserMedia: async () => ({
          getTracks: () => [{ stop() {} }],
          getAudioTracks: () => [{ getSettings: () => ({ sampleRate: 16000 }) }]
        })
      }
    },
    MediaRecorder: FakeMediaRecorder,
    performance: { now: () => clock.now },
    requestAnimationFrame: () => 0,
    cancelAnimationFrame() {},
    crypto: { randomUUID: () => '0123456789abcdef0123456789abcdef' },
    URL: { createObjectURL: () => 'blob:test', revokeObjectURL() {} },
    Blob: class {
      constructor(parts, options = {}) {
        this.size = 2048;
        this.type = options.type || '';
      }
    },
    FileReader: class {
      readAsDataURL() {
        Promise.resolve().then(() => this.onload({ target: this }));
      }
      get result() {
        return 'data:audio/webm;base64,QUJD';
      }
    },
    fetch: async () => ({ json: async () => (uploadOk ? { ok: true } : { ok: false, error: { message: 'nope' } }) })
  });
  context.globalThis = context;

  source.runInContext(context);

  /** Runs the callbacks the app deferred (the post-upload advance). */
  const flushTimers = () => {
    const pending = timers.splice(0, timers.length);
    pending.forEach((callback) => callback?.());
  };

  return { dom, confirmCalls, storage, clock, recorders, timers, flushTimers };
}

const tick = () => new Promise((resolve) => setImmediate(resolve));

/** Drives a complete take through the real record/stop path. */
async function recordTake(environment, durationMs = 1500) {
  environment.dom.get('record-button').dispatch('click');
  await tick();
  environment.clock.now += durationMs;
  environment.dom.get('stop-button').dispatch('click');
  await tick();
}

async function uploadTake(environment) {
  environment.dom.get('upload-button').dispatch('click');
  await tick();
  await tick();
}

const text = (environment, id) => environment.dom.get(id).textContent;

// 1. The picker lists every phrase and starts on the first one.
{
  const environment = createEnvironment();
  const select = environment.dom.get('phrase-select');
  assert.equal(select.children.length, phrases.length, 'The picker must list every phrase.');
  assert.equal(select.value, '0', 'The picker must start on the first phrase.');
  assert.ok(select.children[3].textContent.includes(phrases[3].text), 'Options must carry the phrase text.');
  assert.equal(text(environment, 'phrase-text'), phrases[0].text);
}

// 2. Choosing a phrase jumps straight to it, skipping everything in between.
{
  const environment = createEnvironment();
  const select = environment.dom.get('phrase-select');
  select.value = '4';
  select.dispatch('change');
  assert.equal(text(environment, 'phrase-text'), phrases[4].text, 'Selecting a phrase must render it.');
  assert.equal(select.value, '4', 'The picker must keep the chosen phrase.');
  assert.deepEqual(environment.confirmCalls, [], 'With nothing recorded there is nothing to confirm.');
}

// 3. "Next" moves on without recording the current phrase, and wraps around.
{
  const environment = createEnvironment();
  const next = environment.dom.get('next-button');
  assert.equal(next.disabled, false, 'Next must be usable before anything is recorded.');
  next.dispatch('click');
  assert.equal(text(environment, 'phrase-text'), phrases[1].text, 'Next must skip to the following phrase.');
  assert.equal(environment.dom.get('phrase-select').value, '1', 'The picker must follow a skip.');

  for (let step = 1; step < phrases.length; step += 1) next.dispatch('click');
  assert.equal(text(environment, 'phrase-text'), phrases[0].text, 'Next must wrap back to the first phrase.');
  assert.deepEqual(environment.confirmCalls, [], 'Skipping without a take must never prompt.');
}

// 4. A finished take leaves the record button available as "re-record".
{
  const environment = createEnvironment();
  await recordTake(environment);
  assert.equal(text(environment, 'status'), bootstrap.ui.recordingReady, 'The take must be ready.');
  assert.equal(environment.dom.get('record-button').disabled, false, 'Re-recording must be possible.');
  assert.equal(text(environment, 'record-label'), bootstrap.ui.reRecord, 'The button must offer re-recording.');
  assert.equal(environment.dom.get('play-button').disabled, false);
  assert.equal(environment.dom.get('upload-button').disabled, false);
}

// 5. Re-recording discards the waiting take and starts a fresh one, no prompt.
{
  const environment = createEnvironment();
  await recordTake(environment);
  environment.dom.get('record-button').dispatch('click');
  await tick();
  assert.equal(environment.recorders.length, 2, 'A second recorder must be started.');
  assert.equal(environment.recorders[1].state, 'recording', 'The new take must be running.');
  assert.equal(environment.dom.get('stop-button').disabled, false, 'Stop must be armed again.');
  assert.deepEqual(environment.confirmCalls, [], 'Re-recording is explicit and must not prompt.');
  assert.equal(text(environment, 'record-label'), bootstrap.ui.record, 'The label returns to "record" while recording.');
}

// 6. Skipping with an unuploaded take asks first, and honours "no".
{
  const environment = createEnvironment({ confirmResult: false });
  await recordTake(environment);
  environment.dom.get('next-button').dispatch('click');
  assert.equal(environment.confirmCalls.length, 1, 'The volunteer must be warned before losing a take.');
  assert.equal(text(environment, 'phrase-text'), phrases[0].text, 'A refused skip must stay on the phrase.');
  assert.equal(environment.dom.get('upload-button').disabled, false, 'The take must survive a refused skip.');
}

// 7. Confirming the warning skips the phrase and drops the take.
{
  const environment = createEnvironment({ confirmResult: true });
  await recordTake(environment);
  environment.dom.get('next-button').dispatch('click');
  assert.equal(environment.confirmCalls.length, 1);
  assert.equal(text(environment, 'phrase-text'), phrases[1].text, 'A confirmed skip must advance.');
  assert.equal(text(environment, 'status'), bootstrap.ui.recordingDiscarded, 'The discard must be reported.');
  assert.equal(environment.dom.get('upload-button').disabled, true, 'The discarded take must be gone.');
}

// 8. A refused pick rolls the select back so it cannot disagree with the screen.
{
  const environment = createEnvironment({ confirmResult: false });
  await recordTake(environment);
  const select = environment.dom.get('phrase-select');
  select.value = '6';
  select.dispatch('change');
  assert.equal(select.value, '0', 'The picker must roll back when the move is refused.');
  assert.equal(text(environment, 'phrase-text'), phrases[0].text);
}

// 9. Navigation is locked while a take is in progress, and freed after it stops.
{
  const environment = createEnvironment();
  environment.dom.get('record-button').dispatch('click');
  await tick();
  assert.equal(environment.dom.get('next-button').disabled, true, 'No skipping mid-take.');
  assert.equal(environment.dom.get('phrase-select').disabled, true, 'No phrase switch mid-take.');
  environment.clock.now += 1500;
  environment.dom.get('stop-button').dispatch('click');
  await tick();
  assert.equal(environment.dom.get('next-button').disabled, false);
  assert.equal(environment.dom.get('phrase-select').disabled, false);
}

// 10. A successful upload is tallied per phrase and shown in the picker.
{
  const environment = createEnvironment();
  await recordTake(environment);
  await uploadTake(environment);
  assert.deepEqual(
    JSON.parse(environment.storage.get(UPLOAD_COUNTS_KEY)),
    { [phrases[0].id]: 1 },
    'A successful upload must be tallied against its phrase.'
  );
  assert.ok(
    environment.dom.get('phrase-select').children[0].textContent.includes('✓'),
    'The uploaded phrase must be marked in the picker.'
  );
}

// 11. A stored tally is restored on the next visit, on the phrase and the picker.
{
  const environment = createEnvironment({ storageSeed: { [phrases[0].id]: 3 } });
  assert.ok(environment.dom.get('phrase-select').children[0].textContent.includes('3'), 'The stored count must show.');
  assert.equal(
    text(environment, 'phrase-note'),
    bootstrap.ui.recordedCount.replace('{count}', '3'),
    'The current phrase must show how many samples it already has.'
  );
}

// 12. After an upload the app moves on to a phrase that has no samples yet.
{
  const seed = Object.fromEntries(phrases.slice(1, 4).map((phrase) => [phrase.id, 1]));
  const environment = createEnvironment({ storageSeed: seed });
  await recordTake(environment);
  await uploadTake(environment);
  environment.flushTimers();
  assert.equal(
    text(environment, 'phrase-text'),
    phrases[4].text,
    'The next phrase must be the first one still missing a sample, not simply index + 1.'
  );
}

// 13. The completion card appears only once every phrase has a sample.
{
  const seed = Object.fromEntries(phrases.slice(1).map((phrase) => [phrase.id, 1]));
  const environment = createEnvironment({ storageSeed: seed });
  assert.equal(text(environment, 'phrase-text'), phrases[0].text, 'The only uncovered phrase is the first.');
  await recordTake(environment);
  await uploadTake(environment);
  environment.flushTimers();
  assert.equal(text(environment, 'phrase-text'), '✓', 'Covering the last remaining phrase must complete the set.');
  assert.equal(text(environment, 'status'), bootstrap.ui.completed);
  assert.equal(environment.dom.get('phrase-select').disabled, false, 'The picker must survive the completion card.');

  // Picking a phrase leaves the card and makes recording possible again.
  const select = environment.dom.get('phrase-select');
  select.value = '2';
  select.dispatch('change');
  assert.equal(text(environment, 'phrase-text'), phrases[2].text);
  assert.equal(environment.dom.get('record-button').disabled, false, 'Recording must resume after the card.');
}

// 14. Uploading before every phrase is covered must not show the completion card.
{
  const environment = createEnvironment();
  environment.dom.get('phrase-select').value = String(phrases.length - 1);
  environment.dom.get('phrase-select').dispatch('change');
  await recordTake(environment);
  await uploadTake(environment);
  environment.flushTimers();
  assert.notEqual(text(environment, 'phrase-text'), '✓', 'Reaching the last phrase is not the same as covering them all.');
  assert.equal(text(environment, 'phrase-text'), phrases[0].text, 'It must wrap to the first uncovered phrase.');
}

// 15. A failed upload keeps the take, and never tallies it.
{
  const environment = createEnvironment({ uploadOk: false });
  await recordTake(environment);
  await uploadTake(environment);
  assert.equal(environment.storage.has(UPLOAD_COUNTS_KEY), false, 'A failed upload must not be tallied.');
  assert.equal(environment.dom.get('upload-button').disabled, false, 'The take must be kept for a retry.');
  assert.equal(text(environment, 'upload-label'), bootstrap.ui.retry, 'The button must offer a retry.');
}

// 16. An insecure page blocks recording but leaves phrase navigation usable.
{
  const environment = createEnvironment({ secureContext: false });
  assert.equal(environment.dom.get('record-button').disabled, true, 'Recording needs HTTPS.');
  assert.equal(environment.dom.get('next-button').disabled, false, 'Navigation must stay usable.');
  assert.equal(environment.dom.get('phrase-select').disabled, false, 'The picker must stay usable.');
  assert.equal(text(environment, 'status'), bootstrap.ui.insecureContext);
}

console.log('UI behaviour tests passed: phrase picker, skipping, re-recording, and upload tallies.');
