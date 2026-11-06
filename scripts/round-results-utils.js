// Builds the public, poster-friendly summary of a closed Mohamed Lovers round and
// writes it under round-results/ in the repo (the repo is public, so this file must
// never carry uids, winner codes, emails, phones or any other private field).
//
// Output per round:
//   round-results/<round>.json   — the round's summary (kept forever)
//   round-results/latest.json    — copy of the newest round's summary
//   round-results/latest.md      — human-readable Arabic table of the newest round
//
// Pure helpers (no firebase-admin import) so they can be unit-tested with node:test.
const fs = require('fs');
const path = require('path');

const ANONYMOUS_NAME = 'مجهول';
const TOP_N = 10;
const DEFAULT_RESULTS_DIR = path.join(__dirname, '..', 'round-results');

// ISO-8601 timestamp in Africa/Cairo local time with its real UTC offset
// (e.g. 2026-10-30T17:10:00+02:00 in winter, +03:00 in summer DST).
function cairoIsoTimestamp(date = new Date()) {
  const parts = {};
  for (const p of new Intl.DateTimeFormat('en-US', {
    timeZone: 'Africa/Cairo',
    hourCycle: 'h23',
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    timeZoneName: 'longOffset',
  }).formatToParts(date)) {
    parts[p.type] = p.value;
  }
  // longOffset renders as "GMT+02:00" (or plain "GMT" for a zero offset).
  const offset = (parts.timeZoneName || 'GMT').replace('GMT', '') || '+00:00';
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}${offset}`;
}

function cleanName(nickname) {
  const name = typeof nickname === 'string' ? nickname.trim().slice(0, 20) : '';
  return name.length > 0 ? name : ANONYMOUS_NAME;
}

function cleanCountryCode(countryCode) {
  if (typeof countryCode !== 'string') return null;
  const code = countryCode.trim().toUpperCase();
  if (!/^[A-Z]{2,3}$/.test(code) || code === 'NA') return null;
  return code;
}

// `players` items: { score, updatedAt, nickname, countryCode, scoreMasked } — any extra
// fields (uid, winnerCode, …) are ignored and never copied into the output.
// Ranking matches aggregate-all-time.js / populateMohamedLoversRound: score desc,
// then the most recent updatedAt first.
function buildRoundResults({ round, players, roundTotal, playerCount, closedAt, limit = TOP_N }) {
  const ranked = (players || [])
    .filter(p => p && typeof p.score === 'number')
    .slice()
    .sort((a, b) => b.score - a.score || (b.updatedAt || 0) - (a.updatedAt || 0));

  const top = ranked.slice(0, limit);
  let top10Sum = 0;
  let maskedCount = 0;
  const top10 = top.map((p, i) => {
    const entry = { rank: i + 1, name: cleanName(p.nickname) };
    const countryCode = cleanCountryCode(p.countryCode);
    if (countryCode) entry.countryCode = countryCode;
    if (p.scoreMasked === true) {
      // The player hid their score in the app — keep it hidden here too, and keep
      // it out of top10Sum so it can't be derived by subtraction.
      entry.scoreMasked = true;
      maskedCount += 1;
    } else {
      entry.total = p.score;
      top10Sum += p.score;
    }
    return entry;
  });

  const results = {
    round,
    closedAt: closedAt || cairoIsoTimestamp(),
    roundTotal: typeof roundTotal === 'number' ? roundTotal : ranked.reduce((s, p) => s + p.score, 0),
    playerCount: typeof playerCount === 'number' ? playerCount : ranked.length,
    top10Sum,
    top10,
  };
  if (maskedCount > 0) results.top10MaskedCount = maskedCount;
  return results;
}

// Turns the raw value of mohamed_lovers/<round>/players (an object keyed by player id)
// into the `players` list used by aggregate-all-time.js and write-round-results.js:
// only players with a uid and totalCount > 0, plus the public-safe display fields.
// Returns { players, roundTotal } (roundTotal = sum of those players' scores).
function extractRoundPlayers(playersVal) {
  const players = [];
  let roundTotal = 0;
  for (const data of Object.values(playersVal || {})) {
    const uid = data?.uid;
    const totalCount = typeof data?.totalCount === 'number' ? data.totalCount : 0;
    if (typeof uid === 'string' && totalCount > 0) {
      players.push({
        uid,
        score: totalCount,
        updatedAt: data.updatedAt || 0,
        // Public-safe display fields, used only for round-results/.
        nickname: typeof data.nickname === 'string' ? data.nickname : '',
        countryCode: typeof data.countryCode === 'string' ? data.countryCode : '',
        scoreMasked: data.scoreMasked === true,
      });
      roundTotal += totalCount;
    }
  }
  return { players, roundTotal };
}

function formatNumber(n) {
  return typeof n === 'number' ? n.toLocaleString('en-US') : String(n);
}

function escapeCell(value) {
  return String(value).replace(/\|/g, '\\|').replace(/[\r\n]+/g, ' ');
}

function renderRoundResultsMarkdown(results) {
  const lines = [
    `# نتائج جولة ${results.round}`,
    '',
    `- وقت الإغلاق (القاهرة): ${results.closedAt}`,
    `- إجمالي الصلوات في الجولة: ${formatNumber(results.roundTotal)}`,
    `- عدد المشاركين: ${formatNumber(results.playerCount)}`,
    `- مجموع العشرة الأوائل: ${formatNumber(results.top10Sum)}${results.top10MaskedCount ? ` (بدون ${results.top10MaskedCount} مخفي)` : ''}`,
    '',
    '| الترتيب | الاسم | الدولة | الإجمالي |',
    '|---|---|---|---|',
  ];
  for (const e of results.top10) {
    const total = e.scoreMasked ? 'مخفي' : formatNumber(e.total);
    lines.push(`| ${e.rank} | ${escapeCell(e.name)} | ${e.countryCode || ''} | ${total} |`);
  }
  return lines.join('\n') + '\n';
}

// Writes <round>.json always, and latest.json / latest.md only when this round is
// the newest one seen (so a manual re-run of an older CLOSED_ROUND does not clobber
// "latest"). Returns the list of written file paths.
function writeRoundResults(results, dir = process.env.ROUND_RESULTS_DIR || DEFAULT_RESULTS_DIR) {
  fs.mkdirSync(dir, { recursive: true });
  const json = JSON.stringify(results, null, 2) + '\n';
  const written = [];

  const roundFile = path.join(dir, `${results.round}.json`);
  fs.writeFileSync(roundFile, json);
  written.push(roundFile);

  const latestFile = path.join(dir, 'latest.json');
  let latestRound = null;
  try {
    latestRound = JSON.parse(fs.readFileSync(latestFile, 'utf8')).round || null;
  } catch (_) {
    latestRound = null;
  }
  if (!latestRound || String(results.round) >= String(latestRound)) {
    fs.writeFileSync(latestFile, json);
    written.push(latestFile);
    const mdFile = path.join(dir, 'latest.md');
    fs.writeFileSync(mdFile, renderRoundResultsMarkdown(results));
    written.push(mdFile);
  }
  return written;
}

module.exports = {
  ANONYMOUS_NAME,
  TOP_N,
  DEFAULT_RESULTS_DIR,
  cairoIsoTimestamp,
  buildRoundResults,
  extractRoundPlayers,
  renderRoundResultsMarkdown,
  writeRoundResults,
};
