export interface RateLimiter {
  (key: string): boolean;
}

/**
 * Sliding window. A rejected call does not consume a slot.
 * The map lives in this isolate only; Deno Deploy can run more than one.
 */
export function createRateLimiter(options: {
  limit: number;
  windowMs: number;
  now?: () => number;
}): RateLimiter {
  const hits = new Map<string, number[]>();
  const now = options.now ?? Date.now;
  return (key: string) => {
    const at = now();
    const recent = (hits.get(key) ?? []).filter((time) => at - time < options.windowMs);
    if (recent.length >= options.limit) {
      hits.set(key, recent);
      return false;
    }
    recent.push(at);
    hits.set(key, recent);
    if (hits.size > 5000) {
      for (const [storedKey, times] of hits) {
        if (times.every((time) => at - time >= options.windowMs)) hits.delete(storedKey);
      }
    }
    return true;
  };
}

export function clientIp(req: Request): string {
  const forwarded = req.headers.get("x-forwarded-for");
  if (forwarded) {
    const first = forwarded.split(",")[0]?.trim();
    if (first) return first;
  }
  const real = req.headers.get("x-real-ip")?.trim();
  return real || "unknown";
}
