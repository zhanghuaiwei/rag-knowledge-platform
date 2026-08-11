import { describe, expect, it } from "vitest";

import { can, canAll, canAny, kbRoleAtLeast } from "@/lib/roles";

describe("lib/roles 权限 helper", () => {
  it("can 命中单个权限；未知/缺失默认拒绝", () => {
    expect(can(["kb:list", "chat:use"], "kb:list")).toBe(true);
    expect(can(["kb:list"], "api-key:manage")).toBe(false);
    expect(can([], "api-key:manage")).toBe(false);
  });

  it("canAny / canAll", () => {
    expect(canAny(["a:1"], ["a:1", "b:2"])).toBe(true);
    expect(canAny(["c:3"], ["a:1", "b:2"])).toBe(false);
    expect(canAll(["a:1", "b:2"], ["a:1", "b:2"])).toBe(true);
    expect(canAll(["a:1"], ["a:1", "b:2"])).toBe(false);
  });

  it("kbRoleAtLeast 未知角色默认拒绝（不按 VIEWER 宽松回退）", () => {
    expect(kbRoleAtLeast("OWNER", "EDITOR")).toBe(true);
    expect(kbRoleAtLeast("EDITOR", "VIEWER")).toBe(true);
    expect(kbRoleAtLeast("VIEWER", "EDITOR")).toBe(false);
    expect(kbRoleAtLeast("UNKNOWN", "VIEWER")).toBe(false);
    expect(kbRoleAtLeast(undefined, "VIEWER")).toBe(false);
  });
});
