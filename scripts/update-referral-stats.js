// Daily script: precomputes referral stats (referralCount, salawatTotal) for
// every user who has referrals, and writes them to referral_stats/{code} so the
// landing page can display them without auth.
// Usage: node scripts/update-referral-stats.js

const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

const db = admin.database();

async function main() {
  console.log('Updating referral stats...');

  const codesSnap = await db.ref('mohamed_lovers/referral_codes').get();
  if (!codesSnap.exists()) {
    console.log('No referral codes found.');
    process.exit(0);
  }

  // Build code → uid mapping
  const codeToUid = {};
  codesSnap.forEach(child => {
    const uid = child.val();
    if (typeof uid === 'string') {
      codeToUid[child.key] = uid;
    }
  });

  const codeCount = Object.keys(codeToUid).length;
  console.log(`Found ${codeCount} referral codes`);

  const usersSnap = await db.ref('mohamed_lovers/users').get();
  if (!usersSnap.exists()) {
    console.log('No users found.');
    process.exit(0);
  }

  const writes = {};
  let updatedCount = 0;

  for (const [code, uid] of Object.entries(codeToUid)) {
    const userSnap = usersSnap.child(uid);
    if (!userSnap.exists()) continue;

    const referralsSnap = userSnap.child('referrals');
    if (!referralsSnap.exists()) continue;

    const referredUids = [];
    referralsSnap.forEach(child => {
      if (child.key) referredUids.push(child.key);
    });

    if (referredUids.length === 0) continue;

    let salawatTotal = 0;
    for (const referredUid of referredUids) {
      const refUserSnap = usersSnap.child(referredUid);
      const allTime = refUserSnap.child('allTimeTotal').val();
      if (typeof allTime === 'number') {
        salawatTotal += allTime;
      }
    }

    writes[`mohamed_lovers/referral_stats/${code}`] = {
      referralCount: referredUids.length,
      salawatTotal,
    };
    updatedCount++;
  }

  if (updatedCount === 0) {
    console.log('No users with referrals found.');
    process.exit(0);
  }

  console.log(`Writing stats for ${updatedCount} referral codes...`);
  await db.ref('/').update(writes);

  // Firestore mirror
  try {
    const firestore = admin.firestore();
    const BATCH_LIMIT = 500;
    const entries = Object.entries(writes);

    for (let i = 0; i < entries.length; i += BATCH_LIMIT) {
      const chunk = entries.slice(i, i + BATCH_LIMIT);
      const batch = firestore.batch();
      for (const [path, data] of chunk) {
        const code = path.split('/').pop();
        const docRef = firestore.collection('referral_stats').doc(code);
        batch.set(docRef, data, { merge: true });
      }
      await batch.commit();
      console.log(`  Firestore batch ${Math.floor(i / BATCH_LIMIT) + 1} committed (${chunk.length} docs)`);
    }
  } catch (err) {
    console.warn('Firestore mirror failed (non-blocking):', err.message);
  }

  const sample = Object.entries(writes).slice(0, 5);
  console.log('\nSample results:');
  for (const [path, data] of sample) {
    console.log(`  ${path}: count=${data.referralCount}, salawat=${data.salawatTotal}`);
  }

  console.log('\nDone.');
  process.exit(0);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
