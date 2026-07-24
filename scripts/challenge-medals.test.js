const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  awardChallengeMedals,
  attachChallengeMedals,
  DHIKR_CHALLENGE_ROOT,
} = require('./leaderboard-utils');

// RTDB stub supporting the surface the medal helpers use: get() on a path (returns
// exists/val/forEach), update() with a path→value write map, and set(). `increment`
// values written via update are captured verbatim so tests can assert on them.
function makeFakeDb(store) {
  const updates = [];
  const sets = [];
  return {
    updates,
    sets,
    ref(path) {
      return {
        async get() {
          const value = store[path];
          return {
            exists: () => value != null,
            val: () => value,
            forEach(cb) {
              for (const [key, v] of Object.entries(value || {})) {
                cb({ key, val: () => v });
              }
            },
          };
        },
        async update(map) {
          updates.push(map);
          for (const [k, v] of Object.entries(map)) store[k] = v;
        },
        async set(v) {
          sets.push({ path, value: v });
          store[path] = v;
        },
      };
    },
  };
}

const fakeAdmin = {
  database: {
    ServerValue: {
      increment: (n) => ({ __increment: n }),
    },
  },
};

describe('attachChallengeMedals', () => {
  it('copies positive medal counts onto matching entries', async () => {
    const store = {
      [`${DHIKR_CHALLENGE_ROOT}/users/champ/medals`]: { gold: 4, silver: 2, bronze: 1 },
      [`${DHIKR_CHALLENGE_ROOT}/users/partial/medals`]: { gold: 3, silver: 0 },
    };
    const db = makeFakeDb(store);
    const entries = [
      ['0', { uid: 'champ', count: 100 }],
      ['1', { uid: 'partial', count: 90 }],
      ['2', { uid: 'nobody', count: 80 }],
    ];

    await attachChallengeMedals(db, DHIKR_CHALLENGE_ROOT, entries);

    assert.equal(entries[0][1].goldMedals, 4);
    assert.equal(entries[0][1].silverMedals, 2);
    assert.equal(entries[0][1].bronzeMedals, 1);
    // Zero/absent tiers are never attached (so the app renders no pill).
    assert.equal(entries[1][1].goldMedals, 3);
    assert.equal('silverMedals' in entries[1][1], false);
    assert.equal('bronzeMedals' in entries[1][1], false);
    // A uid with no medals node stays untouched.
    assert.equal('goldMedals' in entries[2][1], false);
  });

  it('does nothing for an empty entry list', async () => {
    const db = makeFakeDb({});
    await attachChallengeMedals(db, DHIKR_CHALLENGE_ROOT, []);
    assert.equal(db.updates.length, 0);
  });
});

describe('awardChallengeMedals', () => {
  function dayUsers() {
    return {
      [`${DHIKR_CHALLENGE_ROOT}/2026-07-24/users`]: {
        a: { count: 300, data: { uid: 'a' } },
        b: { count: 200, data: { uid: 'b' } },
        c: { count: 100, data: { uid: 'c' } },
        d: { count: 50, data: { uid: 'd' } },
      },
    };
  }

  it('increments gold/silver/bronze for the top three and sets the marker', async () => {
    const store = dayUsers();
    const db = makeFakeDb(store);

    await awardChallengeMedals(db, fakeAdmin, DHIKR_CHALLENGE_ROOT, '2026-07-24');

    assert.equal(db.updates.length, 1);
    const write = db.updates[0];
    assert.deepEqual(write[`${DHIKR_CHALLENGE_ROOT}/users/a/medals/gold`], { __increment: 1 });
    assert.deepEqual(write[`${DHIKR_CHALLENGE_ROOT}/users/b/medals/silver`], { __increment: 1 });
    assert.deepEqual(write[`${DHIKR_CHALLENGE_ROOT}/users/c/medals/bronze`], { __increment: 1 });
    // 4th place gets nothing.
    assert.equal(Object.keys(write).length, 3);
    // Marker set on the day node so a re-run is a no-op.
    assert.equal(store[`${DHIKR_CHALLENGE_ROOT}/2026-07-24/medalsAwarded`], true);
  });

  it('is idempotent when the marker is already set', async () => {
    const store = dayUsers();
    store[`${DHIKR_CHALLENGE_ROOT}/2026-07-24/medalsAwarded`] = true;
    const db = makeFakeDb(store);

    await awardChallengeMedals(db, fakeAdmin, DHIKR_CHALLENGE_ROOT, '2026-07-24');

    assert.equal(db.updates.length, 0);
  });

  it('sets the marker but awards nothing when there is no eligible podium', async () => {
    const store = {
      [`${DHIKR_CHALLENGE_ROOT}/2026-07-24/users`]: {
        a: { count: 0, data: { uid: 'a' } },
      },
    };
    const db = makeFakeDb(store);

    await awardChallengeMedals(db, fakeAdmin, DHIKR_CHALLENGE_ROOT, '2026-07-24');

    assert.equal(db.updates.length, 0);
    assert.equal(store[`${DHIKR_CHALLENGE_ROOT}/2026-07-24/medalsAwarded`], true);
  });
});
