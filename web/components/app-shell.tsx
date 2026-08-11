"use client";

import { usePathname, useRouter } from "next/navigation";
import { useMemo, useState, type ReactNode } from "react";
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

import { useAuth } from "@/components/auth-provider";
import { useToast } from "@/components/feedback";
import { NotificationCenter } from "@/components/notification-center";
import { TaskCenter } from "@/components/task-center";
import { useTheme } from "@/components/theme-provider";
import { ThemeSettings } from "@/components/theme-settings";
import { buildNav, findSelectedKey, NAV_ICON } from "@/components/nav-config";
import { clearSession } from "@/lib/auth";
import { resolveMode } from "@/lib/theme";

/**
 * 应用外壳：侧边栏 + 顶栏 + 租户切换。
 *
 * 用户/权限上下文来自 AuthProvider 单一快照；菜单按 permissions/features 动态过滤；
 * 租户切换调用后端 /auth/tenant/switch（JWT 重签），成功后原子替换上下文并清理旧租户缓存，
 * 当前路径不再可用时回工作台。
 */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const toast = useToast();
  const { config, update } = useTheme();
  const { user, switchTenant, logout } = useAuth();
  const [switching, setSwitching] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [globalKeyword, setGlobalKeyword] = useState("");

  const collapsed = config.sidebarCollapsed;
  const dark = resolveMode(config.mode) === "dark";

  // 导航按当前租户授权上下文过滤（体验层；服务端独立授权兜底）
  const nav = useMemo(
    () => buildNav({ permissions: user?.permissions ?? [], features: user?.features ?? [] }),
    [user],
  );
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

  const tenantName = user?.tenantName ?? "";

  const handleSwitchTenant = async (id: number) => {
    if (id === user?.tenantId || switching) return;
    setSwitching(true);
    try {
      const next = await switchTenant(id);
      toast("info", `已切换到「${next.tenantName}」，正在刷新上下文…`);
      // 当前路径在新租户菜单中不再允许 → 回工作台（dynamic-menu §7.5）
      if (pathname !== "/dashboard" && !findSelectedKey(buildNav(next), pathname)) {
        router.replace("/dashboard");
      }
    } catch {
      toast("error", "切换租户失败：你不是该租户的可用成员");
    } finally {
      setSwitching(false);
    }
  };

  const submitGlobalSearch = () => {
    const kw = globalKeyword.trim();
    if (kw) router.push(`/search?keyword=${encodeURIComponent(kw)}`);
  };

  const navigate = ({ key }: { key: string }) => router.push(key);

  const tenantMenu: MenuProps["items"] = [
    ...(user?.tenants ?? []).map((tenant) => ({
      key: String(tenant.tenantId),
      label: tenant.tenantName,
      icon: tenant.tenantId === user?.tenantId ? <CheckOutlined /> : undefined,
    })),
    { type: "divider" },
    { key: "hint", label: "切换租户将清空当前缓存上下文", disabled: true },
  ];

  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      // 后端登出失败也兜底清理本地内存 token
    } finally {
      clearSession();
      router.replace("/login");
    }
  };

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
      onClick: () => void handleLogout(),
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

          <Dropdown menu={{ items: tenantMenu, onClick: ({ key }) => void handleSwitchTenant(Number(key)) }} trigger={["click"]}>
            <Button type="text" icon={<ApartmentOutlined />} style={{ maxWidth: 180 }} loading={switching}>
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
          {/* key=tenantId：切换租户即整体重挂载，清空旧租户请求缓存/SSE/轮询 */}
          <div className="shell-main-inner" key={user?.tenantId ?? "no-tenant"}>
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
