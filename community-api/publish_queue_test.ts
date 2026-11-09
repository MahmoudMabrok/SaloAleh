import { assertEquals, assertThrows } from "jsr:@std/assert";
import { newExperienceId } from "./ids.ts";
import {
  applyPublish,
  formatReport,
  parseIdList,
  PENDING_PATH,
  planPublish,
  PUBLISHED_PATH,
} from "./publish_queue.mjs";

const story = (id: string, extra: Record<string, string> = {}) => ({
  id,
  displayName: "هدى",
  body: "نص " + id,
  category: "app",
  createdAt: "2026-06-15T10:00:00.000Z",
  ...extra,
});

Deno.test("new ids are 12 hex characters and differ", () => {
  const first = newExperienceId(new Uint8Array([0, 1, 2, 10, 11, 12]));
  assertEquals(first, "0001020a0b0c");
  const a = newExperienceId();
  const b = newExperienceId();
  assertEquals(/^[0-9a-f]{12}$/.test(a), true);
  assertEquals(a === b, false);
});

Deno.test("id list accepts newlines, commas, and repeated ids", () => {
  assertEquals(parseIdList(" a,\nb a\n; a "), ["a", "b"]);
  assertEquals(parseIdList(""), []);
  assertEquals(parseIdList("   \n,"), []);
});

Deno.test("publish moves only the requested pending ids and keeps the rest", () => {
  const keep = story("keep", { note: "leave-me" });
  const first = story("one", { note: "first" });
  const second = story("two");
  const already = story("old", { note: "published" });
  const duplicate = story("one", { note: "second-copy" });
  const plan = planPublish(
    [keep, first, duplicate, second],
    [already],
    ["two", "one", "old", "missing", "one"],
  );

  assertEquals(plan.moved, ["two", "one"]);
  assertEquals(plan.skippedPublished, ["old"]);
  assertEquals(plan.unknown, ["missing"]);
  assertEquals(plan.pending, [keep, duplicate]);
  assertEquals(plan.published, [already, second, first]);
  assertEquals(plan.published[0], already);
  assertEquals(plan.published[2], first);
  assertEquals((plan.pending[0] as { note: string }).note, "leave-me");
});

Deno.test("unknown and already-published ids do not rewrite the files", () => {
  const files = {
    [PENDING_PATH]: JSON.stringify([story("pend")], null, 2) + "\n",
    [PUBLISHED_PATH]: JSON.stringify([story("pub")], null, 2) + "\n",
    "other.txt": "untouched",
  };
  const result = applyPublish("missing, pub", files);
  assertEquals(result.plan.moved, []);
  assertEquals(result.plan.skippedPublished, ["pub"]);
  assertEquals(result.plan.unknown, ["missing"]);
  assertEquals(result.files, files);
});

Deno.test("a real move rewrites only the two queues", () => {
  const files = {
    [PENDING_PATH]: JSON.stringify([story("pend"), story("stay")], null, 2) + "\n",
    [PUBLISHED_PATH]: "[]\n",
    "other.txt": "untouched",
  };
  const result = applyPublish("pend", files);
  assertEquals(result.plan.moved, ["pend"]);
  assertEquals(result.files["other.txt"], "untouched");
  const pending = JSON.parse(result.files[PENDING_PATH]);
  const published = JSON.parse(result.files[PUBLISHED_PATH]);
  assertEquals(pending.map((item: { id: string }) => item.id), ["stay"]);
  assertEquals(published.map((item: { id: string }) => item.id), ["pend"]);
  assertEquals(published[0].body, "نص pend");
});

Deno.test("corrupt json is rejected and the report names each bucket", () => {
  assertThrows(() => planPublish({}, [], ["a"]), Error, "not_array");
  const report = formatReport({
    moved: ["aa"],
    skippedPublished: [],
    unknown: ["zz"],
  });
  assertEquals(report.includes("aa"), true);
  assertEquals(report.includes("zz"), true);
  assertEquals(report.includes("—"), true);
});
