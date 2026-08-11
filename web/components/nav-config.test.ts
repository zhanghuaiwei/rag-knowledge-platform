import { describe, expect, it } from "vitest";

import { buildNav, findSelectedKey } from "@/components/nav-config";
import { PERMISSION } from "@/lib/permissions";

/** 全量权限 + 全 feature（管理员视角）。 */
const ADMIN_CTX = {
  permissions: [
    PERMISSION.DASHBOARD_VIEW,
    PERMISSION.CHAT_USE,
    PERMISSION.SEARCH_EXECUTE,
    PERMISSION.KB_LIST,
    PERMISSION.DOCUMENT_LIST,
    PERMISSION.FAVORITE_LIST,
    PERMISSION.REVIEW_LIST,
    PERMISSION.METADATA_SCHEMA_MANAGE,
    PERMISSION.RETENTION_MANAGE,
    PERMISSION.DELETION_READ,
    PERMISSION.ANALYTICS_READ,
    PERMISSION.ANALYTICS_SCREEN,
    PERMISSION.TENANT_MEMBER_MANAGE,
    PERMISSION.TAG_MANAGE,
    PERMISSION.API_KEY_MANAGE,
    PERMISSION.WEBHOOK_MANAGE,
    PERMISSION.AUDIT_READ,
  ],
  features: ["governance", "analytics"],
};

function hrefs(nav: ReturnType<typeof buildNav>): string[] {
  return nav.flatMap((group) => group.items.map((item) => item.href));
}

describe("buildNav 动态菜单过滤", () => {
  it("MEMBER 仅基础消费权限时只显示知识消费与资产入口", () => {
    const nav = buildNav({
      permissions: [
        PERMISSION.DASHBOARD_VIEW,
        PERMISSION.CHAT_USE,
        PERMISSION.SEARCH_EXECUTE,
        PERMISSION.KB_LIST,
        PERMISSION.DOCUMENT_LIST,
        PERMISSION.FAVORITE_LIST,
      ],
      features: [],
    });
    const list = hrefs(nav);
    expect(list).toContain("/dashboard");
    expect(list).toContain("/kbs");
    expect(list).not.toContain("/admin/users");
    expect(list).not.toContain("/governance/review");
    expect(list).not.toContain("/analytics");
  });

  it("未知/缺失权限默认隐藏，不宽松降级", () => {
    const nav = buildNav({ permissions: [PERMISSION.DASHBOARD_VIEW, "totally:unknown"], features: [] });
    const list = hrefs(nav);
    expect(list).toContain("/dashboard");
    expect(list).not.toContain("/chat"); // 无 chat:use
    expect(list).not.toContain("/kbs"); // 无 kb:list
  });

  it("feature 未启用时隐藏治理中心", () => {
    const nav = buildNav({
      permissions: [PERMISSION.REVIEW_LIST, PERMISSION.DASHBOARD_VIEW], // 有权限但 governance feature 未启用
      features: [],
    });
    expect(hrefs(nav)).not.toContain("/governance/review");
  });

  it("子菜单全为空时隐藏父菜单", () => {
    const nav = buildNav({ permissions: [PERMISSION.DASHBOARD_VIEW], features: ["governance"] });
    expect(nav.some((group) => group.items.some((item) => item.href.startsWith("/governance")))).toBe(false);
  });

  it("父菜单下可见子项保留并命中当前路径", () => {
    const nav = buildNav(ADMIN_CTX);
    const governance = nav.find((group) => group.items.some((item) => item.href.startsWith("/governance")));
    expect(governance).toBeDefined();
    expect(governance!.items.find((item) => item.href.startsWith("/governance"))?.children?.length).toBeGreaterThan(0);
    expect(findSelectedKey(nav, "/governance/metadata")).toBe("/governance/metadata");
    expect(findSelectedKey(nav, "/admin/users")).toBe("/admin/users");
    expect(findSelectedKey(nav, "/not-in-nav")).toBe("");
  });
});
