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
  if (daysToFriday === 0 && cairoHour >= 19) daysToFriday = 7;
  const fridayDate = new Date(now.getTime() + daysToFriday * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fridayDate);
}

async function main() {
  const db       = admin.database();
  const roundKey = cairoRoundKey();
  console.log(`Active round: ${roundKey}`);

  const [allTimeTotalSnap, playersSnap, roundTotalSnap, leaderboardSnap, dailyLeaderboardSnap] = await Promise.all([
    db.ref('mohamed_lovers/allTimeTotal').get(),
    db.ref(`mohamed_lovers/${roundKey}/players`).get(),
    db.ref(`mohamed_lovers/${roundKey}/roundTotal`).get(),
    db.ref(`mohamed_lovers/${roundKey}/leaderboard`).get(),
    db.ref(`mohamed_lovers/${roundKey}/dailyLeaderboard`).get(),
  ]);

  const weekSalawat    = roundTotalSnap.val()    || 0;
  const allTimeSalawat = (allTimeTotalSnap.val() || 0) + weekSalawat;

  // Compute today's count by diffing against yesterday's file (same round only)
  const dateStr = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
  const statsDir = path.join(__dirname, '..', 'stats');
  const yesterdayDate = new Date(Date.now() - 86400000);
  const yesterdayStr  = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(yesterdayDate);
  let prevWeekSalawat = 0;
  try {
    const prev = JSON.parse(fs.readFileSync(path.join(statsDir, `${yesterdayStr}.json`), 'utf8'));
    if (prev.roundKey === roundKey) prevWeekSalawat = prev.weekSalawat || 0;
  } catch {}
  const todaySalawat = Math.max(0, weekSalawat - prevWeekSalawat);

  let activePlayers = 0;
  let topScore      = 0;
  const countries   = new Set();
  const yesterdayTotalScoreUpdates = {};

  if (playersSnap.exists()) {
    playersSnap.forEach(child => {
      const data  = child.val();
      const score = data?.totalCount || 0;
      activePlayers++;
      if (score > topScore) topScore = score;
      if (data?.countryCode) countries.add(data.countryCode);
      if (child.key) {
        yesterdayTotalScoreUpdates[
          `mohamed_lovers/${roundKey}/players/${child.key}/yesterdayTotalScore`
        ] = score;
      }
    });
  }

  const leaderboard = [];
  if (leaderboardSnap.exists()) {
    const lb = leaderboardSnap.val();
    for (let i = 1; i <= 10; i++) {
      const entry = lb[String(i)];
      if (entry) leaderboard.push(entry);
    }
  }

  const stats = {
    allTimeSalawat,
    weekSalawat,
    todaySalawat,
    activePlayers,
    countriesCount: countries.size,
    countries: [...countries].sort(),
    topScore,
    roundKey,
    leaderboard,
    updatedAt: new Date().toISOString(),
  };

  if (Object.keys(yesterdayTotalScoreUpdates).length > 0) {
    await db.ref('/').update(yesterdayTotalScoreUpdates);
    console.log(`Updated yesterdayTotalScore for ${Object.keys(yesterdayTotalScoreUpdates).length} player(s).`);
  }

  // Clear dailyBadge for all players (midnight reset)
  const badgeUpdates = {};
  if (playersSnap.exists()) {
    playersSnap.forEach((child) => {
      if (child.val().dailyBadge) {
        badgeUpdates[`mohamed_lovers/${roundKey}/players/${child.key}/dailyBadge`] = null;
      }
    });
  }
  // Also clear from leaderboard entries
  if (leaderboardSnap.exists()) {
    leaderboardSnap.forEach((child) => {
      if (child.val().dailyBadge) {
        badgeUpdates[`mohamed_lovers/${roundKey}/leaderboard/${child.key}/dailyBadge`] = null;
      }
    });
  }
  if (Object.keys(badgeUpdates).length > 0) {
    await db.ref('/').update(badgeUpdates);
    console.log(`Cleared ${Object.keys(badgeUpdates).length} dailyBadge fields`);
  }

  if (!fs.existsSync(statsDir)) fs.mkdirSync(statsDir);
  const outPath = path.join(statsDir, `${dateStr}.json`);
  fs.writeFileSync(outPath, JSON.stringify(stats, null, 2));
  console.log(`stats/${dateStr}.json written:`, stats);

  await sendDailyTop3Notifications(db, dailyLeaderboardSnap);

  process.exit(0);
}

async function sendDailyTop3Notifications(db, dailyLeaderboardSnap) {
  if (!dailyLeaderboardSnap.exists()) {
    console.log('[daily-top3] no daily leaderboard data — skip');
    return;
  }
  const lb = dailyLeaderboardSnap.val();
  if (lb.isFinal) {
    console.log('[daily-top3] round is final — skip');
    return;
  }

  const medals = ['🥇', '🥈', '🥉'];
  const nameParts = [];
  for (let rank = 1; rank <= 3; rank++) {
    const entry = lb[String(rank)];
    if (!entry?.uid) break;
    const name = entry.nickname || entry.uid.slice(-6).toUpperCase();
    nameParts.push(`${medals[rank - 1]} ${name}`);
  }

  if (nameParts.length === 0) {
    console.log('[daily-top3] no entries — skip');
    return;
  }

  const title = 'أبطال اليوم 🌟';
  const body = `تهانينا للمتصدرين في الصلاة على النبي ﷺ اليوم: ${nameParts.join(' | ')}`;

  const msgId = await admin.messaging().send({
    topic: 'leaderboard_notifs',
    notification: { title, body },
    data: { title, body, notification_type: 'daily_top3' },
  });
  console.log(`[daily-top3] broadcast to topic "general" msgId=${msgId}`);
}

main().catch(err => { console.error(err); process.exit(1); });
