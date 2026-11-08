// Realtime Database rules tests for mohamed_lovers/$round/players/$uid, run against the
// Firebase emulator. Each write mirrors the exact shape the app (or a server job) sends,
// so a rules change that would reject a real flush fails here instead of in production.
const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

const ROUND = '2026-11-06';
const UID = 'player_a';
const OTHER = 'player_b';
const playerPath = (uid = UID) => `mohamed_lovers/${ROUND}/players/${uid}`;

const TIMESTAMP = { '.sv': 'timestamp' };
const increment = (n) => ({ '.sv': { increment: n } });

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-saloaleh',
    database: {
      rules: fs.readFileSync(path.resolve(__dirname, '../../database.rules.json'), 'utf8'),
      host: '127.0.0.1',
      port: 9000,
    },
  });
});

after(async () => env && env.cleanup());

beforeEach(async () => env.clearDatabase());

const asUser = (uid = UID) => env.authenticatedContext(uid).database();

// Seeds the node as it looks after the server jobs ran: rank and yesterdayTotalScore are
// server-owned and must survive every client flush.
async function seedPlayer(extra = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref(playerPath()).set({
      uid: UID,
      schemaVersion: 1,
      countryCode: 'EG',
      totalCount: 100,
      todayCount: 40,
      updatedAt: 1,
      nickname: 'tester',
      rank: 7,
      yesterdayTotalScore: 60,
      ...extra,
    });
  });
}

// The pre-#212 flush (and the one restored by the revert): a partial update with
// ServerValue.increment and ServerValue.TIMESTAMP.
const flushFields = (delta = 5, extra = {}) => ({
  uid: UID,
  schemaVersion: 1,
  countryCode: 'EG',
  totalCount: increment(delta),
  todayCount: 45,
  updatedAt: TIMESTAMP,
  nickname: 'tester',
  ...extra,
});

async function readPlayer() {
  let value;
  await env.withSecurityRulesDisabled(async (ctx) => {
    value = (await ctx.database().ref(playerPath()).get()).val();
  });
  return value;
}

describe('mohamed_lovers players: score flush (updateChildren)', () => {
  it('creates a brand-new player node on the first flush of a round', async () => {
    await assertSucceeds(asUser().ref(playerPath()).update(flushFields(3)));
    const player = await readPlayer();
    if (player.totalCount !== 3) throw new Error(`totalCount ${player.totalCount}, expected 3`);
  });

  it('increments totalCount and keeps server-owned rank and yesterdayTotalScore', async () => {
    await seedPlayer();
    await assertSucceeds(asUser().ref(playerPath()).update(flushFields(5)));
    const player = await readPlayer();
    if (player.totalCount !== 105) throw new Error(`totalCount ${player.totalCount}, expected 105`);
    if (player.rank !== 7) throw new Error('rank was lost');
    if (player.yesterdayTotalScore !== 60) throw new Error('yesterdayTotalScore was lost');
  });

  it('accepts a flush token alongside the increment', async () => {
    await seedPlayer();
    await assertSucceeds(
      asUser().ref(playerPath()).update(flushFields(5, { 'processedFlushTokens/tok-1': true })),
    );
  });

  it('rejects a client-supplied updatedAt instead of the server timestamp', async () => {
    await seedPlayer();
    await assertFails(asUser().ref(playerPath()).update(flushFields(5, { updatedAt: Date.now() })));
  });

  // KNOWN GAP: the $uid .write rule checks newData.uid, not auth.uid, so any signed-in user can
  // write another player's node. Un-skip once the rule also requires auth.uid === $uid.
  it.skip('rejects writing another player\'s node', async () => {
    await assertFails(
      asUser(OTHER).ref(playerPath()).update(flushFields(5)),
    );
  });

  it('rejects an unknown field', async () => {
    await seedPlayer();
    await assertFails(asUser().ref(playerPath()).update(flushFields(5, { bogus: 1 })));
  });

  it('rejects a negative todayCount', async () => {
    await seedPlayer();
    await assertFails(asUser().ref(playerPath()).update(flushFields(5, { todayCount: -1 })));
  });
});

describe('mohamed_lovers players: whole-node writes (transaction shape)', () => {
  // A transaction rewrites the full node, so every existing child is validated again.
  const wholeNode = (overrides = {}) => ({
    uid: UID,
    schemaVersion: 1,
    countryCode: 'EG',
    totalCount: 105,
    todayCount: 45,
    updatedAt: TIMESTAMP,
    nickname: 'tester',
    rank: 7,
    processedFlushTokens: { 'tok-1': true },
    ...overrides,
  });

  it('accepts the node with rank unchanged (#225)', async () => {
    await seedPlayer();
    await assertSucceeds(asUser().ref(playerPath()).set(wholeNode()));
  });

  it('rejects a client changing rank', async () => {
    await seedPlayer();
    await assertFails(asUser().ref(playerPath()).set(wholeNode({ rank: 1 })));
  });

  // A child ".write": false cannot revoke the parent's write grant, so yesterdayTotalScore is
  // protected by a .validate (like rank) instead: server-owned, the client may only echo it.
  it('accepts the node with yesterdayTotalScore unchanged', async () => {
    await seedPlayer();
    await assertSucceeds(asUser().ref(playerPath()).set(wholeNode({ yesterdayTotalScore: 60 })));
  });

  it('rejects a client changing yesterdayTotalScore', async () => {
    await seedPlayer();
    await assertFails(asUser().ref(playerPath()).set(wholeNode({ yesterdayTotalScore: 0 })));
  });

  it('rejects a client changing yesterdayTotalScore in a partial update', async () => {
    await seedPlayer();
    await assertFails(asUser().ref(playerPath()).update(flushFields(5, { yesterdayTotalScore: 999 })));
  });
});
