const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  ANONYMOUS_NAME,
  cairoIsoTimestamp,
  buildRoundResults,
  extractRoundPlayers,
  renderRoundResultsMarkdown,
  writeRoundResults,
} = require('./round-results-utils');

function player(overrides) {
  return {
    uid: 'secret-uid',
    winnerCode: 'SECRET12',
    score: 0,
    updatedAt: 0,
    nickname: '',
    countryCode: 'EG',
    scoreMasked: false,
    ...overrides,
  };
}

describe('cairoIsoTimestamp', () => {
  it('uses +02:00 in winter (after Egyptian DST ends)', () => {
    assert.equal(cairoIsoTimestamp(new Date('2026-10-30T15:10:00Z')), '2026-10-30T17:10:00+02:00');
  });

  it('uses +03:00 in summer DST', () => {
    assert.equal(cairoIsoTimestamp(new Date('2026-07-03T16:10:00Z')), '2026-07-03T19:10:00+03:00');
  });
});

describe('buildRoundResults', () => {
  const players = Array.from({ length: 12 }, (_, i) => player({
    uid: `u${i}`,
    score: (12 - i) * 100,
    updatedAt: i,
    nickname: i === 1 ? '' : `name${i}`,
  }));

  it('keeps only the top 10 ranked by score desc', () => {
    const r = buildRoundResults({ round: '2026-10-30', players, roundTotal: 7800, playerCount: 12, closedAt: 'x' });
    assert.equal(r.top10.length, 10);
    assert.deepEqual(r.top10.map(e => e.rank), [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    assert.equal(r.top10[0].total, 1200);
    assert.equal(r.top10[9].total, 300);
    assert.equal(r.top10Sum, 1200 + 1100 + 1000 + 900 + 800 + 700 + 600 + 500 + 400 + 300);
    assert.equal(r.roundTotal, 7800);
    assert.equal(r.playerCount, 12);
    assert.equal(r.round, '2026-10-30');
    assert.equal(r.closedAt, 'x');
  });

  it('falls back to the anonymous name when nickname is empty', () => {
    const r = buildRoundResults({ round: 'r', players, closedAt: 'x' });
    assert.equal(r.top10[1].name, ANONYMOUS_NAME);
    assert.equal(r.top10[0].name, 'name0');
  });

  it('breaks score ties by most recent updatedAt (same as the leaderboard)', () => {
    const r = buildRoundResults({
      round: 'r',
      closedAt: 'x',
      players: [
        player({ nickname: 'older', score: 50, updatedAt: 1 }),
        player({ nickname: 'newer', score: 50, updatedAt: 2 }),
      ],
    });
    assert.deepEqual(r.top10.map(e => e.name), ['newer', 'older']);
  });

  it('hides masked scores and keeps them out of top10Sum', () => {
    const r = buildRoundResults({
      round: 'r',
      closedAt: 'x',
      players: [
        player({ nickname: 'a', score: 300 }),
        player({ nickname: 'b', score: 200, scoreMasked: true }),
        player({ nickname: 'c', score: 100 }),
      ],
    });
    assert.equal('total' in r.top10[1], false);
    assert.equal(r.top10[1].scoreMasked, true);
    assert.equal(r.top10Sum, 400);
    assert.equal(r.top10MaskedCount, 1);
  });

  it('never leaks uid or winner codes', () => {
    const r = buildRoundResults({ round: 'r', players, closedAt: 'x' });
    const json = JSON.stringify(r);
    assert.equal(json.includes('secret-uid'), false);
    assert.equal(json.includes('SECRET12'), false);
    assert.equal(json.includes('uid'), false);
  });

  it('drops placeholder / invalid country codes', () => {
    const r = buildRoundResults({
      round: 'r',
      closedAt: 'x',
      players: [
        player({ score: 3, countryCode: 'eg' }),
        player({ score: 2, countryCode: 'NA' }),
        player({ score: 1, countryCode: 'not a code' }),
      ],
    });
    assert.equal(r.top10[0].countryCode, 'EG');
    assert.equal('countryCode' in r.top10[1], false);
    assert.equal('countryCode' in r.top10[2], false);
  });
});

describe('extractRoundPlayers', () => {
  it('keeps players with a uid and totalCount > 0, with public display fields only', () => {
    const { players, roundTotal } = extractRoundPlayers({
      k1: { uid: 'u1', totalCount: 30, updatedAt: 5, nickname: 'a', countryCode: 'EG', scoreMasked: true, winnerCode: 'X' },
      k2: { uid: 'u2', totalCount: 0 },
      k3: { totalCount: 10 },
      k4: { uid: 'u4', totalCount: 12 },
    });
    assert.equal(roundTotal, 42);
    assert.deepEqual(players, [
      { uid: 'u1', score: 30, updatedAt: 5, nickname: 'a', countryCode: 'EG', scoreMasked: true },
      { uid: 'u4', score: 12, updatedAt: 0, nickname: '', countryCode: '', scoreMasked: false },
    ]);
  });

  it('handles a missing players node', () => {
    assert.deepEqual(extractRoundPlayers(null), { players: [], roundTotal: 0 });
  });
});

describe('renderRoundResultsMarkdown', () => {
  it('renders a table row per entry and escapes pipes', () => {
    const md = renderRoundResultsMarkdown({
      round: '2026-10-30',
      closedAt: '2026-10-30T17:10:00+02:00',
      roundTotal: 1000,
      playerCount: 5,
      top10Sum: 900,
      top10: [
        { rank: 1, name: 'a|b', countryCode: 'EG', total: 600 },
        { rank: 2, name: 'c', scoreMasked: true },
      ],
    });
    assert.match(md, /# نتائج جولة 2026-10-30/);
    assert.match(md, /\| 1 \| a\\\|b \| EG \| 600 \|/);
    assert.match(md, /\| 2 \| c \|  \| مخفي \|/);
  });
});

describe('writeRoundResults', () => {
  it('writes the round file + latest, and does not move latest backwards', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'round-results-'));
    const newer = buildRoundResults({ round: '2026-10-30', players: [player({ score: 5 })], closedAt: 'x' });
    const older = buildRoundResults({ round: '2026-10-23', players: [player({ score: 9 })], closedAt: 'y' });

    writeRoundResults(newer, dir);
    assert.ok(fs.existsSync(path.join(dir, '2026-10-30.json')));
    assert.ok(fs.existsSync(path.join(dir, 'latest.md')));
    assert.equal(JSON.parse(fs.readFileSync(path.join(dir, 'latest.json'), 'utf8')).round, '2026-10-30');

    writeRoundResults(older, dir);
    assert.ok(fs.existsSync(path.join(dir, '2026-10-23.json')));
    assert.equal(JSON.parse(fs.readFileSync(path.join(dir, 'latest.json'), 'utf8')).round, '2026-10-30');
  });
});
