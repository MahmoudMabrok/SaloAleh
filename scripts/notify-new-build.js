const admin = require('firebase-admin');
const { publishLatestVersion } = require('./app-config-utils');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const version = process.env.NEW_BUILD_VERSION || '';
const track = process.env.GOOGLE_PLAY_TRACK || 'production';

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

async function main() {
  console.log(`[notify-new-build] ===== run start ===== version=${version} track=${track}`);

  const title = 'تحديث جديد قادم 🎉';
  const body = version
    ? `الإصدار ${version} في الطريق إليك — قد يستغرق ظهوره بعض الوقت، تحقق من المتجر قريباً`
    : 'إصدار جديد من التطبيق في الطريق إليك — قد يستغرق ظهوره بعض الوقت، تحقق من المتجر قريباً';

  // Single fan-out via the "general" FCM topic — both Android and iOS subscribe
  // on launch once notifications are granted, so one send reaches everyone.
  const msgId = await admin.messaging().send({
    topic: 'general',
    notification: { title, body },
    data: {
      title,
      body,
      notification_type: 'version_update',
      new_version: version,
    },
  });

  console.log(`[notify-new-build] broadcast to topic "general" msgId=${msgId}`);

  // Publish the version to remote config so clients on older builds show the
  // in-app update prompt alongside this notification.
  await publishLatestVersion(admin.database(), version);

  console.log('[notify-new-build] ===== run end =====');
  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
