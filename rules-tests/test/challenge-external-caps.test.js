// External-entry day counts: the rules must not block a 10,000-count external entry
// for any challenge — the daily external limit is enforced client-side only. What the
// rules keep is the 100,000 sanity bound shared by every challenge, non-negativity,
// and the own-node write guard.
const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

const DATE = '2026-11-14';
const UID = 'player_a';

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

// The day-node shapes as the app writes them. Baqiyat keeps the legacy flat shape
// ({uid, count, streak}); every other challenge nests identity under data.uid.
const baqiyatPath = (uid = UID) => `baqiyat_saliha/${DATE}/players/${uid}`;
const baqiyatDay = (count, uid = UID) => ({ uid, count, streak: 30 });

const challengePath = (challenge, uid = UID) => `${challenge}/${DATE}/users/${uid}`;
const challengeDay = (count, uid = UID) => ({
  count,
  streak: 30,
  data: { uid, date: DATE, countryCode: 'EG', nickname: 'tester' },
});

describe('challenge external-entry day counts: 10k must pass', () => {
  // Baqiyat and quran historically carried low rules caps (2,000 / 1,000) that
  // rejected the eligible 10,000 external allowance at the rules layer.
  it('accepts a 10,000 baqiyat day count', async () => {
    await assertSucceeds(asUser().ref(baqiyatPath()).set(baqiyatDay(10000)));
  });

  it('accepts a 10,000 quran day count', async () => {
    await assertSucceeds(asUser().ref(challengePath('quran_challenge')).set(challengeDay(10000)));
  });

  // Control: the challenges that were already at the shared 100,000 bound.
  it('accepts a 10,000 day count on the other challenges', async () => {
    for (const challenge of [
      '100_challenge', 'istighfar_challenge', 'zabad_challenge',
      'ghars_challenge', 'hawqala_challenge', 'alf_hasana_challenge', 'kalimat_challenge',
    ]) {
      await assertSucceeds(asUser().ref(challengePath(challenge)).set(challengeDay(10000)));
    }
  });
});

describe('challenge external-entry day counts: sanity bounds stay', () => {
  it('rejects a day count above the shared 100,000 bound', async () => {
    await assertFails(asUser().ref(baqiyatPath()).set(baqiyatDay(100001)));
    await assertFails(asUser().ref(challengePath('quran_challenge')).set(challengeDay(100001)));
    await assertFails(asUser().ref(challengePath('zabad_challenge')).set(challengeDay(100001)));
  });

  it('rejects a negative day count', async () => {
    await assertFails(asUser().ref(baqiyatPath()).set(baqiyatDay(-1)));
    await assertFails(asUser().ref(challengePath('quran_challenge')).set(challengeDay(-1)));
  });

  // Identity here is self-asserted (no Firebase Auth; App Check attests the build,
  // not the user), so the guard is "the uid field must match the node's path key" —
  // a write claiming a different uid than the node it targets is rejected.
  it('rejects a day node whose uid field mismatches its path', async () => {
    await assertFails(asUser().ref(baqiyatPath('player_b')).set(baqiyatDay(10, UID)));
    await assertFails(
      asUser().ref(challengePath('quran_challenge', 'player_b')).set(challengeDay(10, UID)),
    );
  });
});
