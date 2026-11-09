import type { GitHubContents } from "./github.ts";
import { parseExperienceArray, selectPublished, type Experience } from "./validate.ts";

export const EXPERIENCES_PATH = "community/experiences.json";
export const PENDING_PATH = "community/pending.json";
export const PUBLISHED_CACHE_SECONDS = 60;
const MAX_WRITE_ATTEMPTS = 5;

export function serializeQueue(items: unknown[]): string {
  return JSON.stringify(items, null, 2) + "\n";
}

function alreadyQueued(items: unknown[], id: string): boolean {
  return items.some((item) =>
    !!item && typeof item === "object" && !Array.isArray(item) &&
    (item as { id?: unknown }).id === id
  );
}

/**
 * Append one experience to the moderation queue.
 * Retries when the contents SHA changed underneath us. Never writes experiences.json.
 * A queue file that is not a JSON array is left untouched.
 */
export async function appendPending(
  github: GitHubContents,
  item: Experience,
): Promise<void> {
  for (let attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
    const file = await github.getFile(PENDING_PATH);
    const items = parseExperienceArray(file.text);
    if (!alreadyQueued(items, item.id)) items.push(item);
    else return;
    const message = `Queue community experience for review (${item.id})`;
    const result = await github.putFile(
      PENDING_PATH,
      serializeQueue(items),
      file.sha,
      message,
    );
    if (result === "ok") return;
  }
  throw new Error("github_conflict");
}

export async function readPublished(github: GitHubContents): Promise<Experience[]> {
  const file = await github.getFile(EXPERIENCES_PATH);
  return selectPublished(parseExperienceArray(file.text));
}
