"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Avatar, Button, Drawer, Dropdown, Input, Layout, Menu, Tooltip } from "antd";
import type { MenuProps } from "antd";
import {
  ApartmentOutlined,
  CheckOutlined,
  DownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  MoonOutlined,
  SearchOutlined,
  SettingOutlined,
  SunOutlined,
} from "@ant-design/icons";

import { api } from "@/api-client";
import type { CurrentUser } from "@/api-client";
import { useToast } from "@/components/feedback";
import { NotificationCenter } from "@/components/notification-center";
import { TaskCenter } from "@/components/task-center";
import { useTheme } from "@/components/theme-provider";
import { ThemeSettings } from "@/components/theme-settings";
import { buildNav, findSelectedKey, NAV_ICON } from "@/components/nav-config";
import { clearSession } from "@/lib/auth";
import { resolveMode } from "@/lib/theme";

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

  // 导航按角色过滤（体验层），管理中心/治理中心对非授权角色隐藏
  const nav = useMemo(() => buildNav(user ?? { roles: [] }), [user]);
  const menuItems: MenuProps["items"] = nav.map((group) => ({
    type: "group",
    label: group.section,
    children: group.items.map((item) => ({
      key: item.href,
      icon: NAV_ICON[item.icon],
      label: item.label,
      children: item.children?.map((child) => ({
        key: child.href,
        icon: NAV_ICON[child.icon],
        label: child.label,
      })),
    })),
  }));
  const selectedKey = useMemo(() => findSelectedKey(nav, pathname), [nav, pathname]);

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
      items={menuItems}
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

          <TaskCenter />
          <NotificationCenter />

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
        <Menu mode="inline" items={menuItems} selectedKeys={[selectedKey]} onClick={navigate} style={{ borderInlineEnd: 0 }} />
      </Drawer>
    </Layout>
  );
}
