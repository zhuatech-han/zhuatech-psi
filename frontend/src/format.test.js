// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { money, quantity, decimal } from "./format.js";
test("quantity keeps three decimal places without forcing trailing zeros", () =>
  assert.equal(quantity("1.125"), "1.125"));
test("localized currency preserves negative customer credit", () =>
  assert.match(money("-30", "CNY", "en"), /-/));
test("invalid quantities are rejected rather than silently replaced with zero", () => {
  assert.throws(() => decimal(""));
  assert.throws(() => decimal("-1"));
  assert.throws(() => decimal("1e3"));
  assert.equal(decimal("0.001"), "0.001");
});
