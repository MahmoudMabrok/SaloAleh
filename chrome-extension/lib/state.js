// Storage shape:
// {
//   schemaVersion: 2,
//   uid: "<hex sha256>",
//   countryCode: "EG",
//   countryAuto: true,
//   rounds: { "YYYY-MM-DD": { count: 1234 } },
//   pendingHandoff: { nonce, roundKey, count, createdAt } | null,
//   lastApplied: { nonce, roundKey, count, byUid, at } | null
// }

const STORAGE_KEY = "saloAleh";
const SCHEMA_VERSION = 2;

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
      pendingHandoff: null,
      lastApplied: null,
    };
    await saveRaw(state);
  } else if ((state.schemaVersion ?? 1) < 2) {
    state.schemaVersion = SCHEMA_VERSION;
    state.pendingHandoff = state.pendingHandoff ?? null;
    state.lastApplied = state.lastApplied ?? null;
    delete state.lastSubmittedRound;
    await saveRaw(state);
  }
  return state;
}

function randomNonce() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
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
    pendingHandoff: state.pendingHandoff,
    lastApplied: state.lastApplied,
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
  const current = state.rounds[roundKey]?.count ?? 0;
  state.rounds[roundKey] = { count: 0 };
  state.lastApplied = {
    nonce: null,
    roundKey,
    count: current,
    byUid: null,
    at: Date.now(),
    manual: true,
  };
  state.pendingHandoff = null;
  await saveRaw(state);
  return roundKey;
}

async function refreshHandoff() {
  const state = await ensureState();
  const roundKey = SaloRound.currentRoundKey();
  const count = state.rounds[roundKey]?.count ?? 0;
  const nonce = randomNonce();
  state.pendingHandoff = { nonce, roundKey, count, createdAt: Date.now() };
  await saveRaw(state);
  return state.pendingHandoff;
}

async function ensureHandoff() {
  const state = await ensureState();
  const roundKey = SaloRound.currentRoundKey();
  const count = state.rounds[roundKey]?.count ?? 0;
  const pending = state.pendingHandoff;
  // Regenerate when:
  //  - none yet
  //  - belongs to an older round
  //  - count changed (taps added/removed since last QR)
  if (!pending || pending.roundKey !== roundKey || pending.count !== count) {
    return refreshHandoff();
  }
  return pending;
}

async function applyHandoffRecord(record) {
  const state = await ensureState();
  const pending = state.pendingHandoff;
  if (!pending) return { applied: false, reason: "no-pending" };
  if (record?.round !== pending.roundKey) {
    return { applied: false, reason: "round-mismatch" };
  }
  if (typeof record?.count !== "number" || record.count < 0) {
    return { applied: false, reason: "bad-count" };
  }
  if (record.count > pending.count) {
    // Mobile claims more taps than we offered — clamp to what we sent.
    record.count = pending.count;
  }
  const current = state.rounds[pending.roundKey]?.count ?? 0;
  const next = Math.max(0, current - record.count);
  state.rounds[pending.roundKey] = { count: next };
  state.lastApplied = {
    nonce: pending.nonce,
    roundKey: pending.roundKey,
    count: record.count,
    byUid: typeof record.byUid === "string" ? record.byUid : null,
    at: Date.now(),
    manual: false,
  };
  state.pendingHandoff = null;
  await saveRaw(state);
  return { applied: true, newCount: next, previousCount: current };
}

async function clearPendingHandoff() {
  const state = await ensureState();
  state.pendingHandoff = null;
  await saveRaw(state);
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

function buildQrPayload(view, handoff) {
  return JSON.stringify({
    v: 2,
    type: "saloaleh-submit",
    round: view.roundKey,
    count: handoff.count,
    country: view.countryCode,
    nonce: handoff.nonce,
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
  ensureHandoff,
  refreshHandoff,
  applyHandoffRecord,
  clearPendingHandoff,
  buildQrPayload,
  STORAGE_KEY,
};
