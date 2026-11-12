// Derive the current Cairo-day count and daily badge from the immutable score journal.
// The client may append score events, but it cannot write todayCount or dailyBadge directly.
const admin = require('firebase-admin');

const DAILY_CAP = 25_000;
const BADGES = [
  ['spark', 10],
  ['sprout', 100],
  ['heart', 200],
  ['tasbih', 500],
  ['dome', 1000],
  ['crescent', 2000],
  ['lantern', 4000],
  ['crown', 5000],
  ['mihrab', 8000],
  ['star', 10000],
];

function cairoDateFromMs(ms) {
  if (!Number.isFinite(ms)) return '';
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(ms));
}

function badgeForCount(count) {
  let badge = null;
  for (const [key, threshold] of BADGES) {
    if (count >= threshold) badge = key;
  }
  return badge;
}

function derivePlayerFields(player, today) {
  const events = player?.events && typeof player.events === 'object' ? player.events : {};
  const journalCount = Object.values(events).reduce((sum, event) => {
    if (!event || cairoDateFromMs(Number(event.at)) !== today) return sum;
    const delta = Number(event.delta);
    return Number.isFinite(delta) && delta > 0 ? sum + Math.floor(delta) : sum;
  }, 0);
  // Never expose more than the rules allow for one server baseline interval, even if
  // a malformed or duplicated journal is present.
  const serverGrowth = Math.max(0, Number(player?.totalCount || 0) - Number(player?.yesterdayTotalScore || 0));
  const todayCount = Math.min(journalCount, serverGrowth, DAILY_CAP);
  return { todayCount, dailyBadge: badgeForCount(todayCount) };
}

function cairoToday() {
  return cairoDateFromMs(Date.now());
}

async function main() {
  const rawServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT;
  const databaseURL = process.env.FIREBASE_DATABASE_URL;
  if (!rawServiceAccount || !databaseURL) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT and FIREBASE_DATABASE_URL are required');
  }
  const serviceAccount = JSON.parse(rawServiceAccount);
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });
  const db = admin.database();
  const today = cairoToday();
  const roundKey = process.env.ROUND_KEY || (() => {
    const now = new Date();
    const zone = 'Africa/Cairo';
    const weekday = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(now);
    const hour = Number(new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(now));
    const days = ({ Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 })[weekday];
    let toFriday = (5 - days + 7) % 7;
    if (toFriday === 0 && hour >= 19) toFriday = 7;
    return new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' })
      .format(new Date(now.getTime() + toFriday * 86400000));
  })();

  const snap = await db.ref(`mohamed_lovers/${roundKey}/players`).get();
  const updates = {};
  let changed = 0;
  if (snap.exists()) {
    snap.forEach(child => {
      const player = child.val() || {};
      const derived = derivePlayerFields(player, today);
      if (player.todayCount !== derived.todayCount) {
        updates[`mohamed_lovers/${roundKey}/players/${child.key}/todayCount`] = derived.todayCount;
        changed++;
      }
      const existingBadge = player.dailyBadge || null;
      if (existingBadge !== derived.dailyBadge) {
        updates[`mohamed_lovers/${roundKey}/players/${child.key}/dailyBadge`] = derived.dailyBadge;
        changed++;
      }
    });
  }
  if (Object.keys(updates).length) await db.ref('/').update(updates);
  console.log(`Event journal: round=${roundKey} day=${today} updated=${changed} field(s)`);
}

if (require.main === module) {
  let exitCode = 0;
  main()
    .catch(error => {
      console.error(error);
      exitCode = 1;
    })
    .finally(async () => {
      if (admin.apps.length) await admin.app().delete();
      process.exit(exitCode);
    });
}

module.exports = { badgeForCount, derivePlayerFields, cairoDateFromMs };
