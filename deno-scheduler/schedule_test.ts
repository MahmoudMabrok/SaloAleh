import { cairoClock, isDueInCairo, SLOTS, utcCandidateHours } from "./schedule.ts";

function assertEquals<T>(actual: T, expected: T, msg = "") {
  const a = JSON.stringify(actual);
  const e = JSON.stringify(expected);
  if (a !== e) throw new Error(`${msg} expected ${e}, got ${a}`);
}

Deno.test("cairoClock uses +2 in winter and +3 in summer", () => {
  assertEquals(cairoClock(new Date("2026-10-30T15:10:00Z")), { weekday: 5, hour: 17, minute: 10 });
  assertEquals(cairoClock(new Date("2026-07-03T14:10:00Z")), { weekday: 5, hour: 17, minute: 10 });
});

Deno.test("cron expressions", () => {
  assertEquals(SLOTS.aggregate.cron, "10 14,15 * * 5");
  assertEquals(SLOTS["update-stats"].cron, "45 20,21 * * *");
  assertEquals(SLOTS["notify-morning"].cron, "0 6,7 * * *");
  assertEquals(SLOTS["notify-evening"].cron, "0 16,17 * * *");
  assertEquals(SLOTS["notify-friday"].cron, "0 6,7,8,9,10,11,12,13,14,15 * * 5");
  assertEquals(utcCandidateHours([1]), [22, 23]);
});

Deno.test("aggregate dispatches exactly once per Friday, winter and summer", () => {
  // Winter (UTC+2): 14:10Z = 16:10 Cairo (skip), 15:10Z = 17:10 Cairo (run).
  assertEquals(isDueInCairo(SLOTS.aggregate, new Date("2026-11-06T14:10:00Z")), false);
  assertEquals(isDueInCairo(SLOTS.aggregate, new Date("2026-11-06T15:10:00Z")), true);
  // Summer (UTC+3): 14:10Z = 17:10 Cairo (run), 15:10Z = 18:10 Cairo (skip).
  assertEquals(isDueInCairo(SLOTS.aggregate, new Date("2026-07-03T14:10:00Z")), true);
  assertEquals(isDueInCairo(SLOTS.aggregate, new Date("2026-07-03T15:10:00Z")), false);
});

Deno.test("every Cairo slot passes exactly once per intended hour in both seasons", () => {
  const days = ["2026-11-06", "2026-07-03"]; // a winter Friday and a summer Friday
  for (const [name, slot] of Object.entries(SLOTS)) {
    const [minuteField, hourField] = slot.cron.split(" ");
    for (const day of days) {
      const passedHours: number[] = [];
      for (const utcHour of hourField.split(",").map(Number)) {
        const d = new Date(`${day}T${String(utcHour).padStart(2, "0")}:${minuteField.padStart(2, "0")}:00Z`);
        if (isDueInCairo(slot, d)) passedHours.push(cairoClock(d).hour);
      }
      assertEquals(passedHours.sort((a, b) => a - b), [...slot.cairoHours].sort((a, b) => a - b), `${name} ${day}`);
    }
  }
});

Deno.test("friday-only slots never pass on other days", () => {
  assertEquals(isDueInCairo(SLOTS["notify-friday"], new Date("2026-11-05T09:00:00Z")), false); // Thursday
  assertEquals(isDueInCairo(SLOTS.aggregate, new Date("2026-11-07T15:10:00Z")), false); // Saturday
});
