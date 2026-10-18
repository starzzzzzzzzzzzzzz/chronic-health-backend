import { test } from "node:test";
import assert from "node:assert/strict";
import { permittedRequest } from "../local-proxy.mjs";
import {
  editableDraft,
  draftPatch,
  validationMessage,
  factText,
} from "../src/domain.js";
const request = (method, url, headers = {}) => ({
  method,
  url,
  headers: {
    host: "127.0.0.1:9090",
    origin: "http://127.0.0.1:9090",
    "content-type": "application/json",
    ...headers,
  },
});
test("local editor proxy admits exact workflow endpoints", () => {
  assert.ok(
    permittedRequest(
      request("POST", "/api/plan-versions/test-id/generation-jobs"),
      9090,
    ),
  );
  assert.ok(permittedRequest(request("GET", "/api/patients?q=demo"), 9090));
  assert.ok(
    permittedRequest(request("PATCH", "/api/plan-versions/test-id"), 9090),
  );
});
test("proxy rejects cross-site requests, rebinding, arbitrary routes and encoded traversal", () => {
  for (const req of [
    request("GET", "/api/patients", { origin: "https://evil.example" }),
    request("GET", "/api/patients", { host: "evil.example:9090" }),
    request("GET", "/api/patients", { "sec-fetch-site": "cross-site" }),
    request("POST", "/api/plan-versions/id/review-submissions"),
    request("POST", "/api/plan-versions/id/generation-jobs", {
      "content-type": "text/plain",
    }),
    request("GET", "/api/plan-versions/%2e%2e/health-summary"),
    request("GET", "/api/patients/../health"),
    request("DELETE", "/api/care-plans"),
  ])
    assert.equal(permittedRequest(req, 9090), false);
});
test("editable projection retains advice identity and excludes workflow/status fields", () => {
  const original = {
    primaryGoal: "规律记录",
    status: "APPROVED",
    inputSnapshot: { private: true },
    dimensions: [{ dimensionCode: "routine", decision: "ENABLED" }],
    items: [
      {
        id: "own-item",
        title: "作息",
        content: "记录",
        basis: [{ factId: "fact", moduleCode: "L" }],
        isSelected: true,
        dimensionCode: "routine",
      },
    ],
  };
  const edit = editableDraft(original);
  edit.items[0].content = "人工调整";
  const patch = draftPatch(
    { ...edit, status: "APPROVED", inputSnapshot: {} },
    "7",
  );
  assert.equal(patch.expectedRevision, "7");
  assert.equal(patch.items[0].id, "own-item");
  assert.deepEqual(patch.items[0].basis, original.items[0].basis);
  assert.equal(original.items[0].content, "记录");
  assert.equal("status" in patch, false);
  assert.equal("inputSnapshot" in patch, false);
});
test("editor blocks selected advice in a closed dimension and empty goals", () => {
  assert.match(
    validationMessage({ primaryGoal: "", dimensions: [], items: [] }),
    /主目标/,
  );
  assert.match(
    validationMessage({
      primaryGoal: "目标",
      dimensions: [
        {
          dimensionCode: "routine",
          decision: "CLOSED",
          decisionReason: "本期不做",
        },
      ],
      items: [{ dimensionCode: "routine", isSelected: true }],
    }),
    /取消/,
  );
});
test("fact text preserves unknown and structured facts as text", () => {
  assert.equal(factText(null), "暂无记录");
  assert.equal(factText({ value: "自述晚睡" }), "自述晚睡");
  assert.equal(
    factText({ value: "<script>alert(1)</script>" }),
    "<script>alert(1)</script>",
  );
});
