import { describe, expect, it } from "vitest";

import { adminApi } from "@/api-client/mock/admin";

describe("mock admin · 用户管理（V0.5）", () => {
  it("创建用户：加入列表、初始密码须首登改密", async () => {
    const created = await adminApi.createUser({
      username: "testuser01",
      email: "testuser01@example.com",
      displayName: "测试用户一",
      password: "secret123",
      roles: ["MEMBER"],
    });
    expect(created.mustChangePassword).toBe(true);
    expect(created.roles).toEqual(["MEMBER"]);

    const page = await adminApi.listUsers({ page: 1, size: 50 });
    expect(page.items.some((u) => u.id === created.id)).toBe(true);
  });

  it("创建用户：登录标识已存在被拒绝", async () => {
    // 复用既有 mock 用户邮箱触发查重
    await expect(
      adminApi.createUser({
        username: "dupe",
        email: "zhanghuaiwei@example.com",
        displayName: "重复",
        password: "secret123",
        roles: ["MEMBER"],
      }),
    ).rejects.toThrow("已存在");
  });

  it("编辑角色：覆盖式替换角色集合", async () => {
    const page = await adminApi.listUsers({ page: 1, size: 50 });
    const target = page.items[0];
    const updated = await adminApi.setRoles(target.id, ["AUDITOR", "MEMBER"]);
    expect(updated.roles).toEqual(["AUDITOR", "MEMBER"]);
  });

  it("重置密码：置 mustChangePassword 首登改密", async () => {
    const created = await adminApi.createUser({
      username: "resetme01",
      email: "resetme01@example.com",
      displayName: "重置我",
      password: "secret123",
      roles: ["MEMBER"],
    });
    expect(created.mustChangePassword).toBe(true);

    await adminApi.resetPassword(created.id, { newPassword: "newpass123" });
    const after = await adminApi.listUsers({ page: 1, size: 50 }).then((p) => p.items.find((u) => u.id === created.id));
    expect(after?.mustChangePassword).toBe(true);
  });

  it("移出租户：从成员列表移除", async () => {
    const created = await adminApi.createUser({
      username: "remove01",
      email: "remove01@example.com",
      displayName: "待移除",
      password: "secret123",
      roles: ["MEMBER"],
    });
    await adminApi.removeUser(created.id);

    const page = await adminApi.listUsers({ page: 1, size: 50 });
    expect(page.items.some((u) => u.id === created.id)).toBe(false);
  });
});
