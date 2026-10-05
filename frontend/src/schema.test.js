// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { actions, date } from "./schema.js";
const view = (status, decision = "LAST_BUY", overdue = false) => ({
  record: { authorId: 1, buyerId: 2, reviewerId: 3, status, decision },
  overdue,
});
const me = (id, permission) => ({ id, permissions: [permission] });
test("author only submits draft", () => {
  assert.deepEqual(actions(view("DRAFT"), me(1, "case.write")), ["submit"]);
  assert.deepEqual(actions(view("REVIEW"), me(1, "case.write")), []);
});
test("reviewer independently approves or returns", () =>
  assert.deepEqual(actions(view("REVIEW"), me(3, "case.review")), [
    "approve",
    "return",
    "cancel",
  ]));
test("expired buy blocks approval but preserves cancellation", () =>
  assert.deepEqual(
    actions(view("REVIEW", "LAST_BUY", true), me(3, "case.review")),
    ["return", "cancel"],
  ));
test("buyer can receive but cannot close", () =>
  assert.deepEqual(actions(view("ORDERED"), me(2, "case.fulfill")), [
    "receive",
  ]));
test("alternative route records implementation", () =>
  assert.deepEqual(
    actions(view("APPROVED", "ALTERNATE"), me(2, "case.fulfill")),
    ["implement"],
  ));
test("unassigned and missing permissions cannot act", () => {
  assert.deepEqual(actions(view("APPROVED"), me(99, "case.fulfill")), []);
  assert.deepEqual(actions(view("APPROVED"), me(2, "case.read")), []);
});
test("date-only values do not shift timezone", () =>
  assert.equal(date("2026-10-05"), "2026-10-05"));
