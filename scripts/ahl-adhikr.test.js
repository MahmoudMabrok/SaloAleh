const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  CHALLENGE_KEYS,
  MILESTONES,
  cairoDateStr,
  publicId,
  bucketByMilestone,
  salawatStreaksFromPlayers,
  buildSnapshot,
  validateSnapshot,
} = require('./ahl-adhikr-utils');
const { parseArgs, roundKeyForDate, writeSnapshotFiles, withRetry } = require('./generate-ahl-adhikr');
const { DHIKR_CHALLENGE_ROOT, readChallengeRankedUsers } = require('./leaderboard-utils');

const user = (uid, streak, nickname = '', countryCode = '') => ({ uid, count: 1, nickname, streak, rank: 1, countryCode });

describe('cairoDateStr', () => {
  it('formats a UTC instant as its Cairo calendar day', () => {
    // 21:30 UTC = 23:30 Cairo (winter) — same day. 22:30 UTC = 00:30 Cairo — next day.
    assert.equal(cairoDateStr(new Date('2026-11-14T21:30:00Z')), '2026-11-14');
    assert.equal(cairoDateStr(new Date('2026-11-14T22:30:00Z')), '2026-11-15');
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
    user('u-7', 7),
    user('u-30', 30),
    user('u-60', 60),
    user('u-90', 90),
    user('u-31', 31),
  ]);

  it('excludes zero from every list and keeps 29 in the weekly tier', () => {
    for (const { key } of MILESTONES) {
      assert.equal(buckets[key].some(e => e.id === publicId('u-zero')), false, key);
    }
    assert.equal(buckets['7_days'].some(e => e.id === publicId('u-29')), true);
    assert.equal(buckets['30_days'].some(e => e.id === publicId('u-29')), false);
  });

  it('includes a 7-day user in the weekly tier only', () => {
    assert.equal(buckets['7_days'].some(e => e.id === publicId('u-7')), true);
    assert.equal(buckets['30_days'].some(e => e.id === publicId('u-7')), false);
  });

  it('includes 30 in 30_days only', () => {
    assert.equal(buckets['30_days'].some(e => e.id === publicId('u-30')), true);
    assert.equal(buckets['60_days'].some(e => e.id === publicId('u-30')), false);
  });

  it('places 60 in 60_days only', () => {
    assert.equal(buckets['30_days'].some(e => e.id === publicId('u-60')), false);
    assert.equal(buckets['60_days'].some(e => e.id === publicId('u-60')), true);
    assert.equal(buckets['90_days'].some(e => e.id === publicId('u-60')), false);
  });

  it('places 90 in 90_days only', () => {
    assert.equal(buckets['30_days'].some(e => e.id === publicId('u-90')), false);
    assert.equal(buckets['60_days'].some(e => e.id === publicId('u-90')), false);
    assert.equal(buckets['90_days'].some(e => e.id === publicId('u-90')), true);
  });

  it('places a 91-day user once in 90_days, never in 30_days or 60_days', () => {
    const ninetyOne = publicId('u-91');
    const exclusive = bucketByMilestone([user('u-91', 91)]);
    assert.equal(exclusive['30_days'].some(e => e.id === ninetyOne), false);
    assert.equal(exclusive['60_days'].some(e => e.id === ninetyOne), false);
    assert.equal(exclusive['90_days'].filter(e => e.id === ninetyOne).length, 1);
  });

  it('sorts by streak descending with id tiebreaker', () => {
    const ids = buckets['30_days'].map(e => e.id);
    assert.deepEqual(ids, [publicId('u-31'), publicId('u-30')].map(String));
  });

  it('publishes only public identity, country, and streak', () => {
    for (const entry of buckets['30_days']) {
      assert.deepEqual(Object.keys(entry).sort(), ['countryCode', 'id', 'name', 'streak']);
    }
  });

  it('normalizes a valid country code and hides malformed values', () => {
    const entries = bucketByMilestone([
      user('egyptian', 30, '', ' eg '),
      user('unknown', 30, '', 'not-a-country'),
    ]);
    assert.equal(entries['30_days'].find(e => e.id === publicId('egyptian')).countryCode, 'EG');
    assert.equal(entries['30_days'].find(e => e.id === publicId('unknown')).countryCode, '');
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

describe('salawatStreaksFromPlayers', () => {
  it('takes the client-published roundStreak, exactly what the leaderboard shows', () => {
    const players = {
      a100: { roundStreak: 112, totalCount: 5000, nickname: '  صاحب الهمة  ', countryCode: 'eg' },
      a45: { roundStreak: 45, totalCount: 12, nickname: 'سهير', countryCode: 'sa' },
    };
    assert.deepEqual(salawatStreaksFromPlayers(players), {
      a100: { streak: 112, nickname: 'صاحب الهمة', countryCode: 'EG' },
      a45: { streak: 45, nickname: 'سهير', countryCode: 'SA' },
    });
  });

  it('publishes a salawat nickname instead of the public id when available', () => {
    const snap = buildSnapshot({
      dateStr: '2026-11-15',
      challenges: {},
      salawatStreaks: salawatStreaksFromPlayers({ named: { roundStreak: 35, nickname: 'أبو الذكر' } }),
    });
    assert.equal(snap.challenges.salawat['30_days'][0].name, 'أبو الذكر');
  });

  it('omits players without a positive numeric roundStreak (old clients, fresh joins)', () => {
    const players = {
      noField: { totalCount: 42 },
      zero: { roundStreak: 0 },
      negative: { roundStreak: -3 },
      garbage: { roundStreak: '7' },
      nullPlayer: null,
    };
    assert.deepEqual(salawatStreaksFromPlayers(players), {});
  });

  it('tolerates a missing or non-object players node without throwing', () => {
    assert.deepEqual(salawatStreaksFromPlayers(null), {});
    assert.deepEqual(salawatStreaksFromPlayers(undefined), {});
    assert.deepEqual(salawatStreaksFromPlayers('garbage'), {});
    assert.deepEqual(salawatStreaksFromPlayers({}), {});
  });

  it('feeds bucketByMilestone through buildSnapshot — 100+ day users are published', () => {
    const streaks = salawatStreaksFromPlayers({ 'abcdef112233': { roundStreak: 112 } });
    const snap = buildSnapshot({ dateStr: '2026-11-15', challenges: {}, salawatStreaks: streaks });
    const counts = validateSnapshot(snap);
    assert.equal(counts.salawat['90_days'], 1);
    assert.equal(snap.challenges.salawat['90_days'][0].streak, 112);
  });
});

describe('buildSnapshot + validateSnapshot', () => {
  const dateStr = '2026-11-14';

  it('contains all 10 challenges even with no data (empty lists)', () => {
    const snap = buildSnapshot({ dateStr, challenges: {}, salawatStreaks: {} });
    assert.deepEqual(Object.keys(snap.challenges), CHALLENGE_KEYS);
    assert.deepEqual(snap.challenges.dhikr, { '7_days': [], '30_days': [], '60_days': [], '90_days': [] });
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
  it('writes only latest.json, leaves no tmp dir behind', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ahl-adhikr-out-'));
    const snap = buildSnapshot({ dateStr: '2026-11-14', challenges: {}, salawatStreaks: {} });
    writeSnapshotFiles(snap, dir);
    const latest = JSON.parse(fs.readFileSync(path.join(dir, 'latest.json'), 'utf8'));
    assert.deepEqual(latest, snap);
    const dated = fs.readdirSync(dir).filter(n => n !== 'latest.json');
    assert.deepEqual(dated, []);
    const leftovers = fs.readdirSync(path.dirname(dir)).filter(n => n.startsWith('.ahl-adhikr-tmp-'));
    assert.deepEqual(leftovers, []);
    fs.rmSync(dir, { recursive: true, force: true });
  });

  it('overwrites a previous latest.json with identical bytes on a re-run', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ahl-adhikr-out-'));
    const snap = buildSnapshot({ dateStr: '2026-11-14', challenges: {}, salawatStreaks: {} });
    writeSnapshotFiles(snap, dir);
    const first = fs.readFileSync(path.join(dir, 'latest.json'), 'utf8');
    writeSnapshotFiles(snap, dir);
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
