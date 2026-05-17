// Round key = ISO date of next Friday 18:00 in Africa/Cairo.
// Matches CompetitionWindowUtils.kt on the mobile app.

const ROUND_BOUNDARY_HOUR = 18;
const CAIRO_TZ = "Africa/Cairo";

function cairoDateParts(date = new Date()) {
  const fmt = new Intl.DateTimeFormat("en-CA", {
    timeZone: CAIRO_TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = Object.fromEntries(
    fmt.formatToParts(date).map((p) => [p.type, p.value]),
  );
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour) === 24 ? 0 : Number(parts.hour),
  };
}

function formatYmd(year, month, day) {
  const mm = String(month).padStart(2, "0");
  const dd = String(day).padStart(2, "0");
  return `${year}-${mm}-${dd}`;
}

function currentRoundKey(now = new Date()) {
  const { year, month, day, hour } = cairoDateParts(now);
  // Anchor at UTC midnight of Cairo's calendar date so getUTCDay()
  // returns Cairo's day-of-week without needing further timezone math.
  const anchor = new Date(Date.UTC(year, month - 1, day));
  const dow = anchor.getUTCDay(); // 0=Sun, 5=Fri
  let daysUntilFriday = (5 - dow + 7) % 7;
  if (daysUntilFriday === 0 && hour >= ROUND_BOUNDARY_HOUR) {
    daysUntilFriday = 7;
  }
  const target = new Date(anchor.getTime() + daysUntilFriday * 86400000);
  return formatYmd(
    target.getUTCFullYear(),
    target.getUTCMonth() + 1,
    target.getUTCDate(),
  );
}

function isFridayBonus(now = new Date()) {
  const { hour } = cairoDateParts(now);
  const anchor = (() => {
    const { year, month, day } = cairoDateParts(now);
    return new Date(Date.UTC(year, month - 1, day));
  })();
  return anchor.getUTCDay() === 5 && hour < ROUND_BOUNDARY_HOUR;
}

self.SaloRound = { currentRoundKey, isFridayBonus, ROUND_BOUNDARY_HOUR };
