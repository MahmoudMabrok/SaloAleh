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
const WORKFLOW_FILE = "leaderboard-populate.yml";
const REF = "main";

let lastRunAt: string | null = null;
let lastResult: string | null = null;

async function dispatchWorkflow(): Promise<void> {
  const token = Deno.env.get("GITHUB_TOKEN");
  if (!token) throw new Error("GITHUB_TOKEN env var is not set");

  const url =
    `https://api.github.com/repos/${OWNER}/${REPO}/actions/workflows/${WORKFLOW_FILE}/dispatches`;

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

  lastRunAt = new Date().toISOString();

  // GitHub returns 204 No Content on a successful workflow dispatch.
  if (res.status !== 204) {
    lastResult = `FAILED ${res.status} ${res.statusText}: ${await res.text()}`;
    // Throwing lets Deno Cron apply the backoffSchedule retries below.
    throw new Error(lastResult);
  }

  lastResult = "OK";
  console.log(`[${lastRunAt}] Dispatched ${WORKFLOW_FILE} on ${REF}`);
}

// UTC schedule — runs at :00 and :30 every hour. populate-leaderboard.js
// derives the Cairo round key itself, so the scheduler timezone is irrelevant.
// backoffSchedule retries a failed dispatch after 1s, 5s, then 30s.
Deno.cron(
  "trigger-leaderboard-populate",
  "*/30 * * * *",
  { backoffSchedule: [1_000, 5_000, 30_000] },
  dispatchWorkflow,
);

// Minimal health endpoint so the deployment is verifiable from a browser.
Deno.serve((req) => {
  const { pathname } = new URL(req.url);
  if (pathname === "/" || pathname === "/health") {
    return Response.json({
      service: "saloaleh-leaderboard-scheduler",
      schedule: "*/30 * * * * (UTC)",
      target: `${OWNER}/${REPO} → ${WORKFLOW_FILE}`,
      lastRunAt,
      lastResult,
    });
  }
  return new Response("Not found", { status: 404 });
});
