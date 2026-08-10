/**
 * mock 用户与组织数据（对齐 sys_tenant / sys_user / sys_org / tenant_member 语义）。
 * 均为演示数据，不代表真实身份；生产由企业 IdP / SCIM 提供。
 */
import type { CurrentUser, Org, User } from "@/api-client/types";

export const currentUser: CurrentUser = {
  id: 1,
  name: "张怀伟",
  email: "zhanghuaiwei@example.com",
  tenantId: 1,
  tenantName: "华云科技",
  roles: ["TENANT_ADMIN", "KNOWLEDGE_ADMIN"],
  orgName: "研发中心",
};

export const orgs: Org[] = [
  { id: 1, parentId: null, name: "华云科技", path: "/", memberCount: 12, status: "ACTIVE" },
  { id: 2, parentId: 1, name: "研发中心", path: "/研发中心", memberCount: 5, status: "ACTIVE" },
  { id: 3, parentId: 1, name: "产品部", path: "/产品部", memberCount: 3, status: "ACTIVE" },
  { id: 4, parentId: 1, name: "销售部", path: "/销售部", memberCount: 2, status: "ACTIVE" },
  { id: 5, parentId: 1, name: "客户成功部", path: "/客户成功部", memberCount: 1, status: "ACTIVE" },
  { id: 6, parentId: 1, name: "法务合规部", path: "/法务合规部", memberCount: 1, status: "ACTIVE" },
];

export const users: User[] = [
  { id: 1, name: "张怀伟", email: "zhanghuaiwei@example.com", status: "ACTIVE", role: "TENANT_ADMIN", orgName: "研发中心", lastLoginAt: "2026-08-10T01:20:00Z" },
  { id: 2, name: "李佳宁", email: "lijianing@example.com", status: "ACTIVE", role: "KNOWLEDGE_ADMIN", orgName: "研发中心", lastLoginAt: "2026-08-09T09:10:00Z" },
  { id: 3, name: "王建国", email: "wangjianguo@example.com", status: "ACTIVE", role: "MEMBER", orgName: "研发中心", lastLoginAt: "2026-08-08T02:45:00Z" },
  { id: 4, name: "陈晓芸", email: "chenxiaoyun@example.com", status: "ACTIVE", role: "MEMBER", orgName: "产品部", lastLoginAt: "2026-08-10T00:05:00Z" },
  { id: 5, name: "赵子豪", email: "zhaozihao@example.com", status: "ACTIVE", role: "MEMBER", orgName: "产品部", lastLoginAt: "2026-08-07T06:30:00Z" },
  { id: 6, name: "刘思彤", email: "liusitong@example.com", status: "DISABLED", role: "MEMBER", orgName: "销售部", lastLoginAt: "2026-07-20T03:00:00Z" },
  { id: 7, name: "孙志强", email: "sunzhiqiang@example.com", status: "ACTIVE", role: "AUDITOR", orgName: "法务合规部", lastLoginAt: "2026-08-09T08:40:00Z" },
  { id: 8, name: "周雨桐", email: "zhouyutong@example.com", status: "ACTIVE", role: "MEMBER", orgName: "客户成功部", lastLoginAt: "2026-08-10T02:15:00Z" },
];
