const { mirrorMohamedLoversRound: firestoreMirrorRound } = require('./firestore-utils');

const DHIKR_CHALLENGE_ROOT = '100_challenge';
const BAQIYAT_CHALLENGE_ROOT = 'baqiyat_saliha';
const ISTIGHFAR_CHALLENGE_ROOT = 'istighfar_challenge';
const ZABAD_CHALLENGE_ROOT = 'zabad_challenge';
const GHARS_CHALLENGE_ROOT = 'ghars_challenge';
const QURAN_CHALLENGE_ROOT = 'quran_challenge';
const ALBAQARA_CHALLENGE_ROOT = 'albaqara_challenge';
const ALF_HASANA_CHALLENGE_ROOT = 'alf_hasana_challenge';
const MOHAMED_LOVERS_ROOT = 'mohamed_lovers';

// A day's salawat above this is treated as abnormal and recorded for admin review.
const ABNORMAL_DAILY_THRESHOLD = 12000;

// Salawat pace ceiling: a player's cumulative round total is expected to grow by at
// most this per competition day. On day N of the round a total above N * this is
// treated as abnormal pace and recorded (per-user, for later analysis). Saturday —
// the first day after the Friday 19:00 reset — is day 1, so its ceiling is one
// increment (11k), Sunday (day 2) is 22k, and so on.
const PACE_DAILY_INCREMENT = 11000;

// A player's competition score for the current Cairo day. Prefers the client-published absolute
// `todayCount`; falls back to the yesterday-diff (`totalCount - yesterdayTotalScore`) for clients
// that predate todayCount, so the daily leaderboard never zeroes out an un-updated user.
function computeTodayScore({ total, today, yesterday }) {
  if (typeof today === 'number' && today >= 0) return Math.floor(today);
  return Math.max(0, (total || 0) - (yesterday || 0));
}

// Builds the per-user daily snapshots written at the daily close (generate-stats.js): a score
// history entry per active user, an abnormal-user flag for anyone above the daily threshold, a
// pace flag for anyone whose cumulative round total outruns the day-of-round ceiling, and a
// todayCount reset so the next Cairo day starts from zero. Pure so it can be unit-tested.
// `players` items: { uid, totalCount, todayCount, yesterdayTotalScore, countryCode }.
// `roundDay` (1-based competition day; see roundDayNumber) gates the pace flag; pass null to skip it.
function buildDailyScoreSnapshots({
  players,
  dateKey,
  roundKey,
  roundDay = null,
  threshold = ABNORMAL_DAILY_THRESHOLD,
  paceIncrement = PACE_DAILY_INCREMENT,
}) {
  const scoreHistoryUpdates = {};
  const abnormalUpdates = {};
  const todayCountResets = {};
  const paceFlagUpdates = {};
  const paceCeiling = typeof roundDay === 'number' && roundDay > 0 ? roundDay * paceIncrement : null;
  for (const p of players || []) {
    if (!p || !p.uid) continue;
    const dayTotal = computeTodayScore({
      total: p.totalCount,
      today: p.todayCount,
      yesterday: p.yesterdayTotalScore,
    });
    if (dayTotal > 0) {
      scoreHistoryUpdates[`${MOHAMED_LOVERS_ROOT}/users/${p.uid}/scoreHistory/${dateKey}`] = dayTotal;
    }
    if (dayTotal > threshold) {
      abnormalUpdates[`${MOHAMED_LOVERS_ROOT}/abnormal_users/${dateKey}/${p.uid}`] = {
        count: dayTotal,
        totalCount: p.totalCount || 0,
        countryCode: typeof p.countryCode === 'string' ? p.countryCode : 'NA',
      };
    }
    // Pace flag: cumulative round total outrunning the day-of-round ceiling (day N * increment).
    if (paceCeiling != null) {
      const total = typeof p.totalCount === 'number' ? p.totalCount : 0;
      if (total > paceCeiling) {
        paceFlagUpdates[`${MOHAMED_LOVERS_ROOT}/users/${p.uid}/paceFlags/${dateKey}`] = {
          totalCount: total,
          dayOfRound: roundDay,
          threshold: paceCeiling,
          countryCode: typeof p.countryCode === 'string' ? p.countryCode : 'NA',
        };
      }
    }
    // Only reset players who actually carry a non-zero client-pushed todayCount.
    if (typeof p.todayCount === 'number' && p.todayCount !== 0) {
      todayCountResets[`${MOHAMED_LOVERS_ROOT}/${roundKey}/players/${p.uid}/todayCount`] = 0;
    }
  }
  return { scoreHistoryUpdates, abnormalUpdates, todayCountResets, paceFlagUpdates };
}

function buildOldRankMap(source) {
  const entries = source && typeof source.exists === 'function'
    ? (source.exists() ? source.val() : {})
    : (source || {});

  const map = {};
  for (const [key, entry] of Object.entries(entries)) {
    if (entry?.uid && !isNaN(Number(key))) map[entry.uid] = entry.rank;
  }
  return map;
}

function computeRankChange(uid, newRank, oldRankMap) {
  const oldRank = oldRankMap[uid];
  if (oldRank == null) return 'new';
  if (oldRank === newRank) return 'same';
  return newRank < oldRank ? 'up' : 'down';
}

// Detects top-3 movements between an old rank map and the new ranked ordering.
// Returns one notification per user who was in the top 3 and either dropped out
// of it ('dropped') or slipped to a lower rank still within it ('lost_position').
// `rankedUsers` is the new ordering (index 0 = rank 1); each item has a `uid`.
// Shared by mohamed_lovers and every daily challenge so the signal stays identical.
function computeTop3Changes(oldRanks, rankedUsers) {
  const top3Notifs = [];
  for (const [uid, oldRank] of Object.entries(oldRanks)) {
    if (oldRank > 3) continue;
    const newIndex = rankedUsers.findIndex(u => u.uid === uid);
    const newRank = newIndex >= 0 ? newIndex + 1 : null;
    if (newRank == null || newRank > 3) {
      top3Notifs.push({ uid, event: 'dropped', oldRank, newRank });
    } else if (newRank > oldRank) {
      top3Notifs.push({ uid, event: 'lost_position', oldRank, newRank });
    }
  }
  return top3Notifs;
}

// Sends the top-3 change FCM notifications produced by computeTop3Changes.
// FCM tokens and the leaderboard opt-out flag live under mohamed_lovers/users
// for every feature (challenges included). `messages` maps event → {title, body}.
// `label` only tags log lines. Fire-and-forget per token, mirroring mohamed_lovers.
async function sendTop3ChangeNotifications(db, admin, top3Notifs, messages, label) {
  if (!top3Notifs || top3Notifs.length === 0) return;
  console.log(`[${label}] Top-3 changes: ${top3Notifs.length} notification(s) to send`);
  const promises = top3Notifs.map(async ({ uid, event }) => {
    const msg = messages[event];
    if (!msg) return;
    const userSnap = await db.ref(`${MOHAMED_LOVERS_ROOT}/users/${uid}`).get();
    const user = userSnap.val();
    if (!user?.fcmToken) { console.log(`  [${label}] top3 uid=${uid}: no FCM token — skip`); return; }
    if (user.leaderboardNotifsEnabled === false) { console.log(`  [${label}] top3 uid=${uid}: leaderboard notifications disabled — skip`); return; }
    return admin.messaging().send({
      token: user.fcmToken,
      notification: { title: msg.title, body: msg.body },
      data: { title: msg.title, body: msg.body },
    })
      .then(msgId => console.log(`  [${label}] top3 uid=${uid} (${event}): sent msgId=${msgId}`))
      .catch(e => console.error(`  [${label}] top3 uid=${uid} (${event}): send failed: ${e.message}`));
  });
  await Promise.all(promises);
}

// Top-3 change message copy, keyed by event, for mohamed_lovers and each challenge.
const MOHAMED_LOVERS_TOP3_MESSAGES = {
  dropped: {
    title: 'مكانك بين المحبين يناديك 🤍',
    body: 'كنت من أكثر المصلّين على النبي ﷺ — لا تتوقف، فالصلاة عليه نور وشفاعة يوم القيامة!',
  },
  lost_position: {
    title: 'المنافسة تشتد بين المحبين 🔥',
    body: 'تراجع ترتيبك بين أكثر المصلّين على النبي ﷺ — زِد صلواتك وارتقِ، فأقربكم مني مجلسًا أكثركم صلاةً عليّ!',
  },
};

const CHALLENGE_TOP3_MESSAGES = {
  dhikr: {
    dropped: {
      title: 'مكانك في تحدي أهل لا إله إلا الله يناديك 🤍',
      body: 'كنت من المتصدرين في تحدي أهل لا إله إلا الله — عُد وواصل ذكر الله، فذكر الله أكبر!',
    },
    lost_position: {
      title: 'المنافسة تشتد في تحدي أهل لا إله إلا الله 🔥',
      body: 'تراجع ترتيبك في تحدي أهل لا إله إلا الله — أكثِر من ذكر الله وارتقِ!',
    },
  },
  baqiyat: {
    dropped: {
      title: 'مكانك في الباقيات الصالحات يناديك 🤍',
      body: 'كنت من المتصدرين في تحدي الباقيات الصالحات — عُد وواصل، فهي خير عند ربك ثوابًا وخير أملاً!',
    },
    lost_position: {
      title: 'المنافسة تشتد في الباقيات الصالحات 🔥',
      body: 'تراجع ترتيبك في تحدي الباقيات الصالحات — زِد من الباقيات وارتقِ!',
    },
  },
  istighfar: {
    dropped: {
      title: 'مكانك في تحدي واستغفروه يناديك 🤍',
      body: 'كنت من المتصدرين في تحدي واستغفروه — عُد واستغفر، فالمستغفرون بالأسحار لهم البشرى!',
    },
    lost_position: {
      title: 'المنافسة تشتد في تحدي واستغفروه 🔥',
      body: 'تراجع ترتيبك في تحدي واستغفروه — أكثِر من الاستغفار وارتقِ!',
    },
  },
  zabad: {
    dropped: {
      title: 'مكانك في تحدي تسبيح المئة يناديك 🤍',
      body: 'كنت من المتصدرين في تحدي تسبيح المئة — عُد وسبِّح، فهما كلمتان حبيبتان إلى الرحمن!',
    },
    lost_position: {
      title: 'المنافسة تشتد في تحدي تسبيح المئة 🔥',
      body: 'تراجع ترتيبك في تحدي تسبيح المئة — زِد من تسبيحك وارتقِ!',
    },
  },
  ghars: {
    dropped: {
      title: 'مكانك في تحدي اغرس نخلة يناديك 🌴',
      body: 'كنت من المتصدرين في تحدي اغرس نخلة — عُد واغرس، فمن قالها غُرست له نخلة في الجنة!',
    },
    lost_position: {
      title: 'المنافسة تشتد في تحدي اغرس نخلة 🔥',
      body: 'تراجع ترتيبك في تحدي اغرس نخلة — أكثِر من الغرس وارتقِ!',
    },
  },
  quran: {
    dropped: {
      title: 'مكانك في تحدي القرآن الكريم يناديك 🤍',
      body: 'كنت من المتصدرين في تحدي القرآن الكريم — عُد واتلُ، فبكل حرف حسنة والحسنة بعشر أمثالها!',
    },
    lost_position: {
      title: 'المنافسة تشتد في تحدي القرآن الكريم 🔥',
      body: 'تراجع ترتيبك في تحدي القرآن الكريم — زِد من تلاوتك وارتقِ!',
    },
  },
  alf_hasana: {
    dropped: {
      title: 'مكانك في تحدي ألف حسنة يناديك 🤍',
      body: 'كنت من المتصدرين في تحدي ألف حسنة — عُد وسبِّح، فبمئة تسبيحة تُكتب لك ألف حسنة وتُحَطّ عنك ألف خطيئة!',
    },
    lost_position: {
      title: 'المنافسة تشتد في تحدي ألف حسنة 🔥',
      body: 'تراجع ترتيبك في تحدي ألف حسنة — أكثِر من التسبيح وارتقِ!',
    },
  },
};

function normalizeDhikrCount(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return 0;
  return Math.max(0, Math.floor(value));
}

function compareUidAsc(a, b) {
  if (a.uid < b.uid) return -1;
  if (a.uid > b.uid) return 1;
  return 0;
}

function cairoToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

// Adds `days` to a plain YYYY-MM-DD date key. Round keys carry no time-of-day,
// so UTC-anchored arithmetic is safe here (no DST edge cases to worry about).
function addDaysToDateKey(dateKey, days) {
  const d = new Date(`${dateKey}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

// The 1-based competition day for a Cairo date within a round. Round keys are the
// round's END Friday (Cairo); the round began the previous Friday at 19:00, so
// Saturday = 1 … Friday(end) = 7. Clamped to [1, 7] so the post-reset Friday-night
// daily close (already a fresh round) reads as day 1 rather than day 0.
function roundDayNumber(roundKey, dateKey) {
  const startFriday = addDaysToDateKey(roundKey, -7);
  const start = new Date(`${startFriday}T00:00:00Z`);
  const day = new Date(`${dateKey}T00:00:00Z`);
  const diff = Math.round((day - start) / 86400000);
  return Math.max(1, Math.min(7, diff));
}

function buildDailyCountChallengeRanking({
  dateKey,
  players,
  rootPath,
  playersPath,
}) {
  const normalizedPlayers = players
    .map(user => ({
      uid: typeof user.uid === 'string' ? user.uid : '',
      count: normalizeDhikrCount(user.count),
      countryCode: typeof user.countryCode === 'string' ? user.countryCode.toUpperCase().slice(0, 3) : '',
      nickname: typeof user.nickname === 'string' ? user.nickname.trim().slice(0, 20) : '',
      streak: typeof user.streak === 'number' && user.streak > 0 ? user.streak : 0,
      currentRank: typeof user.currentRank === 'number' && user.currentRank > 0 ? user.currentRank : null,
    }))
    .filter(user => user.uid.length > 0);

  const activeUsers = normalizedPlayers
    .filter(user => user.count > 0)
    .sort((a, b) => b.count - a.count || compareUidAsc(a, b));

  const rankUpdates = {};
  const activeUids = new Set(activeUsers.map(user => user.uid));

  // Diff against the rank already stored in RTDB (currentRank): only clear a
  // dropped user that actually has a rank, and only write an active user's rank
  // when it changed — avoids re-writing unchanged ranks (and re-notifying every
  // listening client) on every 30-min run.
  normalizedPlayers.forEach(user => {
    if (!activeUids.has(user.uid) && user.currentRank != null) {
      rankUpdates[`${rootPath}/${dateKey}/${playersPath}/${user.uid}/rank`] = null;
    }
  });

  const rankedUsers = activeUsers.map((user, index) => ({
    ...user,
    rank: index + 1,
  }));

  rankedUsers.forEach(user => {
    if (user.rank !== user.currentRank) {
      rankUpdates[`${rootPath}/${dateKey}/${playersPath}/${user.uid}/rank`] = user.rank;
    }
  });

  return {
    rankUpdates,
    rankedUsers,
    participantCount: rankedUsers.length,
    totalCount: rankedUsers.reduce((sum, user) => sum + user.count, 0),
  };
}

function buildDhikrChallengeDailyRanking(dateKey, users, rootPath = DHIKR_CHALLENGE_ROOT) {
  const ranking = buildDailyCountChallengeRanking({
    dateKey,
    players: users,
    rootPath,
    playersPath: 'users',
  });

  return {
    ...ranking,
    totalTodayDhikr: ranking.totalCount,
  };
}

function buildBaqiyatChallengeDailyRanking(dateKey, players, rootPath = BAQIYAT_CHALLENGE_ROOT) {
  const ranking = buildDailyCountChallengeRanking({
    dateKey,
    players,
    rootPath,
    playersPath: 'players',
  });

  return {
    ...ranking,
    totalTodayBaqiyat: ranking.totalCount,
  };
}

function buildIstighfarChallengeDailyRanking(dateKey, users, rootPath = ISTIGHFAR_CHALLENGE_ROOT) {
  const ranking = buildDailyCountChallengeRanking({
    dateKey,
    players: users,
    rootPath,
    playersPath: 'users',
  });

  return {
    ...ranking,
    totalTodayIstighfar: ranking.totalCount,
  };
}

function buildAlBaqaraChallengeDailyRanking(dateKey, users, rootPath = ALBAQARA_CHALLENGE_ROOT) {
  const ranking = buildDailyCountChallengeRanking({
    dateKey,
    players: users,
    rootPath,
    playersPath: 'users',
  });

  return {
    ...ranking,
    totalTodayAlBaqara: ranking.totalCount,
  };
}

function buildZabadChallengeDailyRanking(dateKey, users, rootPath = ZABAD_CHALLENGE_ROOT) {
  const result = buildDailyCountChallengeRanking({ dateKey, players: users, rootPath, playersPath: 'users' });
  return { ...result, totalTodayZabad: result.totalCount };
}

function buildGharsChallengeDailyRanking(dateKey, users, rootPath = GHARS_CHALLENGE_ROOT) {
  const result = buildDailyCountChallengeRanking({ dateKey, players: users, rootPath, playersPath: 'users' });
  return { ...result, totalTodayGhars: result.totalCount };
}

function buildQuranChallengeDailyRanking(dateKey, users, rootPath = QURAN_CHALLENGE_ROOT) {
  const ranking = buildDailyCountChallengeRanking({
    dateKey,
    players: users,
    rootPath,
    playersPath: 'users',
  });

  return {
    ...ranking,
    totalTodayQuran: ranking.totalCount,
  };
}

function buildAlfHasanaChallengeDailyRanking(dateKey, users, rootPath = ALF_HASANA_CHALLENGE_ROOT) {
  const ranking = buildDailyCountChallengeRanking({
    dateKey,
    players: users,
    rootPath,
    playersPath: 'users',
  });

  return {
    ...ranking,
    totalTodayAlfHasana: ranking.totalCount,
  };
}

// Per-challenge participant-node layout. The daily count challenges store their
// participants under different child paths, and baqiyat keeps each player's
// metadata (uid/countryCode/nickname) directly on the child while the others nest
// it under a `.data` object. `build` is the ranking function that sorts the
// normalized participants into competition order.
const CHALLENGE_PARTICIPANT_CONFIG = {
  [DHIKR_CHALLENGE_ROOT]:     { playersPath: 'users',   nested: true,  build: buildDhikrChallengeDailyRanking },
  [BAQIYAT_CHALLENGE_ROOT]:   { playersPath: 'players', nested: false, build: buildBaqiyatChallengeDailyRanking },
  [ISTIGHFAR_CHALLENGE_ROOT]: { playersPath: 'users',   nested: true,  build: buildIstighfarChallengeDailyRanking },
  [ZABAD_CHALLENGE_ROOT]:     { playersPath: 'users',   nested: true,  build: buildZabadChallengeDailyRanking },
  [GHARS_CHALLENGE_ROOT]:     { playersPath: 'users',   nested: true,  build: buildGharsChallengeDailyRanking },
  [QURAN_CHALLENGE_ROOT]:     { playersPath: 'users',   nested: true,  build: buildQuranChallengeDailyRanking },
  [ALBAQARA_CHALLENGE_ROOT]:  { playersPath: 'users',   nested: true,  build: buildAlBaqaraChallengeDailyRanking },
  [ALF_HASANA_CHALLENGE_ROOT]: { playersPath: 'users',   nested: true,  build: buildAlfHasanaChallengeDailyRanking },
};

// Reads a daily count-challenge's raw participant node and returns the users
// sorted into competition rank order (highest count first, uid ascending on ties),
// each carrying a 1-based `rank`. This recomputes the ranking from the live
// per-user counts rather than trusting the periodically-written `leaderboard`
// node, so winner selection reflects the final counts even when the leaderboard
// cron has not run since the day's last taps.
async function readChallengeRankedUsers(db, rootPath, dateKey) {
  const config = CHALLENGE_PARTICIPANT_CONFIG[rootPath];
  if (!config) throw new Error(`Unknown challenge root: ${rootPath}`);

  const snap = await db.ref(`${rootPath}/${dateKey}/${config.playersPath}`).get();
  const participants = [];
  if (snap.exists()) {
    snap.forEach(child => {
      const data = child.val() || {};
      const metadata = config.nested ? (data.data || {}) : data;
      const uid = typeof metadata.uid === 'string' && metadata.uid.length > 0
        ? metadata.uid
        : child.key;
      participants.push({
        uid,
        count: data.count,
        countryCode: typeof metadata.countryCode === 'string' ? metadata.countryCode.toUpperCase() : '',
        nickname: typeof metadata.nickname === 'string' ? metadata.nickname.trim() : '',
      });
    });
  }

  return config.build(dateKey, participants).rankedUsers;
}

// Awards a daily challenge's podium medals: gold to rank 1, silver to rank 2,
// bronze to rank 3, mirroring the weekly mohamed_lovers medals. Cumulative counts
// live at {rootPath}/users/{uid}/medals (server-owned; clients read-only), a
// persistent sibling of the deleted-daily {dateKey} nodes. Ranking is recomputed
// from the live per-user counts (same source as the winner notifications), so it
// reflects the final end-of-day standings.
//
// Idempotent per Cairo day via a {rootPath}/{dateKey}/medalsAwarded marker: the
// marker lives on the day node, so it is deleted along with it by the daily
// aggregate-and-clean step — a normal re-run after cleanup finds no participants and
// awards nothing, while a re-run after a crash between award and cleanup sees the
// marker and skips. MUST run BEFORE the day node is deleted.
async function awardChallengeMedals(db, admin, rootPath, dateKey) {
  const markerRef = db.ref(`${rootPath}/${dateKey}/medalsAwarded`);
  const markerSnap = await markerRef.get();
  if (markerSnap.exists() && markerSnap.val() === true) {
    console.log(`[medals:${rootPath}] ${dateKey} already awarded — skip`);
    return;
  }

  const rankedUsers = await readChallengeRankedUsers(db, rootPath, dateKey);
  const podium = rankedUsers.slice(0, 3).filter(u => u && typeof u.uid === 'string' && u.uid.length > 0 && u.count > 0);
  if (podium.length === 0) {
    console.log(`[medals:${rootPath}] ${dateKey} no eligible podium — skip`);
    await markerRef.set(true);
    return;
  }

  const medalTypes = ['gold', 'silver', 'bronze'];
  const medalWrites = {};
  podium.forEach((user, i) => {
    medalWrites[`${rootPath}/users/${user.uid}/medals/${medalTypes[i]}`] =
      admin.database.ServerValue.increment(1);
  });
  await db.ref('/').update(medalWrites);
  await markerRef.set(true);
  console.log(
    `[medals:${rootPath}] ${dateKey} awarded to ${podium.length}: ${podium.map((u, i) => `${medalTypes[i]}=${u.uid}(${u.count})`).join(' ')}`,
  );
}

// Copies each leaderboard entry's cumulative medal counts (from the participant's
// {rootPath}/users/{uid}/medals node) onto the entry as goldMedals/silverMedals/
// bronzeMedals so the app can render 🥇/🥈/🥉 pills. Mutates the entry objects in
// place. `leaderboardEntries` is the array of [key, entry] pairs the populate step
// builds; each entry carries a `uid`. Mirrors attachMedals in populateMohamedLoversRound.
async function attachChallengeMedals(db, rootPath, leaderboardEntries) {
  const uids = [...new Set(
    leaderboardEntries.map(([, entry]) => entry && entry.uid).filter(uid => typeof uid === 'string' && uid.length > 0),
  )];
  if (uids.length === 0) return;

  const medalsByUid = {};
  await Promise.all(uids.map(async uid => {
    const snap = await db.ref(`${rootPath}/users/${uid}/medals`).get();
    const medals = snap.val();
    if (medals && typeof medals === 'object') medalsByUid[uid] = medals;
  }));

  for (const [, entry] of leaderboardEntries) {
    const medals = entry && medalsByUid[entry.uid];
    if (!medals) continue;
    if (typeof medals.gold === 'number' && medals.gold > 0) entry.goldMedals = medals.gold;
    if (typeof medals.silver === 'number' && medals.silver > 0) entry.silverMedals = medals.silver;
    if (typeof medals.bronze === 'number' && medals.bronze > 0) entry.bronzeMedals = medals.bronze;
  }
}

// Builds/writes the mohamed_lovers leaderboard + dailyLeaderboard + per-player ranks
// for a single round, and (when the round is not final) sends top-3/dropout/idle
// notifications. Shared by populate-leaderboard.js (periodic runs against whichever
// round is currently active) and aggregate-all-time.js (seeds the brand-new round
// right after closing the previous one, at the same 19:10 Cairo cron slot).
async function populateMohamedLoversRound(db, admin, roundKey, isFinal) {
  const playersRef = db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/players`);

  // Single ordered query — ascending by totalCount; we reverse for ranking.
  const allPlayersSnapshot = await playersRef.orderByChild('totalCount').get();

  if (!allPlayersSnapshot.exists()) {
    console.log(`[${roundKey}] No players found. Writing empty leaderboard.`);
    await Promise.all([
      db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/leaderboard`).set({ isFinal }),
      db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/roundTotal`).set(0),
      db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/roundPlayerCount`).set(0),
    ]);
    return { roundTotal: 0, roundPlayerCount: 0 };
  }

  // Build full list (Firebase returns ascending order from orderByChild).
  const allPlayers = [];
  let roundTotal = 0;
  allPlayersSnapshot.forEach(child => {
    const data = child.val();
    if (data && typeof data.uid === 'string' && typeof data.totalCount === 'number') {
      roundTotal += data.totalCount;
      allPlayers.push({
        uid: data.uid,
        score: data.totalCount,
        updatedAt: data.updatedAt || 0,
        countryCode: typeof data.countryCode === 'string' ? data.countryCode : 'NA',
        yesterdayTotalScore: typeof data.yesterdayTotalScore === 'number' ? data.yesterdayTotalScore : 0,
        todayCount: typeof data.todayCount === 'number' ? data.todayCount : null,
        scoreMasked: data.scoreMasked === true,
        isSupporter: data.isSupporter === true,
        dailyBadge: typeof data.dailyBadge === 'string' ? data.dailyBadge : null,
        roundStreak: typeof data.roundStreak === 'number' && data.roundStreak > 0 ? data.roundStreak : null,
        nickname: typeof data.nickname === 'string' ? data.nickname : '',
        currentRank: typeof data.rank === 'number' && data.rank > 0 ? data.rank : null,
      });
    }
  });

  // Sort descending to assign ranks (highest score = rank 1).
  allPlayers.sort((a, b) => b.score - a.score || b.updatedAt - a.updatedAt);

  const roundPlayerCount = allPlayers.length;

  // Write rank into each player node, then build top-10 leaderboard.
  // Diff against the rank already stored in RTDB (currentRank): only write ranks
  // that changed since the last run.
  const rankUpdates = {};
  allPlayers.forEach((player, i) => {
    const newRank = i + 1;
    if (newRank !== player.currentRank) {
      rankUpdates[`${MOHAMED_LOVERS_ROOT}/${roundKey}/players/${player.uid}/rank`] = newRank;
    }
  });

  const top10 = allPlayers.slice(0, 10);
  const leaderboard = { isFinal };
  top10.forEach((player, i) => {
    const entry = {
      rank: i + 1,
      uid: player.uid,
      score: player.score,
      countryCode: player.countryCode,
    };
    if (player.scoreMasked) entry.scoreMasked = true;
    if (player.isSupporter) entry.isSupporter = true;
    if (player.dailyBadge) entry.dailyBadge = player.dailyBadge;
    if (player.roundStreak) entry.roundStreak = player.roundStreak;
    if (player.nickname) entry.nickname = player.nickname;
    leaderboard[String(i + 1)] = entry;
  });

  // Daily leaderboard: rank by the client-published todayCount (falls back to the
  // yesterday-diff for clients that don't publish it yet).
  const dailyPlayers = allPlayers.map(p => ({
    ...p,
    dailyScore: computeTodayScore({ total: p.score, today: p.todayCount, yesterday: p.yesterdayTotalScore }),
  }));
  dailyPlayers.sort((a, b) => b.dailyScore - a.dailyScore || b.updatedAt - a.updatedAt);
  const dailyTop10 = dailyPlayers.slice(0, 10);
  const dailyLeaderboard = { isFinal };
  dailyTop10.forEach((player, i) => {
    const entry = {
      rank: i + 1,
      uid: player.uid,
      score: player.dailyScore,
      countryCode: player.countryCode,
    };
    if (player.scoreMasked) entry.scoreMasked = true;
    if (player.isSupporter) entry.isSupporter = true;
    if (player.dailyBadge) entry.dailyBadge = player.dailyBadge;
    if (player.roundStreak) entry.roundStreak = player.roundStreak;
    if (player.nickname) entry.nickname = player.nickname;
    dailyLeaderboard[String(i + 1)] = entry;
  });

  // Copy each leaderboard participant's medal counts (from their user node) onto the
  // entry so the app can render 🥇/🥈/🥉 pills. Medals live under users/{uid}/medals,
  // not on the player node, so they're fetched per-uid for the top-10 weekly + daily set.
  const medalUids = [...new Set([...top10, ...dailyTop10].map(p => p.uid))];
  const medalsByUid = {};
  await Promise.all(medalUids.map(async uid => {
    const snap = await db.ref(`${MOHAMED_LOVERS_ROOT}/users/${uid}/medals`).get();
    const medals = snap.val();
    if (medals && typeof medals === 'object') medalsByUid[uid] = medals;
  }));
  const attachMedals = (entry, uid) => {
    const medals = medalsByUid[uid];
    if (!medals) return;
    if (typeof medals.gold === 'number' && medals.gold > 0) entry.goldMedals = medals.gold;
    if (typeof medals.silver === 'number' && medals.silver > 0) entry.silverMedals = medals.silver;
    if (typeof medals.bronze === 'number' && medals.bronze > 0) entry.bronzeMedals = medals.bronze;
  };
  top10.forEach((player, i) => attachMedals(leaderboard[String(i + 1)], player.uid));
  dailyTop10.forEach((player, i) => attachMedals(dailyLeaderboard[String(i + 1)], player.uid));

  // Read old leaderboards before overwriting — used for rank-diff and drop-out detection.
  let droppedUids = [];
  const oldLbSnap = await db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/leaderboard`).get();
  const oldDailyLbSnap = await db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/dailyLeaderboard`).get();

  const oldRanks = buildOldRankMap(oldLbSnap);
  const oldDailyRanks = buildOldRankMap(oldDailyLbSnap);

  // Enrich weekly leaderboard entries with rankChange.
  top10.forEach((player, i) => {
    const rank = i + 1;
    leaderboard[String(rank)].rankChange = computeRankChange(player.uid, rank, oldRanks);
  });

  // Enrich daily leaderboard entries with rankChange.
  dailyTop10.forEach((player, i) => {
    const rank = i + 1;
    dailyLeaderboard[String(rank)].rankChange = computeRankChange(player.uid, rank, oldDailyRanks);
  });

  // Drop-out detection.
  if (!isFinal && oldLbSnap.exists()) {
    const oldTop10Uids = new Set(Object.values(oldRanks).length ? Object.keys(oldRanks) : []);
    const newTop10Uids = new Set(top10.map(p => p.uid));
    droppedUids = [...oldTop10Uids].filter(uid => !newTop10Uids.has(uid));
    console.log(`Drop-out detection: old=${oldTop10Uids.size} new=${newTop10Uids.size} dropped=${droppedUids.length}`);
  }

  await Promise.all([
    Object.keys(rankUpdates).length ? db.ref('/').update(rankUpdates) : Promise.resolve(),
    db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/leaderboard`).set(leaderboard),
    db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/dailyLeaderboard`).set(dailyLeaderboard),
    db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/roundTotal`).set(roundTotal),
    db.ref(`${MOHAMED_LOVERS_ROOT}/${roundKey}/roundPlayerCount`).set(roundPlayerCount),
  ]);
  console.log(`[${roundKey}] Wrote ${top10.length} leaderboard + ${dailyTop10.length} daily entries. roundTotal=${roundTotal} players=${roundPlayerCount}`);
  console.log(JSON.stringify(leaderboard, null, 2));

  // Phase 1: mirror to Firestore (non-blocking)
  await firestoreMirrorRound(admin.firestore(), roundKey, {
    leaderboard,
    dailyLeaderboard,
    roundTotal,
    roundPlayerCount,
    allPlayers,
  });

  // Top-3 change notifications — detect drops from top 3 and position losses.
  // Phase 1 migration note: FCM sends remain here (RTDB path only).
  // In Phase 2, move FCM to Firestore-based scripts and remove from here.
  if (!isFinal) {
    const top3Notifs = computeTop3Changes(oldRanks, top10);
    await sendTop3ChangeNotifications(db, admin, top3Notifs, MOHAMED_LOVERS_TOP3_MESSAGES, 'mohamed_lovers');
  }

  // Notify dropped-out users — once per round (debounced via lastDropOutNotifRound).
  if (droppedUids.length > 0) {
    console.log(`Notifying ${droppedUids.length} dropped user(s)...`);
    const fcmUpdates = {};
    const notifPromises = droppedUids.map(async uid => {
      const userSnap = await db.ref(`${MOHAMED_LOVERS_ROOT}/users/${uid}`).get();
      const user = userSnap.val();
      if (!user?.fcmToken) { console.log(`  uid=${uid}: no FCM token — skip`); return; }
      if (user.leaderboardNotifsEnabled === false) { console.log(`  uid=${uid}: leaderboard notifications disabled — skip`); return; }
      if (user.lastDropOutNotifRound === roundKey) { console.log(`  uid=${uid}: already notified this round — skip`); return; }
      fcmUpdates[`${MOHAMED_LOVERS_ROOT}/users/${uid}/lastDropOutNotifRound`] = roundKey;
      return admin.messaging().send({
        token: user.fcmToken,
        notification: { title: 'خرجت من قائمة الأوائل 😔', body: 'لقد سبقك المحبين في الصلاة علي النبي، عد وتنافس علي محبة النبي بذكره والصلاة عليه ﷺ' },
        data: { title: 'خرجت من قائمة الأوائل 😔', body: 'لقد سبقك المحبين في الصلاة علي النبي، عد وتنافس علي محبة النبي بذكره والصلاة عليه ﷺ' },
      })
        .then(msgId => console.log(`  uid=${uid}: sent dropout alert msgId=${msgId}`))
        .catch(e => console.error(`  uid=${uid}: send failed: ${e.message}`));
    });
    await Promise.all(notifPromises);
    if (Object.keys(fcmUpdates).length > 0) {
      await db.ref('/').update(fcmUpdates);
      console.log(`Wrote ${Object.keys(fcmUpdates).length} lastDropOutNotifRound flag(s)`);
    }
  }

  // --- Idle >8h notification segment ---
  const IDLE_THRESHOLD_MS = 8 * 60 * 60 * 1000; // 8 hours
  const nowMs = Date.now();
  const todayStr = cairoToday();

  if (!isFinal) {
    const idleCandidates = allPlayers.filter(p =>
      p.updatedAt && p.score > 0 && (nowMs - p.updatedAt) > IDLE_THRESHOLD_MS
    );
    console.log(`\nIdle >8h check: ${idleCandidates.length} candidate(s) of ${allPlayers.length} total`);

    if (idleCandidates.length > 0) {
      const idleUpdates = {};
      const idlePromises = idleCandidates.map(async p => {
        const userSnap = await db.ref(`${MOHAMED_LOVERS_ROOT}/users/${p.uid}`).get();
        const user = userSnap.val();
        if (!user?.fcmToken) { console.log(`  idle uid=${p.uid}: no FCM token — skip`); return; }
        if (user.leaderboardNotifsEnabled === false) { console.log(`  idle uid=${p.uid}: leaderboard notifications disabled — skip`); return; }
        if (user.lastIdleNotifDate === todayStr) { console.log(`  idle uid=${p.uid}: already notified today — skip`); return; }
        idleUpdates[`${MOHAMED_LOVERS_ROOT}/users/${p.uid}/lastIdleNotifDate`] = todayStr;
        return admin.messaging().send({
          token: user.fcmToken,
          notification: {
            title: 'أين صلاتك على النبي ﷺ؟',
            body: 'الحبيب لا يغفل عن ذكر محبوبه، فاين انت من ذكر الحبيب المصطفي ﷺ',
          },
          data: {
            title: 'أين صلاتك على النبي ﷺ؟',
            body: 'الحبيب لا يغفل عن ذكر محبوبه، فاين انت من ذكر الحبيب المصطفي ﷺ',
          },
        })
          .then(msgId => console.log(`  idle uid=${p.uid}: sent msgId=${msgId}`))
          .catch(e => console.error(`  idle uid=${p.uid}: send failed: ${e.message}`));
      });
      await Promise.all(idlePromises);
      if (Object.keys(idleUpdates).length > 0) {
        await db.ref('/').update(idleUpdates);
        console.log(`Wrote ${Object.keys(idleUpdates).length} lastIdleNotifDate flag(s)`);
      }
    }
  } else {
    console.log('\nRound is final — skipping idle notifications');
  }

  return { roundTotal, roundPlayerCount };
}

module.exports = {
  DHIKR_CHALLENGE_ROOT,
  BAQIYAT_CHALLENGE_ROOT,
  ISTIGHFAR_CHALLENGE_ROOT,
  ZABAD_CHALLENGE_ROOT,
  GHARS_CHALLENGE_ROOT,
  QURAN_CHALLENGE_ROOT,
  ALBAQARA_CHALLENGE_ROOT,
  ALF_HASANA_CHALLENGE_ROOT,
  MOHAMED_LOVERS_ROOT,
  ABNORMAL_DAILY_THRESHOLD,
  PACE_DAILY_INCREMENT,
  computeTodayScore,
  buildDailyScoreSnapshots,
  roundDayNumber,
  buildOldRankMap,
  computeRankChange,
  computeTop3Changes,
  sendTop3ChangeNotifications,
  MOHAMED_LOVERS_TOP3_MESSAGES,
  CHALLENGE_TOP3_MESSAGES,
  normalizeDhikrCount,
  buildDailyCountChallengeRanking,
  buildBaqiyatChallengeDailyRanking,
  buildIstighfarChallengeDailyRanking,
  buildZabadChallengeDailyRanking,
  buildGharsChallengeDailyRanking,
  buildQuranChallengeDailyRanking,
  buildAlBaqaraChallengeDailyRanking,
  buildAlfHasanaChallengeDailyRanking,
  readChallengeRankedUsers,
  awardChallengeMedals,
  attachChallengeMedals,
  cairoToday,
  addDaysToDateKey,
  populateMohamedLoversRound,
  buildDhikrChallengeDailyRanking,
};
