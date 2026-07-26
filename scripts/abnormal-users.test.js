const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  ABNORMAL_DAILY_THRESHOLD,
  computeTodayScore,
  buildDailyScoreSnapshots,
} = require('./leaderboard-utils');

describe('computeTodayScore', () => {
  it('prefers the client-published todayCount when present', () => {
    assert.equal(computeTodayScore({ total: 5000, today: 800, yesterday: 4000 }), 800);
  });

  it('floors a fractional todayCount', () => {
    assert.equal(computeTodayScore({ total: 0, today: 12.9, yesterday: 0 }), 12);
  });

  it('treats an explicit 0 todayCount as zero (does not fall back)', () => {
    assert.equal(computeTodayScore({ total: 5000, today: 0, yesterday: 4000 }), 0);
  });

  it('falls back to the yesterday-diff when todayCount is absent', () => {
    assert.equal(computeTodayScore({ total: 5000, today: null, yesterday: 4000 }), 1000);
    assert.equal(computeTodayScore({ total: 5000, today: undefined, yesterday: 4000 }), 1000);
  });

  it('never returns a negative fallback', () => {
    assert.equal(computeTodayScore({ total: 3000, today: null, yesterday: 4000 }), 0);
  });
});

describe('buildDailyScoreSnapshots', () => {
  const dateKey = '2026-07-26';
  const roundKey = '2026-07-31';

  it('writes a history snapshot per active user using todayCount', () => {
    const { scoreHistoryUpdates } = buildDailyScoreSnapshots({
      players: [
        { uid: 'a', totalCount: 5000, todayCount: 800, yesterdayTotalScore: 4000 },
        { uid: 'b', totalCount: 200, todayCount: 200, yesterdayTotalScore: 0 },
      ],
      dateKey,
      roundKey,
    });
    assert.equal(scoreHistoryUpdates[`mohamed_lovers/users/a/scoreHistory/${dateKey}`], 800);
    assert.equal(scoreHistoryUpdates[`mohamed_lovers/users/b/scoreHistory/${dateKey}`], 200);
  });

  it('falls back to the yesterday-diff for clients without todayCount', () => {
    const { scoreHistoryUpdates } = buildDailyScoreSnapshots({
      players: [{ uid: 'legacy', totalCount: 5000, todayCount: null, yesterdayTotalScore: 4200 }],
      dateKey,
      roundKey,
    });
    assert.equal(scoreHistoryUpdates[`mohamed_lovers/users/legacy/scoreHistory/${dateKey}`], 800);
  });

  it('omits users with a zero day total from the history', () => {
    const { scoreHistoryUpdates } = buildDailyScoreSnapshots({
      players: [{ uid: 'idle', totalCount: 4000, todayCount: 0, yesterdayTotalScore: 4000 }],
      dateKey,
      roundKey,
    });
    assert.deepEqual(scoreHistoryUpdates, {});
  });

  it('flags users above the abnormal threshold with count + metadata', () => {
    const { abnormalUpdates } = buildDailyScoreSnapshots({
      players: [
        { uid: 'bot', totalCount: 20000, todayCount: ABNORMAL_DAILY_THRESHOLD + 1, yesterdayTotalScore: 0, countryCode: 'eg' },
        { uid: 'human', totalCount: 500, todayCount: 500, yesterdayTotalScore: 0, countryCode: 'sa' },
      ],
      dateKey,
      roundKey,
    });
    assert.deepEqual(abnormalUpdates, {
      [`mohamed_lovers/abnormal_users/${dateKey}/bot`]: {
        count: ABNORMAL_DAILY_THRESHOLD + 1,
        totalCount: 20000,
        countryCode: 'eg',
      },
    });
  });

  it('does not flag a user exactly at the threshold', () => {
    const { abnormalUpdates } = buildDailyScoreSnapshots({
      players: [{ uid: 'edge', totalCount: ABNORMAL_DAILY_THRESHOLD, todayCount: ABNORMAL_DAILY_THRESHOLD, yesterdayTotalScore: 0 }],
      dateKey,
      roundKey,
    });
    assert.deepEqual(abnormalUpdates, {});
  });

  it('resets only players carrying a non-zero client todayCount', () => {
    const { todayCountResets } = buildDailyScoreSnapshots({
      players: [
        { uid: 'active', totalCount: 900, todayCount: 900, yesterdayTotalScore: 0 },
        { uid: 'already-zero', totalCount: 900, todayCount: 0, yesterdayTotalScore: 900 },
        { uid: 'legacy', totalCount: 900, todayCount: null, yesterdayTotalScore: 0 },
      ],
      dateKey,
      roundKey,
    });
    assert.deepEqual(todayCountResets, {
      [`mohamed_lovers/${roundKey}/players/active/todayCount`]: 0,
    });
  });

  it('skips entries without a uid', () => {
    const result = buildDailyScoreSnapshots({
      players: [{ totalCount: 5000, todayCount: 5000 }, null],
      dateKey,
      roundKey,
    });
    assert.deepEqual(result.scoreHistoryUpdates, {});
    assert.deepEqual(result.abnormalUpdates, {});
    assert.deepEqual(result.todayCountResets, {});
  });
});
