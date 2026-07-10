// Firestore dual-write utilities for Phase 1 migration.
// All functions mirror RTDB writes to Firestore. Failures are logged but do not
// block the main RTDB path — Firestore is not yet the source of truth.

const ROUNDS_COLLECTION = 'mohamed_lovers_rounds';
const USERS_COLLECTION = 'mohamed_lovers_users';
const META_COLLECTION = 'mohamed_lovers_meta';
const DHIKR_COLLECTION = 'dhikr_challenge';
const BAQIYAT_COLLECTION = 'baqiyat_challenge';
const ISTIGHFAR_COLLECTION = 'istighfar_challenge';
const TEN_DAYS_COLLECTION = 'ten_days';

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

    // Write per-player ranks
    if (allPlayers) {
      for (let i = 0; i < allPlayers.length; i++) {
        const player = allPlayers[i];
        batch.set(
          roundRef.collection('players').doc(player.uid),
          { rank: i + 1 },
          { merge: true },
        );
      }
    }

    await batch.commit();
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

    // Write per-user ranks
    for (const user of rankedUsers) {
      batch.set(
        dayRef.collection('users').doc(user.uid),
        { rank: user.rank },
        { merge: true },
      );
    }

    // Write leaderboard
    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();
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

    for (const user of rankedUsers) {
      batch.set(
        dayRef.collection('players').doc(user.uid),
        { rank: user.rank },
        { merge: true },
      );
    }

    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();
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

    for (const user of rankedUsers) {
      batch.set(
        dayRef.collection('users').doc(user.uid),
        { rank: user.rank },
        { merge: true },
      );
    }

    if (leaderboardEntries) {
      for (const [key, entry] of leaderboardEntries) {
        batch.set(dayRef.collection('leaderboard').doc(key), entry);
      }
    }

    await batch.commit();
    console.log(`[firestore-mirror] istighfar ${dateKey} mirrored`);
  } catch (e) {
    console.error(`[firestore-mirror] istighfar ${dateKey} failed: ${e.message}`);
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
  TEN_DAYS_COLLECTION,
  mirrorMohamedLoversRound,
  mirrorDhikrChallenge,
  mirrorBaqiyatChallenge,
  mirrorIstighfarChallenge,
  mirrorAllTimeTotal,
  mirrorHeroes,
  mirrorAchievements,
  mirrorYesterdayTotalScores,
  mirrorDailyBadgeClear,
  mirrorRoundStreakClear,
  mirrorDhikrAggregateAndClean,
  mirrorBaqiyatAggregateAndClean,
  mirrorIstighfarAggregateAndClean,
  mirrorUserAllTimeTotals,
};
