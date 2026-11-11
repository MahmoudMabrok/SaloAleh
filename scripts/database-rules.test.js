// Rules tests for the anti-cheat backstop in database.rules.json. Runs against the
// RTDB emulator:
//   firebase emulators:exec --only database --project demo-rules "node --test database-rules.test.js"
// Fixtures are written with the Admin SDK (bypasses rules, like generate-stats.js);
// assertions use plain unauthenticated REST calls, exactly like a client write.
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const admin = require('firebase-admin');

const HOST = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
if (!HOST) {
  throw new Error('FIREBASE_DATABASE_EMULATOR_HOST not set — run via firebase emulators:exec');
}

admin.initializeApp({ databaseURL: `https://demo-rules-default-rtdb.firebaseio.com` });
const db = admin.database();

const ROUND = '2026-11-13';
// The emulator routes by namespace; without ?ns= client calls land on an unruled
// default namespace instead of the one the rules were loaded into.
const base = `http://${HOST}`;
const ns = 'demo-rules-default-rtdb';

async function clientPatch(path, body) {
  const res = await fetch(`${base}/${path}.json?ns=${ns}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return res.status;
}

async function clientPut(path, body) {
  const res = await fetch(`${base}/${path}.json?ns=${ns}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return res.status;
}

async function clientGet(path) {
  const res = await fetch(`${base}/${path}.json?ns=${ns}`);
  return { status: res.status, body: await res.json() };
}

describe('totalCount day ceiling (vs server-stamped yesterdayTotalScore)', () => {
  before(async () => {
    await db.ref(`mohamed_lovers/${ROUND}/players/baselineUser`).set({
      uid: 'baselineUser', schemaVersion: 2, yesterdayTotalScore: 1000, totalCount: 1000,
    });
    await db.ref(`mohamed_lovers/${ROUND}/players/freshUser`).set({
      uid: 'freshUser', schemaVersion: 2, totalCount: 0,
    });
  });

  it('allows growth exactly at the 25000 cap', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/baselineUser`, { totalCount: 26000 }), 200);
  });

  it('rejects growth beyond the 25000 cap', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/baselineUser`, { totalCount: 26001 }), 401);
  });

  it('treats a missing baseline as 0', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/freshUser`, { totalCount: 25000 }), 200);
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/freshUser`, { totalCount: 25001 }), 401);
  });

  it('still rejects negative totals', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/baselineUser`, { totalCount: -5 }), 401);
  });

  it('allows corrections that lower the total', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/baselineUser`, { totalCount: 2000 }), 200);
  });
});

describe('externalDaily bounds', () => {
  before(async () => {
    await db.ref(`mohamed_lovers/${ROUND}/players/extUser`).set({
      uid: 'extUser', schemaVersion: 2,
    });
  });

  it('accepts the manual-cap range but nothing beyond it', async () => {
    const p = `mohamed_lovers/${ROUND}/players/extUser`;
    assert.equal(await clientPatch(p, { externalDaily: { '2026-11-13': 15000 } }), 200);
    assert.equal(await clientPatch(p, { externalDaily: { '2026-11-13': 15001 } }), 401);
    assert.equal(await clientPatch(p, { externalDaily: { '2026-11-13': -15000 } }), 200);
    assert.equal(await clientPatch(p, { externalDaily: { '2026-11-13': -15001 } }), 401);
    assert.equal(await clientPatch(p, { externalDaily: { '2026-11-13': 0 } }), 200);
  });
});

describe('strictMode is server-owned', () => {
  before(async () => {
    await db.ref('mohamed_lovers/strictMode/flaggedUser').set({
      since: Date.now(), reason: 'pace', dateKey: '2026-11-13', cleanDays: 0,
    });
  });

  it('rejects client writes (create)', async () => {
    assert.equal(await clientPut('mohamed_lovers/strictMode/sneakyUser', { since: 1 }), 401);
  });

  it('rejects client writes (update/erase)', async () => {
    assert.equal(await clientPatch('mohamed_lovers/strictMode/flaggedUser', { cleanDays: 99 }), 401);
    assert.equal(await clientPut('mohamed_lovers/strictMode/flaggedUser', null), 401);
  });

  it('allows clients to read their own flag', async () => {
    const { status, body } = await clientGet('mohamed_lovers/strictMode/flaggedUser');
    assert.equal(status, 200);
    assert.equal(body.reason, 'pace');
  });
});

describe('totalCount ceiling with server-side increments', () => {
  before(async () => {
    await db.ref(`mohamed_lovers/${ROUND}/players/incUser`).set({
      uid: 'incUser', schemaVersion: 2, yesterdayTotalScore: 5000, totalCount: 5000,
    });
  });

  it('accepts an increment that stays within the day cap', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/incUser`, {
      totalCount: { '.sv': { increment: 25000 } },
    }), 200);
  });

  it('rejects an increment that would push past the day cap', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/incUser`, {
      totalCount: { '.sv': { increment: 1 } },
    }), 401);
  });
});

describe('schemaVersion gate (force update)', () => {
  before(async () => {
    await db.ref(`mohamed_lovers/${ROUND}/players/currentClient`).set({
      uid: 'currentClient', schemaVersion: 2, totalCount: 100,
    });
  });

  it('rejects a downgrade to the old schema', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/currentClient`, { schemaVersion: 1 }), 401);
  });

  it('rejects a new node created by a pre-schema-2 client', async () => {
    assert.equal(await clientPut(`mohamed_lovers/${ROUND}/players/oldNew`, {
      uid: 'oldNew', schemaVersion: 1, totalCount: 1,
    }), 401);
  });

  it('accepts schemaVersion 2', async () => {
    assert.equal(await clientPatch(`mohamed_lovers/${ROUND}/players/currentClient`, { totalCount: 200 }), 200);
  });
});

// Release the admin SDK's open handles so node --test exits and emulators:exec can finish.
after(async () => {
  await admin.app().delete();
});
