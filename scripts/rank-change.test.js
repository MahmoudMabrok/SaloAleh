const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  addDaysToDateKey,
  buildBaqiyatChallengeDailyRanking,
  buildDailyCountChallengeRanking,
  buildDhikrChallengeDailyRanking,
  buildIstighfarChallengeDailyRanking,
  buildOldRankMap,
  computeRankChange,
  computeTop3Changes,
  normalizeDhikrCount,
  readChallengeRankedUsers,
  DHIKR_CHALLENGE_ROOT,
  BAQIYAT_CHALLENGE_ROOT,
} = require('./leaderboard-utils');

// Minimal RTDB stub: maps a ref path to a plain object of children and mimics the
// snapshot surface (`exists()` / ordered `forEach` yielding `{ key, val() }`).
function makeFakeDb(pathToChildren) {
  return {
    ref(path) {
      return {
        async get() {
          const children = pathToChildren[path];
          return {
            exists: () => children != null && Object.keys(children).length > 0,
            forEach(cb) {
              for (const [key, value] of Object.entries(children || {})) {
                cb({ key, val: () => value });
              }
            },
          };
        },
      };
    },
  };
}

describe('computeRankChange', () => {
  it('returns "new" when player not in old leaderboard', () => {
    assert.equal(computeRankChange('uid-x', 3, {}), 'new');
  });

  it('returns "same" when rank unchanged', () => {
    assert.equal(computeRankChange('uid-a', 1, { 'uid-a': 1 }), 'same');
  });

  it('returns "up" when rank improved (lower number)', () => {
    assert.equal(computeRankChange('uid-a', 2, { 'uid-a': 5 }), 'up');
  });

  it('returns "down" when rank dropped (higher number)', () => {
    assert.equal(computeRankChange('uid-a', 5, { 'uid-a': 2 }), 'down');
  });

  it('returns "up" when moving from rank 10 to rank 1', () => {
    assert.equal(computeRankChange('uid-a', 1, { 'uid-a': 10 }), 'up');
  });

  it('returns "down" when moving from rank 1 to rank 10', () => {
    assert.equal(computeRankChange('uid-a', 10, { 'uid-a': 1 }), 'down');
  });
});

describe('buildOldRankMap', () => {
  it('builds map from numbered entries', () => {
    const entries = {
      isFinal: false,
      '1': { rank: 1, uid: 'aaa', score: 100 },
      '2': { rank: 2, uid: 'bbb', score: 80 },
    };
    const map = buildOldRankMap(entries);
    assert.deepEqual(map, { aaa: 1, bbb: 2 });
  });

  it('skips entries without uid', () => {
    const entries = {
      '1': { rank: 1, score: 100 },
      '2': { rank: 2, uid: 'bbb', score: 80 },
    };
    const map = buildOldRankMap(entries);
    assert.deepEqual(map, { bbb: 2 });
  });

  it('skips non-numeric keys like isFinal', () => {
    const entries = {
      isFinal: true,
      '1': { rank: 1, uid: 'aaa', score: 100 },
    };
    const map = buildOldRankMap(entries);
    assert.deepEqual(map, { aaa: 1 });
  });

  it('returns empty map from empty input', () => {
    assert.deepEqual(buildOldRankMap({}), {});
  });
});

describe('full leaderboard rank-change integration', () => {
  it('computes correct changes for a full top-10 reshuffle', () => {
    const oldEntries = {
      '1': { rank: 1, uid: 'alice', score: 100 },
      '2': { rank: 2, uid: 'bob', score: 90 },
      '3': { rank: 3, uid: 'carol', score: 80 },
    };
    const oldRanks = buildOldRankMap(oldEntries);

    const newTop = [
      { uid: 'bob', rank: 1 },
      { uid: 'dave', rank: 2 },
      { uid: 'alice', rank: 3 },
    ];

    const results = newTop.map(p => ({
      uid: p.uid,
      rankChange: computeRankChange(p.uid, p.rank, oldRanks),
    }));

    assert.equal(results[0].rankChange, 'up');    // bob: 2→1
    assert.equal(results[1].rankChange, 'new');    // dave: not in old
    assert.equal(results[2].rankChange, 'down');   // alice: 1→3
  });
});

describe('ten-days leaderboard rank-change', () => {
  it('detects top-3 dropout in ten-days leaderboard', () => {
    const oldEntries = {
      '1': { rank: 1, uid: 'user-a', totalScore: 500 },
      '2': { rank: 2, uid: 'user-b', totalScore: 400 },
      '3': { rank: 3, uid: 'user-c', totalScore: 300 },
    };
    const oldRanks = buildOldRankMap(oldEntries);

    assert.equal(computeRankChange('user-d', 1, oldRanks), 'new');
    assert.equal(computeRankChange('user-a', 2, oldRanks), 'down');
    assert.equal(computeRankChange('user-b', 3, oldRanks), 'down');
  });

  it('detects dropout from top-10', () => {
    const oldEntries = {};
    for (let i = 1; i <= 10; i++) {
      oldEntries[String(i)] = { rank: i, uid: `p${i}`, totalScore: 1000 - i * 10 };
    }
    const oldRanks = buildOldRankMap(oldEntries);
    const oldUids = new Set(Object.keys(oldRanks));

    const newTop10Uids = new Set(['p1', 'p2', 'p3', 'p4', 'p5', 'p6', 'p7', 'p8', 'p9', 'newcomer']);
    const dropped = [...oldUids].filter(uid => !newTop10Uids.has(uid));
    assert.deepEqual(dropped, ['p10']);
  });

  it('handles complete top-10 turnover', () => {
    const oldEntries = {
      '1': { rank: 1, uid: 'old-1', totalScore: 100 },
      '2': { rank: 2, uid: 'old-2', totalScore: 90 },
    };
    const oldRanks = buildOldRankMap(oldEntries);

    assert.equal(computeRankChange('new-1', 1, oldRanks), 'new');
    assert.equal(computeRankChange('new-2', 2, oldRanks), 'new');

    const oldUids = new Set(Object.keys(oldRanks));
    const newUids = new Set(['new-1', 'new-2']);
    const dropped = [...oldUids].filter(uid => !newUids.has(uid));
    assert.deepEqual(dropped, ['old-1', 'old-2']);
  });
});

describe('computeTop3Changes', () => {
  const ranked = uids => uids.map(uid => ({ uid }));

  it('returns no notifications when the top 3 is unchanged', () => {
    const oldRanks = { a: 1, b: 2, c: 3 };
    assert.deepEqual(computeTop3Changes(oldRanks, ranked(['a', 'b', 'c'])), []);
  });

  it('flags a user who dropped out of the top 3 entirely', () => {
    const oldRanks = { a: 1, b: 2, c: 3 };
    const notifs = computeTop3Changes(oldRanks, ranked(['a', 'b', 'x']));
    assert.deepEqual(notifs, [{ uid: 'c', event: 'dropped', oldRank: 3, newRank: null }]);
  });

  it('flags a user who fell to rank 4+ as dropped', () => {
    const oldRanks = { a: 1, b: 2, c: 3 };
    const notifs = computeTop3Changes(oldRanks, ranked(['a', 'b', 'x', 'c']));
    assert.deepEqual(notifs, [{ uid: 'c', event: 'dropped', oldRank: 3, newRank: 4 }]);
  });

  it('flags a user who lost a position but is still in the top 3', () => {
    const oldRanks = { a: 1, b: 2, c: 3 };
    const notifs = computeTop3Changes(oldRanks, ranked(['b', 'a', 'c']));
    assert.deepEqual(notifs, [{ uid: 'a', event: 'lost_position', oldRank: 1, newRank: 2 }]);
  });

  it('does not notify users who climbed or held within the top 3', () => {
    const oldRanks = { a: 2, b: 1 };
    // b (was 1) is now 2 -> lost_position; a (was 2) is now 1 -> improved, no notif
    const notifs = computeTop3Changes(oldRanks, ranked(['a', 'b']));
    assert.deepEqual(notifs, [{ uid: 'b', event: 'lost_position', oldRank: 1, newRank: 2 }]);
  });

  it('ignores users who were outside the top 3', () => {
    const oldRanks = { a: 1, d: 4, e: 5 };
    const notifs = computeTop3Changes(oldRanks, ranked(['x', 'y', 'z']));
    assert.deepEqual(notifs, [{ uid: 'a', event: 'dropped', oldRank: 1, newRank: null }]);
  });
});

describe('dhikr challenge daily ranking', () => {
  it('ranks active users by count and uid, then writes count and total summaries', () => {
    const result = buildDhikrChallengeDailyRanking('2026-06-25', [
      { uid: 'user-b', count: 12 },
      { uid: 'user-a', count: 12 },
      { uid: 'user-c', count: 3 },
      { uid: 'zero-user', count: 0 },
    ]);

    assert.equal(result.participantCount, 3);
    assert.equal(result.totalTodayDhikr, 27);
    assert.deepEqual(result.rankedUsers, [
      { uid: 'user-a', count: 12, countryCode: '', nickname: '', currentRank: null, rank: 1 },
      { uid: 'user-b', count: 12, countryCode: '', nickname: '', currentRank: null, rank: 2 },
      { uid: 'user-c', count: 3, countryCode: '', nickname: '', currentRank: null, rank: 3 },
    ]);
    assert.equal(result.rankUpdates['100_challenge/2026-06-25/users/user-a/rank'], 1);
    assert.equal(result.rankUpdates['100_challenge/2026-06-25/users/user-b/rank'], 2);
    assert.equal(result.rankUpdates['100_challenge/2026-06-25/users/user-c/rank'], 3);
    assert.equal(result.rankUpdates['100_challenge/2026-06-25/users/zero-user/rank'], null);
  });

  it('normalizes invalid and negative counts to zero', () => {
    assert.equal(normalizeDhikrCount(undefined), 0);
    assert.equal(normalizeDhikrCount(-4), 0);
    assert.equal(normalizeDhikrCount(4.8), 4);

    const result = buildDhikrChallengeDailyRanking('2026-06-25', [
      { uid: 'bad-user', count: '11' },
      { uid: 'negative-user', count: -2 },
    ]);

    assert.equal(result.participantCount, 0);
    assert.equal(result.totalTodayDhikr, 0);
    assert.deepEqual(result.rankedUsers, []);
    assert.equal(result.rankUpdates['100_challenge/2026-06-25/users/bad-user/rank'], null);
    assert.equal(result.rankUpdates['100_challenge/2026-06-25/users/negative-user/rank'], null);
  });

  it('carries sanitized country code, nickname, and current rank into ranked users', () => {
    const result = buildDhikrChallengeDailyRanking('2026-06-25', [
      { uid: 'user-a', count: 7, countryCode: 'egp', nickname: '  Dhikr Friend  ', currentRank: 3 },
    ]);

    assert.deepEqual(result.rankedUsers, [
      { uid: 'user-a', count: 7, countryCode: 'EGP', nickname: 'Dhikr Friend', currentRank: 3, rank: 1 },
    ]);
  });
});

describe('generic daily count challenge ranking', () => {
  it('builds ranks under the configured players path', () => {
    const result = buildDailyCountChallengeRanking({
      dateKey: '2026-07-02',
      rootPath: 'custom_challenge',
      playersPath: 'players',
      players: [
        { uid: 'b', count: 4 },
        { uid: 'a', count: 9 },
        { uid: 'zero', count: 0 },
      ],
    });

    assert.equal(result.participantCount, 2);
    assert.equal(result.totalCount, 13);
    assert.deepEqual(result.rankedUsers.map(user => user.uid), ['a', 'b']);
    assert.equal(result.rankUpdates['custom_challenge/2026-07-02/players/a/rank'], 1);
    assert.equal(result.rankUpdates['custom_challenge/2026-07-02/players/b/rank'], 2);
    assert.equal(result.rankUpdates['custom_challenge/2026-07-02/players/zero/rank'], null);
  });
});

describe('baqiyat challenge daily ranking', () => {
  it('uses the baqiyat root and player nodes', () => {
    const result = buildBaqiyatChallengeDailyRanking('2026-07-02', [
      { uid: 'user-a', count: 3, countryCode: 'eg', nickname: '  Sabah  ' },
      { uid: 'user-b', count: 5, countryCode: 'sa' },
    ]);

    assert.equal(result.participantCount, 2);
    assert.equal(result.totalTodayBaqiyat, 8);
    assert.deepEqual(result.rankedUsers, [
      { uid: 'user-b', count: 5, countryCode: 'SA', nickname: '', currentRank: null, rank: 1 },
      { uid: 'user-a', count: 3, countryCode: 'EG', nickname: 'Sabah', currentRank: null, rank: 2 },
    ]);
    assert.equal(result.rankUpdates['baqiyat_saliha/2026-07-02/players/user-b/rank'], 1);
    assert.equal(result.rankUpdates['baqiyat_saliha/2026-07-02/players/user-a/rank'], 2);
  });
});

describe('istighfar challenge daily ranking', () => {
  it('uses the istighfar root and user nodes', () => {
    const result = buildIstighfarChallengeDailyRanking('2026-07-02', [
      { uid: 'user-a', count: 35, countryCode: 'eg', nickname: '  Taa\'ib  ' },
      { uid: 'user-b', count: 70, countryCode: 'sa' },
      { uid: 'zero-user', count: 0 },
    ]);

    assert.equal(result.participantCount, 2);
    assert.equal(result.totalTodayIstighfar, 105);
    assert.deepEqual(result.rankedUsers, [
      { uid: 'user-b', count: 70, countryCode: 'SA', nickname: '', currentRank: null, rank: 1 },
      { uid: 'user-a', count: 35, countryCode: 'EG', nickname: "Taa'ib", currentRank: null, rank: 2 },
    ]);
    assert.equal(result.rankUpdates['istighfar_challenge/2026-07-02/users/user-b/rank'], 1);
    assert.equal(result.rankUpdates['istighfar_challenge/2026-07-02/users/user-a/rank'], 2);
    assert.equal(result.rankUpdates['istighfar_challenge/2026-07-02/users/zero-user/rank'], null);
  });
});

describe('addDaysToDateKey', () => {
  it('advances a round key by one week', () => {
    assert.equal(addDaysToDateKey('2026-07-03', 7), '2026-07-10');
  });

  it('rolls over a month boundary', () => {
    assert.equal(addDaysToDateKey('2026-07-31', 7), '2026-08-07');
  });

  it('rolls over a year boundary', () => {
    assert.equal(addDaysToDateKey('2026-12-25', 7), '2027-01-01');
  });
});

describe('readChallengeRankedUsers', () => {
  it('ranks nested-metadata challenges (users path) by live count, highest first', async () => {
    const db = makeFakeDb({
      '100_challenge/2026-07-12/users': {
        'child-a': { count: 40, data: { uid: 'uid-a', countryCode: 'eg', nickname: ' Ali ' } },
        'child-b': { count: 90, data: { uid: 'uid-b', countryCode: 'sa', nickname: 'Sara' } },
        'child-c': { count: 90, data: { uid: 'uid-c' } },
      },
    });

    const ranked = await readChallengeRankedUsers(db, DHIKR_CHALLENGE_ROOT, '2026-07-12');

    assert.deepEqual(ranked.map(u => u.uid), ['uid-b', 'uid-c', 'uid-a']); // 90 tie broken by uid asc
    assert.deepEqual(ranked.map(u => u.rank), [1, 2, 3]);
    assert.equal(ranked[0].count, 90);
    assert.equal(ranked[0].countryCode, 'SA');
    assert.equal(ranked[2].nickname, 'Ali'); // trimmed
  });

  it('ranks flat-metadata challenges (baqiyat players path) and drops zero counts', async () => {
    const db = makeFakeDb({
      'baqiyat_saliha/2026-07-12/players': {
        'uid-a': { uid: 'uid-a', count: 5, countryCode: 'eg' },
        'uid-b': { uid: 'uid-b', count: 12, countryCode: 'sa' },
        'uid-z': { uid: 'uid-z', count: 0, countryCode: 'kw' },
      },
    });

    const ranked = await readChallengeRankedUsers(db, BAQIYAT_CHALLENGE_ROOT, '2026-07-12');

    assert.deepEqual(ranked.map(u => u.uid), ['uid-b', 'uid-a']); // zero-count user excluded
    assert.equal(ranked[0].count, 12);
  });

  it('returns [] when the participant node is absent', async () => {
    const db = makeFakeDb({});
    const ranked = await readChallengeRankedUsers(db, DHIKR_CHALLENGE_ROOT, '2026-07-12');
    assert.deepEqual(ranked, []);
  });

  it('throws on an unknown challenge root', async () => {
    const db = makeFakeDb({});
    await assert.rejects(() => readChallengeRankedUsers(db, 'not_a_challenge', '2026-07-12'));
  });
});
