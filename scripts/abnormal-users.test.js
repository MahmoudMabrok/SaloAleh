const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  ABNORMAL_DAILY_THRESHOLD,
  PACE_DAILY_INCREMENT,
  computeTodayScore,
  buildDailyScoreSnapshots,
  roundDayNumber,
} = require('./leaderboard-utils');

describe('computeTodayScore', () => {
  it('takes the larger of todayCount and the yesterday-diff', () => {
    // Diff (1000) outruns the masked todayCount (800) — the diff wins.
    assert.equal(computeTodayScore({ total: 5000, today: 800, yesterday: 4000 }), 1000);
    // todayCount (1200) is ahead of the flushed diff (500) — todayCount wins.
    assert.equal(computeTodayScore({ total: 5000, today: 1200, yesterday: 4500 }), 1200);
  });

  it('floors a fractional todayCount', () => {
    assert.equal(computeTodayScore({ total: 0, today: 12.9, yesterday: 0 }), 12);
  });

  it('uncovers totalCount inflation hidden behind a zero todayCount', () => {
    // The masking cheat pattern: totalCount jumps while todayCount stays tiny.
    assert.equal(computeTodayScore({ total: 5000, today: 0, yesterday: 4000 }), 1000);
    assert.equal(computeTodayScore({ total: 30000, today: 5, yesterday: 0 }), 30000);
  });

  it('falls back to the yesterday-diff when todayCount is absent', () => {
    assert.equal(computeTodayScore({ total: 5000, today: null, yesterday: 4000 }), 1000);
    assert.equal(computeTodayScore({ total: 5000, today: undefined, yesterday: 4000 }), 1000);
  });

  it('never returns a negative fallback', () => {
    assert.equal(computeTodayScore({ total: 3000, today: null, yesterday: 4000 }), 0);
    assert.equal(computeTodayScore({ total: 3000, today: -5, yesterday: 4000 }), 0);
  });

  it('defaults a missing/undefined yesterday baseline to 0', () => {
    // Friday 19:00 rollover: the fresh round node has no yesterdayTotalScore until the
    // 23:45 Cairo close stamps it. The whole round total is that day's count.
    assert.equal(computeTodayScore({ total: 5000, today: 800, yesterday: undefined }), 5000);
    assert.equal(computeTodayScore({ total: 5000, today: 800, yesterday: null }), 5000);
    assert.equal(computeTodayScore({ total: 0, today: 0, yesterday: undefined }), 0);
  });
});

describe('buildDailyScoreSnapshots', () => {
  const dateKey = '2026-07-26';
  const roundKey = '2026-07-31';

  it('writes a history snapshot per active user using the max of todayCount and diff', () => {
    const { scoreHistoryUpdates } = buildDailyScoreSnapshots({
      players: [
        // day total = max(800, 5000-4000) = 1000
        { uid: 'a', totalCount: 5000, todayCount: 800, yesterdayTotalScore: 4000 },
        { uid: 'b', totalCount: 200, todayCount: 200, yesterdayTotalScore: 0 },
      ],
      dateKey,
      roundKey,
    });
    assert.equal(scoreHistoryUpdates[`mohamed_lovers/users/a/scoreHistory/${dateKey}`], 1000);
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

  it('treats a missing yesterdayTotalScore as 0 (Friday rollover node)', () => {
    const { scoreHistoryUpdates, paceFlagUpdates } = buildDailyScoreSnapshots({
      players: [{ uid: 'fresh', totalCount: 5000, todayCount: 800 }],
      dateKey,
      roundKey,
      roundDay: 1,
    });
    // No baseline ⇒ the whole (fresh) round total is today's count, not 800.
    assert.equal(scoreHistoryUpdates[`mohamed_lovers/users/fresh/scoreHistory/${dateKey}`], 5000);
    // …and it stays under the day-1 pace ceiling (11k), so no false pace flag.
    assert.deepEqual(paceFlagUpdates, {});
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
        // day total = max(20001, 20001-0) = 20001 — the diff uncovers the masked inflation.
        { uid: 'bot', totalCount: 20001, todayCount: ABNORMAL_DAILY_THRESHOLD + 1, yesterdayTotalScore: 0, countryCode: 'eg' },
        { uid: 'human', totalCount: 500, todayCount: 500, yesterdayTotalScore: 0, countryCode: 'sa' },
      ],
      dateKey,
      roundKey,
    });
    assert.deepEqual(abnormalUpdates, {
      [`mohamed_lovers/abnormal_users/${dateKey}/bot`]: {
        count: 20001,
        totalCount: 20001,
        countryCode: 'eg',
      },
    });
  });

  it('flags totalCount inflation hidden behind a tiny todayCount as abnormal', () => {
    const { abnormalUpdates, strictModeFlags } = buildDailyScoreSnapshots({
      players: [
        { uid: 'masker', totalCount: 30000, todayCount: 5, yesterdayTotalScore: 0 },
      ],
      dateKey,
      roundKey,
      roundDay: 1,
    });
    assert.deepEqual(abnormalUpdates, {
      [`mohamed_lovers/abnormal_users/${dateKey}/masker`]: {
        count: 30000,
        totalCount: 30000,
        countryCode: 'NA',
      },
    });
    assert.equal(strictModeFlags.masker.reason, 'both');
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
    assert.deepEqual(result.paceFlagUpdates, {});
  });

  it('does not build pace flags when roundDay is omitted', () => {
    const { paceFlagUpdates } = buildDailyScoreSnapshots({
      players: [{ uid: 'fast', totalCount: 999999, todayCount: 100, yesterdayTotalScore: 0 }],
      dateKey,
      roundKey,
    });
    assert.deepEqual(paceFlagUpdates, {});
  });

  it('flags a cumulative total above the day-of-round pace ceiling', () => {
    // Day 2 → ceiling 22k. `fast` outruns it; `steady` is right at it (not flagged).
    const { paceFlagUpdates } = buildDailyScoreSnapshots({
      players: [
        { uid: 'fast', totalCount: 2 * PACE_DAILY_INCREMENT + 1, todayCount: 500, yesterdayTotalScore: 0, countryCode: 'eg' },
        { uid: 'steady', totalCount: 2 * PACE_DAILY_INCREMENT, todayCount: 500, yesterdayTotalScore: 0, countryCode: 'sa' },
      ],
      dateKey,
      roundKey,
      roundDay: 2,
    });
    assert.deepEqual(paceFlagUpdates, {
      [`mohamed_lovers/users/fast/paceFlags/${dateKey}`]: {
        totalCount: 2 * PACE_DAILY_INCREMENT + 1,
        dayOfRound: 2,
        threshold: 2 * PACE_DAILY_INCREMENT,
        countryCode: 'eg',
      },
    });
  });

  it('uses an 11k ceiling on day 1 (Saturday)', () => {
    const { paceFlagUpdates } = buildDailyScoreSnapshots({
      players: [
        { uid: 'over', totalCount: PACE_DAILY_INCREMENT + 1, todayCount: 100, yesterdayTotalScore: 0, countryCode: 'eg' },
        { uid: 'under', totalCount: PACE_DAILY_INCREMENT, todayCount: 100, yesterdayTotalScore: 0, countryCode: 'eg' },
      ],
      dateKey,
      roundKey,
      roundDay: 1,
    });
    assert.deepEqual(Object.keys(paceFlagUpdates), [`mohamed_lovers/users/over/paceFlags/${dateKey}`]);
    assert.equal(paceFlagUpdates[`mohamed_lovers/users/over/paceFlags/${dateKey}`].threshold, PACE_DAILY_INCREMENT);
  });

  it('defaults a missing countryCode to NA in the pace flag', () => {
    const { paceFlagUpdates } = buildDailyScoreSnapshots({
      players: [{ uid: 'nocc', totalCount: 3 * PACE_DAILY_INCREMENT + 5, todayCount: 100, yesterdayTotalScore: 0 }],
      dateKey,
      roundKey,
      roundDay: 3,
    });
    assert.equal(paceFlagUpdates[`mohamed_lovers/users/nocc/paceFlags/${dateKey}`].countryCode, 'NA');
  });
});

describe('buildDailyScoreSnapshots strictModeFlags', () => {
  const dateKey = '2026-07-26';
  const roundKey = '2026-07-31';

  it('flags an abnormal-only day as reason abnormal', () => {
    const { strictModeFlags } = buildDailyScoreSnapshots({
      players: [{ uid: 'spike', totalCount: 21000, todayCount: 21000, yesterdayTotalScore: 0 }],
      dateKey,
      roundKey,
    });
    assert.deepEqual(strictModeFlags, {
      spike: { reason: 'abnormal', dateKey },
    });
  });

  it('flags a pace-only overrun as reason pace', () => {
    // Day 2 → ceiling 22k; total 23k overruns pace but day growth (23k vs 0) …
    // would also be above the 20k abnormal threshold, so use a low-growth case: yesterday ≈ total.
    const { strictModeFlags, abnormalUpdates } = buildDailyScoreSnapshots({
      players: [{ uid: 'steady', totalCount: 23000, todayCount: 100, yesterdayTotalScore: 22950 }],
      dateKey,
      roundKey,
      roundDay: 2,
    });
    assert.deepEqual(abnormalUpdates, {});
    assert.deepEqual(strictModeFlags, {
      steady: { reason: 'pace', dateKey },
    });
  });

  it('does not flag clean players', () => {
    const { strictModeFlags } = buildDailyScoreSnapshots({
      players: [{ uid: 'clean', totalCount: 5000, todayCount: 800, yesterdayTotalScore: 4500 }],
      dateKey,
      roundKey,
      roundDay: 2,
    });
    assert.deepEqual(strictModeFlags, {});
  });
});

describe('roundDayNumber', () => {
  // Round key is the round's END Friday (Cairo); the round began the previous Friday 19:00.
  const roundKey = '2026-07-31'; // Friday

  it('maps Saturday to day 1', () => {
    assert.equal(roundDayNumber(roundKey, '2026-07-25'), 1);
  });

  it('maps Sunday to day 2', () => {
    assert.equal(roundDayNumber(roundKey, '2026-07-26'), 2);
  });

  it('maps the end Friday to day 7', () => {
    assert.equal(roundDayNumber(roundKey, '2026-07-31'), 7);
  });

  it('clamps the post-reset Friday-night close (fresh round) to day 1', () => {
    // On the end Friday's 23:45 close the round has already advanced to the next Friday,
    // so dateKey === startFriday → raw 0, clamped up to 1.
    assert.equal(roundDayNumber('2026-08-07', '2026-07-31'), 1);
  });
});
