/**
 * Deno Deploy cron — triggers the SaloAleh "Populate Round Leaderboard"
 * GitHub Actions workflow every 30 minutes, plus the Cairo-time jobs below.
 *
 * Why this exists:
 *   scripts/populate-leaderboard.js relies on the firebase-admin SDK, which
 *   does not run reliably on Deno Deploy (its RTDB client breaks in the
 *   isolate runtime). So the heavy work stays in GitHub Actions on Node, and
 *   this Deno Cron job acts purely as a scheduler: it fires one HTTP request
 *   to the GitHub REST API to dispatch the workflow. GitHub's own `schedule:`
 *   cron is best-effort and frequently delayed; Deno Cron fires on time.
 *
 * Cairo time / DST:
 *   Deno.cron is UTC-only and Egypt switches between UTC+2 and UTC+3. Each
 *   Cairo-time job (see schedule.ts) is registered at both candidate UTC hours
 *   and only dispatches when the real Africa/Cairo clock matches, so the jobs
 *   keep their Cairo times across DST changes with no manual edit.
 *
 * Deploy:
 *   1. Create a project on https://dash.deno.com with this file
 *      (deno-scheduler/main.ts) as the entrypoint.
 *   2. Add an environment variable GITHUB_TOKEN — a fine-grained PAT with the
 *      repository's "Actions" permission set to Read and write (or a classic
 *      PAT with the `workflow` scope).
 *   Deno Deploy auto-detects the Deno.cron job and lists it under the
 *   project's "Cron" tab after the first production deploy.
 *
 * Local test:
 *   GITHUB_TOKEN=... deno task dev   (then GET http://localhost:8000/health)
 *   deno test deno-scheduler/        (schedule unit tests)
 */

import { type CairoSlot, cairoClock, isDueInCairo, SLOTS } from "./schedule.ts";

const OWNER = "MahmoudMabrok";
const REPO = "SaloAleh";
const REF = "main";

const cronStatus: Record<string, { lastRunAt: string | null; lastResult: string | null }> = {
  leaderboard: { lastRunAt: null, lastResult: null },
  aggregate: { lastRunAt: null, lastResult: null },
  "update-stats": { lastRunAt: null, lastResult: null },
  "notify-morning": { lastRunAt: null, lastResult: null },
  "notify-evening": { lastRunAt: null, lastResult: null },
  "notify-friday": { lastRunAt: null, lastResult: null },
};

async function dispatchWorkflow(workflowFile: string, key: string): Promise<void> {
  const token = Deno.env.get("GITHUB_TOKEN");
  if (!token) throw new Error("GITHUB_TOKEN env var is not set");

  const url =
    `https://api.github.com/repos/${OWNER}/${REPO}/actions/workflows/${workflowFile}/dispatches`;

  const res = await fetch(url, {
    method: "POST",
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "X-GitHub-Api-Version": "2022-11-28",
      "User-Agent": `${OWNER}-${REPO}-deno-cron`,
    },
    body: JSON.stringify({ ref: REF }),
  });

  cronStatus[key].lastRunAt = new Date().toISOString();

  if (res.status !== 204) {
    cronStatus[key].lastResult = `FAILED ${res.status} ${res.statusText}: ${await res.text()}`;
    throw new Error(cronStatus[key].lastResult!);
  }

  cronStatus[key].lastResult = "OK";
  console.log(`[${cronStatus[key].lastRunAt}] Dispatched ${workflowFile} on ${REF}`);
}

// Dispatches only on the firing whose Africa/Cairo wall clock matches the slot.
async function dispatchIfDueInCairo(slot: CairoSlot, workflowFile: string, key: string): Promise<void> {
  const now = new Date();
  if (!isDueInCairo(slot, now)) {
    const c = cairoClock(now);
    console.log(`[${now.toISOString()}] ${key}: Cairo ${c.hour}:${String(c.minute).padStart(2, "0")} not in ${slot.cairoHours} — skip`);
    return;
  }
  await dispatchWorkflow(workflowFile, key);
}

const BACKOFF = { backoffSchedule: [1_000, 5_000, 30_000] };

Deno.cron(
  "trigger-leaderboard-populate",
  "*/30 * * * *",
  BACKOFF,
  () => dispatchWorkflow("leaderboard-populate.yml", "leaderboard"),
);

// Friday 17:10 Cairo — close the round (17:00 Cairo) and aggregate totals.
Deno.cron(
  "trigger-aggregate-all-time",
  SLOTS.aggregate.cron,
  BACKOFF,
  () => dispatchIfDueInCairo(SLOTS.aggregate, "aggregate-all-time.yml", "aggregate"),
);

// Daily close 23:45 Cairo.
Deno.cron(
  "update-stats",
  SLOTS["update-stats"].cron,
  BACKOFF,
  () => dispatchIfDueInCairo(SLOTS["update-stats"], "update-stats.yml", "update-stats"),
);

// Retention notifications — 09:00 and 19:00 Cairo daily.
Deno.cron(
  "notify-morning",
  SLOTS["notify-morning"].cron,
  BACKOFF,
  () => dispatchIfDueInCairo(SLOTS["notify-morning"], "notify-users.yml", "notify-morning"),
);
Deno.cron(
  "notify-evening",
  SLOTS["notify-evening"].cron,
  BACKOFF,
  () => dispatchIfDueInCairo(SLOTS["notify-evening"], "notify-users.yml", "notify-evening"),
);

// Retention notifications — Friday every 2h until the 17:00 Cairo round close.
// One cron instead of the previous five (Deno Deploy cron quota).
Deno.cron(
  "notify-friday",
  SLOTS["notify-friday"].cron,
  BACKOFF,
  () => dispatchIfDueInCairo(SLOTS["notify-friday"], "notify-users.yml", "notify-friday"),
);

Deno.serve(async (req) => {
  const { pathname } = new URL(req.url);

  if (pathname === "/" || pathname === "/health") {
    const entry = (key: keyof typeof SLOTS, workflow: string) => ({
      schedule: `${SLOTS[key].cron} (UTC, both DST offsets) — ${SLOTS[key].description}`,
      workflow,
      ...cronStatus[key],
    });
    return Response.json({
      service: "saloaleh-scheduler",
      timezone: "Africa/Cairo (DST-aware)",
      cairoNow: cairoClock(),
      crons: {
        leaderboard: {
          schedule: "*/30 * * * * (UTC)",
          workflow: "leaderboard-populate.yml",
          ...cronStatus.leaderboard,
        },
        aggregate: entry("aggregate", "aggregate-all-time.yml"),
        "update-stats": entry("update-stats", "update-stats.yml"),
        "notify-morning": entry("notify-morning", "notify-users.yml"),
        "notify-evening": entry("notify-evening", "notify-users.yml"),
        "notify-friday": entry("notify-friday", "notify-users.yml"),
      },
    });
  }

  return new Response("Not found", { status: 404 });
});
