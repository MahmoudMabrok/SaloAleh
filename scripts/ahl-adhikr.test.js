const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  CHALLENGE_KEYS,
  MILESTONES,
  cairoDateStr,
  daysBetween,
  publicId,
  bucketByMilestone,
  nextSalawatStreaks,
  buildSnapshot,
  validateSnapshot,
} = require('./ahl-adhikr-utils');
const { parseArgs, roundKeyForDate, writeSnapshotFiles, withRetry } = require('./generate-ahl-adhikr');
const { DHIKR_CHALLENGE_ROOT, readChallengeRankedUsers } = require('./leaderboard-utils');

const user = (uid, streak, nickname = '') => ({ uid, count: 1, nickname, streak, rank: 1 });

describe('cairoDateStr', () => {
  it('formats a UTC instant as its Cairo calendar day', () => {
    // 21:30 UTC = 23:30 Cairo (winter) — same day. 22:30 UTC = 00:30 Cairo — next day.
    assert.equal(cairoDateStr(new Date('2026-11-14T21:30:00Z')), '2026-11-14');
    assert.equal(cairoDateStr(new Date('2026-11-14T22:30:00Z')), '2026-11-15');
  });
});

describe('daysBetween', () => {
  it('is positive forward, negative backward, zero same-day', () => {
    assert.equal(daysBetween('2026-11-01', '2026-11-04'), 3);
    assert.equal(daysBetween('2026-11-04', '2026-11-01'), -3);
    assert.equal(daysBetween('2026-11-01', '2026-11-01'), 0);
  });
});

describe('publicId', () => {
  it('publishes only the last 6 chars, uppercased', () => {
    assert.equal(publicId('abcdef0123456789'), '456789');
  });
});

describe('bucketByMilestone — thresholds', () => {
  const buckets = bucketByMilestone([
    user('u-zero', 0),
    user('u-29', 29),
    user('u-30', 30),
    user('u-60', 60),
    user('u-90', 90),
    user('u-31', 31),
  ]);

  it('excludes 0 and 29 from every list', () => {
    for (const { key } of MILESTONES) {
      assert.equal(buckets[key].some(e => e.id === publicId('u-zero')), false, key);
      assert.equal(buckets[key].some(e => e.id === publicId('u-29')), false, key);
    }
  });

  it('includes 30 in 30_days only', () => {
    assert.equal(buckets['30_days'].some(e => e.id === publicId('u-30')), true);
    assert.equal(buckets['60_days'].some(e => e.id === publicId('u-30')), false);
  });

  it('includes 60 in 30_days and 60_days, not 90_days', () => {
    assert.equal(buckets['30_days'].some(e => e.id === publicId('u-60')), true);
    assert.equal(buckets['60_days'].some(e => e.id === publicId('u-60')), true);
    assert.equal(buckets['90_days'].some(e => e.id === publicId('u-60')), false);
  });

  it('includes 90 in all three lists', () => {
    for (const { key } of MILESTONES) {
      assert.equal(buckets[key].some(e => e.id === publicId('u-90')), true, key);
    }
  });

  it('sorts by streak descending with id tiebreaker', () => {
    const ids = buckets['30_days'].map(e => e.id);
    assert.deepEqual(ids, [publicId('u-90'), publicId('u-60'), publicId('u-31'), publicId('u-30')].map(String));
  });

  it('publishes only id, name, streak', () => {
    for (const entry of buckets['30_days']) {
      assert.deepEqual(Object.keys(entry).sort(), ['id', 'name', 'streak']);
    }
  });

  it('falls back to the public id when no nickname', () => {
    const [entry] = bucketByMilestone([user('secret-uid-1', 45)])['30_days'];
    assert.equal(entry.name, publicId('secret-uid-1'));
  });

  it('trims nicknames to 20 chars', () => {
    const [entry] = bucketByMilestone([user('u', 45, 'x'.repeat(30))])['30_days'];
    assert.equal(entry.name.length, 20);
  });
});

describe('nextSalawatStreaks', () => {
  const D = (n) => `2026-11-${String(n).padStart(2, '0')}`;

  it('starts a streak of 1 on first activity', () => {
    const { state, streaks } = nextSalawatStreaks({}, ['a'], D(10));
    assert.equal(streaks.a, 1);
    assert.deepEqual(state.a, { streak: 1, lastActive: D(10) });
  });

  it('extends a consecutive streak', () => {
    const prev = { a: { streak: 29, lastActive: D(9) } };
    const { streaks } = nextSalawatStreaks(prev, ['a'], D(10));
    assert.equal(streaks.a, 30);
  });

  it('absorbs a 2-day freeze (activity resumes after 2 missed days)', () => {
    const prev = { a: { streak: 40, lastActive: D(1) } };
    const { streaks } = nextSalawatStreaks(prev, ['a'], D(4));
    assert.equal(streaks.a, 43);
  });

  it('resets after 3+ consecutive inactive days, then restarts at 1', () => {
    const prev = { a: { streak: 40, lastActive: D(1) } };
    const reset = nextSalawatStreaks(prev, [], D(4)); // 3 inactive days
    assert.equal(reset.streaks.a, undefined);
    const restart = nextSalawatStreaks(reset.state, ['a'], D(5));
    assert.equal(restart.streaks.a, 1);
  });

  it('keeps the streak through the 2-day freeze window while inactive', () => {
    const prev = { a: { streak: 40, lastActive: D(1) } };
    const { streaks } = nextSalawatStreaks(prev, [], D(2));
    assert.equal(streaks.a, 40);
  });

  it('is idempotent for the same day (re-run does not double-increment)', () => {
    const prev = { a: { streak: 29, lastActive: D(9) } };
    const first = nextSalawatStreaks(prev, ['a', 'b'], D(10));
    const second = nextSalawatStreaks(first.state, ['a', 'b'], D(10));
    assert.deepEqual(second.streaks, first.streaks);
    assert.deepEqual(second.state, first.state);
  });

  it('tolerates a malformed previous state without throwing', () => {
    const prev = {
      a: { streak: 'garbage', lastActive: null },
      b: null,
      c: { streak: -5, lastActive: 'not-a-date' },
    };
    const { streaks } = nextSalawatStreaks(prev, ['a', 'b', 'c'], D(10));
    assert.equal(streaks.a, 1);
    assert.equal(streaks.b, 1);
    assert.equal(streaks.c, 1);
  });
});

describe('buildSnapshot + validateSnapshot', () => {
  const dateStr = '2026-11-14';

  it('contains all 10 challenges even with no data (empty lists)', () => {
    const snap = buildSnapshot({ dateStr, challenges: {}, salawatStreaks: {} });
    assert.deepEqual(Object.keys(snap.challenges), CHALLENGE_KEYS);
    assert.deepEqual(snap.challenges.dhikr, { '30_days': [], '60_days': [], '90_days': [] });
    const counts = validateSnapshot(snap);
    assert.equal(counts.salawat['30_days'], 0);
  });

  it('is idempotent: same inputs → same JSON bytes', () => {
    const challenges = { dhikr: [user('u1', 35), user('u2', 61)] };
    const salawat = { u3: 90, u4: 12 };
    const a = buildSnapshot({ dateStr, challenges, salawatStreaks: salawat });
    const b = buildSnapshot({ dateStr, challenges, salawatStreaks: salawat });
    assert.equal(JSON.stringify(a), JSON.stringify(b));
  });

  it('carries generatedAt derived from the date, date and source', () => {
    const snap = buildSnapshot({ dateStr, challenges: {}, salawatStreaks: {} });
    assert.equal(snap.generatedAt, `${dateStr}T12:00:00Z`);
    assert.equal(snap.date, dateStr);
    assert.equal(snap.source, 'firebase-rtdb');
  });

  it('drops duplicate public ids derived from different uids sharing a tail', () => {
    // Two distinct uids whose last 6 chars collide → same public id. The bucket
    // keeps both (they are distinct users); validation must not fail on that.
    const buckets = bucketByMilestone([user('aaaa-Xy1234', 31), user('bbbb-Xy1234', 33)]);
    assert.equal(buckets['30_days'].length, 2);
    validateSnapshot(buildSnapshot({ dateStr, challenges: { dhikr: buckets['30_days'].map(e => ({ uid: e.id, streak: e.streak })) }, salawatStreaks: {} }));
  });

  it('rejects a leaked private field', () => {
    const snap = buildSnapshot({ dateStr, challenges: { dhikr: [user('u1', 35)] }, salawatStreaks: {} });
    snap.challenges.dhikr['30_days'][0].fcmToken = 'SECRET';
    assert.throws(() => validateSnapshot(snap), /non-public field/);
  });

  it('rejects a forbidden key anywhere in the tree', () => {
    const snap = buildSnapshot({ dateStr, challenges: {}, salawatStreaks: {} });
    snap.tokens = { someone: 'SECRET' }; // top level: only the deep scan sees it
    assert.throws(() => validateSnapshot(snap), /forbidden key/);
  });

  it('rejects a streak below its milestone minimum', () => {
    const snap = buildSnapshot({ dateStr, challenges: {}, salawatStreaks: {} });
    snap.challenges.salawat['60_days'] = [{ id: 'A', name: 'B', streak: 45 }];
    assert.throws(() => validateSnapshot(snap), /out of range/);
  });

  it('rejects a long (non-public) id', () => {
    const snap = buildSnapshot({ dateStr, challenges: {}, salawatStreaks: {} });
    snap.challenges.salawat['30_days'] = [{ id: 'FULL-UID-1234', name: 'B', streak: 30 }];
    assert.throws(() => validateSnapshot(snap), /short public id/);
  });

  it('rejects a missing challenge section', () => {
    const snap = buildSnapshot({ dateStr, challenges: {}, salawatStreaks: {} });
    delete snap.challenges.quran;
    assert.throws(() => validateSnapshot(snap), /challenge missing/);
  });

  it('rejects a malformed date', () => {
    const snap = buildSnapshot({ dateStr: '14/11/2026', challenges: {}, salawatStreaks: {} });
    assert.throws(() => validateSnapshot(snap), /bad date/);
  });
});

describe('roundKeyForDate', () => {
  it('maps a Cairo day to the round closing the following Friday', () => {
    // 2026-11-14 is a Saturday; the round ends Friday 2026-11-20.
    assert.equal(roundKeyForDate('2026-11-14'), '2026-11-20');
    // On the Friday itself the round closes that day.
    assert.equal(roundKeyForDate('2026-11-20'), '2026-11-20');
  });
});

describe('parseArgs', () => {
  it('parses --dry-run and --date, exits on a malformed date', () => {
    assert.deepEqual(parseArgs(['--dry-run', '--date', '2026-11-14']), { dryRun: true, date: '2026-11-14' });
    assert.throws(() => parseArgs(['--date', 'tomorrow']));
  });
});

describe('writeSnapshotFiles — atomicity', () => {
  it('writes latest.json and the dated file, leaves no tmp dir behind', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ahl-adhikr-out-'));
    const snap = buildSnapshot({ dateStr: '2026-11-14', challenges: {}, salawatStreaks: {} });
    writeSnapshotFiles(snap, '2026-11-14', dir);
    const latest = JSON.parse(fs.readFileSync(path.join(dir, 'latest.json'), 'utf8'));
    const dated = JSON.parse(fs.readFileSync(path.join(dir, '2026-11-14.json'), 'utf8'));
    assert.deepEqual(latest, snap);
    assert.deepEqual(dated, snap);
    const leftovers = fs.readdirSync(path.dirname(dir)).filter(n => n.startsWith('.ahl-adhikr-tmp-'));
    assert.deepEqual(leftovers, []);
    fs.rmSync(dir, { recursive: true, force: true });
  });

  it('overwrites a previous latest.json with identical bytes on a re-run', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ahl-adhikr-out-'));
    const snap = buildSnapshot({ dateStr: '2026-11-14', challenges: {}, salawatStreaks: {} });
    writeSnapshotFiles(snap, '2026-11-14', dir);
    const first = fs.readFileSync(path.join(dir, 'latest.json'), 'utf8');
    writeSnapshotFiles(snap, '2026-11-14', dir);
    const second = fs.readFileSync(path.join(dir, 'latest.json'), 'utf8');
    assert.equal(first, second);
    fs.rmSync(dir, { recursive: true, force: true });
  });
});

describe('withRetry', () => {
  it('retries a transient failure and returns the eventual result', async () => {
    let calls = 0;
    const result = await withRetry('read', async () => {
      calls++;
      if (calls < 3) throw new Error('transient');
      return 42;
    }, 3, 0);
    assert.equal(result, 42);
    assert.equal(calls, 3);
  });

  it('rejects after the final attempt without retrying forever', async () => {
    let calls = 0;
    await assert.rejects(
      withRetry('read', async () => { calls++; throw new Error('down'); }, 2, 0),
      /read failed after 2 attempts: down/,
    );
    assert.equal(calls, 2);
  });
});

describe('readChallengeRankedUsers — streak propagation', () => {
  it('carries each participant\'s client streak into the ranked users', async () => {
    const child = (uid, count, streak) => ({
      key: uid,
      val: () => ({ count, streak, data: { uid, nickname: `n-${uid}` } }),
    });
    const snap = {
      exists: () => true,
      forEach: (cb) => [child('u29', 7, 29), child('u30', 5, 30)].forEach(cb),
    };
    const db = { ref: () => ({ get: async () => snap }) };

    const ranked = await readChallengeRankedUsers(db, DHIKR_CHALLENGE_ROOT, '2026-11-14');

    assert.deepEqual(
      Object.fromEntries(ranked.map(u => [u.uid, u.streak])),
      { u29: 29, u30: 30 },
    );
    // And the milestone bucketing sees those streaks.
    assert.equal(bucketByMilestone(ranked)['30_days'].length, 1);
  });
});
