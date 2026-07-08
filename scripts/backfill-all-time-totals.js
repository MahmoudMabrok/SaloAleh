// One-time script: recomputes allTimeTotal for every user by summing totalCount
// across all historical rounds except the current active round.
// Usage: CURRENT_ROUND=2026-07-10 node scripts/backfill-all-time-totals.js

const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

const db = admin.database();

const CURRENT_ROUND = process.env.CURRENT_ROUND || process.argv[2];
if (!CURRENT_ROUND) {
  console.error('CURRENT_ROUND env var or CLI arg required (e.g. 2026-07-10)');
  process.exit(1);
}

const ROUND_KEY_RE = /^\d{4}-\d{2}-\d{2}$/;

async function main() {
  console.log(`Backfilling allTimeTotal — skipping current round: ${CURRENT_ROUND}`);

  const rootSnap = await db.ref('mohamed_lovers').get();
  if (!rootSnap.exists()) {
    console.error('mohamed_lovers node not found');
    process.exit(1);
  }

  const userTotals = {};
  let roundCount = 0;

  rootSnap.forEach(child => {
    const key = child.key;
    if (!ROUND_KEY_RE.test(key)) return;
    if (key === CURRENT_ROUND) {
      console.log(`  Skipping current round: ${key}`);
      return;
    }

    const playersSnap = child.child('players');
    if (!playersSnap.exists()) return;

    roundCount++;
    let roundPlayerCount = 0;

    playersSnap.forEach(playerChild => {
      const data = playerChild.val();
      const uid = data?.uid;
      const totalCount = typeof data?.totalCount === 'number' ? data.totalCount : 0;
      if (typeof uid === 'string' && totalCount > 0) {
        userTotals[uid] = (userTotals[uid] || 0) + totalCount;
        roundPlayerCount++;
      }
    });

    console.log(`  Round ${key}: ${roundPlayerCount} players`);
  });

  const userCount = Object.keys(userTotals).length;
  console.log(`\nProcessed ${roundCount} rounds, ${userCount} unique users`);

  if (userCount === 0) {
    console.log('No users found — nothing to write.');
    process.exit(0);
  }

  const writes = {};
  for (const [uid, total] of Object.entries(userTotals)) {
    writes[`mohamed_lovers/users/${uid}/allTimeTotal`] = total;
  }

  console.log(`Writing allTimeTotal for ${userCount} users...`);
  await db.ref('/').update(writes);

  // Firestore mirror
  try {
    const firestore = admin.firestore();
    const BATCH_LIMIT = 500;
    const entries = Object.entries(userTotals);

    for (let i = 0; i < entries.length; i += BATCH_LIMIT) {
      const chunk = entries.slice(i, i + BATCH_LIMIT);
      const batch = firestore.batch();
      for (const [uid, total] of chunk) {
        const docRef = firestore.collection('mohamed_lovers_users').doc(uid);
        batch.set(docRef, { allTimeTotal: total }, { merge: true });
      }
      await batch.commit();
      console.log(`  Firestore batch ${Math.floor(i / BATCH_LIMIT) + 1} committed (${chunk.length} docs)`);
    }
  } catch (err) {
    console.warn('Firestore mirror failed (non-blocking):', err.message);
  }

  // Log a sample for verification
  const sample = Object.entries(userTotals).slice(0, 5);
  console.log('\nSample results:');
  for (const [uid, total] of sample) {
    console.log(`  ${uid}: ${total}`);
  }

  console.log('\nDone.');
  process.exit(0);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
