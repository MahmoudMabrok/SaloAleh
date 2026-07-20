// One-time script: computes medal counts for every user from their achievements
// history — gold = #rank-1 finishes, silver = #rank-2, bronze = #rank-3 — and
// writes them to mohamed_lovers/users/{uid}/medals. The periodic leaderboard cron
// then copies each winner's gold count into the leaderboard so the app can render it.
// Going forward, aggregate-all-time.js increments these counts as rounds close.
// Usage: node scripts/backfill-medal-counts.js

const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

const db = admin.database();

async function main() {
  console.log('Backfilling medal counts from achievements history...');

  const usersSnap = await db.ref('mohamed_lovers/users').get();
  if (!usersSnap.exists()) {
    console.error('mohamed_lovers/users node not found');
    process.exit(1);
  }

  const medalsByUid = {};

  usersSnap.forEach(userChild => {
    const uid = userChild.key;
    const achievements = userChild.child('achievements');
    if (!achievements.exists()) return;

    let gold = 0;
    let silver = 0;
    let bronze = 0;

    achievements.forEach(achievement => {
      const rank = achievement.child('rank').val();
      if (rank === 1) gold++;
      else if (rank === 2) silver++;
      else if (rank === 3) bronze++;
    });

    if (gold > 0 || silver > 0 || bronze > 0) {
      medalsByUid[uid] = { gold, silver, bronze };
    }
  });

  const uids = Object.keys(medalsByUid);
  console.log(`Computed medals for ${uids.length} user(s) with at least one podium finish.`);

  if (uids.length === 0) {
    console.log('No medals to write — nothing to do.');
    process.exit(0);
  }

  const writes = {};
  for (const [uid, medals] of Object.entries(medalsByUid)) {
    writes[`mohamed_lovers/users/${uid}/medals`] = medals;
  }

  console.log(`Writing medals for ${uids.length} users...`);
  await db.ref('/').update(writes);

  // Firestore mirror — batched in chunks of 500, non-blocking.
  try {
    const firestore = admin.firestore();
    const BATCH_LIMIT = 500;
    const entries = Object.entries(medalsByUid);

    for (let i = 0; i < entries.length; i += BATCH_LIMIT) {
      const chunk = entries.slice(i, i + BATCH_LIMIT);
      const batch = firestore.batch();
      for (const [uid, medals] of chunk) {
        const docRef = firestore.collection('mohamed_lovers_users').doc(uid);
        batch.set(docRef, { medals }, { merge: true });
      }
      await batch.commit();
      console.log(`  Firestore batch ${Math.floor(i / BATCH_LIMIT) + 1} committed (${chunk.length} docs)`);
    }
  } catch (err) {
    console.warn('Firestore mirror failed (non-blocking):', err.message);
  }

  // Log a sample for verification.
  const sample = Object.entries(medalsByUid).slice(0, 5);
  console.log('\nSample results:');
  for (const [uid, medals] of sample) {
    console.log(`  ${uid}: gold=${medals.gold} silver=${medals.silver} bronze=${medals.bronze}`);
  }

  console.log('\nDone.');
  process.exit(0);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
