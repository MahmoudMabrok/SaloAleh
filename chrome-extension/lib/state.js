// Storage shape:
// {
//   schemaVersion: 3,
//   uid: "<hex sha256>",
//   countryCode: "EG",
//   countryAuto: true,
//   rounds: { "YYYY-MM-DD": { count: 1234 } },
//   pendingSubmission: { roundKey, count, createdAt } | null,
//   lastSubmittedAt: 1747000000000 | null
// }

const STORAGE_KEY = "saloAleh";
const SCHEMA_VERSION = 3;

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
      pendingSubmission: null,
      lastSubmittedAt: null,
    };
    await saveRaw(state);
  } else if ((state.schemaVersion ?? 1) < SCHEMA_VERSION) {
    state.schemaVersion = SCHEMA_VERSION;
    state.pendingSubmission =
      state.pendingHandoff
        ? {
            roundKey: state.pendingHandoff.roundKey,
            count: state.pendingHandoff.count,
            createdAt: state.pendingHandoff.createdAt,
          }
        : (state.pendingSubmission ?? null);
    state.lastSubmittedAt = state.lastApplied?.at ?? state.lastSubmittedAt ?? null;
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
  return {
    uid: state.uid,
    roundKey,
    count,
    countryCode: country,
    countryAuto: state.countryAuto,
    isFridayBonus: SaloRound.isFridayBonus(),
    pendingSubmission: state.pendingSubmission,
    lastSubmittedAt: state.lastSubmittedAt,
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

async function setCountry(code, auto) {
  const state = await ensureState();
  state.countryAuto = !!auto;
  state.countryCode = auto
    ? SaloCountry.detectCountryCode()
    : (code || SaloCountry.UNKNOWN).toUpperCase();
  await saveRaw(state);
  return state.countryCode;
}

// Snapshot the current round count for the QR. Refreshes whenever the
// user has tapped more since the last snapshot, so the displayed QR
// always matches what they'd hand off right now.
async function ensureSubmissionSnapshot() {
  const state = await ensureState();
  const roundKey = SaloRound.currentRoundKey();
  const count = state.rounds[roundKey]?.count ?? 0;
  const pending = state.pendingSubmission;
  if (!pending || pending.roundKey !== roundKey || pending.count !== count) {
    state.pendingSubmission = { roundKey, count, createdAt: Date.now() };
    await saveRaw(state);
  }
  return state.pendingSubmission;
}

// Called when the user confirms "submitted from phone".
// Subtracts the SNAPSHOTTED count (what the QR contained) from the
// current round total, so taps made after the QR was generated are
// preserved.
async function applySubmitted() {
  const state = await ensureState();
  const pending = state.pendingSubmission;
  if (!pending) return { applied: false, reason: "no-snapshot" };
  const roundKey = SaloRound.currentRoundKey();
  if (pending.roundKey !== roundKey) {
    // Round rolled over since the QR was generated — drop the snapshot.
    state.pendingSubmission = null;
    await saveRaw(state);
    return { applied: false, reason: "round-rolled" };
  }
  const current = state.rounds[roundKey]?.count ?? 0;
  const subtract = Math.min(pending.count, current);
  const next = current - subtract;
  state.rounds[roundKey] = { count: next };
  state.pendingSubmission = null;
  state.lastSubmittedAt = Date.now();
  await saveRaw(state);
  return { applied: true, subtracted: subtract, previousCount: current, newCount: next };
}

function buildQrPayload(view, snapshot) {
  return JSON.stringify({
    v: 2,
    type: "saloaleh-submit",
    round: view.roundKey,
    count: snapshot.count,
    country: view.countryCode,
    src: "chrome-ext",
    ts: Date.now(),
  });
}

self.SaloState = {
  ensureState,
  getView,
  increment,
  setCountry,
  ensureSubmissionSnapshot,
  applySubmitted,
  buildQrPayload,
  STORAGE_KEY,
};
