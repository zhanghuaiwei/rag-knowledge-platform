import { describe, expect, it } from "vitest";

import Home from "./page";

describe("Home 页面", () => {
  it("重定向到工作台", () => {
    expect(() => Home()).toThrowError(/NEXT_REDIRECT/);
  });
});
