/**
 * Submission rules for community experiences.
 * Keep the limits aligned with ExperienceLimits in
 * app/src/commonMain/kotlin/tools/mo3ta/salo/domain/CommunityExperience.kt.
 */

export const NAME_MAX = 40;
export const BODY_MAX = 1000;
export const ANONYMOUS_DISPLAY_NAME = "أخ/أخت";
export const CATEGORIES = ["app", "dhikr"] as const;

export type ExperienceCategory = (typeof CATEGORIES)[number];

export interface Experience {
  id: string;
  displayName: string;
  body: string;
  category: ExperienceCategory;
  createdAt: string;
}

export type RejectionReason =
  | "bad_json"
  | "bad_category"
  | "empty_body"
  | "body_too_long"
  | "name_too_long"
  | "unsafe";

export type PrepareResult =
  | { ok: true; item: Experience }
  | { ok: false; reason: RejectionReason };

const RAW_HTML = /[<>]/;

function isIsoControl(char: string): boolean {
  const code = char.charCodeAt(0);
  return code <= 0x1f || (code >= 0x7f && code <= 0x9f);
}

function isDisallowedInName(char: string): boolean {
  return isIsoControl(char);
}

function isDisallowedInBody(char: string): boolean {
  if (char === "\n" || char === "\r" || char === "\t") return false;
  return isIsoControl(char);
}

function hasUnsafeChars(value: string, inName: boolean): boolean {
  if (RAW_HTML.test(value)) return true;
  const check = inName ? isDisallowedInName : isDisallowedInBody;
  for (const char of value) {
    if (check(char)) return true;
  }
  return false;
}

export function prepareSubmission(
  input: unknown,
  id: string,
  createdAt: string,
): PrepareResult {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    return { ok: false, reason: "bad_json" };
  }
  const record = input as Record<string, unknown>;
  if (typeof record.body !== "string") return { ok: false, reason: "bad_json" };

  const category = typeof record.category === "string" ? record.category.trim() : "";
  if (category !== "app" && category !== "dhikr") {
    return { ok: false, reason: "bad_category" };
  }

  const rawName = typeof record.displayName === "string" ? record.displayName : "";
  const name = rawName.trim();
  const body = record.body.trim();

  if (hasUnsafeChars(name, true) || hasUnsafeChars(body, false)) {
    return { ok: false, reason: "unsafe" };
  }
  if (name.length > NAME_MAX) return { ok: false, reason: "name_too_long" };
  if (body.length === 0) return { ok: false, reason: "empty_body" };
  if (body.length > BODY_MAX) return { ok: false, reason: "body_too_long" };

  return {
    ok: true,
    item: {
      id,
      displayName: name.length === 0 ? ANONYMOUS_DISPLAY_NAME : name,
      body,
      category,
      createdAt,
    },
  };
}

/** Drop entries that would not pass the same rules the app is willing to show. */
export function selectPublished(items: unknown[]): Experience[] {
  const published: Experience[] = [];
  for (const item of items) {
    if (!item || typeof item !== "object" || Array.isArray(item)) continue;
    const record = item as Record<string, unknown>;
    const id = record.id;
    const createdAt = record.createdAt;
    if (typeof id !== "string" || typeof createdAt !== "string") continue;
    if (id.length === 0 || id.length > 80 || createdAt.length === 0 || createdAt.length > 40) {
      continue;
    }
    if (RAW_HTML.test(id) || RAW_HTML.test(createdAt)) continue;
    const prepared = prepareSubmission(
      {
        displayName: record.displayName,
        body: record.body,
        category: record.category,
      },
      id,
      createdAt,
    );
    if (!prepared.ok) continue;
    if (prepared.item.displayName.length === 0) continue;
    published.push(prepared.item);
  }
  published.sort((a, b) => b.createdAt.localeCompare(a.createdAt) || a.id.localeCompare(b.id));
  return published;
}

export function parseExperienceArray(text: string): unknown[] {
  const trimmed = text.trim();
  if (trimmed === "") return [];
  const parsed = JSON.parse(trimmed);
  if (!Array.isArray(parsed)) throw new Error("not_array");
  return parsed;
}
