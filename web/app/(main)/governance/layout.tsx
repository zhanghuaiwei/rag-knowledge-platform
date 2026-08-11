"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Tabs } from "antd";

import { RouteGuard } from "@/components/route-guard";

const TABS = [
  { key: "/governance/review", label: "审核队列" },
  { key: "/governance/metadata", label: "元数据与分类" },
  { key: "/governance/retention", label: "保留与法律保全" },
  { key: "/governance/deletion", label: "删除与证明" },
];

/** 治理中心路由门禁 + 子导航（体验层，服务端策略兜底）。 */
function GovernanceNav({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const active = TABS.find((tab) => pathname === tab.key || pathname.startsWith(`${tab.key}/`))?.key ?? TABS[0].key;
  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">治理中心</h1>
          <p className="page-desc">发布审核 · 元数据 · 保留与法律保全 · 可证明删除</p>
        </div>
      </div>
      <Tabs
        activeKey={active}
        items={TABS.map((tab) => ({
          key: tab.key,
          label: <Link href={tab.key}>{tab.label}</Link>,
        }))}
        style={{ marginBottom: 16 }}
      />
      {children}
    </div>
  );
}

export default function GovernanceLayout({ children }: { children: React.ReactNode }) {
  return (
    <RouteGuard mode="governance">
      <GovernanceNav>{children}</GovernanceNav>
    </RouteGuard>
  );
}
