const admin = require('firebase-admin');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const version = process.env.NEW_BUILD_VERSION || '';
const track = process.env.GOOGLE_PLAY_TRACK || 'production';

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

async function main() {
  console.log(`[notify-new-build] ===== run start ===== version=${version} track=${track}`);
  const db = admin.database();

  const usersSnap = await db.ref('mohamed_lovers/users').get();
  if (!usersSnap.exists()) {
    console.log('[notify-new-build] no users found — exiting');
    process.exit(0);
  }

  const title = 'تحديث جديد قادم 🎉';
  const body = version
    ? `الإصدار ${version} في الطريق إليك — قد يستغرق ظهوره بعض الوقت، تحقق من المتجر قريباً`
    : 'إصدار جديد من التطبيق في الطريق إليك — قد يستغرق ظهوره بعض الوقت، تحقق من المتجر قريباً';

  const sendPromises = [];
  let tokenCount = 0;
  let noTokenCount = 0;

  usersSnap.forEach(userSnap => {
    const { fcmToken } = userSnap.val() || {};
    if (!fcmToken) { noTokenCount++; return; }
    tokenCount++;
    sendPromises.push(
      admin.messaging().send({
        token: fcmToken,
        notification: { title, body },
        data: {
          title,
          body,
          notification_type: 'version_update',
          new_version: version,
        },
      })
        .then(msgId => console.log(`[notify-new-build] sent uid=${userSnap.key} msgId=${msgId}`))
        .catch(e => console.error(`[notify-new-build] failed uid=${userSnap.key}: ${e.message}`))
    );
  });

  console.log(`[notify-new-build] sending to ${tokenCount} user(s) (${noTokenCount} skipped — no token)`);
  await Promise.all(sendPromises);
  console.log('[notify-new-build] ===== run end =====');
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
