import { beforeEach, describe, expect, it } from "vitest";

import { authApi } from "@/api-client/mock/auth";
import { clearSession, getAccessToken, isAuthed } from "@/lib/auth";
import { db } from "@/mocks/db";

describe("mock auth", () => {
  beforeEach(() => {
    clearSession();
  });

  it("未登录时 getCurrentUser 抛错", async () => {
    await expect(authApi.getCurrentUser()).rejects.toThrow("登录已过期");
  });

  it("登录后置 token 并可取当前用户", async () => {
    const user = await authApi.login({ username: "admin@ragkb.dev", password: "admin123" });
    expect(user.name).toBeTruthy();
    expect(isAuthed()).toBe(true);
    expect(getAccessToken()).toBe("mock-token");

    const current = await authApi.getCurrentUser();
    expect(current.id).toBe(user.id);
  });

  it("登出清空 token 后不可再取用户", async () => {
    await authApi.login({ username: "admin@ragkb.dev", password: "admin123" });
    await authApi.logout();
    expect(isAuthed()).toBe(false);
    await expect(authApi.getCurrentUser()).rejects.toThrow("登录已过期");
  });

  it("空账号或空密码登录被拒绝", async () => {
    await expect(authApi.login({ username: "", password: "x" })).rejects.toThrow("用户名或密码错误");
    await expect(authApi.login({ username: "a", password: "" })).rejects.toThrow("用户名或密码错误");
  });

  it("V0.5：修改密码后清除 mustChangePassword 标志", async () => {
    await authApi.login({ username: "admin@ragkb.dev", password: "admin123" });
    db.currentUser.mustChangePassword = true;
    await authApi.changePassword({ currentPassword: "admin123", newPassword: "newpass123" });
    expect(db.currentUser.mustChangePassword).toBe(false);
  });

  it("V0.5：新密码长度不足被拒绝", async () => {
    await authApi.login({ username: "admin@ragkb.dev", password: "admin123" });
    await expect(
      authApi.changePassword({ currentPassword: "admin123", newPassword: "123" }),
    ).rejects.toThrow("至少 6 位");
  });
});
