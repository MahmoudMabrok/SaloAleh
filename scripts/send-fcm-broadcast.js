const admin = require('firebase-admin');

const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT;
const databaseURL = process.env.FIREBASE_DATABASE_URL;
const title = (process.env.FCM_TITLE || '').trim();
const body = (process.env.FCM_BODY || '').trim();
const topic = process.env.FCM_TOPIC || 'general';

if (!serviceAccountJson) throw new Error('FIREBASE_SERVICE_ACCOUNT is required');
if (!databaseURL) throw new Error('FIREBASE_DATABASE_URL is required');
if (!title) throw new Error('FCM_TITLE is required');
if (!body) throw new Error('FCM_BODY is required');

admin.initializeApp({
  credential: admin.credential.cert(JSON.parse(serviceAccountJson)),
  databaseURL,
});

async function main() {
  console.log(`[send-fcm-broadcast] sending topic=${topic} title=${JSON.stringify(title)}`);

  const messageId = await admin.messaging().send({
    topic,
    notification: { title, body },
    data: {
      title,
      body,
      notification_type: 'broadcast',
    },
  });

  console.log(`[send-fcm-broadcast] sent topic=${topic} messageId=${messageId}`);
}

main().catch((error) => {
  console.error('[send-fcm-broadcast] failed:', error);
  process.exit(1);
});
