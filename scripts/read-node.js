// Prints one Realtime Database node as JSON for debugging (manual workflow only).
// The repo is public, so its Actions logs are public: only paths under mohamed_lovers/
// are allowed and large nodes are refused rather than dumped.
const admin = require('firebase-admin');

const MAX_BYTES = 20000;
const path = (process.env.NODE_PATH_INPUT || '').trim().replace(/^\/+|\/+$/g, '');
const shallow = process.env.SHALLOW === 'true';

if (!/^mohamed_lovers\/[A-Za-z0-9_\-:]+(\/[A-Za-z0-9_\-:]+)*$/.test(path)) {
  console.error(`Refusing path "${path}": it must be a node under mohamed_lovers/ (e.g. mohamed_lovers/<round>/players/<uid>).`);
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)),
  databaseURL: process.env.FIREBASE_DATABASE_URL,
});

(async () => {
  const snap = await admin.database().ref(path).get();
  let value = snap.val();
  if (shallow && value && typeof value === 'object') {
    value = Object.fromEntries(Object.entries(value).map(([k, v]) => [k, v && typeof v === 'object' ? '{…}' : v]));
  }
  const json = JSON.stringify(value, null, 2);
  if (json && json.length > MAX_BYTES) {
    console.error(`Node is ${json.length} bytes (limit ${MAX_BYTES}); use a deeper path or shallow=true.`);
    process.exit(1);
  }
  console.log(`${path}:`);
  console.log(json ?? 'null');
  process.exit(0);
})().catch((err) => { console.error(err); process.exit(1); });
