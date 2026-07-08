// Reads top-10 players for the active round from Firebase RTDB and writes
// them to the leaderboard node. Dispatched every ~30 min; detects isFinal automatically.
const admin = require('firebase-admin');
const {
  BAQIYAT_CHALLENGE_ROOT,
  DHIKR_CHALLENGE_ROOT,
  ISTIGHFAR_CHALLENGE_ROOT,
  buildBaqiyatChallengeDailyRanking,
  buildDhikrChallengeDailyRanking,
  buildIstighfarChallengeDailyRanking,
  buildOldRankMap,
  computeRankChange,
  cairoToday,
  populateMohamedLoversRound,
} = require('./leaderboard-utils');
const {
  mirrorDhikrChallenge,
  mirrorBaqiyatChallenge,
  mirrorIstighfarChallenge,
} = require('./firestore-utils');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const explicitRoundKey = process.env.ROUND_KEY || '';

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Mirrors CompetitionWindowUtils.kt: roundKey = date of next Friday 19:00 Cairo.
// If now IS past that boundary, round already closed — advance to following Friday.
function cairoRoundKey() {
  const now = new Date();
  const zone = 'Africa/Cairo';

  const weekdayStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(now);
  const dayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const jsDow = dayMap[weekdayStr];

  const hourStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(now);
  const cairoHour = parseInt(hourStr, 10);

  let daysToFriday = (5 - jsDow + 7) % 7;
  // If today is Friday and round has already closed (>= 19:00 Cairo), next round is 7 days away
  if (daysToFriday === 0 && cairoHour >= 19) daysToFriday = 7;

  const fridayDate = new Date(now.getTime() + daysToFriday * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone,
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
}

// Round is final when we are at or past Friday 19:00 Cairo for that roundKey date.
function isRoundFinal(roundKey) {
  const now = new Date();
  const zone = 'Africa/Cairo';
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: zone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', hour12: false,
  });
  const parts = Object.fromEntries(fmt.formatToParts(now).map(p => [p.type, p.value]));
  const cairoDate = `${parts.year}-${parts.month}-${parts.day}`;
  const cairoHour = parseInt(parts.hour, 10);
  if (cairoDate > roundKey) return true;
  if (cairoDate === roundKey && cairoHour >= 19) return true;
  return false;
}

// Delayed new-build notification: deploy.yml writes a scheduledBuildNotification
// node (with firesAt) after a production release; we broadcast once that time
// has passed, then delete the node so it fires exactly once.
async function sendDueBuildNotification(db) {
  const ref = db.ref('mohamed_lovers/scheduledBuildNotification');
  const snap = await ref.get();
  if (!snap.exists()) return;

  const { version = '', firesAt = 0 } = snap.val() || {};
  const now = Date.now();
  if (typeof firesAt !== 'number' || now < firesAt) {
    console.log(`[build-notif] scheduled v${version} not due yet (fires at ${new Date(firesAt).toISOString()}) — skipping`);
    return;
  }

  const title = 'تحديث جديد قادم 🎉';
  const body = version
    ? `الإصدار ${version} في الطريق إليك — قد يستغرق ظهوره بعض الوقت، تحقق من المتجر قريباً`
    : 'إصدار جديد من التطبيق في الطريق إليك — قد يستغرق ظهوره بعض الوقت، تحقق من المتجر قريباً';

  // Single fan-out via the "general" FCM topic — both Android and iOS subscribe
  // on launch once notifications are granted, so one send reaches everyone
  // (no need to read users or loop over per-device tokens).
  try {
    const msgId = await admin.messaging().send({
      topic: 'general',
      notification: { title, body },
      data: { title, body, notification_type: 'version_update', new_version: version },
    });
    console.log(`[build-notif] v${version} broadcast to topic "general" msgId=${msgId}`);
  } catch (e) {
    // Leave the node in place so the next run retries instead of silently dropping it.
    console.error(`[build-notif] topic send failed: ${e.message} — leaving node for retry`);
    return;
  }

  await ref.remove();
  console.log('[build-notif] done — node cleared');
}

async function populateDhikrChallengeToday(db) {
  const dateKey = cairoToday();
  console.log(`\n--- Dhikr Challenge [${dateKey}] ---`);

  const usersSnap = await db.ref(`${DHIKR_CHALLENGE_ROOT}/${dateKey}/users`).get();
  const users = [];

  if (usersSnap.exists()) {
    usersSnap.forEach(child => {
      const data = child.val() || {};
      const metadata = data.data || {};
      const uid = typeof metadata.uid === 'string' && metadata.uid.length > 0
        ? metadata.uid
        : child.key;
      const currentRank = typeof data.rank === 'number' && data.rank > 0 ? data.rank : null;
      const countryCode = typeof metadata.countryCode === 'string' ? metadata.countryCode.toUpperCase() : '';
      const nickname = typeof metadata.nickname === 'string' ? metadata.nickname.trim() : '';
      users.push({ uid, count: data.count, countryCode, nickname, currentRank });
    });
  }

  const dailyRanking = buildDhikrChallengeDailyRanking(dateKey, users);

  // Build top-10 leaderboard with rank change vs. the rank already stored in Firebase.
  const leaderboardEntries = dailyRanking.rankedUsers.slice(0, 10).map((user, i) => {
    let rankChange = 'same';
    if (user.currentRank == null || user.currentRank === 0) {
      rankChange = 'new';
    } else if (user.rank < user.currentRank) {
      rankChange = 'up';
    } else if (user.rank > user.currentRank) {
      rankChange = 'down';
    }
    const entry = { uid: user.uid, countryCode: user.countryCode, count: user.count, rank: user.rank, rankChange };
    if (user.nickname) entry.nickname = user.nickname;
    return [String(i), entry];
  });

  const updates = {
    ...dailyRanking.rankUpdates,
    [`${DHIKR_CHALLENGE_ROOT}/${dateKey}/participantCount`]: dailyRanking.participantCount,
    [`${DHIKR_CHALLENGE_ROOT}/${dateKey}/totalTodayDhikr`]: dailyRanking.totalTodayDhikr,
    [`${DHIKR_CHALLENGE_ROOT}/${dateKey}/lastRankedAt`]: admin.database.ServerValue.TIMESTAMP,
    [`${DHIKR_CHALLENGE_ROOT}/${dateKey}/leaderboard`]: Object.fromEntries(leaderboardEntries),
  };

  await db.ref('/').update(updates);
  console.log(
    `Wrote dhikr ranks + leaderboard(${leaderboardEntries.length}) for ${dailyRanking.participantCount} participant(s). totalTodayDhikr=${dailyRanking.totalTodayDhikr}`,
  );

  // Phase 1: mirror to Firestore
  await mirrorDhikrChallenge(admin.firestore(), dateKey, {
    rankedUsers: dailyRanking.rankedUsers,
    participantCount: dailyRanking.participantCount,
    totalTodayDhikr: dailyRanking.totalTodayDhikr,
    leaderboardEntries,
  });
}

async function populateBaqiyatChallengeToday(db) {
  const dateKey = cairoToday();
  console.log(`\n--- Baqiyat Challenge [${dateKey}] ---`);

  const playersSnap = await db.ref(`${BAQIYAT_CHALLENGE_ROOT}/${dateKey}/players`).get();
  const players = [];

  if (playersSnap.exists()) {
    playersSnap.forEach(child => {
      const data = child.val() || {};
      const uid = typeof data.uid === 'string' && data.uid.length > 0
        ? data.uid
        : child.key;
      players.push({
        uid,
        count: data.count,
        countryCode: typeof data.countryCode === 'string' ? data.countryCode.toUpperCase() : '',
        nickname: typeof data.nickname === 'string' ? data.nickname.trim() : '',
        currentRank: typeof data.rank === 'number' && data.rank > 0 ? data.rank : null,
      });
    });
  }

  const oldLbSnap = await db.ref(`${BAQIYAT_CHALLENGE_ROOT}/${dateKey}/leaderboard`).get();
  const oldRanks = buildOldRankMap(oldLbSnap);
  const dailyRanking = buildBaqiyatChallengeDailyRanking(dateKey, players);

  const leaderboardEntries = dailyRanking.rankedUsers.slice(0, 10).map((user, i) => {
    const entry = {
      uid: user.uid,
      countryCode: user.countryCode,
      count: user.count,
      rank: user.rank,
      rankChange: computeRankChange(user.uid, user.rank, oldRanks),
    };
    if (user.nickname) entry.nickname = user.nickname;
    return [String(i), entry];
  });

  const updates = {
    ...dailyRanking.rankUpdates,
    [`${BAQIYAT_CHALLENGE_ROOT}/${dateKey}/participantCount`]: dailyRanking.participantCount,
    [`${BAQIYAT_CHALLENGE_ROOT}/${dateKey}/totalTodayBaqiyat`]: dailyRanking.totalTodayBaqiyat,
    [`${BAQIYAT_CHALLENGE_ROOT}/${dateKey}/lastRankedAt`]: admin.database.ServerValue.TIMESTAMP,
    [`${BAQIYAT_CHALLENGE_ROOT}/${dateKey}/leaderboard`]: Object.fromEntries(leaderboardEntries),
  };

  await db.ref('/').update(updates);
  console.log(
    `Wrote baqiyat ranks + leaderboard(${leaderboardEntries.length}) for ${dailyRanking.participantCount} participant(s). totalTodayBaqiyat=${dailyRanking.totalTodayBaqiyat}`,
  );

  // Phase 1: mirror to Firestore
  await mirrorBaqiyatChallenge(admin.firestore(), dateKey, {
    rankedUsers: dailyRanking.rankedUsers,
    participantCount: dailyRanking.participantCount,
    totalTodayBaqiyat: dailyRanking.totalTodayBaqiyat,
    leaderboardEntries,
  });
}

async function populateIstighfarChallengeToday(db) {
  const dateKey = cairoToday();
  console.log(`\n--- Istighfar Challenge [${dateKey}] ---`);

  const usersSnap = await db.ref(`${ISTIGHFAR_CHALLENGE_ROOT}/${dateKey}/users`).get();
  const users = [];

  if (usersSnap.exists()) {
    usersSnap.forEach(child => {
      const data = child.val() || {};
      const metadata = data.data || {};
      const uid = typeof metadata.uid === 'string' && metadata.uid.length > 0
        ? metadata.uid
        : child.key;
      const currentRank = typeof data.rank === 'number' && data.rank > 0 ? data.rank : null;
      const countryCode = typeof metadata.countryCode === 'string' ? metadata.countryCode.toUpperCase() : '';
      const nickname = typeof metadata.nickname === 'string' ? metadata.nickname.trim() : '';
      users.push({ uid, count: data.count, countryCode, nickname, currentRank });
    });
  }

  const oldLbSnap = await db.ref(`${ISTIGHFAR_CHALLENGE_ROOT}/${dateKey}/leaderboard`).get();
  const oldRanks = buildOldRankMap(oldLbSnap);
  const dailyRanking = buildIstighfarChallengeDailyRanking(dateKey, users);

  const leaderboardEntries = dailyRanking.rankedUsers.slice(0, 10).map((user, i) => {
    const entry = {
      uid: user.uid,
      countryCode: user.countryCode,
      count: user.count,
      rank: user.rank,
      rankChange: computeRankChange(user.uid, user.rank, oldRanks),
    };
    if (user.nickname) entry.nickname = user.nickname;
    return [String(i), entry];
  });

  const updates = {
    ...dailyRanking.rankUpdates,
    [`${ISTIGHFAR_CHALLENGE_ROOT}/${dateKey}/participantCount`]: dailyRanking.participantCount,
    [`${ISTIGHFAR_CHALLENGE_ROOT}/${dateKey}/totalTodayIstighfar`]: dailyRanking.totalTodayIstighfar,
    [`${ISTIGHFAR_CHALLENGE_ROOT}/${dateKey}/lastRankedAt`]: admin.database.ServerValue.TIMESTAMP,
    [`${ISTIGHFAR_CHALLENGE_ROOT}/${dateKey}/leaderboard`]: Object.fromEntries(leaderboardEntries),
  };

  await db.ref('/').update(updates);
  console.log(
    `Wrote istighfar ranks + leaderboard(${leaderboardEntries.length}) for ${dailyRanking.participantCount} participant(s). totalTodayIstighfar=${dailyRanking.totalTodayIstighfar}`,
  );

  // Phase 1: mirror to Firestore
  await mirrorIstighfarChallenge(admin.firestore(), dateKey, {
    rankedUsers: dailyRanking.rankedUsers,
    participantCount: dailyRanking.participantCount,
    totalTodayIstighfar: dailyRanking.totalTodayIstighfar,
    leaderboardEntries,
  });
}

async function main() {
  const roundKey = explicitRoundKey || cairoRoundKey();
  const isFinal = isRoundFinal(roundKey);
  console.log(`Round key: ${roundKey} | isFinal: ${isFinal}`);

  const db = admin.database();

  // Deliver any delayed new-build notification whose scheduled time has passed.
  await sendDueBuildNotification(db);

  // Rank today's standalone challenge users.
  await populateDhikrChallengeToday(db);
  await populateBaqiyatChallengeToday(db);
  await populateIstighfarChallengeToday(db);

  await populateMohamedLoversRound(db, admin, roundKey, isFinal);

  // --- Ten Days of Dhul Hijjah leaderboard ---
  // await populateTenDaysLeaderboard(db);

  process.exit(0);
}

// function isTenDaysPeriodActive(periodKey) {
//   const zone = 'Africa/Cairo';
//   const today = new Intl.DateTimeFormat('en-CA', {
//     timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
//   }).format(new Date());
//   const start = new Date(periodKey + 'T00:00:00');
//   const endDate = new Date(start.getTime() + 9 * 86400000);
//   const end = new Intl.DateTimeFormat('en-CA', {
//     timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
//   }).format(endDate);
//   return today >= periodKey && today < end;
// }

// async function populateTenDaysLeaderboard(db) {
//   const root = 'ten_days_dhul_hijjah';
//   const periodKeys = ['2026-05-18'];
//
//   for (const periodKey of periodKeys) {
//     const active = isTenDaysPeriodActive(periodKey);
//     console.log(`\n--- Ten Days Leaderboard [${periodKey}] active=${active} ---`);
//
//     const playersSnap = await db.ref(`${root}/${periodKey}/players`).orderByChild('totalScore').get();
//     if (!playersSnap.exists()) {
//       console.log('No ten-days players found.');
//       await Promise.all([
//         db.ref(`${root}/${periodKey}/leaderboard`).set({}),
//         db.ref(`${root}/${periodKey}/playerCount`).set(0),
//       ]);
//       continue;
//     }
//
//     const allPlayers = [];
//     playersSnap.forEach(child => {
//       const data = child.val();
//       if (data && typeof data.uid === 'string' && typeof data.totalScore === 'number') {
//         allPlayers.push({
//           uid: data.uid,
//           totalScore: data.totalScore,
//           updatedAt: data.updatedAt || 0,
//           countryCode: typeof data.countryCode === 'string' ? data.countryCode : '',
//         });
//       }
//     });
//
//     allPlayers.sort((a, b) => b.totalScore - a.totalScore || b.updatedAt - a.updatedAt);
//
//     const top10 = allPlayers.slice(0, 10);
//
//     // Read old leaderboard for rank-diff and drop-out detection.
//     const oldLbSnap = await db.ref(`${root}/${periodKey}/leaderboard`).get();
//     const oldRanks = buildOldRankMap(oldLbSnap);
//
//     const leaderboard = {};
//     top10.forEach((player, i) => {
//       const rank = i + 1;
//       leaderboard[String(rank)] = {
//         rank,
//         uid: player.uid,
//         totalScore: player.totalScore,
//         countryCode: player.countryCode,
//         rankChange: computeRankChange(player.uid, rank, oldRanks),
//       };
//     });
//
//     const rankUpdates = {};
//     allPlayers.forEach((player, i) => {
//       rankUpdates[`${root}/${periodKey}/players/${player.uid}/rank`] = i + 1;
//     });
//
//     await Promise.all([
//       db.ref('/').update(rankUpdates),
//       db.ref(`${root}/${periodKey}/leaderboard`).set(leaderboard),
//       db.ref(`${root}/${periodKey}/playerCount`).set(allPlayers.length),
//     ]);
//     console.log(`Wrote ${top10.length} ten-days leaderboard entries (${allPlayers.length} total players).`);
//
//     // Notifications only when period is active.
//     if (!active) continue;
//
//     // Top-3 change notifications.
//     const top3Notifs = [];
//     for (const [uid, oldRank] of Object.entries(oldRanks)) {
//       if (oldRank > 3) continue;
//       const newEntry = top10.find(p => p.uid === uid);
//       const newRank = newEntry ? top10.indexOf(newEntry) + 1 : null;
//       if (newRank == null || newRank > 3) {
//         top3Notifs.push({ uid, event: 'dropped', oldRank, newRank });
//       } else if (newRank > oldRank) {
//         top3Notifs.push({ uid, event: 'lost_position', oldRank, newRank });
//       }
//     }
//
//     if (top3Notifs.length > 0) {
//       console.log(`Ten-days top-3 changes: ${top3Notifs.length} notification(s)`);
//       const top3Messages = {
//         dropped: {
//           title: 'مكانك بين المتسابقين يناديك 🤍',
//           body: 'كنت من أعلى المتنافسين في عشر ذي الحجة — لا تتوقف، فالعمل الصالح في هذه الأيام أحب إلى الله!',
//         },
//         lost_position: {
//           title: 'المنافسة تشتد في العشر 🔥',
//           body: 'تراجع ترتيبك في عشر ذي الحجة — زِد من عملك الصالح وارتقِ!',
//         },
//       };
//       const top3Promises = top3Notifs.map(async ({ uid, event }) => {
//         const userSnap = await db.ref(`mohamed_lovers/users/${uid}`).get();
//         const user = userSnap.val();
//         if (!user?.fcmToken) { console.log(`  ten-days top3 uid=${uid}: no FCM token — skip`); return; }
//         if (user.leaderboardNotifsEnabled === false) { console.log(`  ten-days top3 uid=${uid}: leaderboard notifications disabled — skip`); return; }
//         const msg = top3Messages[event];
//         return admin.messaging().send({
//           token: user.fcmToken,
//           notification: { title: msg.title, body: msg.body },
//           data: { title: msg.title, body: msg.body },
//         })
//           .then(msgId => console.log(`  ten-days top3 uid=${uid} (${event}): sent msgId=${msgId}`))
//           .catch(e => console.error(`  ten-days top3 uid=${uid} (${event}): send failed: ${e.message}`));
//       });
//       await Promise.all(top3Promises);
//     }
//
//     // Drop-out detection: users who were in top 10 but no longer.
//     let droppedUids = [];
//     if (oldLbSnap.exists()) {
//       const oldTop10Uids = new Set(Object.keys(oldRanks));
//       const newTop10Uids = new Set(top10.map(p => p.uid));
//       droppedUids = [...oldTop10Uids].filter(uid => !newTop10Uids.has(uid));
//       console.log(`Ten-days drop-out: old=${oldTop10Uids.size} new=${newTop10Uids.size} dropped=${droppedUids.length}`);
//     }
//
//     if (droppedUids.length > 0) {
//       console.log(`Notifying ${droppedUids.length} ten-days dropped user(s)...`);
//       const notifPromises = droppedUids.map(async uid => {
//         const userSnap = await db.ref(`mohamed_lovers/users/${uid}`).get();
//         const user = userSnap.val();
//         if (!user?.fcmToken) { console.log(`  ten-days uid=${uid}: no FCM token — skip`); return; }
//         if (user.leaderboardNotifsEnabled === false) { console.log(`  ten-days uid=${uid}: leaderboard notifications disabled — skip`); return; }
//         return admin.messaging().send({
//           token: user.fcmToken,
//           notification: { title: 'خرجت من قائمة العشر الأوائل 😔', body: 'مكانك في عشر ذي الحجة يستحق المنافسة — عُد وزِد من عملك الصالح!' },
//           data: { title: 'خرجت من قائمة العشر الأوائل 😔', body: 'مكانك في عشر ذي الحجة يستحق المنافسة — عُد وزِد من عملك الصالح!' },
//         })
//           .then(msgId => console.log(`  ten-days uid=${uid}: sent dropout alert msgId=${msgId}`))
//           .catch(e => console.error(`  ten-days uid=${uid}: send failed: ${e.message}`));
//       });
//       await Promise.all(notifPromises);
//     }
//   }
// }

main().catch(err => { console.error(err); process.exit(1); });
