const DHIKR_CHALLENGE_ROOT = '100_challenge';
const BAQIYAT_CHALLENGE_ROOT = 'baqiyat_saliha';

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

module.exports = {
  DHIKR_CHALLENGE_ROOT,
  BAQIYAT_CHALLENGE_ROOT,
  buildOldRankMap,
  computeRankChange,
  normalizeDhikrCount,
  buildDailyCountChallengeRanking,
  buildBaqiyatChallengeDailyRanking,
  buildDhikrChallengeDailyRanking,
};
