/**
 * Deno Deploy cron — triggers the SaloAleh "Populate Round Leaderboard"
 * GitHub Actions workflow every 30 minutes.
 *
 * Why this exists:
 *   scripts/populate-leaderboard.js relies on the firebase-admin SDK, which
 *   does not run reliably on Deno Deploy (its RTDB client breaks in the
 *   isolate runtime). So the heavy work stays in GitHub Actions on Node, and
 *   this Deno Cron job acts purely as a scheduler: it fires one HTTP request
 *   to the GitHub REST API to dispatch the workflow. GitHub's own `schedule:`
 *   cron is best-effort and frequently delayed; Deno Cron fires on time.
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
 */

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

Deno.cron(
  "trigger-leaderboard-populate",
  "*/30 * * * *",
  { backoffSchedule: [1_000, 5_000, 30_000] },
  () => dispatchWorkflow("leaderboard-populate.yml", "leaderboard"),
);

// Friday 16:10 UTC = 19:10 Cairo (UTC+3) — aggregate round totals after the 19:00 Cairo round close.
Deno.cron(
  "trigger-aggregate-all-time",
  "10 16 * * 5",
  { backoffSchedule: [1_000, 5_000, 30_000] },
  () => dispatchWorkflow("aggregate-all-time.yml", "aggregate"),
);

Deno.cron(
  "update-stats",
  "45 20 * * *",
  { backoffSchedule: [1_000, 5_000, 30_000] },
  () => dispatchWorkflow("update-stats.yml", "update-stats"),
);

// Retention notifications — morning daily (7 UTC = 9 AM Cairo)
Deno.cron("notify-morning", "0 7 * * *", { backoffSchedule: [1_000, 5_000, 30_000] }, () => dispatchWorkflow("notify-users.yml", "notify-morning"));

// Retention notifications — evening daily (17 UTC = 7 PM Cairo)
Deno.cron("notify-evening", "0 17 * * *", { backoffSchedule: [1_000, 5_000, 30_000] }, () => dispatchWorkflow("notify-users.yml", "notify-evening"));

// Retention notifications — Friday every 2h (8–16 UTC → until 19:00 Cairo)
Deno.cron("notify-fri-08", "0 8 * * 5", { backoffSchedule: [1_000, 5_000, 30_000] }, () => dispatchWorkflow("notify-users.yml", "notify-friday"));
Deno.cron("notify-fri-10", "0 10 * * 5", { backoffSchedule: [1_000, 5_000, 30_000] }, () => dispatchWorkflow("notify-users.yml", "notify-friday"));
Deno.cron("notify-fri-12", "0 12 * * 5", { backoffSchedule: [1_000, 5_000, 30_000] }, () => dispatchWorkflow("notify-users.yml", "notify-friday"));
Deno.cron("notify-fri-14", "0 14 * * 5", { backoffSchedule: [1_000, 5_000, 30_000] }, () => dispatchWorkflow("notify-users.yml", "notify-friday"));
Deno.cron("notify-fri-16", "0 16 * * 5", { backoffSchedule: [1_000, 5_000, 30_000] }, () => dispatchWorkflow("notify-users.yml", "notify-friday"));

// Minimal health endpoint so the deployment is verifiable from a browser.
Deno.serve((req) => {
  const { pathname } = new URL(req.url);
  if (pathname === "/" || pathname === "/health") {
    return Response.json({
      service: "saloaleh-scheduler",
      crons: {
        leaderboard: {
          schedule: "*/30 * * * * (UTC)",
          workflow: "leaderboard-populate.yml",
          ...cronStatus.leaderboard,
        },
        aggregate: {
          schedule: "10 16 * * 5 (UTC) — Friday 19:10 Cairo (UTC+3)",
          workflow: "aggregate-all-time.yml",
          ...cronStatus.aggregate,
        },
        "update-stats": {
          schedule: "45 21 * * * (UTC) — daily 23:45 Cairo",
          workflow: "update-stats.yml",
          ...cronStatus["update-stats"],
        },
        "notify-morning": {
          schedule: "0 7 * * 0,1,2,3,4,6 (UTC) — 9 AM Cairo, non-Friday",
          workflow: "notify-users.yml",
          ...cronStatus["notify-morning"],
        },
        "notify-evening": {
          schedule: "0 17 * * 0,1,2,3,4,6 (UTC) — 7 PM Cairo, non-Friday",
          workflow: "notify-users.yml",
          ...cronStatus["notify-evening"],
        },
        "notify-friday": {
          schedule: "0 8,10,12,14,16 * * 5 (UTC) — Friday every 2h until 19:00 Cairo",
          workflow: "notify-users.yml",
          ...cronStatus["notify-friday"],
        },
      },
    });
  }
  return new Response("Not found", { status: 404 });
});
