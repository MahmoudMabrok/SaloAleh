const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { buildRoundResults } = require('./round-results-utils');
const { JSON_BEGIN, JSON_END, formatRoundResultsOutput } = require('./write-round-results');

describe('formatRoundResultsOutput', () => {
  const results = buildRoundResults({
    round: '2026-10-30',
    closedAt: 'x',
    players: [{ uid: 'secret-uid', score: 5, updatedAt: 1, nickname: 'a', countryCode: 'EG' }],
  });

  it('puts the Arabic table and a json block in the job summary', () => {
    const { summary } = formatRoundResultsOutput(results);
    assert.match(summary, /# نتائج جولة 2026-10-30/);
    assert.match(summary, /\| 1 \| a \| EG \| 5 \|/);
    const json = summary.split('```json\n')[1].split('\n```')[0];
    assert.deepEqual(JSON.parse(json), results);
  });

  it('wraps the JSON in log markers and never leaks uids', () => {
    const { log } = formatRoundResultsOutput(results);
    assert.equal(JSON_BEGIN, '=== ROUND_RESULTS_JSON_BEGIN ===');
    assert.equal(JSON_END, '=== ROUND_RESULTS_JSON_END ===');
    const lines = log.split('\n');
    assert.equal(lines[0], JSON_BEGIN);
    assert.equal(lines[lines.length - 1], JSON_END);
    assert.deepEqual(JSON.parse(lines.slice(1, -1).join('\n')), results);
    assert.equal(log.includes('secret-uid'), false);
  });
});
