/**
 * Cairo-local schedule for the Deno cron jobs.
 *
 * Deno.cron only speaks UTC, but Egypt observes DST (UTC+3 in summer, UTC+2 in
 * winter). So every Cairo-time job is registered at BOTH candidate UTC hours
 * (cairoHour-3 and cairoHour-2) and the handler only dispatches when the actual
 * Africa/Cairo wall clock matches. Exactly one of the two firings passes, all
 * year round, with no manual edit at the DST switch.
 */

export const CAIRO_TZ = "Africa/Cairo";

/** Friday round close, Cairo local time. Keep in sync with ROUND_BOUNDARY_HOUR (app) and scripts. */
export const ROUND_CLOSE_HOUR_CAIRO = 17;

const WEEKDAYS: Record<string, number> = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
export const FRIDAY = 5;

export interface CairoClock {
  weekday: number; // 0 = Sunday … 5 = Friday … 6 = Saturday
  hour: number; // 0–23
  minute: number;
}

export function cairoClock(date: Date = new Date()): CairoClock {
  const parts: Record<string, string> = {};
  for (
    const p of new Intl.DateTimeFormat("en-US", {
      timeZone: CAIRO_TZ,
      weekday: "short",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    }).formatToParts(date)
  ) {
    parts[p.type] = p.value;
  }
  return {
    weekday: WEEKDAYS[parts.weekday],
    hour: Number(parts.hour) % 24,
    minute: Number(parts.minute),
  };
}

export interface CairoSlot {
  /** Deno.cron expression in UTC, covering both possible UTC offsets. */
  cron: string;
  /** Cairo local hours at which the job should really run. */
  cairoHours: number[];
  /** Cairo weekdays (0 = Sunday); omit for every day. */
  weekdays?: number[];
  /** Human-readable description for /health. */
  description: string;
}

/** True when `date` (Cairo wall clock) is inside one of the slot's intended hours/days. */
export function isDueInCairo(slot: CairoSlot, date: Date = new Date()): boolean {
  const c = cairoClock(date);
  if (slot.weekdays && !slot.weekdays.includes(c.weekday)) return false;
  return slot.cairoHours.includes(c.hour);
}

/** UTC hours that can map to the given Cairo hours (UTC+2 or UTC+3). */
export function utcCandidateHours(cairoHours: number[]): number[] {
  const set = new Set<number>();
  for (const h of cairoHours) {
    set.add((h - 2 + 24) % 24);
    set.add((h - 3 + 24) % 24);
  }
  return [...set].sort((a, b) => a - b);
}

function slot(
  minute: number,
  cairoHours: number[],
  description: string,
  weekdays?: number[],
): CairoSlot {
  // Every Cairo hour used here is >= 3, so UTC = Cairo-2/-3 stays on the same
  // calendar day and the cron weekday field matches the Cairo weekday.
  const dow = weekdays ? weekdays.join(",") : "*";
  return {
    cron: `${minute} ${utcCandidateHours(cairoHours).join(",")} * * ${dow}`,
    cairoHours,
    weekdays,
    description,
  };
}

export const SLOTS = {
  // Friday 17:10 Cairo — 10 min after the 17:00 Cairo round close.
  aggregate: slot(10, [ROUND_CLOSE_HOUR_CAIRO], "Friday 17:10 Cairo (10 min after the 17:00 round close)", [FRIDAY]),
  // Daily close 23:45 Cairo.
  "update-stats": slot(45, [23], "daily 23:45 Cairo"),
  // Retention notifications.
  "notify-morning": slot(0, [9], "daily 09:00 Cairo"),
  "notify-evening": slot(0, [19], "daily 19:00 Cairo"),
  // Friday every 2h until the round close; the 17:00 run sends the round-end recap.
  "notify-friday": slot(
    0,
    [9, 11, 13, 15, ROUND_CLOSE_HOUR_CAIRO],
    "Friday 09,11,13,15,17 Cairo (every 2h until the 17:00 round close)",
    [FRIDAY],
  ),
} satisfies Record<string, CairoSlot>;
