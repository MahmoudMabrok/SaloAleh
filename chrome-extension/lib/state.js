// Storage shape:
// {
//   schemaVersion: 1,
//   uid: "<hex sha256>",        // stable per browser profile
//   countryCode: "EG",          // last known value
//   countryAuto: true,          // re-detect on read when true
//   rounds: { "YYYY-MM-DD": { count: 1234 } },
//   lastSubmittedRound: "YYYY-MM-DD" | null
// }

const STORAGE_KEY = "saloAleh";
const SCHEMA_VERSION = 1;

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
      lastSubmittedRound: null,
    };
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
  return {
    uid: state.uid,
    roundKey,
    count,
    countryCode: country,
    countryAuto: state.countryAuto,
    isFridayBonus: SaloRound.isFridayBonus(),
    lastSubmittedRound: state.lastSubmittedRound,
  };
}

async function increment(delta = 1) {
  const state = await ensureState();
  const roundKey = SaloRound.currentRoundKey();
  const prev = state.rounds[roundKey]?.count ?? 0;
  const next = Math.max(0, prev + delta);
  state.rounds[roundKey] = { count: next };
  if (state.countryAuto) {
    state.countryCode = SaloCountry.detectCountryCode();
  }
  await saveRaw(state);
  return next;
}

async function resetCurrentRound() {
  const state = await ensureState();
  const roundKey = SaloRound.currentRoundKey();
  if (state.rounds[roundKey]) {
    state.rounds[roundKey].count = 0;
  } else {
    state.rounds[roundKey] = { count: 0 };
  }
  state.lastSubmittedRound = roundKey;
  await saveRaw(state);
  return roundKey;
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

function buildQrPayload(view) {
  return JSON.stringify({
    v: 1,
    type: "saloaleh-submit",
    round: view.roundKey,
    count: view.count,
    country: view.countryCode,
    src: "chrome-ext",
    ts: Date.now(),
  });
}

self.SaloState = {
  ensureState,
  getView,
  increment,
  resetCurrentRound,
  setCountry,
  buildQrPayload,
  STORAGE_KEY,
};
