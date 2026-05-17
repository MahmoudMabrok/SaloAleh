// Storage shape:
// {
//   schemaVersion: 4,
//   uid: "<hex sha256>",
//   countryCode: "EG",
//   countryAuto: true,
//   rounds: { "YYYY-MM-DD": { count: 1234 } },
//   qrSession: {
//     roundKey: "YYYY-MM-DD",
//     count: 1234,           // snapshot at the moment the QR opened
//     startedAt: 1747000000000,
//     endsAt: 1747000120000  // startedAt + WINDOW_MS
//   } | null,
//   lastSubmittedAt: 1747000120000 | null
// }

const STORAGE_KEY = "saloAleh";
const SCHEMA_VERSION = 4;
const WINDOW_MS = 2 * 60 * 1000; // 2 minutes

async function loadRaw() {
  const obj = await chrome.storage.local.get(STORAGE_KEY);
  return obj[STORAGE_KEY] ?? null;
}

async function saveRaw(state) {
  await chrome.storage.local.set({ [STORAGE_KEY]: state });
}

function uuidV4() {
  if (crypto.randomUUID) return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

async function sha256Hex(input) {
  const buf = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", buf);
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function ensureState() {
  let state = await loadRaw();
  if (!state) {
    const uid = await sha256Hex(uuidV4());
    state = {
      schemaVersion: SCHEMA_VERSION,
      uid,
      countryCode: SaloCountry.detectCountryCode(),
      countryAuto: true,
      rounds: {},
      qrSession: null,
      lastSubmittedAt: null,
    };
    await saveRaw(state);
  } else if ((state.schemaVersion ?? 1) < SCHEMA_VERSION) {
    state.schemaVersion = SCHEMA_VERSION;
    state.qrSession = state.qrSession ?? null;
    state.lastSubmittedAt =
      state.lastSubmittedAt ?? state.lastApplied?.at ?? null;
    delete state.pendingSubmission;
    delete state.pendingHandoff;
    delete state.lastApplied;
    delete state.lastSubmittedRound;
    await saveRaw(state);
  }
  return state;
}

async function getView() {
  const state = await ensureState();
  const roundKey = SaloRound.currentRoundKey();
  const country = state.countryAuto
    ? SaloCountry.detectCountryCode()
    : state.countryCode || SaloCountry.UNKNOWN;
  const count = state.rounds?.[roundKey]?.count ?? 0;
  const now = Date.now();
  const session = state.qrSession;
  const isActive = !!(
    session &&
    session.roundKey === roundKey &&
    session.endsAt > now
  );
  return {
    uid: state.uid,
    roundKey,
    count,
    countryCode: country,
    countryAuto: state.countryAuto,
    isFridayBonus: SaloRound.isFridayBonus(),
    qrSession: session,
    qrSessionActive: isActive,
    msRemaining: isActive ? Math.max(0, session.endsAt - now) : 0,
    lastSubmittedAt: state.lastSubmittedAt,
  };
}

async function increment(delta = 1) {
  const state = await ensureState();
  const roundKey = SaloRound.currentRoundKey();
  // Lock the counter while a QR submission window is open.
  if (
    state.qrSession &&
    state.qrSession.roundKey === roundKey &&
    state.qrSession.endsAt > Date.now()
  ) {
    return state.rounds[roundKey]?.count ?? 0;
  }
  const prev = state.rounds[roundKey]?.count ?? 0;
  const next = Math.max(0, prev + delta);
  state.rounds[roundKey] = { count: next };
  if (state.countryAuto) {
    state.countryCode = SaloCountry.detectCountryCode();
  }
  await saveRaw(state);
  return next;
}

async function setCountry(code, auto) {
  const state = await ensureState();
  state.countryAuto = !!auto;
  state.countryCode = auto
    ? SaloCountry.detectCountryCode()
    : (code || SaloCountry.UNKNOWN).toUpperCase();
  await saveRaw(state);
  return state.countryCode;
}

// Start a fresh 2-minute QR submission window. Snapshots the count
// that the mobile app should apply.
async function startQrSession() {
  const state = await ensureState();
  const roundKey = SaloRound.currentRoundKey();
  const count = state.rounds[roundKey]?.count ?? 0;
  const now = Date.now();
  state.qrSession = {
    roundKey,
    count,
    startedAt: now,
    endsAt: now + WINDOW_MS,
  };
  await saveRaw(state);
  return state.qrSession;
}

// Cancel the window without resetting anything.
async function cancelQrSession() {
  const state = await ensureState();
  state.qrSession = null;
  await saveRaw(state);
}

// Window expired (or finished manually) → zero the round count.
async function finishQrSession() {
  const state = await ensureState();
  const session = state.qrSession;
  if (!session) return { applied: false, reason: "no-session" };
  const roundKey = session.roundKey;
  state.rounds[roundKey] = { count: 0 };
  state.qrSession = null;
  state.lastSubmittedAt = Date.now();
  await saveRaw(state);
  return { applied: true, roundKey, submittedCount: session.count };
}

function buildQrPayload(view, session) {
  return JSON.stringify({
    v: 2,
    type: "saloaleh-submit",
    round: view.roundKey,
    count: session.count,
    country: view.countryCode,
    src: "chrome-ext",
    ts: session.startedAt,
    ttl: session.endsAt,
  });
}

self.SaloState = {
  ensureState,
  getView,
  increment,
  setCountry,
  startQrSession,
  cancelQrSession,
  finishQrSession,
  buildQrPayload,
  STORAGE_KEY,
  WINDOW_MS,
};
