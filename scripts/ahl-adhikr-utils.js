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

// Milestone buckets. Each user appears exactly once, in the highest milestone reached.
// The first tier starts at one week, then continues through the monthly milestones.
const MILESTONES = [
  { key: '7_days', min: 7 },
  { key: '30_days', min: 30 },
  { key: '60_days', min: 60 },
  { key: '90_days', min: 90 },
];

// Identity fields published for each user — the public uid suffix the app already
// shows when no nickname exists (generate-stats.js uses uid.slice(-6) too), plus the
// optional nickname and coarse country code. Nothing else (no tokens, codes, email, totals) leaves.
const ALLOWED_ENTRY_FIELDS = new Set(['id', 'name', 'streak', 'countryCode']);

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

function publicId(uid) {
  const tail = String(uid || '').slice(-6);
  return tail ? tail.toUpperCase() : '';
}

function entryName(user) {
  return (user.nickname || '').trim().slice(0, 20) || publicId(user.uid);
}

function publicCountryCode(value) {
  const code = typeof value === 'string' ? value.trim().toUpperCase() : '';
  return /^[A-Z]{2,3}$/.test(code) ? code : '';
}

// Challenge users → milestone buckets. `users` come straight from
// readChallengeRankedUsers (uid, count, nickname, streak); only the streak matters here.
function bucketByMilestone(users) {
  const entries = users
    .filter(u => typeof u.streak === 'number' && u.streak >= MILESTONES[0].min)
    .map(u => ({
      id: publicId(u.uid),
      name: entryName(u),
      streak: u.streak,
      countryCode: publicCountryCode(u.countryCode),
    }))
    .sort((a, b) => b.streak - a.streak || a.id.localeCompare(b.id));

  const buckets = Object.fromEntries(MILESTONES.map(({ key }) => [key, []]));
  for (const entry of entries) {
    const milestone = [...MILESTONES].reverse().find(({ min }) => entry.streak >= min);
    if (milestone) buckets[milestone.key].push(entry);
  }
  return buckets;
}

// Salawat streaks — read exactly what the mohamed_lovers leaderboard shows: the
// continuous day streak and nickname each client publishes to its own player node
// (mohamed_lovers/{roundKey}/players/{uid}/roundStreak, written by the app's
// writeRoundStreak from RoundStreakStore). No server-side recomputation, no ledger —
// the published value and the leaderboard can never disagree.
function salawatStreaksFromPlayers(players) {
  const streaks = {};
  if (!players || typeof players !== 'object') return streaks;
  for (const [uid, p] of Object.entries(players)) {
    const streak = p && typeof p === 'object' ? p.roundStreak : null;
    if (typeof uid === 'string' && uid && typeof streak === 'number' && streak > 0) {
      const nickname = typeof p.nickname === 'string' ? p.nickname.trim().slice(0, 20) : '';
      streaks[uid] = { streak, nickname, countryCode: publicCountryCode(p.countryCode) };
    }
  }
  return streaks;
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
    .map(([uid, value]) => {
      if (typeof value === 'number') return { uid, streak: value, nickname: '', countryCode: '' };
      return {
        uid,
        streak: value && value.streak,
        nickname: value && typeof value.nickname === 'string' ? value.nickname : '',
        countryCode: value && typeof value.countryCode === 'string' ? value.countryCode : '',
      };
    })
    .filter(user => typeof user.streak === 'number' && user.streak > 0);
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
        if (entry.countryCode !== undefined && (typeof entry.countryCode !== 'string' || (entry.countryCode !== '' && !/^[A-Z]{2,3}$/.test(entry.countryCode)))) {
          throw new Error(`${key}.${mKey} entry countryCode is invalid`);
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
  publicId,
  publicCountryCode,
  bucketByMilestone,
  salawatStreaksFromPlayers,
  buildSnapshot,
  validateSnapshot,
};
