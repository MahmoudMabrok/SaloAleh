// Pure core of the أهل الذكر (Ahl Adhikr) daily snapshot — no Firebase, no fs.
// Everything here is deterministic so generate-ahl-adhikr.js stays a thin IO shell
// and ahl-adhikr.test.js can cover the semantics (thresholds, idempotency, privacy).

const CHALLENGE_KEYS = [
  'dhikr',
  'baqiyat',
  'istighfar',
  'zabad',
  'quran',
  'ghars',
  'alf_hasana',
  'kalimat',
  'hawqala',
  'salawat',
];

// Milestone buckets. A user with a higher streak appears in every lower bucket too
// (30_days is "current streak at least 30"), matching issue #250's semantics.
const MILESTONES = [
  { key: '30_days', min: 30 },
  { key: '60_days', min: 60 },
  { key: '90_days', min: 90 },
];

// Identity fields published for each user — the public uid suffix the app already
// shows when no nickname exists (generate-stats.js uses uid.slice(-6) too), plus the
// optional nickname. Nothing else (no tokens, codes, country, email, totals) leaves.
const ALLOWED_ENTRY_FIELDS = new Set(['id', 'name', 'streak']);

// Forbidden anywhere in the snapshot — a hard stop in validateSnapshot so a future
// schema change can never silently leak credentials.
const FORBIDDEN_KEYS = new Set([
  'token', 'tokens', 'fcmToken', 'backupCode', 'backupCodes', 'password', 'secret',
]);

function cairoDateStr(date = new Date()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(date);
}

// Days between two YYYY-MM-DD strings (b - a), via UTC epoch — date keys carry no
// zone, and both are Cairo days, so plain day arithmetic is exact.
function daysBetween(a, b) {
  return Math.round(Date.UTC(...b.split('-').map(Number)) / 86400000)
    - Math.round(Date.UTC(...a.split('-').map(Number)) / 86400000);
}

function publicId(uid) {
  const tail = String(uid || '').slice(-6);
  return tail ? tail.toUpperCase() : '';
}

function entryName(user) {
  return (user.nickname || '').trim().slice(0, 20) || publicId(user.uid);
}

// Challenge users → milestone buckets. `users` come straight from
// readChallengeRankedUsers (uid, count, nickname, streak); only the streak matters here.
function bucketByMilestone(users) {
  const entries = users
    .filter(u => typeof u.streak === 'number' && u.streak >= MILESTONES[0].min)
    .map(u => ({ id: publicId(u.uid), name: entryName(u), streak: u.streak }))
    .sort((a, b) => b.streak - a.streak || a.id.localeCompare(b.id));

  const buckets = {};
  for (const { key, min } of MILESTONES) {
    buckets[key] = entries.filter(e => e.streak >= min).map(e => ({ id: e.id, name: e.name, streak: e.streak }));
  }
  return buckets;
}

// Salawat streak ledger — the one challenge without a per-user continuous streak in
// RTDB (roundStreak is round-scoped, max ~7). The generator keeps
// ahl_adhikr_state/salawat/{uid} = {streak, lastActive} and updates it per run.
//
// Semantics mirror the app's RoundStreakStore: activity extends the streak (a gap of
// 1–2 frozen days still counts — 3 consecutive inactive days reset it, so a user
// returning after ≤2 missed days adds the whole gap).
//
// Idempotent: the new state is computed from (prevState, activeToday, date) only —
// re-running the same day with the same inputs yields the same state, never double-
// increments, because activeToday members are keyed by lastActive === date.
function nextSalawatStreaks(prevState, activeUids, dateStr) {
  const prev = prevState || {};
  const active = new Set(activeUids);
  const state = {};
  const streaks = {};

  const uids = new Set([...Object.keys(prev), ...active]);
  for (const uid of uids) {
    const entry = prev[uid] || null;
    const prevStreak = entry && typeof entry.streak === 'number' && entry.streak > 0 ? entry.streak : 0;
    const lastActive = entry && typeof entry.lastActive === 'string' ? entry.lastActive : null;

    if (active.has(uid)) {
      // Already updated by an earlier run today → keep (idempotency).
      if (lastActive === dateStr) {
        state[uid] = { streak: prevStreak, lastActive };
        streaks[uid] = prevStreak;
        continue;
      }
      const gap = lastActive ? daysBetween(lastActive, dateStr) : null;
      // gap 1 = consecutive, gap 2..3 = frozen days absorbed, gap > 3 = reset.
      const grown = gap != null && gap >= 1 && gap <= 3
        ? prevStreak + gap
        : (gap != null && gap === 0 ? prevStreak : 1);
      state[uid] = { streak: grown, lastActive: dateStr };
      streaks[uid] = grown;
    } else {
      const gap = lastActive ? daysBetween(lastActive, dateStr) : null;
      if (prevStreak > 0 && gap != null && gap < 3) {
        // Still within the freeze window — keep the streak, not yet reset.
        state[uid] = { streak: prevStreak, lastActive };
        streaks[uid] = prevStreak;
      }
      // else: reset (or never active) — omit from state entirely.
    }
  }

  return { state, streaks };
}

// Full snapshot object. generatedAt is derived from the Cairo date (not the wall
// clock) so re-running the same day produces the exact same file — the issue's
// idempotency requirement.
function buildSnapshot({ dateStr, challenges, salawatStreaks, source = 'firebase-rtdb' }) {
  const out = { generatedAt: `${dateStr}T12:00:00Z`, date: dateStr, source, challenges: {} };
  for (const key of CHALLENGE_KEYS) {
    const users = key === 'salawat' ? toUsers(salawatStreaks) : (challenges[key] || []);
    out.challenges[key] = bucketByMilestone(users);
  }
  return out;
}

function toUsers(streakMap) {
  return Object.entries(streakMap || {})
    .filter(([, s]) => typeof s === 'number' && s > 0)
    .map(([uid, streak]) => ({ uid, streak }));
}

// Hard validation gate before anything is written. Throws on structural problems,
// leaked private fields, or out-of-range streaks; returns per-challenge counts for
// the workflow log.
function validateSnapshot(snap) {
  if (!snap || typeof snap !== 'object') throw new Error('snapshot is not an object');
  if (typeof snap.generatedAt !== 'string' || !snap.generatedAt) throw new Error('generatedAt missing');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(snap.date || '')) throw new Error(`bad date: ${snap.date}`);
  if (typeof snap.source !== 'string' || !snap.source) throw new Error('source missing');
  if (!snap.challenges || typeof snap.challenges !== 'object') throw new Error('challenges missing');

  const counts = {};
  for (const key of CHALLENGE_KEYS) {
    const buckets = snap.challenges[key];
    if (!buckets || typeof buckets !== 'object') throw new Error(`challenge missing: ${key}`);
    counts[key] = {};
    for (const { key: mKey, min } of MILESTONES) {
      const list = buckets[mKey];
      if (!Array.isArray(list)) throw new Error(`${key}.${mKey} is not an array`);
      for (const entry of list) {
        if (!entry || typeof entry !== 'object') throw new Error(`${key}.${mKey} entry is not an object`);
        for (const field of Object.keys(entry)) {
          if (!ALLOWED_ENTRY_FIELDS.has(field)) {
            throw new Error(`${key}.${mKey} entry has a non-public field: ${field}`);
          }
        }
        if (typeof entry.id !== 'string' || entry.id.length === 0 || entry.id.length > 6) {
          throw new Error(`${key}.${mKey} entry id is not a short public id`);
        }
        if (typeof entry.streak !== 'number' || entry.streak < min || entry.streak > 3650) {
          throw new Error(`${key}.${mKey} entry streak out of range: ${entry.streak}`);
        }
        if (entry.name !== undefined && typeof entry.name !== 'string') {
          throw new Error(`${key}.${mKey} entry name is not a string`);
        }
      }
      counts[key][mKey] = list.length;
    }
  }

  // Defense in depth: no forbidden key anywhere in the serialized shape.
  const scan = (node, path) => {
    if (Array.isArray(node)) {
      node.forEach((v, i) => scan(v, `${path}[${i}]`));
    } else if (node && typeof node === 'object') {
      for (const [k, v] of Object.entries(node)) {
        if (FORBIDDEN_KEYS.has(k)) throw new Error(`forbidden key at ${path}.${k}`);
        scan(v, `${path}.${k}`);
      }
    }
  };
  scan(snap, '$');

  return counts;
}

module.exports = {
  CHALLENGE_KEYS,
  MILESTONES,
  ALLOWED_ENTRY_FIELDS,
  FORBIDDEN_KEYS,
  cairoDateStr,
  daysBetween,
  publicId,
  bucketByMilestone,
  nextSalawatStreaks,
  buildSnapshot,
  validateSnapshot,
};
