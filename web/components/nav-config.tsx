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
import { canAccessAdmin, canAccessGovernance } from "@/lib/roles";

export type NavIconKey =
  | "dashboard" | "chat" | "search" | "kb" | "doc" | "favorite"
  | "shield" | "review" | "metadata" | "retention" | "deletion"
  | "chart" | "monitor" | "users" | "key" | "webhook" | "audit" | "tag";

export interface NavItem {
  href: string;
  label: string;
  icon: NavIconKey;
  children?: NavItem[];
}

export interface NavGroup {
  section: string;
  items: NavItem[];
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

const BASE_NAV: NavGroup[] = [
  {
    section: "知识消费",
    items: [
      { href: "/dashboard", label: "工作台", icon: "dashboard" },
      { href: "/chat", label: "智能问答", icon: "chat" },
      { href: "/search", label: "全文搜索", icon: "search" },
    ],
  },
  {
    section: "知识资产",
    items: [
      { href: "/kbs", label: "知识库", icon: "kb" },
      { href: "/documents", label: "文档库", icon: "doc" },
      { href: "/favorites", label: "我的收藏", icon: "favorite" },
      {
        href: "/governance/review",
        label: "治理中心",
        icon: "shield",
        children: [
          { href: "/governance/review", label: "审核队列", icon: "review" },
          { href: "/governance/metadata", label: "元数据与分类", icon: "metadata" },
          { href: "/governance/retention", label: "保留与法律保全", icon: "retention" },
          { href: "/governance/deletion", label: "删除与证明", icon: "deletion" },
        ],
      },
    ],
  },
  {
    section: "运营",
    items: [
      { href: "/analytics", label: "质量与用量", icon: "chart" },
      { href: "/screen", label: "数据大屏", icon: "monitor" },
    ],
  },
  {
    section: "管理中心",
    items: [
      { href: "/admin/users", label: "成员与组织", icon: "users" },
      { href: "/admin/tags", label: "标签管理", icon: "tag" },
      { href: "/admin/api-keys", label: "API Key", icon: "key" },
      { href: "/admin/webhooks", label: "Webhook", icon: "webhook" },
      { href: "/admin/audit", label: "审计日志", icon: "audit" },
    ],
  },
];

function canSeeItem(item: NavItem, roles: string[]): boolean {
  if (item.href.startsWith("/governance")) return canAccessGovernance(roles);
  if (item.href.startsWith("/admin")) return canAccessAdmin(roles);
  return true;
}

/** 按当前用户角色过滤导航（体验层）；管理中心仅管理员可见，治理中心仅治理角色可见。 */
export function buildNav(user: { roles: string[] }): NavGroup[] {
  const groups: NavGroup[] = [];
  for (const group of BASE_NAV) {
    if (group.section === "管理中心" && !canAccessAdmin(user.roles)) continue;
    const items = group.items
      .map((item) => {
        if (!item.children) return canSeeItem(item, user.roles) ? item : null;
        const children = item.children.filter((child) => canSeeItem(child, user.roles));
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
