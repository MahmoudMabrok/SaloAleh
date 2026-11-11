// Anti-cheat rules: the server-owned strictMode flag, the day ceilings that bound client
// writes, and the schemaVersion gate that force-updates old builds.
const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

const ROUND = '2026-11-06';
const UID = 'player_a';
const playerPath = (uid = UID) => `mohamed_lovers/${ROUND}/players/${uid}`;
const strictModePath = (uid = UID) => `mohamed_lovers/strictMode/${uid}`;

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

// Seeds a player node as the server jobs leave it (admin write, rules bypassed).
async function seed(fields = {}, uid = UID) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref(playerPath(uid)).set({
      uid,
      schemaVersion: 2,
      countryCode: 'EG',
      updatedAt: 1,
      nickname: 'tester',
      totalCount: 0,
      ...fields,
    });
  });
}

// One client flush: the exact partial update / increment shape the app sends today.
const flush = (delta, extra = {}) => ({
  uid: UID,
  schemaVersion: 2,
  countryCode: 'EG',
  totalCount: increment(delta),
  todayCount: 0,
  updatedAt: TIMESTAMP,
  nickname: 'tester',
  ...extra,
});

describe('anti-cheat: totalCount day ceiling', () => {
  it('allows growth exactly at the 25000 cap', async () => {
    await seed({ totalCount: 1000, yesterdayTotalScore: 1000 });
    await assertSucceeds(asUser().ref(playerPath()).update(flush(25000)));
  });

  it('rejects growth beyond the 25000 cap', async () => {
    await seed({ totalCount: 1000, yesterdayTotalScore: 1000 });
    await assertFails(asUser().ref(playerPath()).update(flush(25001)));
  });

  it('treats a missing baseline as 0 (fresh round node after Friday close)', async () => {
    // No yesterdayTotalScore until the 23:45 Cairo close stamps one.
    await assertSucceeds(asUser().ref(playerPath()).update(flush(25000)));
    await assertFails(asUser().ref(playerPath()).update(flush(1)));
  });

  it('still allows corrections that lower the total', async () => {
    await seed({ totalCount: 30000, yesterdayTotalScore: 1000 });
    await assertSucceeds(
      asUser().ref(playerPath()).update(flush(0, { totalCount: 2000 })),
    );
  });

  it('rejects a negative total', async () => {
    await seed({ totalCount: 1000, yesterdayTotalScore: 1000 });
    await assertFails(asUser().ref(playerPath()).update(flush(0, { totalCount: -5 })));
  });
});

describe('anti-cheat: externalDaily bounds', () => {
  it('accepts the manual-cap range', async () => {
    await seed();
    await assertSucceeds(asUser().ref(playerPath()).update(flush(1, { 'externalDaily/2026-11-06': 15000 })));
    await assertSucceeds(asUser().ref(playerPath()).update(flush(1, { 'externalDaily/2026-11-06': -15000 })));
  });

  it('rejects values outside the manual cap', async () => {
    await seed();
    await assertFails(asUser().ref(playerPath()).update(flush(1, { 'externalDaily/2026-11-06': 15001 })));
    await assertFails(asUser().ref(playerPath()).update(flush(1, { 'externalDaily/2026-11-06': -15001 })));
  });
});

describe('anti-cheat: strictMode is server-owned', () => {
  async function seedFlag() {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(strictModePath()).set({
        since: Date.now(), reason: 'pace', dateKey: '2026-11-06', cleanDays: 0,
      });
    });
  }

  it('lets a client read its own flag', async () => {
    await seedFlag();
    const snap = await asUser().ref(strictModePath()).get();
    if (!snap.exists() || snap.val().reason !== 'pace') throw new Error('flag not readable');
  });

  it('rejects a client creating a flag', async () => {
    await assertFails(asUser().ref(strictModePath('sneaky')).set({ since: 1, reason: 'pace' }));
  });

  it('rejects a client updating or clearing a flag', async () => {
    await seedFlag();
    await assertFails(asUser().ref(strictModePath()).update({ cleanDays: 99 }));
    await assertFails(asUser().ref(strictModePath()).remove());
  });
});

describe('anti-cheat: schemaVersion gate (force update)', () => {
  it('rejects a pre-schema-2 flush on an existing node', async () => {
    await seed({ totalCount: 100 });
    await assertFails(asUser().ref(playerPath()).update(flush(5, { schemaVersion: 1 })));
  });

  it('rejects a new node created by a pre-schema-2 client', async () => {
    await assertFails(asUser().ref(playerPath()).update(flush(3, { schemaVersion: 1 })));
  });

  it('accepts the current schema', async () => {
    await seed({ totalCount: 100 });
    await assertSucceeds(asUser().ref(playerPath()).update(flush(5)));
  });
});
