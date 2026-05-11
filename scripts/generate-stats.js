// Reads live data from Firebase RTDB and writes stats.json to the repo root.
// Run daily via GitHub Actions (update-stats.yml) so landing.html always shows
// current numbers without any client-side Firebase credentials.
const admin = require('firebase-admin');
const fs    = require('fs');
const path  = require('path');

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const databaseURL    = process.env.FIREBASE_DATABASE_URL;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });

// Mirrors cairoRoundKey() in other scripts — returns the active round's key.
function cairoRoundKey() {
  const now  = new Date();
  const zone = 'Africa/Cairo';
  const weekdayStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(now);
  const dayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  const jsDow  = dayMap[weekdayStr];
  const hourStr = new Intl.DateTimeFormat('en-US', { timeZone: zone, hour: 'numeric', hour12: false }).format(now);
  const cairoHour = parseInt(hourStr, 10);
  let daysToFriday = (5 - jsDow + 7) % 7;
  if (daysToFriday === 0 && cairoHour >= 18) daysToFriday = 7;
  const fridayDate = new Date(now.getTime() + daysToFriday * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
}

async function main() {
  const db       = admin.database();
  const roundKey = cairoRoundKey();
  console.log(`Active round: ${roundKey}`);

  const [allTimeTotalSnap, playersSnap, roundTotalSnap] = await Promise.all([
    db.ref('mohamed_lovers/allTimeTotal').get(),
    db.ref(`mohamed_lovers/${roundKey}/players`).get(),
    db.ref(`mohamed_lovers/${roundKey}/roundTotal`).get(),
  ]);

  const weekSalawat    = roundTotalSnap.val()    || 0;
  const allTimeSalawat = (allTimeTotalSnap.val() || 0) + weekSalawat;

  let activePlayers = 0;
  let topScore      = 0;
  const countries   = new Set();

  if (playersSnap.exists()) {
    playersSnap.forEach(child => {
      const data  = child.val();
      const score = data?.totalCount || 0;
      activePlayers++;
      if (score > topScore) topScore = score;
      if (data?.countryCode) countries.add(data.countryCode);
    });
  }

  const stats = {
    allTimeSalawat,
    weekSalawat,
    activePlayers,
    countriesCount: countries.size,
    countries: [...countries].sort(),
    topScore,
    roundKey,
    updatedAt: new Date().toISOString(),
  };

  const outPath = path.join(__dirname, '..', 'stats.json');
  fs.writeFileSync(outPath, JSON.stringify(stats, null, 2));
  console.log('stats.json written:', stats);

  process.exit(0);
}

main().catch(err => { console.error(err); process.exit(1); });
