import { assertEquals } from "jsr:@std/assert";
import { decodeUtf8Base64, encodeUtf8Base64 } from "./github.ts";
import { clientIp, createRateLimiter } from "./rate_limit.ts";
import type { GitHubContents, GitHubFile } from "./github.ts";
import { EXPERIENCES_PATH, PENDING_PATH, serializeQueue } from "./store.ts";
import { handleCommunityRequest, type CommunityDeps } from "./server.ts";

Deno.test("utf-8 base64 round trip keeps arabic", () => {
  const text = "أخ/أخت\nتجربة";
  const encoded = encodeUtf8Base64(text);
  const wrapped = encoded.replace(/(.{8})/g, "$1\n");
  assertEquals(decodeUtf8Base64(wrapped), text);
});

Deno.test("rate limiter blocks the next call and does not consume a rejection", () => {
  let now = 1_000;
  const allow = createRateLimiter({ limit: 2, windowMs: 100, now: () => now });
  assertEquals(allow("a"), true);
  assertEquals(allow("a"), true);
  assertEquals(allow("a"), false);
  assertEquals(allow("a"), false);
  now = 1_200;
  assertEquals(allow("a"), true);
  assertEquals(allow("b"), true);
});

Deno.test("client ip uses the first forwarded hop", () => {
  const request = new Request("https://example.test/experiences", {
    headers: { "x-forwarded-for": " 203.0.113.5, 10.0.0.1 " },
  });
  assertEquals(clientIp(request), "203.0.113.5");
});

interface StoredFile {
  text: string;
  sha: string;
}

class FakeGitHub implements GitHubContents {
  files = new Map<string, StoredFile>();
  gets: string[] = [];
  puts: { path: string; text: string; sha: string | null; message: string }[] = [];
  conflictsRemaining = 0;
  /** Applied to the pending file when a conflict is simulated. */
  concurrentPending: StoredFile | null = null;

  async getFile(path: string): Promise<GitHubFile> {
    this.gets.push(path);
    const file = this.files.get(path);
    if (!file) return { text: "[]", sha: null };
    return { text: file.text, sha: file.sha };
  }

  async putFile(
    path: string,
    text: string,
    sha: string | null,
    message: string,
  ): Promise<"ok" | "conflict"> {
    this.puts.push({ path, text, sha, message });
    if (path !== PENDING_PATH) throw new Error("refusing to write " + path);
    if (this.conflictsRemaining > 0) {
      this.conflictsRemaining--;
      if (this.concurrentPending) this.files.set(path, this.concurrentPending);
      return "conflict";
    }
    this.files.set(path, { text, sha: "written" });
    return "ok";
  }
}

function deps(github: FakeGitHub, extra: Partial<CommunityDeps> = {}): CommunityDeps {
  return {
    github,
    tokenPresent: true,
    now: () => new Date("2026-11-09T16:40:00.000Z"),
    newId: () => "server-id",
    rateLimit: () => true,
    ...extra,
  };
}

function post(body: unknown, headers: HeadersInit = {}): Request {
  return new Request("https://community.test/experiences", {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
}

Deno.test("GET returns published items only", async () => {
  const github = new FakeGitHub();
  github.files.set(EXPERIENCES_PATH, {
    sha: "pub",
    text: serializeQueue([{
      id: "pub-1",
      displayName: "هدى",
      body: "تجربة منشورة",
      category: "app",
      createdAt: "2026-05-01T00:00:00.000Z",
    }]),
  });
  github.files.set(PENDING_PATH, {
    sha: "pend",
    text: serializeQueue([{
      id: "pend-1",
      displayName: "سر",
      body: "PENDING_SECRET_BODY",
      category: "dhikr",
      createdAt: "2026-05-02T00:00:00.000Z",
    }]),
  });

  const response = await handleCommunityRequest(
    new Request("https://community.test/experiences"),
    deps(github),
  );
  const payload = await response.json();
  assertEquals(response.status, 200);
  assertEquals(response.headers.get("cache-control"), "public, max-age=60");
  assertEquals(payload.experiences.length, 1);
  assertEquals(payload.experiences[0].body, "تجربة منشورة");
  assertEquals(JSON.stringify(payload).includes("PENDING_SECRET_BODY"), false);
  assertEquals(github.gets.includes(PENDING_PATH), false);
  assertEquals(github.puts.length, 0);
});

Deno.test("a corrupt published file is not served as an empty list", async () => {
  const github = new FakeGitHub();
  github.files.set(EXPERIENCES_PATH, { sha: "bad", text: "{" });
  const response = await handleCommunityRequest(
    new Request("https://community.test/experiences"),
    deps(github),
  );
  assertEquals(response.status, 503);
  assertEquals(github.gets.includes(PENDING_PATH), false);
});

Deno.test("GET of an empty published file is an empty list", async () => {
  const github = new FakeGitHub();
  const response = await handleCommunityRequest(
    new Request("https://community.test/experiences/"),
    deps(github),
  );
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { experiences: [] });
});

Deno.test("POST queues a validated item and does not publish it", async () => {
  const github = new FakeGitHub();
  github.files.set(EXPERIENCES_PATH, {
    sha: "pub",
    text: "[]\n",
  });
  const response = await handleCommunityRequest(
    post({
      id: "client-id",
      createdAt: "1999-01-01T00:00:00.000Z",
      displayName: "  ",
      body: "  UNIQUE_BODY_PHRASE  ",
      category: "dhikr",
    }),
    deps(github),
  );
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { ok: true, status: "pending", id: "server-id" });
  assertEquals(github.puts.length, 1);
  assertEquals(github.puts[0].path, PENDING_PATH);
  assertEquals(github.puts[0].message.includes("UNIQUE_BODY_PHRASE"), false);
  assertEquals(github.puts[0].message, "Queue community experience for review (server-id)");
  const queued = JSON.parse(github.puts[0].text);
  assertEquals(queued, [{
    id: "server-id",
    displayName: "أخ/أخت",
    body: "UNIQUE_BODY_PHRASE",
    category: "dhikr",
    createdAt: "2026-11-09T16:40:00.000Z",
  }]);
  assertEquals(github.files.get(EXPERIENCES_PATH)?.text, "[]\n");
});

Deno.test("POST retries when the queue SHA conflicts and keeps the concurrent entry", async () => {
  const github = new FakeGitHub();
  github.files.set(PENDING_PATH, { sha: "aaa", text: "[]\n" });
  github.conflictsRemaining = 1;
  github.concurrentPending = {
    sha: "bbb",
    text: serializeQueue([{
      id: "other",
      displayName: "أ",
      body: "موجود",
      category: "app",
      createdAt: "2026-01-01T00:00:00.000Z",
    }]),
  };
  const response = await handleCommunityRequest(
    post({ displayName: "ليلى", body: "تجربتي", category: "app" }),
    deps(github),
  );
  assertEquals(response.status, 200);
  assertEquals(github.puts.length, 2);
  assertEquals(github.puts[1].sha, "bbb");
  const queued = JSON.parse(github.files.get(PENDING_PATH)!.text);
  assertEquals(queued.map((item: { id: string }) => item.id), ["other", "server-id"]);
});

Deno.test("a corrupt queue is not overwritten", async () => {
  const github = new FakeGitHub();
  github.files.set(PENDING_PATH, { sha: "bad", text: "{" });
  const response = await handleCommunityRequest(
    post({ displayName: "ليلى", body: "تجربتي", category: "app" }),
    deps(github),
  );
  assertEquals(response.status, 503);
  assertEquals(github.puts.length, 0);
  assertEquals(github.files.get(PENDING_PATH)?.text, "{");
});

Deno.test("invalid submissions never touch GitHub", async () => {
  const github = new FakeGitHub();
  const response = await handleCommunityRequest(
    post({ displayName: "ليلى", body: "<p>لا</p>", category: "app" }),
    deps(github),
  );
  assertEquals(response.status, 400);
  assertEquals((await response.json()).reason, "unsafe");
  assertEquals(github.gets.length, 0);
  assertEquals(github.puts.length, 0);
});

Deno.test("rate limit responds 429 before writing", async () => {
  const github = new FakeGitHub();
  const response = await handleCommunityRequest(
    post(
      { body: "تجربة", category: "app" },
      { "x-forwarded-for": "198.51.100.20" },
    ),
    deps(github, { rateLimit: () => false }),
  );
  assertEquals(response.status, 429);
  assertEquals(response.headers.get("retry-after"), "3600");
  assertEquals(github.puts.length, 0);
});

Deno.test("missing token does not call GitHub", async () => {
  const github = new FakeGitHub();
  const response = await handleCommunityRequest(
    new Request("https://community.test/experiences"),
    deps(github, { tokenPresent: false }),
  );
  assertEquals(response.status, 503);
  assertEquals(github.gets.length, 0);
});

Deno.test("health and unknown routes", async () => {
  const github = new FakeGitHub();
  const health = await handleCommunityRequest(
    new Request("https://community.test/health"),
    deps(github, { tokenPresent: false }),
  );
  assertEquals(health.status, 200);
  const hidden = await handleCommunityRequest(
    new Request("https://community.test/pending"),
    deps(github),
  );
  assertEquals(hidden.status, 404);
  const file = await handleCommunityRequest(
    new Request("https://community.test/community/pending.json"),
    deps(github),
  );
  assertEquals(file.status, 404);
  assertEquals(github.gets.length, 0);
});
