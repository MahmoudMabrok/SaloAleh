/**
 * Move selected community experiences from the moderation queue to the published list.
 * The workflow passes ids. This file does not talk to GitHub and has no token.
 */

import { pathToFileURL } from "node:url";

export const PENDING_PATH = "community/pending.json";
export const PUBLISHED_PATH = "community/experiences.json";

export function parseIdList(text) {
  const ids = [];
  const seen = new Set();
  for (const part of String(text ?? "").split(/[\s,;]+/)) {
    const id = part.trim();
    if (!id || seen.has(id)) continue;
    seen.add(id);
    ids.push(id);
  }
  return ids;
}

function itemId(item) {
  if (!item || typeof item !== "object" || Array.isArray(item)) return "";
  return typeof item.id === "string" ? item.id : "";
}

/**
 * Append matching pending items onto published and remove the first matching
 * row from pending. Ids already published stay as they are. Unknown ids stay
 * untouched. Items that were not requested keep their original order.
 */
export function planPublish(pending, published, requestedIds) {
  if (!Array.isArray(pending) || !Array.isArray(published)) {
    throw new Error("not_array");
  }
  const publishedIds = new Set();
  for (const item of published) {
    const id = itemId(item);
    if (id) publishedIds.add(id);
  }
  const pendingById = new Map();
  for (const item of pending) {
    const id = itemId(item);
    if (id && !pendingById.has(id)) pendingById.set(id, item);
  }

  const moved = [];
  const skippedPublished = [];
  const unknown = [];
  const moveSet = new Set();
  for (const id of requestedIds) {
    if (publishedIds.has(id)) {
      skippedPublished.push(id);
      continue;
    }
    const item = pendingById.get(id);
    if (!item) {
      unknown.push(id);
      continue;
    }
    if (moveSet.has(id)) continue;
    moveSet.add(id);
    moved.push(item);
  }

  const removed = new Set();
  const nextPending = [];
  for (const item of pending) {
    const id = itemId(item);
    if (id && moveSet.has(id) && !removed.has(id)) {
      removed.add(id);
      continue;
    }
    nextPending.push(item);
  }

  return {
    pending: nextPending,
    published: published.concat(moved),
    moved: moved.map((item) => item.id),
    skippedPublished,
    unknown,
  };
}

export function serializeQueue(items) {
  return JSON.stringify(items, null, 2) + "\n";
}

export function applyPublish(idsText, files) {
  const pendingText = files[PENDING_PATH];
  const publishedText = files[PUBLISHED_PATH];
  if (typeof pendingText !== "string" || typeof publishedText !== "string") {
    throw new Error("missing_file");
  }
  const plan = planPublish(
    JSON.parse(pendingText),
    JSON.parse(publishedText),
    parseIdList(idsText),
  );
  if (plan.moved.length === 0) return { plan, files };
  return {
    plan,
    files: {
      ...files,
      [PENDING_PATH]: serializeQueue(plan.pending),
      [PUBLISHED_PATH]: serializeQueue(plan.published),
    },
  };
}

export function formatReport(plan) {
  const list = (ids) => (ids.length ? ids.join(" ") : "—");
  return [
    `تم النقل (${plan.moved.length}): ${list(plan.moved)}`,
    `منشورة مسبقًا (${plan.skippedPublished.length}): ${list(plan.skippedPublished)}`,
    `غير معروفة (${plan.unknown.length}): ${list(plan.unknown)}`,
  ].join("\n") + "\n";
}

async function main() {
  const { readFileSync, writeFileSync, appendFileSync } = await import("node:fs");
  const before = {
    [PENDING_PATH]: readFileSync(PENDING_PATH, "utf8"),
    [PUBLISHED_PATH]: readFileSync(PUBLISHED_PATH, "utf8"),
  };
  const { plan, files } = applyPublish(process.env.EXPERIENCE_IDS ?? "", before);
  const report = formatReport(plan);
  process.stdout.write(report);
  if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, report);
  }
  if (plan.moved.length === 0) return;
  writeFileSync(PENDING_PATH, files[PENDING_PATH]);
  writeFileSync(PUBLISHED_PATH, files[PUBLISHED_PATH]);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    await main();
  } catch (error) {
    console.error(error instanceof Error ? error.name : "error");
    process.exit(1);
  }
}
