/**
 * Behaviour tests for app.js.
 *
 * app.js is a browser IIFE with no exports, so it runs inside a vm against a
 * minimal DOM/MediaRecorder stub. The stub implements only what the app
 * touches, which keeps the tests honest: they drive the same buttons a
 * volunteer does and assert on what the page ends up showing.
 *
 * The recorder cards are built at runtime, one per phrase, so the stub tracks
 * every element the app creates and resolves it by id — `record-3` is the
 * record button on the fourth card, exactly as in the real page.
 *
 * Covered: one card per prompt (the phrases plus the negative-class card that
 * asks for a word which is not a dhikr), one take at a time across the whole
 * list, re-recording, per-card upload, the serialized upload-all bar, failure
 * handling, and the per-prompt tally.
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
// The page offers one card per prompt: every phrase, then the unknown card.
const prompts = bootstrap.unknownPrompt ? [...phrases, bootstrap.unknownPrompt] : [...phrases];
const unknownIndex = prompts.length - 1;
const UPLOAD_COUNTS_KEY = 'speech_collector_upload_counts';

// The static shell from Index.html. Everything else is created by the app.
const SHELL_IDS = [
  'phrase-list', 'summary-phrases', 'summary-samples', 'progress-fill', 'page-status',
  'upload-all-bar', 'upload-all-button', 'upload-all-label', 'audio-player',
  'standalone-banner', 'standalone-link'
];

class FakeElement {
  constructor(tagName = 'div', id = '') {
    this.tagName = tagName;
    this.id = id;
    this.textContent = '';
    this.className = '';
    this.innerHTML = '';
    this.disabled = false;
    this.hidden = true;
    this.href = '';
    this.src = '';
    this.paused = true;
    this.width = 720;
    this.height = 96;
    this.dataset = {};
    this.children = [];
    this.style = { setProperty() {}, width: '' };
    this.handlers = new Map();
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

  setAttribute() {}
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

function createEnvironment({ secureContext = true, storageSeed = null, uploadOk = true } = {}) {
  const shell = new Map(SHELL_IDS.map((id) => [id, new FakeElement('div', id)]));
  const bootstrapNode = new FakeElement('script', 'bootstrap-data');
  bootstrapNode.textContent = JSON.stringify(bootstrap);
  shell.set('bootstrap-data', bootstrapNode);

  // Cards are built at runtime, so anything the app creates is resolvable by id
  // once it has been given one — the same contract the real document offers.
  const created = [];
  const timers = [];
  const clock = { now: 0 };
  const storage = new Map(storageSeed ? [[UPLOAD_COUNTS_KEY, JSON.stringify(storageSeed)]] : []);
  const recorders = [];
  const uploads = [];
  let uploadGate = null;

  const findById = (id) => shell.get(id) || created.find((element) => element.id === id) || null;

  const document = {
    documentElement: { lang: '', dir: '', style: { setProperty() {} } },
    title: '',
    getElementById: findById,
    querySelectorAll: () => [],
    querySelector: () => ({ content: '' }),
    createElement: (tagName) => {
      const element = new FakeElement(tagName);
      created.push(element);
      return element;
    },
    createDocumentFragment: () => new FakeElement('fragment')
  };

  const window = {
    isSecureContext: secureContext,
    self: 1,
    top: 1,
    location: { href: 'https://example.test/voice.html' },
    document,
    addEventListener() {},
    // Deferred callbacks are queued, never fired automatically: neither the
    // five-second auto-stop nor the microphone release may go off mid-test.
    setTimeout: (callback) => timers.push(callback),
    clearTimeout: (id) => {
      if (id) timers[id - 1] = null;
    },
    setInterval: () => 0,
    clearInterval() {},
    localStorage: {
      getItem: (key) => (storage.has(key) ? storage.get(key) : null),
      setItem: (key, value) => storage.set(key, String(value))
    },
    AudioContext: class {
      createMediaStreamSource() {
        return { connect() {}, disconnect() {} };
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

  let microphoneRequests = 0;
  const track = { readyState: 'live', stop() { track.readyState = 'ended'; }, getSettings: () => ({ sampleRate: 16000 }) };

  const context = vm.createContext({
    window,
    document,
    console: { log() {}, warn() {}, error() {} },
    navigator: {
      language: 'ar-EG',
      userAgent: 'Mozilla/5.0 (Linux; Android 13) Chrome/120.0.0.0',
      platform: 'Android',
      mediaDevices: {
        getUserMedia: async () => {
          microphoneRequests += 1;
          track.readyState = 'live';
          return { getTracks: () => [track], getAudioTracks: () => [track] };
        }
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
    fetch: async (url, options) => {
      uploads.push(JSON.parse(options.body));
      if (uploadGate) await uploadGate.promise;
      return { json: async () => (uploadOk ? { ok: true } : { ok: false, error: { message: 'nope' } }) };
    }
  });
  context.globalThis = context;

  source.runInContext(context);

  return {
    dom: {
      get: (id) => assertFound(findById(id), id),
      /** Nullable lookup, for asserting that an element was *not* created. */
      find: (id) => findById(id)
    },
    storage,
    clock,
    recorders,
    uploads,
    timers,
    microphoneRequestCount: () => microphoneRequests,
    /** Holds every upload open so a queue can be observed mid-flight. */
    blockUploads() {
      let release = () => {};
      uploadGate = { promise: new Promise((resolve) => { release = resolve; }) };
      return async () => {
        uploadGate = null;
        release();
        await settle();
      };
    },
    /** Runs the callbacks the app deferred (auto-stop, microphone release). */
    flushTimers() {
      timers.splice(0, timers.length).forEach((callback) => callback?.());
    }
  };
}

function assertFound(element, id) {
  assert.ok(element, `The page has no #${id}.`);
  return element;
}

const settle = async () => {
  for (let pass = 0; pass < 12; pass += 1) await new Promise((resolve) => setImmediate(resolve));
};

/** Drives a complete take on one card through the real record/stop path. */
async function recordTake(environment, index, durationMs = 1500) {
  environment.dom.get(`record-${index}`).dispatch('click');
  await settle();
  environment.clock.now += durationMs;
  environment.dom.get(`stop-${index}`).dispatch('click');
  await settle();
}

async function uploadTake(environment, index) {
  environment.dom.get(`upload-${index}`).dispatch('click');
  await settle();
}

const text = (environment, id) => environment.dom.get(id).textContent;
const disabled = (environment, id) => environment.dom.get(id).disabled;

// 1. Every prompt gets its own card, with its own controls and its own text.
{
  const environment = createEnvironment();
  prompts.forEach((prompt, index) => {
    assert.equal(text(environment, `phrase-${index}`), prompt.text, `Card ${index} must show its prompt.`);
    for (const role of ['record', 'stop', 'play', 'upload']) {
      assert.ok(environment.dom.get(`${role}-${index}`), `Card ${index} must have its own ${role} button.`);
    }
  });
  assert.equal(
    text(environment, 'summary-phrases'),
    bootstrap.ui.summaryPhrases.replace('{recorded}', '0').replace('{total}', String(prompts.length))
  );
}

// 2. A fresh card offers recording only; the rest of its buttons wait for a take.
{
  const environment = createEnvironment();
  assert.equal(disabled(environment, 'record-0'), false, 'Recording must be the one thing on offer.');
  assert.equal(disabled(environment, 'stop-0'), true);
  assert.equal(disabled(environment, 'play-0'), true);
  assert.equal(disabled(environment, 'upload-0'), true);
  assert.equal(text(environment, 'page-status'), bootstrap.ui.ready);
}

// 3. One microphone, one take: recording a card locks every other card's record
//    button, and stopping frees them again.
{
  const environment = createEnvironment();
  environment.dom.get('record-2').dispatch('click');
  await settle();

  assert.equal(disabled(environment, 'stop-2'), false, 'The live card must be stoppable.');
  assert.equal(disabled(environment, 'record-2'), true, 'The live card cannot restart itself mid-take.');
  assert.equal(disabled(environment, 'record-0'), true, 'Every other card must be locked.');
  assert.equal(disabled(environment, 'record-9'), true, 'Every other card must be locked.');
  assert.equal(text(environment, 'status-2'), bootstrap.ui.recording);

  environment.clock.now += 1500;
  environment.dom.get('stop-2').dispatch('click');
  await settle();

  assert.equal(disabled(environment, 'record-0'), false, 'Stopping must free the other cards.');
  assert.equal(disabled(environment, 'record-9'), false);
}

// 4. A finished take arms play and upload on its own card, and only there.
{
  const environment = createEnvironment();
  await recordTake(environment, 3);
  assert.equal(text(environment, 'status-3'), bootstrap.ui.recordingReady);
  assert.equal(disabled(environment, 'play-3'), false);
  assert.equal(disabled(environment, 'upload-3'), false);
  assert.equal(text(environment, 'record-label-3'), bootstrap.ui.reRecord, 'The button must offer re-recording.');
  assert.equal(disabled(environment, 'upload-4'), true, 'A neighbouring card must be untouched.');
  assert.equal(text(environment, 'record-label-4'), bootstrap.ui.record);
}

// 5. Re-recording replaces the waiting take without asking.
{
  const environment = createEnvironment();
  await recordTake(environment, 0);
  environment.dom.get('record-0').dispatch('click');
  await settle();
  assert.equal(environment.recorders.length, 2, 'A second recorder must be started.');
  assert.equal(environment.recorders[1].state, 'recording', 'The new take must be running.');
  assert.equal(text(environment, 'record-label-0'), bootstrap.ui.record, 'The label returns to "record" mid-take.');
}

// 6. A take is too short to keep, and says so without arming the upload.
{
  const environment = createEnvironment();
  await recordTake(environment, 1, 300);
  assert.equal(text(environment, 'status-1'), bootstrap.ui.tooShort);
  assert.equal(disabled(environment, 'upload-1'), true, 'A rejected take must not be uploadable.');
  assert.equal(text(environment, 'record-label-1'), bootstrap.ui.record, 'No take is waiting.');
}

// 7. Uploading one card tallies it, empties it for another sample, and leaves
//    every other card alone.
{
  const environment = createEnvironment();
  await recordTake(environment, 0);
  await recordTake(environment, 1);
  await uploadTake(environment, 0);

  assert.equal(environment.uploads.length, 1, 'Only the uploaded card may be sent.');
  assert.equal(environment.uploads[0].phrase_id, phrases[0].id, 'The payload must carry that card\'s phrase.');
  assert.deepEqual(
    JSON.parse(environment.storage.get(UPLOAD_COUNTS_KEY)),
    { [phrases[0].id]: 1 },
    'A successful upload must be tallied against its phrase.'
  );
  assert.equal(text(environment, 'tally-0'), bootstrap.ui.recordedCount.replace('{count}', '1'));
  assert.equal(disabled(environment, 'upload-0'), true, 'The uploaded take is gone.');
  assert.equal(disabled(environment, 'record-0'), false, 'The card must be ready for another sample.');
  assert.equal(text(environment, 'record-label-0'), bootstrap.ui.record);
  assert.equal(disabled(environment, 'upload-1'), false, 'The other card keeps its own waiting take.');
}

// 8. A second sample of the same phrase stacks on the tally.
{
  const environment = createEnvironment();
  await recordTake(environment, 0);
  await uploadTake(environment, 0);
  await recordTake(environment, 0);
  await uploadTake(environment, 0);
  assert.deepEqual(JSON.parse(environment.storage.get(UPLOAD_COUNTS_KEY)), { [phrases[0].id]: 2 });
  assert.equal(text(environment, 'tally-0'), bootstrap.ui.recordedCount.replace('{count}', '2'));
  assert.equal(text(environment, 'summary-samples'), bootstrap.ui.summarySamples.replace('{count}', '2'));
  assert.equal(
    text(environment, 'summary-phrases'),
    bootstrap.ui.summaryPhrases.replace('{recorded}', '1').replace('{total}', String(prompts.length)),
    'Two samples of one phrase still cover one phrase.'
  );
}

// 9. A failed upload keeps its take, offers a retry, and never tallies.
{
  const environment = createEnvironment({ uploadOk: false });
  await recordTake(environment, 5);
  await uploadTake(environment, 5);
  assert.equal(environment.storage.has(UPLOAD_COUNTS_KEY), false, 'A failed upload must not be tallied.');
  assert.equal(disabled(environment, 'upload-5'), false, 'The take must be kept for a retry.');
  assert.equal(text(environment, 'upload-label-5'), bootstrap.ui.retry, 'The button must offer a retry.');
  assert.equal(text(environment, 'tally-5'), '', 'Nothing was uploaded, so nothing is tallied.');
}

// 10. The upload-all bar counts waiting takes and appears only once it beats
//     the card's own button, which is already on screen.
{
  const environment = createEnvironment();
  assert.equal(environment.dom.get('upload-all-bar').hidden, true, 'Nothing waiting, no bar.');

  await recordTake(environment, 0);
  assert.equal(environment.dom.get('upload-all-bar').hidden, true, 'One take is the card\'s own business.');

  await recordTake(environment, 1);
  assert.equal(environment.dom.get('upload-all-bar').hidden, false, 'Two waiting takes earn the bar.');
  assert.equal(text(environment, 'upload-all-label'), bootstrap.ui.uploadAll.replace('{count}', '2'));

  await recordTake(environment, 2);
  assert.equal(text(environment, 'upload-all-label'), bootstrap.ui.uploadAll.replace('{count}', '3'));
}

// 11. Upload-all sends every waiting take, one at a time, and clears the bar.
{
  const environment = createEnvironment();
  await recordTake(environment, 0);
  await recordTake(environment, 1);
  await recordTake(environment, 2);

  const releaseUploads = environment.blockUploads();
  environment.dom.get('upload-all-button').dispatch('click');
  await settle();

  assert.equal(environment.uploads.length, 1, 'Uploads must be serialized, not fired at once.');
  assert.equal(text(environment, 'upload-label-0'), bootstrap.ui.uploading, 'The first take is on its way.');
  assert.equal(text(environment, 'upload-label-1'), bootstrap.ui.uploadQueued, 'The rest wait their turn visibly.');
  assert.equal(disabled(environment, 'record-1'), true, 'A queued card cannot be recorded over.');

  await releaseUploads();

  assert.equal(environment.uploads.length, 3, 'Every waiting take must be sent.');
  assert.deepEqual(
    JSON.parse(environment.storage.get(UPLOAD_COUNTS_KEY)),
    { [phrases[0].id]: 1, [phrases[1].id]: 1, [phrases[2].id]: 1 },
    'Each phrase must be tallied once.'
  );
  assert.equal(environment.dom.get('upload-all-bar').hidden, true, 'Nothing is waiting any more.');
  assert.equal(
    text(environment, 'summary-phrases'),
    bootstrap.ui.summaryPhrases.replace('{recorded}', '3').replace('{total}', String(prompts.length))
  );
}

// 12. The microphone is asked for once and held across cards.
{
  const environment = createEnvironment();
  await recordTake(environment, 0);
  await recordTake(environment, 1);
  assert.equal(environment.microphoneRequestCount(), 1, 'A second card must reuse the open microphone.');

  // The idle release is a deferred callback; once it runs, the next take asks again.
  environment.flushTimers();
  await recordTake(environment, 2);
  assert.equal(environment.microphoneRequestCount(), 2, 'A released microphone must be requested again.');
}

// 13. A stored tally is restored onto the cards and the summary.
{
  const seed = { [phrases[0].id]: 3, [phrases[4].id]: 1 };
  const environment = createEnvironment({ storageSeed: seed });
  assert.equal(text(environment, 'tally-0'), bootstrap.ui.recordedCount.replace('{count}', '3'));
  assert.equal(text(environment, 'tally-4'), bootstrap.ui.recordedCount.replace('{count}', '1'));
  assert.equal(text(environment, 'tally-1'), '', 'A phrase with no samples shows no tally.');
  assert.equal(text(environment, 'summary-samples'), bootstrap.ui.summarySamples.replace('{count}', '4'));
  assert.equal(
    text(environment, 'summary-phrases'),
    bootstrap.ui.summaryPhrases.replace('{recorded}', '2').replace('{total}', String(prompts.length))
  );
}

// 14. An insecure page blocks recording on every card and says why once.
{
  const environment = createEnvironment({ secureContext: false });
  assert.equal(text(environment, 'page-status'), bootstrap.ui.insecureContext);
  prompts.forEach((prompt, index) => {
    assert.equal(disabled(environment, `record-${index}`), true, `Card ${index} must not offer recording.`);
  });
}

// 15. The negative-class card comes last, asks for a word that is not a dhikr,
//     and marks itself as the odd one out so it is not read as a phrase.
if (bootstrap.unknownPrompt) {
  const environment = createEnvironment();
  assert.equal(
    text(environment, `phrase-${unknownIndex}`),
    bootstrap.unknownPrompt.text,
    'The last card must ask for a word that is not a dhikr.'
  );
  assert.equal(
    text(environment, `note-${unknownIndex}`),
    bootstrap.unknownPrompt.note,
    'The card must carry its example note; "any word" is not actionable on its own.'
  );
  assert.equal(text(environment, `badge-${unknownIndex}`), bootstrap.ui.unknownBadge);
  assert.match(
    environment.dom.get(`card-${unknownIndex}`).className,
    /phrase-card-unknown/,
    'The card must be styled apart from the phrases.'
  );
  assert.equal(
    environment.dom.find('note-0'),
    null,
    'A dhikr card carries no note: the phrase is its own instruction.'
  );
}

// 16. An unknown take uploads under its own id, which is what sends it to the
//     dataset's unknown/ folder, and tallies against itself alone.
if (bootstrap.unknownPrompt) {
  const environment = createEnvironment();
  await recordTake(environment, unknownIndex);
  await uploadTake(environment, unknownIndex);

  assert.equal(
    environment.uploads[0].phrase_id,
    bootstrap.unknownPrompt.id,
    'The payload must carry the unknown prompt\'s id, not a phrase id.'
  );
  assert.deepEqual(
    JSON.parse(environment.storage.get(UPLOAD_COUNTS_KEY)),
    { [bootstrap.unknownPrompt.id]: 1 },
    'An unknown sample must be tallied against the unknown card only.'
  );
  assert.equal(text(environment, `tally-${unknownIndex}`), bootstrap.ui.recordedCount.replace('{count}', '1'));
  assert.equal(text(environment, 'tally-0'), '', 'No phrase may be credited with the unknown sample.');
  assert.equal(
    text(environment, 'summary-phrases'),
    bootstrap.ui.summaryPhrases.replace('{recorded}', '1').replace('{total}', String(prompts.length)),
    'The unknown card counts towards coverage like any other prompt.'
  );
}

console.log('UI behaviour tests passed: per-prompt recorders, the unknown card, single-take locking, uploads, and tallies.');
