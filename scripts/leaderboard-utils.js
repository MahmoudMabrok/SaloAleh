const { mirrorMohamedLoversRound: firestoreMirrorRound } = require('./firestore-utils');

const DHIKR_CHALLENGE_ROOT = '100_challenge';
const BAQIYAT_CHALLENGE_ROOT = 'baqiyat_saliha';
const ISTIGHFAR_CHALLENGE_ROOT = 'istighfar_challenge';
const ZABAD_CHALLENGE_ROOT = 'zabad_challenge';
const QURAN_CHALLENGE_ROOT = 'quran_challenge';
const MOHAMED_LOVERS_ROOT = 'mohamed_lovers';

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
      currentRank: typeof user.currentRank === 'number' && user.currentRank > 0 ? user.currentRank : null,
    }))
    .filter(user => user.uid.length > 0);

  const activeUsers = normalizedPlayers
    .filter(user => user.count > 0)
    .sort((a, b) => b.count - a.count || compareUidAsc(a, b));

  const rankUpdates = {};
  const activeUids = new Set(activeUsers.map(user => user.uid));

  normalizedPlayers.forEach(user => {
    if (!activeUids.has(user.uid)) {
      rankUpdates[`${rootPath}/${dateKey}/${playersPath}/${user.uid}/rank`] = null;
    }
  });

  const rankedUsers = activeUsers.map((user, index) => ({
    ...user,
    rank: index + 1,
  }));

  rankedUsers.forEach(user => {
    rankUpdates[`${rootPath}/${dateKey}/${playersPath}/${user.uid}/rank`] = user.rank;
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

function buildZabadChallengeDailyRanking(dateKey, users, rootPath = ZABAD_CHALLENGE_ROOT) {
  const result = buildDailyCountChallengeRanking({ dateKey, players: users, rootPath, playersPath: 'users' });
  return { ...result, totalTodayZabad: result.totalCount };
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
        scoreMasked: data.scoreMasked === true,
        isSupporter: data.isSupporter === true,
        dailyBadge: typeof data.dailyBadge === 'string' ? data.dailyBadge : null,
        roundStreak: typeof data.roundStreak === 'number' && data.roundStreak > 0 ? data.roundStreak : null,
        nickname: typeof data.nickname === 'string' ? data.nickname : '',
      });
    }
  });

  // Sort descending to assign ranks (highest score = rank 1).
  allPlayers.sort((a, b) => b.score - a.score || b.updatedAt - a.updatedAt);

  const roundPlayerCount = allPlayers.length;

  // Write rank into each player node, then build top-10 leaderboard.
  const rankUpdates = {};
  allPlayers.forEach((player, i) => {
    rankUpdates[`${MOHAMED_LOVERS_ROOT}/${roundKey}/players/${player.uid}/rank`] = i + 1;
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

  // Daily leaderboard: rank by todayScore = totalCount - yesterdayTotalScore.
  const dailyPlayers = allPlayers.map(p => ({
    ...p,
    dailyScore: Math.max(0, p.score - (p.yesterdayTotalScore || 0)),
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
    db.ref('/').update(rankUpdates),
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
    const top3Notifs = [];

    // Players who were in top 3 but dropped out (now rank 4+ or gone).
    for (const [uid, oldRank] of Object.entries(oldRanks)) {
      if (oldRank > 3) continue;
      const newEntry = top10.find(p => p.uid === uid);
      const newRank = newEntry ? top10.indexOf(newEntry) + 1 : null;
      if (newRank == null || newRank > 3) {
        top3Notifs.push({ uid, event: 'dropped', oldRank, newRank });
      } else if (newRank > oldRank) {
        top3Notifs.push({ uid, event: 'lost_position', oldRank, newRank });
      }
    }

    if (top3Notifs.length > 0) {
      console.log(`Top-3 changes: ${top3Notifs.length} notification(s) to send`);
      const top3Messages = {
        dropped: {
          title: 'مكانك بين المحبين يناديك 🤍',
          body: 'كنت من أكثر المصلّين على النبي ﷺ — لا تتوقف، فالصلاة عليه نور وشفاعة يوم القيامة!',
        },
        lost_position: {
          title: 'المنافسة تشتد بين المحبين 🔥',
          body: 'تراجع ترتيبك بين أكثر المصلّين على النبي ﷺ — زِد صلواتك وارتقِ، فأقربكم مني مجلسًا أكثركم صلاةً عليّ!',
        },
      };
      const top3Promises = top3Notifs.map(async ({ uid, event }) => {
        const userSnap = await db.ref(`${MOHAMED_LOVERS_ROOT}/users/${uid}`).get();
        const user = userSnap.val();
        if (!user?.fcmToken) { console.log(`  top3 uid=${uid}: no FCM token — skip`); return; }
        if (user.leaderboardNotifsEnabled === false) { console.log(`  top3 uid=${uid}: leaderboard notifications disabled — skip`); return; }
        const msg = top3Messages[event];
        return admin.messaging().send({
          token: user.fcmToken,
          notification: { title: msg.title, body: msg.body },
          data: { title: msg.title, body: msg.body },
        })
          .then(msgId => console.log(`  top3 uid=${uid} (${event}): sent msgId=${msgId}`))
          .catch(e => console.error(`  top3 uid=${uid} (${event}): send failed: ${e.message}`));
      });
      await Promise.all(top3Promises);
    }
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
  QURAN_CHALLENGE_ROOT,
  MOHAMED_LOVERS_ROOT,
  buildOldRankMap,
  computeRankChange,
  normalizeDhikrCount,
  buildDailyCountChallengeRanking,
  buildBaqiyatChallengeDailyRanking,
  buildIstighfarChallengeDailyRanking,
  buildZabadChallengeDailyRanking,
  buildQuranChallengeDailyRanking,
  cairoToday,
  addDaysToDateKey,
  populateMohamedLoversRound,
  buildDhikrChallengeDailyRanking,
};
