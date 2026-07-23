// Firestore dual-write utilities for Phase 1 migration.
// All functions mirror RTDB writes to Firestore. Failures are logged but do not
// block the main RTDB path — Firestore is not yet the source of truth.

const ROUNDS_COLLECTION = 'mohamed_lovers_rounds';
const USERS_COLLECTION = 'mohamed_lovers_users';
const META_COLLECTION = 'mohamed_lovers_meta';
const DHIKR_COLLECTION = 'dhikr_challenge';
const BAQIYAT_COLLECTION = 'baqiyat_challenge';
const ISTIGHFAR_COLLECTION = 'istighfar_challenge';
const ZABAD_COLLECTION = 'zabad_challenge';
const GHARS_COLLECTION = 'ghars_challenge';
const QURAN_COLLECTION = 'quran_challenge';
const ALBAQARA_COLLECTION = 'albaqara_challenge';
const TEN_DAYS_COLLECTION = 'ten_days';

// A quota-stalled Firestore commit retries RESOURCE_EXHAUSTED for its full
// ~10-minute gRPC budget (there is no public knob to shorten it), which blocks
// the cron's main() from ever reaching its process.exit(0). The dual-write is
// non-critical (RTDB is the source of truth), so every mirror is time-boxed:
// if it overruns we log and move on, and the script's process.exit(0) then
// abandons the dangling retry. Never rejects — a failed/slow mirror must not
// crash the caller (mirrors are fire-and-forget).
const MIRROR_TIMEOUT_MS = 60000;

function timeBoxMirror(promise, label, ms = MIRROR_TIMEOUT_MS) {
  let timer;
  const timeout = new Promise((resolve) => {
    timer = setTimeout(() => {
      console.error(
        `[firestore-mirror] ${label} timed out after ${ms}ms — skipped (RTDB unaffected)`,
      );
      resolve();
    }, ms);
    if (timer.unref) timer.unref();
  });
  const guarded = Promise.resolve(promise).catch((e) => {
    console.error(`[firestore-mirror] ${label} error: ${e && e.message}`);
  });
  return Promise.race([guarded, timeout]).finally(() => clearTimeout(timer));
}

// TEMP kill-switch — Firestore dual-write is disabled while the project is over
// the Spark free-tier write quota. RTDB stays the source of truth and nothing
// reads Firestore in Phase 1, so every mirror becoming a no-op is safe. Flip back
// to `true` to re-enable the dual-write.
const MIRROR_ENABLED = false;

// Wraps each mirror export so a stalled Firestore op can never block its caller.
// While MIRROR_ENABLED is false, every mirror export is a no-op (see kill-switch).
function timeBoxAll(mirrors) {
  const wrapped = {};
  for (const [name, fn] of Object.entries(mirrors)) {
    wrapped[name] = MIRROR_ENABLED
      ? (...args) => timeBoxMirror(fn(...args), name)
      : async () => {};
  }
  return wrapped;
}

if (!MIRROR_ENABLED) {
  console.log('[firestore-mirror] DISABLED via kill-switch — all mirrors are no-ops (RTDB unaffected)');
}

// Writes only the per-user rank docs whose rank changed since the last mirror,
// chunked at 490 to respect Firestore's 500-op batch limit. Diffs `newRanks`
// ({ uid: rank }) against a per-board snapshot doc that stores the previous map
// as a JSON string field (a plain map would trigger per-key auto-indexing on a
// 600+ key object every run). The snapshot is overwritten only after all rank
// writes succeed, so a partial failure simply re-writes those ranks next run.
async function writeChangedRanks(userCollectionRef, snapshotDocRef, newRanks, label) {
  const snap = await snapshotDocRef.get();
  let oldRanks = {};
  if (snap.exists) {
    const raw = snap.data() && snap.data().ranksJson;
    if (typeof raw === 'string') {
      try {
        oldRanks = JSON.parse(raw);
      } catch (e) {
        oldRanks = {};
      }
    }
  }

  const changedUids = Object.keys(newRanks).filter(
    (uid) => newRanks[uid] !== oldRanks[uid],
  );

  let batch = null;
  let count = 0;
  for (const uid of changedUids) {
    if (!batch) batch = userCollectionRef.firestore.batch();
    batch.set(userCollectionRef.doc(uid), { rank: newRanks[uid] }, { merge: true });
    count++;
    if (count >= 490) {
      await batch.commit();
      batch = null;
      count = 0;
    }
  }
  if (batch && count > 0) await batch.commit();

  // Snapshot is updated only after the rank writes above have committed.
  await snapshotDocRef.set({
    ranksJson: JSON.stringify(newRanks),
    count: Object.keys(newRanks).length,
    updatedAt: Date.now(),
  });

  console.log(
    `[firestore-mirror] ${label} ranks: ${changedUids.length}/${Object.keys(newRanks).length} changed`,
  );
}

async function mirrorMohamedLoversRound(firestore, roundKey, {
  leaderboard,
  dailyLeaderboard,
  roundTotal,
  roundPlayerCount,
  allPlayers,
}) {
  try {
    const roundRef = firestore.collection(ROUNDS_COLLECTION).doc(roundKey);

    await roundRef.set({ roundTotal, roundPlayerCount }, { merge: true });

    const batch = firestore.batch();

    // Write leaderboard entries
    if (leaderboard) {
      for (const [key, entry] of Object.entries(leaderboard)) {
        if (key === 'isFinal') continue;
        batch.set(
          roundRef.collection('leaderboard').doc(key),
          { ...entry, isFinal: leaderboard.isFinal || false },
          { merge: true },
        );
      }
    }

    // Write daily leaderboard entries
    if (dailyLeaderboard) {
      for (const [key, entry] of Object.entries(dailyLeaderboard)) {
        if (key === 'isFinal') continue;
        batch.set(
          roundRef.collection('dailyLeaderboard').doc(key),
          { ...entry, isFinal: dailyLeaderboard.isFinal || false },
          { merge: true },
        );
      }
    }

    await batch.commit();

    // Write per-player ranks (diffed against the last mirror; chunked).
    if (allPlayers) {
      const newRanks = {};
      allPlayers.forEach((player, i) => {
        newRanks[player.uid] = i + 1;
      });
      await writeChangedRanks(
        roundRef.collection('players'),
        roundRef.collection('_meta').doc('rankSnapshot'),
        newRanks,
        `mohamed_lovers round ${roundKey}`,
      );
    }

    console.log(`[firestore-mirror] mohamed_lovers round ${roundKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] mohamed_lovers round ${roundKey} failed: ${e.message}`);
  }
}

async function mirrorDhikrChallenge(firestore, dateKey, {
  rankedUsers,
  participantCount,
  totalTodayDhikr,
  leaderboardEntries,
}) {
  try {
    const dayRef = firestore.collection(DHIKR_COLLECTION).doc(dateKey);
    await dayRef.set({ participantCount, totalTodayDhikr }, { merge: true });

    const batch = firestore.batch();

    // Write leaderboard
    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();

    // Write per-user ranks (diffed against the last mirror; chunked).
    const newRanks = {};
    for (const user of rankedUsers) newRanks[user.uid] = user.rank;
    await writeChangedRanks(
      dayRef.collection('users'),
      dayRef.collection('_meta').doc('rankSnapshot'),
      newRanks,
      `dhikr ${dateKey}`,
    );

    console.log(`[firestore-mirror] dhikr ${dateKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] dhikr ${dateKey} failed: ${e.message}`);
  }
}

async function mirrorBaqiyatChallenge(firestore, dateKey, {
  rankedUsers,
  participantCount,
  totalTodayBaqiyat,
  leaderboardEntries,
}) {
  try {
    const dayRef = firestore.collection(BAQIYAT_COLLECTION).doc(dateKey);
    await dayRef.set({ participantCount, totalTodayBaqiyat }, { merge: true });

    const batch = firestore.batch();

    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();

    // Write per-user ranks (diffed against the last mirror; chunked).
    const newRanks = {};
    for (const user of rankedUsers) newRanks[user.uid] = user.rank;
    await writeChangedRanks(
      dayRef.collection('players'),
      dayRef.collection('_meta').doc('rankSnapshot'),
      newRanks,
      `baqiyat ${dateKey}`,
    );

    console.log(`[firestore-mirror] baqiyat ${dateKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] baqiyat ${dateKey} failed: ${e.message}`);
  }
}

async function mirrorIstighfarChallenge(firestore, dateKey, {
  rankedUsers,
  participantCount,
  totalTodayIstighfar,
  leaderboardEntries,
}) {
  try {
    const dayRef = firestore.collection(ISTIGHFAR_COLLECTION).doc(dateKey);
    await dayRef.set({ participantCount, totalTodayIstighfar }, { merge: true });

    const batch = firestore.batch();

    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();

    // Write per-user ranks (diffed against the last mirror; chunked).
    const newRanks = {};
    for (const user of rankedUsers) newRanks[user.uid] = user.rank;
    await writeChangedRanks(
      dayRef.collection('users'),
      dayRef.collection('_meta').doc('rankSnapshot'),
      newRanks,
      `istighfar ${dateKey}`,
    );

    console.log(`[firestore-mirror] istighfar ${dateKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] istighfar ${dateKey} failed: ${e.message}`);
  }
}

async function mirrorAlBaqaraChallenge(firestore, dateKey, {
  rankedUsers,
  participantCount,
  totalTodayAlBaqara,
  leaderboardEntries,
}) {
  try {
    const dayRef = firestore.collection(ALBAQARA_COLLECTION).doc(dateKey);
    await dayRef.set({ participantCount, totalTodayAlBaqara }, { merge: true });

    const batch = firestore.batch();

    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();

    // Write per-user ranks (diffed against the last mirror; chunked).
    const newRanks = {};
    for (const user of rankedUsers) newRanks[user.uid] = user.rank;
    await writeChangedRanks(
      dayRef.collection('users'),
      dayRef.collection('_meta').doc('rankSnapshot'),
      newRanks,
      `albaqara ${dateKey}`,
    );

    console.log(`[firestore-mirror] albaqara ${dateKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] albaqara ${dateKey} failed: ${e.message}`);
  }
}

async function mirrorZabadChallenge(firestore, dateKey, {
  rankedUsers,
  participantCount,
  totalTodayZabad,
  leaderboardEntries,
}) {
  try {
    const dayRef = firestore.collection(ZABAD_COLLECTION).doc(dateKey);
    await dayRef.set({ participantCount, totalTodayZabad }, { merge: true });

    const batch = firestore.batch();

    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();

    // Write per-user ranks (diffed against the last mirror; chunked).
    const newRanks = {};
    for (const user of rankedUsers) newRanks[user.uid] = user.rank;
    await writeChangedRanks(
      dayRef.collection('users'),
      dayRef.collection('_meta').doc('rankSnapshot'),
      newRanks,
      `zabad ${dateKey}`,
    );

    console.log(`[firestore-mirror] zabad ${dateKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] zabad ${dateKey} failed: ${e.message}`);
  }
}

async function mirrorGharsChallenge(firestore, dateKey, {
  rankedUsers,
  participantCount,
  totalTodayGhars,
  leaderboardEntries,
}) {
  try {
    const dayRef = firestore.collection(GHARS_COLLECTION).doc(dateKey);
    await dayRef.set({ participantCount, totalTodayGhars }, { merge: true });

    const batch = firestore.batch();

    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();

    // Write per-user ranks (diffed against the last mirror; chunked).
    const newRanks = {};
    for (const user of rankedUsers) newRanks[user.uid] = user.rank;
    await writeChangedRanks(
      dayRef.collection('users'),
      dayRef.collection('_meta').doc('rankSnapshot'),
      newRanks,
      `ghars ${dateKey}`,
    );

    console.log(`[firestore-mirror] ghars ${dateKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] ghars ${dateKey} failed: ${e.message}`);
  }
}

async function mirrorQuranChallenge(firestore, dateKey, {
  rankedUsers,
  participantCount,
  totalTodayQuran,
  leaderboardEntries,
}) {
  try {
    const dayRef = firestore.collection(QURAN_COLLECTION).doc(dateKey);
    await dayRef.set({ participantCount, totalTodayQuran }, { merge: true });

    const batch = firestore.batch();

    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();

    // Write per-user ranks (diffed against the last mirror; chunked).
    const newRanks = {};
    for (const user of rankedUsers) newRanks[user.uid] = user.rank;
    await writeChangedRanks(
      dayRef.collection('users'),
      dayRef.collection('_meta').doc('rankSnapshot'),
      newRanks,
      `quran ${dateKey}`,
    );

    console.log(`[firestore-mirror] quran ${dateKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] quran ${dateKey} failed: ${e.message}`);
  }
}

// Mirrors the daily heroes/champions record to Firestore. Overwrites the whole
// doc (no merge) so stale challenges from the previous day are replaced.
async function mirrorHeroes(firestore, heroes) {
  try {
    await firestore.collection(META_COLLECTION).doc('heroes').set(heroes);
    console.log(`[firestore-mirror] heroes ${heroes.date} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] heroes failed: ${e.message}`);
  }
}

async function mirrorAllTimeTotal(firestore, allTimeTotal) {
  try {
    await firestore.collection(META_COLLECTION).doc('stats').set(
      { allTimeTotal },
      { merge: true },
    );
    console.log(`[firestore-mirror] allTimeTotal=${allTimeTotal} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] allTimeTotal failed: ${e.message}`);
  }
}

async function mirrorAchievements(firestore, achievements) {
  try {
    const batch = firestore.batch();
    for (const [path, data] of Object.entries(achievements)) {
      // path format: "mohamed_lovers/users/{uid}/achievements/{roundKey}"
      const parts = path.split('/');
      const uid = parts[2];
      const roundKey = parts[4];
      batch.set(
        firestore.collection(USERS_COLLECTION).doc(uid)
          .collection('achievements').doc(roundKey),
        data,
      );
    }
    await batch.commit();
    console.log(`[firestore-mirror] ${Object.keys(achievements).length} achievements mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] achievements failed: ${e.message}`);
  }
}

async function mirrorYesterdayTotalScores(firestore, roundKey, updates) {
  try {
    const batch = firestore.batch();
    let count = 0;
    for (const [path, score] of Object.entries(updates)) {
      // path format: "mohamed_lovers/{roundKey}/players/{uid}/yesterdayTotalScore"
      const parts = path.split('/');
      const uid = parts[3];
      batch.set(
        firestore.collection(ROUNDS_COLLECTION).doc(roundKey)
          .collection('players').doc(uid),
        { yesterdayTotalScore: score },
        { merge: true },
      );
      count++;
      // Firestore batch limit is 500
      if (count >= 490) {
        await batch.commit();
        count = 0;
      }
    }
    if (count > 0) await batch.commit();
    console.log(`[firestore-mirror] yesterdayTotalScore mirrored for ${Object.keys(updates).length} players`);
  } catch (e) {
    console.error(`[firestore-mirror] yesterdayTotalScore failed: ${e.message}`);
  }
}

async function mirrorDailyBadgeClear(firestore, roundKey, playerUids, leaderboardKeys) {
  try {
    const admin = require('firebase-admin');
    const batch = firestore.batch();
    for (const uid of playerUids) {
      batch.set(
        firestore.collection(ROUNDS_COLLECTION).doc(roundKey)
          .collection('players').doc(uid),
        { dailyBadge: admin.firestore.FieldValue.delete() },
        { merge: true },
      );
    }
    for (const key of leaderboardKeys) {
      batch.set(
        firestore.collection(ROUNDS_COLLECTION).doc(roundKey)
          .collection('leaderboard').doc(key),
        { dailyBadge: admin.firestore.FieldValue.delete() },
        { merge: true },
      );
    }
    await batch.commit();
    console.log(`[firestore-mirror] dailyBadge cleared`);
  } catch (e) {
    console.error(`[firestore-mirror] dailyBadge clear failed: ${e.message}`);
  }
}

async function mirrorRoundStreakClear(firestore, roundKey, playerUids, leaderboardKeys) {
  try {
    const admin = require('firebase-admin');
    const batch = firestore.batch();
    for (const uid of playerUids) {
      batch.set(
        firestore.collection(ROUNDS_COLLECTION).doc(roundKey)
          .collection('players').doc(uid),
        { roundStreak: admin.firestore.FieldValue.delete() },
        { merge: true },
      );
    }
    for (const key of leaderboardKeys) {
      batch.set(
        firestore.collection(ROUNDS_COLLECTION).doc(roundKey)
          .collection('leaderboard').doc(key),
        { roundStreak: admin.firestore.FieldValue.delete() },
        { merge: true },
      );
    }
    await batch.commit();
    console.log(`[firestore-mirror] roundStreak cleared`);
  } catch (e) {
    console.error(`[firestore-mirror] roundStreak clear failed: ${e.message}`);
  }
}

async function mirrorDhikrAggregateAndClean(firestore, dateKey, todayTotal, newGlobalTotal) {
  try {
    await firestore.collection(DHIKR_COLLECTION).doc('_totals').set(
      { totalDhikr: newGlobalTotal },
      { merge: true },
    );
    // Delete the day's doc (subcollections persist but are cleaned up in phase 2)
    await firestore.collection(DHIKR_COLLECTION).doc(dateKey).delete();
    console.log(`[firestore-mirror] dhikr aggregate+clean for ${dateKey} done`);
  } catch (e) {
    console.error(`[firestore-mirror] dhikr aggregate+clean failed: ${e.message}`);
  }
}

async function mirrorBaqiyatAggregateAndClean(firestore, dateKey, todayTotal, newGlobalTotal) {
  try {
    await firestore.collection(BAQIYAT_COLLECTION).doc('_totals').set(
      { totalBaqiyat: newGlobalTotal },
      { merge: true },
    );
    await firestore.collection(BAQIYAT_COLLECTION).doc(dateKey).delete();
    console.log(`[firestore-mirror] baqiyat aggregate+clean for ${dateKey} done`);
  } catch (e) {
    console.error(`[firestore-mirror] baqiyat aggregate+clean failed: ${e.message}`);
  }
}

async function mirrorIstighfarAggregateAndClean(firestore, dateKey, todayTotal, newGlobalTotal) {
  try {
    await firestore.collection(ISTIGHFAR_COLLECTION).doc('_totals').set(
      { totalIstighfar: newGlobalTotal },
      { merge: true },
    );
    await firestore.collection(ISTIGHFAR_COLLECTION).doc(dateKey).delete();
    console.log(`[firestore-mirror] istighfar aggregate+clean for ${dateKey} done`);
  } catch (e) {
    console.error(`[firestore-mirror] istighfar aggregate+clean failed: ${e.message}`);
  }
}

async function mirrorAlBaqaraAggregateAndClean(firestore, dateKey, todayTotal, newGlobalTotal) {
  try {
    await firestore.collection(ALBAQARA_COLLECTION).doc('_totals').set(
      { totalAlBaqara: newGlobalTotal },
      { merge: true },
    );
    await firestore.collection(ALBAQARA_COLLECTION).doc(dateKey).delete();
    console.log(`[firestore-mirror] albaqara aggregate+clean for ${dateKey} done`);
  } catch (e) {
    console.error(`[firestore-mirror] albaqara aggregate+clean failed: ${e.message}`);
  }
}

async function mirrorQuranAggregateAndClean(firestore, dateKey, todayTotal, newGlobalTotal) {
  try {
    await firestore.collection(QURAN_COLLECTION).doc('_totals').set(
      { totalQuran: newGlobalTotal },
      { merge: true },
    );
    await firestore.collection(QURAN_COLLECTION).doc(dateKey).delete();
    console.log(`[firestore-mirror] quran aggregate+clean for ${dateKey} done`);
  } catch (e) {
    console.error(`[firestore-mirror] quran aggregate+clean failed: ${e.message}`);
  }
}

async function mirrorUserAllTimeTotals(firestore, writes) {
  try {
    const batch = firestore.batch();
    let count = 0;
    for (const [path, total] of Object.entries(writes)) {
      // path format: "mohamed_lovers/users/{uid}/allTimeTotal"
      const parts = path.split('/');
      const uid = parts[2];
      batch.set(
        firestore.collection(USERS_COLLECTION).doc(uid),
        { allTimeTotal: total },
        { merge: true },
      );
      count++;
      if (count >= 490) {
        await batch.commit();
        count = 0;
      }
    }
    if (count > 0) await batch.commit();
    console.log(`[firestore-mirror] allTimeTotal mirrored for ${Object.keys(writes).length} users`);
  } catch (e) {
    console.error(`[firestore-mirror] user allTimeTotal failed: ${e.message}`);
  }
}

module.exports = {
  ROUNDS_COLLECTION,
  USERS_COLLECTION,
  META_COLLECTION,
  DHIKR_COLLECTION,
  BAQIYAT_COLLECTION,
  ISTIGHFAR_COLLECTION,
  ZABAD_COLLECTION,
  GHARS_COLLECTION,
  QURAN_COLLECTION,
  ALBAQARA_COLLECTION,
  TEN_DAYS_COLLECTION,
  timeBoxMirror, // exported for tests
  writeChangedRanks, // exported for tests
  // Every mirror is time-boxed so a quota-stalled commit can't hang the cron.
  ...timeBoxAll({
    mirrorMohamedLoversRound,
    mirrorDhikrChallenge,
    mirrorBaqiyatChallenge,
    mirrorIstighfarChallenge,
    mirrorAlBaqaraChallenge,
    mirrorZabadChallenge,
    mirrorGharsChallenge,
    mirrorQuranChallenge,
    mirrorAllTimeTotal,
    mirrorHeroes,
    mirrorAchievements,
    mirrorYesterdayTotalScores,
    mirrorDailyBadgeClear,
    mirrorRoundStreakClear,
    mirrorDhikrAggregateAndClean,
    mirrorBaqiyatAggregateAndClean,
    mirrorIstighfarAggregateAndClean,
    mirrorAlBaqaraAggregateAndClean,
    mirrorQuranAggregateAndClean,
    mirrorUserAllTimeTotals,
  }),
};
