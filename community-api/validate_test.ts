import { assertEquals } from "jsr:@std/assert";
import {
  ANONYMOUS_DISPLAY_NAME,
  BODY_MAX,
  NAME_MAX,
  prepareSubmission,
  selectPublished,
} from "./validate.ts";

const NOW = "2026-11-09T16:40:00.000Z";

Deno.test("empty and whitespace bodies are rejected", () => {
  assertEquals(prepareSubmission({ body: "   ", category: "app" }, "id", NOW).ok, false);
  assertEquals(
    prepareSubmission({ body: "", displayName: "نورة", category: "dhikr" }, "id", NOW),
    { ok: false, reason: "empty_body" },
  );
});

Deno.test("optional name falls back when blank", () => {
  const result = prepareSubmission(
    { displayName: "  \n", body: "ذكر لطيف", category: "dhikr" },
    "server-id",
    NOW,
  );
  assertEquals(result, {
    ok: true,
    item: {
      id: "server-id",
      displayName: ANONYMOUS_DISPLAY_NAME,
      body: "ذكر لطيف",
      category: "dhikr",
      createdAt: NOW,
    },
  });
});

Deno.test("client id and timestamp are ignored", () => {
  const result = prepareSubmission(
    {
      id: "client-id",
      createdAt: "2000-01-01T00:00:00.000Z",
      displayName: "سلمى",
      body: "التطبيق سهّل عليّ الورد",
      category: "app",
    },
    "server-id",
    NOW,
  );
  assertEquals(result.ok && result.item.id, "server-id");
  assertEquals(result.ok && result.item.createdAt, NOW);
});

Deno.test("length bounds", () => {
  const okName = prepareSubmission(
    { displayName: "ا".repeat(NAME_MAX), body: "ب".repeat(BODY_MAX), category: "app" },
    "id",
    NOW,
  );
  assertEquals(okName.ok, true);
  assertEquals(
    prepareSubmission(
      { displayName: "ا".repeat(NAME_MAX + 1), body: "تمام", category: "app" },
      "id",
      NOW,
    ),
    { ok: false, reason: "name_too_long" },
  );
  assertEquals(
    prepareSubmission(
      { displayName: "نورة", body: "ب".repeat(BODY_MAX + 1), category: "app" },
      "id",
      NOW,
    ),
    { ok: false, reason: "body_too_long" },
  );
});

Deno.test("raw html and control characters are rejected", () => {
  assertEquals(
    prepareSubmission({ body: "<b>سلام</b>", category: "app" }, "id", NOW),
    { ok: false, reason: "unsafe" },
  );
  assertEquals(
    prepareSubmission({ displayName: "a<b>", body: "سلام", category: "app" }, "id", NOW),
    { ok: false, reason: "unsafe" },
  );
  assertEquals(
    prepareSubmission({ body: "سلام\u0000", category: "dhikr" }, "id", NOW),
    { ok: false, reason: "unsafe" },
  );
  assertEquals(
    prepareSubmission({ body: "سلام\u0085", category: "dhikr" }, "id", NOW),
    { ok: false, reason: "unsafe" },
  );
  const multiline = prepareSubmission(
    { body: "سطر\nثان", category: "dhikr" },
    "id",
    NOW,
  );
  assertEquals(multiline.ok, true);
});

Deno.test("category must be app or dhikr", () => {
  assertEquals(
    prepareSubmission({ body: "سلام", category: "other" }, "id", NOW),
    { ok: false, reason: "bad_category" },
  );
  assertEquals(prepareSubmission({ body: "سلام" }, "id", NOW), {
    ok: false,
    reason: "bad_category",
  });
});

Deno.test("published selection hides unsafe rows and sorts newest first", () => {
  const selected = selectPublished([
    {
      id: "old",
      displayName: "أ",
      body: "قديم",
      category: "app",
      createdAt: "2026-01-01T00:00:00.000Z",
      secret: "do-not-keep",
    },
    { id: "bad", displayName: "أ", body: "<i>x</i>", category: "app", createdAt: NOW },
    {
      id: "new",
      displayName: "ب",
      body: "جديد",
      category: "dhikr",
      createdAt: "2026-06-01T00:00:00.000Z",
    },
    "not-an-object",
  ]);
  assertEquals(selected.map((item) => item.id), ["new", "old"]);
  assertEquals("secret" in selected[1], false);
});
