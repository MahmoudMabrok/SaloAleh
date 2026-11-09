import { GitHubContentsClient } from "./github.ts";
import { createRateLimiter } from "./rate_limit.ts";
import { handleCommunityRequest, type CommunityDeps } from "./server.ts";

const HOUR_MS = 60 * 60 * 1000;

export function liveDeps(): CommunityDeps {
  const token = Deno.env.get("GITHUB_TOKEN") ?? "";
  const repo = Deno.env.get("GITHUB_REPO") ?? "MahmoudMabrok/SaloAleh";
  const branch = Deno.env.get("GITHUB_BRANCH") ?? "main";
  const perIp = createRateLimiter({ limit: 5, windowMs: HOUR_MS });
  const globalLimit = createRateLimiter({ limit: 60, windowMs: HOUR_MS });
  return {
    github: new GitHubContentsClient({ token, repo, branch }),
    tokenPresent: token.length > 0,
    rateLimit: (ip) => perIp(ip) && globalLimit("global"),
  };
}

if (import.meta.main) {
  const deps = liveDeps();
  Deno.serve((request) => handleCommunityRequest(request, deps));
}
