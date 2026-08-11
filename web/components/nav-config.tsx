"use client";

import type { ReactNode } from "react";
import {
  ApiOutlined,
  BarChartOutlined,
  ClockCircleOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  KeyOutlined,
  MessageOutlined,
  MonitorOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  StarOutlined,
  TagsOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import { PERMISSION, type Permission } from "@/lib/permissions";

export type NavIconKey =
  | "dashboard" | "chat" | "search" | "kb" | "doc" | "favorite"
  | "shield" | "review" | "metadata" | "retention" | "deletion"
  | "chart" | "monitor" | "users" | "key" | "webhook" | "audit" | "tag";

export interface NavItem {
  key: string;
  href: string;
  label: string;
  icon: NavIconKey;
  /** 满足其一即可见（默认拒绝：不在 permissions 中的未知权限一律隐藏）。 */
  requiredAny?: readonly Permission[];
  /** 全部满足才可见。 */
  requiredAll?: readonly Permission[];
  /** 需要当前租户已启用该 feature。 */
  feature?: string;
  children?: NavItem[];
}

export interface NavGroup {
  section: string;
  items: NavItem[];
}

/** 菜单/路由守卫共用的授权上下文。 */
export interface NavContext {
  permissions: string[];
  features: string[];
}

export const NAV_ICON: Record<NavIconKey, ReactNode> = {
  dashboard: <DashboardOutlined />,
  chat: <MessageOutlined />,
  search: <SearchOutlined />,
  kb: <DatabaseOutlined />,
  doc: <FileTextOutlined />,
  favorite: <StarOutlined />,
  shield: <SafetyCertificateOutlined />,
  review: <FileDoneOutlined />,
  metadata: <ProfileOutlined />,
  retention: <ClockCircleOutlined />,
  deletion: <DeleteOutlined />,
  chart: <BarChartOutlined />,
  monitor: <MonitorOutlined />,
  users: <TeamOutlined />,
  key: <KeyOutlined />,
  webhook: <ApiOutlined />,
  audit: <FileDoneOutlined />,
  tag: <TagsOutlined />,
};

/** 静态导航目录（版本受控）；权限码对齐服务端 PermissionCatalog / OpenAPI。 */
const BASE_NAV: NavGroup[] = [
  {
    section: "知识消费",
    items: [
      { key: "dashboard", href: "/dashboard", label: "工作台", icon: "dashboard", requiredAny: [PERMISSION.DASHBOARD_VIEW] },
      { key: "chat", href: "/chat", label: "智能问答", icon: "chat", requiredAny: [PERMISSION.CHAT_USE] },
      { key: "search", href: "/search", label: "全文搜索", icon: "search", requiredAny: [PERMISSION.SEARCH_EXECUTE] },
    ],
  },
  {
    section: "知识资产",
    items: [
      { key: "kb", href: "/kbs", label: "知识库", icon: "kb", requiredAny: [PERMISSION.KB_LIST] },
      { key: "doc", href: "/documents", label: "文档库", icon: "doc", requiredAny: [PERMISSION.DOCUMENT_LIST] },
      { key: "favorite", href: "/favorites", label: "我的收藏", icon: "favorite", requiredAny: [PERMISSION.FAVORITE_LIST] },
      {
        key: "governance",
        href: "/governance/review",
        label: "治理中心",
        icon: "shield",
        feature: "governance",
        children: [
          { key: "review", href: "/governance/review", label: "审核队列", icon: "review", requiredAny: [PERMISSION.REVIEW_LIST] },
          { key: "metadata", href: "/governance/metadata", label: "元数据与分类", icon: "metadata", requiredAny: [PERMISSION.METADATA_SCHEMA_MANAGE] },
          { key: "retention", href: "/governance/retention", label: "保留与法律保全", icon: "retention", requiredAny: [PERMISSION.RETENTION_MANAGE] },
          { key: "deletion", href: "/governance/deletion", label: "删除与证明", icon: "deletion", requiredAny: [PERMISSION.DELETION_READ] },
        ],
      },
    ],
  },
  {
    section: "运营",
    items: [
      { key: "analytics", href: "/analytics", label: "质量与用量", icon: "chart", requiredAny: [PERMISSION.ANALYTICS_READ] },
      { key: "screen", href: "/screen", label: "数据大屏", icon: "monitor", feature: "analytics", requiredAny: [PERMISSION.ANALYTICS_SCREEN] },
    ],
  },
  {
    section: "管理中心",
    items: [
      { key: "users", href: "/admin/users", label: "成员与组织", icon: "users", requiredAny: [PERMISSION.TENANT_MEMBER_MANAGE] },
      { key: "tags", href: "/admin/tags", label: "标签管理", icon: "tag", requiredAny: [PERMISSION.TAG_MANAGE] },
      { key: "api-keys", href: "/admin/api-keys", label: "API Key", icon: "key", requiredAny: [PERMISSION.API_KEY_MANAGE] },
      { key: "webhooks", href: "/admin/webhooks", label: "Webhook", icon: "webhook", requiredAny: [PERMISSION.WEBHOOK_MANAGE] },
      { key: "audit", href: "/admin/audit", label: "审计日志", icon: "audit", requiredAny: [PERMISSION.AUDIT_READ] },
    ],
  },
];

function isVisible(item: NavItem, context: NavContext): boolean {
  // 未知/缺失权限默认隐藏；不宽松降级
  if (item.requiredAll && !item.requiredAll.every((permission) => context.permissions.includes(permission))) {
    return false;
  }
  if (item.requiredAny && !item.requiredAny.some((permission) => context.permissions.includes(permission))) {
    return false;
  }
  if (item.feature && !context.features.includes(item.feature)) {
    return false;
  }
  return true;
}

/** 按当前租户授权上下文过滤静态目录（体验层；服务端策略兜底）。 */
export function buildNav(context: NavContext): NavGroup[] {
  const groups: NavGroup[] = [];
  for (const group of BASE_NAV) {
    const items = group.items
      .map((item) => {
        if (!item.children) return isVisible(item, context) ? item : null;
        // 父节点自身 feature/权限不满足则整组隐藏（如治理中心 feature 未启用）
        if (!isVisible(item, context)) return null;
        // 子菜单过滤后为空时隐藏父菜单
        const children = item.children.filter((child) => isVisible(child, context));
        return children.length ? { ...item, children } : null;
      })
      .filter((item): item is NavItem => item !== null);
    if (items.length) groups.push({ section: group.section, items });
  }
  return groups;
}

/** 展平导航并命中当前路径（支持子菜单）。 */
export function findSelectedKey(nav: NavGroup[], pathname: string): string {
  for (const group of nav) {
    for (const item of group.items) {
      const candidates = [item, ...(item.children ?? [])];
      const hit = candidates.find((c) => pathname === c.href || pathname.startsWith(`${c.href}/`));
      if (hit) return hit.href;
    }
  }
  return "";
}
