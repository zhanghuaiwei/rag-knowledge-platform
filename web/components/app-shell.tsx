"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Avatar, Button, Drawer, Dropdown, Input, Layout, Menu, Tooltip } from "antd";
import type { MenuProps } from "antd";
import {
  ApiOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  CheckOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DownOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  KeyOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  MonitorOutlined,
  MoonOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  SunOutlined,
  TeamOutlined,
} from "@ant-design/icons";

import { api } from "@/api-client";
import type { CurrentUser } from "@/api-client";
import { useToast } from "@/components/feedback";
import { useTheme } from "@/components/theme-provider";
import { ThemeSettings } from "@/components/theme-settings";
import { clearSession } from "@/lib/auth";
import { resolveMode } from "@/lib/theme";

type NavIconKey = "dashboard" | "chat" | "search" | "kb" | "doc" | "shield" | "chart" | "monitor" | "users" | "key" | "webhook" | "audit";

interface NavItem {
  href: string;
  label: string;
  icon: NavIconKey;
}

interface NavGroup {
  section: string;
  items: NavItem[];
}

const NAV_ICON: Record<NavIconKey, ReactNode> = {
  dashboard: <DashboardOutlined />,
  chat: <MessageOutlined />,
  search: <SearchOutlined />,
  kb: <DatabaseOutlined />,
  doc: <FileTextOutlined />,
  shield: <SafetyCertificateOutlined />,
  chart: <BarChartOutlined />,
  monitor: <MonitorOutlined />,
  users: <TeamOutlined />,
  key: <KeyOutlined />,
  webhook: <ApiOutlined />,
  audit: <FileDoneOutlined />,
};

const NAV: NavGroup[] = [
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
      { href: "/governance/review", label: "治理中心", icon: "shield" },
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
      { href: "/admin/api-keys", label: "API Key", icon: "key" },
      { href: "/admin/webhooks", label: "Webhook", icon: "webhook" },
      { href: "/admin/audit", label: "审计日志", icon: "audit" },
    ],
  },
];

const MENU_ITEMS: MenuProps["items"] = NAV.map((group) => ({
  type: "group",
  label: group.section,
  children: group.items.map((item) => ({
    key: item.href,
    icon: NAV_ICON[item.icon],
    label: item.label,
  })),
}));

/** mock 租户列表：真实环境由租户成员关系接口返回（待契约）。 */
const MOCK_TENANTS = [
  { id: 1, name: "云图科技" },
  { id: 2, name: "云图科技（华南）" },
  { id: 3, name: "生态合作伙伴" },
];

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const toast = useToast();
  const { config, update } = useTheme();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [tenantId, setTenantId] = useState(1);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [globalKeyword, setGlobalKeyword] = useState("");

  useEffect(() => {
    api.getCurrentUser().then(setUser).catch(() => undefined);
  }, []);

  useEffect(() => setMobileMenuOpen(false), [pathname]);

  const collapsed = config.sidebarCollapsed;
  const dark = resolveMode(config.mode) === "dark";

  const selectedKey = useMemo(
    () =>
      NAV.flatMap((g) => g.items).find(
        (item) => pathname === item.href || pathname.startsWith(`${item.href}/`),
      )?.href ?? "",
    [pathname],
  );

  const tenantName = MOCK_TENANTS.find((t) => t.id === tenantId)?.name ?? user?.tenantName ?? "";

  const switchTenant = (id: number) => {
    if (id === tenantId) return;
    setTenantId(id);
    const tenant = MOCK_TENANTS.find((t) => t.id === id);
    toast("info", `已切换到「${tenant?.name}」，正在刷新上下文…`);
  };

  const submitGlobalSearch = () => {
    const kw = globalKeyword.trim();
    if (kw) router.push(`/search?keyword=${encodeURIComponent(kw)}`);
  };

  const navigate = ({ key }: { key: string }) => router.push(key);

  const tenantMenu: MenuProps["items"] = [
    ...MOCK_TENANTS.map((t) => ({
      key: String(t.id),
      label: t.name,
      icon: t.id === tenantId ? <CheckOutlined /> : undefined,
    })),
    { type: "divider" },
    { key: "hint", label: "切换租户将清空当前缓存上下文", disabled: true },
  ];

  const userMenu: MenuProps["items"] = [
    {
      key: "info",
      label: (
        <div>
          <div style={{ fontWeight: 600 }}>{user?.name ?? "加载中…"}</div>
          <div style={{ fontSize: 12, color: "var(--text-3)" }}>{user?.email}</div>
          <div style={{ fontSize: 12, color: "var(--text-3)" }}>{user?.orgName}</div>
        </div>
      ),
      disabled: true,
    },
    { type: "divider" },
    {
      key: "logout",
      icon: <LogoutOutlined />,
      label: "退出登录",
      danger: true,
      onClick: () => {
        clearSession();
        router.replace("/login");
      },
    },
  ];

  const siderMenu = (
    <Menu
      mode="inline"
      inlineCollapsed={collapsed && !isMobile}
      items={MENU_ITEMS}
      selectedKeys={[selectedKey]}
      onClick={navigate}
      style={{ borderInlineEnd: 0, background: "transparent" }}
    />
  );

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Layout.Sider
        width={220}
        collapsedWidth={isMobile ? 0 : 64}
        collapsible
        collapsed={collapsed}
        trigger={null}
        breakpoint="lg"
        onBreakpoint={setIsMobile}
        theme="light"
        className="shell-sider"
        style={{ background: "var(--surface)", borderRight: "1px solid var(--border)" }}
      >
        <div className="shell-brand" style={{ justifyContent: collapsed ? "center" : undefined, padding: collapsed ? 0 : undefined }}>
          <span className="brand-logo">知</span>
          {!collapsed ? <span style={{ fontWeight: 700, fontSize: 15 }}>知识库平台</span> : null}
        </div>
        {siderMenu}
      </Layout.Sider>

      <Layout>
        <Layout.Header className="shell-topbar" style={{ background: "var(--surface)", borderBottom: "1px solid var(--border)", height: 60, lineHeight: "60px", padding: "0 20px" }}>
          {isMobile ? (
            <Button type="text" icon={<MenuOutlined />} aria-label="打开菜单" onClick={() => setMobileMenuOpen(true)} />
          ) : (
            <Tooltip title={collapsed ? "展开侧边栏" : "收起侧边栏"}>
              <Button type="text" aria-label="收起侧边栏" icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={() => update({ sidebarCollapsed: !collapsed })} />
            </Tooltip>
          )}

          <Input
            className="topbar-search-input"
            prefix={<SearchOutlined />}
            placeholder="全局搜索文档…"
            value={globalKeyword}
            onChange={(e) => setGlobalKeyword(e.target.value)}
            onPressEnter={submitGlobalSearch}
            allowClear
            aria-label="全局搜索"
            style={{ flex: 1, maxWidth: 420 }}
          />

          <div style={{ flex: 1 }} />

          <Dropdown menu={{ items: tenantMenu, onClick: ({ key }) => switchTenant(Number(key)) }} trigger={["click"]}>
            <Button type="text" icon={<ApartmentOutlined />} style={{ maxWidth: 180 }}>
              <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", display: "inline-block", maxWidth: 120 }}>{tenantName}</span>
              <DownOutlined style={{ fontSize: 10 }} />
            </Button>
          </Dropdown>

          <Tooltip title={dark ? "切换到浅色" : "切换到深色"}>
            <Button type="text" icon={dark ? <SunOutlined /> : <MoonOutlined />} aria-label="切换明暗主题" onClick={() => update({ mode: dark ? "light" : "dark" })} />
          </Tooltip>
          <Tooltip title="外观设置">
            <Button type="text" icon={<SettingOutlined />} aria-label="外观设置" onClick={() => setSettingsOpen(true)} />
          </Tooltip>

          <Dropdown menu={{ items: userMenu }} trigger={["click"]}>
            <Button type="text" icon={<Avatar size={28} style={{ background: "linear-gradient(135deg,var(--primary),var(--violet))", color: "#fff", fontSize: 13 }}>{user?.name?.slice(0, 1) ?? "…"}</Avatar>} aria-label="用户菜单" />
          </Dropdown>
        </Layout.Header>

        <Layout.Content className="shell-main">
          {/* key=tenantId：切换租户即整体重挂载，清空旧租户缓存（mock 演示语义） */}
          <div className="shell-main-inner" key={tenantId}>
            {children}
          </div>
        </Layout.Content>
      </Layout>

      <ThemeSettings open={settingsOpen} onClose={() => setSettingsOpen(false)} />

      <Drawer
        placement="left"
        width={240}
        open={mobileMenuOpen}
        onClose={() => setMobileMenuOpen(false)}
        title="知识库平台"
        styles={{ body: { padding: 0 } }}
      >
        <Menu mode="inline" items={MENU_ITEMS} selectedKeys={[selectedKey]} onClick={navigate} style={{ borderInlineEnd: 0 }} />
      </Drawer>
    </Layout>
  );
}
