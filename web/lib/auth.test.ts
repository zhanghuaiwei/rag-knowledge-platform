import { beforeEach, describe, expect, it } from "vitest";

import { clearSession, getAccessToken, isAuthed, setAuth } from "@/lib/auth";

describe("lib/auth 内存 token store", () => {
  beforeEach(() => {
    clearSession();
  });

  it("未设置时无 token 且未登录", () => {
    expect(getAccessToken()).toBeNull();
    expect(isAuthed()).toBe(false);
  });

  it("setAuth 后返回 token 并视为已登录", () => {
    setAuth("t-1", 3600);
    expect(getAccessToken()).toBe("t-1");
    expect(isAuthed()).toBe(true);
  });

  it("有效期 0 视为立即过期", () => {
    setAuth("t-2", 0);
    expect(getAccessToken()).toBeNull();
    expect(isAuthed()).toBe(false);
  });

  it("clearSession 清空登录态", () => {
    setAuth("t-3", 3600);
    clearSession();
    expect(getAccessToken()).toBeNull();
    expect(isAuthed()).toBe(false);
  });
});
