import type { GitHubContents } from "./github.ts";
import { clientIp } from "./rate_limit.ts";
import {
  appendPending,
  EXPERIENCES_PATH,
  PENDING_PATH,
  PUBLISHED_CACHE_SECONDS,
  readPublished,
} from "./store.ts";
import { prepareSubmission } from "./validate.ts";

export interface CommunityDeps {
  github: GitHubContents;
  tokenPresent: boolean;
  now?: () => Date;
  newId?: () => string;
  /** Return false when this client must wait. Called only after the body is valid. */
  rateLimit?: (ip: string) => boolean;
}

const MAX_BODY_CHARS = 8_000;

export async function handleCommunityRequest(
  req: Request,
  deps: CommunityDeps,
): Promise<Response> {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }

  const path = routePath(req.url);
  if (req.method === "GET" && path === "/health") return json(200, { ok: true });
  if (path === "/experiences" && req.method === "GET") return getPublished(deps);
  if (path === "/experiences" && req.method === "POST") return postExperience(req, deps);
  if (path === "/experiences") return json(405, { error: "method_not_allowed" });
  return json(404, { error: "not_found" });
}

async function getPublished(deps: CommunityDeps): Promise<Response> {
  if (!deps.tokenPresent) return json(503, { error: "unavailable" });
  try {
    const experiences = await readPublished(deps.github);
    return json(200, { experiences }, {
      "cache-control": `public, max-age=${PUBLISHED_CACHE_SECONDS}`,
    });
  } catch (error) {
    console.error(`published read failed: ${errorName(error)}`);
    return json(503, { error: "unavailable" });
  }
}

async function postExperience(req: Request, deps: CommunityDeps): Promise<Response> {
  if (!deps.tokenPresent) return json(503, { error: "unavailable" });

  const raw = await readLimited(req);
  if (raw == null) return json(400, { error: "invalid", reason: "too_large" });

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return json(400, { error: "invalid", reason: "bad_json" });
  }

  const now = (deps.now ?? (() => new Date()))();
  const id = (deps.newId ?? (() => crypto.randomUUID()))();
  const prepared = prepareSubmission(parsed, id, now.toISOString());
  if (!prepared.ok) return json(400, { error: "invalid", reason: prepared.reason });

  const allow = deps.rateLimit ?? (() => true);
  if (!allow(clientIp(req))) {
    return json(429, { error: "rate_limited" }, { "retry-after": "3600" });
  }

  try {
    await appendPending(deps.github, prepared.item);
  } catch (error) {
    console.error(`pending write failed: ${errorName(error)}`);
    return json(503, { error: "unavailable" });
  }

  return json(200, { ok: true, status: "pending", id: prepared.item.id });
}

function routePath(url: string): string {
  const pathname = new URL(url).pathname;
  if (pathname.length > 1 && pathname.endsWith("/")) return pathname.slice(0, -1);
  return pathname;
}

async function readLimited(req: Request): Promise<string | null> {
  const declared = Number(req.headers.get("content-length") ?? "");
  if (Number.isFinite(declared) && declared > MAX_BODY_CHARS) return null;
  const text = await req.text();
  if (text.length > MAX_BODY_CHARS) return null;
  return text;
}

function errorName(error: unknown): string {
  if (error instanceof Error) {
    if (error.message.startsWith("github_") || error.message === "not_array") return error.message;
    return error.name;
  }
  return "error";
}

function corsHeaders(): Record<string, string> {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type",
  };
}

function json(
  status: number,
  body: unknown,
  extra?: Record<string, string>,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...corsHeaders(),
      ...extra,
    },
  });
}

// Re-exported so a reviewer can see the only two paths this process touches.
export const PUBLISHED_FILE = EXPERIENCES_PATH;
export const QUEUE_FILE = PENDING_PATH;
