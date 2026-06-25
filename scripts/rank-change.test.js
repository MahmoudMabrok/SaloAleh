const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  buildDhikrChallengeDailyRanking,
  buildOldRankMap,
  computeRankChange,
  normalizeDhikrCount,
} = require('./leaderboard-utils');

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
      { uid: 'user-a', count: 12, rank: 1 },
      { uid: 'user-b', count: 12, rank: 2 },
      { uid: 'user-c', count: 3, rank: 3 },
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
});
