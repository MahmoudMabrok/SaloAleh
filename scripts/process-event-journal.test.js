const test = require('node:test');
const assert = require('node:assert/strict');
const { badgeForCount, derivePlayerFields } = require('./process-event-journal');

test('derives todayCount from only current-Cairo-day events and server growth', () => {
  const today = '2026-11-12';
  const todayAt = Date.parse('2026-11-12T10:00:00Z');
  const yesterdayAt = Date.parse('2026-11-11T20:00:00Z');
  const result = derivePlayerFields({
    totalCount: 450,
    yesterdayTotalScore: 100,
    events: {
      a: { delta: 200, at: todayAt },
      b: { delta: 100, at: todayAt + 1000 },
      c: { delta: 999, at: yesterdayAt },
    },
  }, today);
  assert.deepEqual(result, { todayCount: 300, dailyBadge: 'heart' });
});

test('caps journal-derived count by server growth and daily limit', () => {
  const today = '2026-11-12';
  const at = Date.parse('2026-11-12T10:00:00Z');
  const result = derivePlayerFields({
    totalCount: 1000,
    yesterdayTotalScore: 900,
    events: { a: { delta: 50000, at } },
  }, today);
  assert.equal(result.todayCount, 100);
  assert.equal(result.dailyBadge, 'sprout');
});

test('returns the highest earned badge and clears it below the first threshold', () => {
  assert.equal(badgeForCount(0), null);
  assert.equal(badgeForCount(10), 'spark');
  assert.equal(badgeForCount(9999), 'mihrab');
  assert.equal(badgeForCount(10000), 'star');
});
