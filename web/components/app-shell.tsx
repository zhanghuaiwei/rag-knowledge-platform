"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

import { api } from "@/api-client";
import type { CurrentUser } from "@/api-client";
import { Icon, type IconName } from "@/components/icons";
import { useTheme } from "@/components/theme-provider";
import { Drawer, Dropdown, Switch, useToast } from "@/components/ui";
import { clearSession } from "@/lib/auth";
import { PRIMARY_PRESETS, resolveMode, type ThemeConfig, type ThemeMode } from "@/lib/theme";

interface NavItem {
  href: string;
  label: string;
  icon: IconName;
}

interface NavGroup {
  section: string;
  items: NavItem[];
}

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
  const [mobileOpen, setMobileOpen] = useState(false);
  const [globalKeyword, setGlobalKeyword] = useState("");

  useEffect(() => {
    api.getCurrentUser().then(setUser).catch(() => undefined);
  }, []);

  useEffect(() => setMobileOpen(false), [pathname]);

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

  const collapsed = config.sidebarCollapsed;
  const tenantName = MOCK_TENANTS.find((t) => t.id === tenantId)?.name ?? user?.tenantName ?? "";

  const shellClass = ["shell", collapsed ? "collapsed" : "", mobileOpen ? "mobile-open" : ""].filter(Boolean).join(" ");

  return (
    <div className={shellClass}>
      <div className="sidebar-mask" onClick={() => setMobileOpen(false)} />

      <aside className="shell-sidebar">
        <div className="shell-brand">
          <span className="brand-logo">知</span>
          <span className="nav-label">知识库平台</span>
        </div>
        <nav className="shell-nav">
          {NAV.map((group) => (
            <div key={group.section}>
              <div className="nav-section">{group.section}</div>
              {group.items.map((item) => {
                const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
                return (
                  <Link key={item.href} className={`nav-item${active ? " active" : ""}`} href={item.href} title={item.label}>
                    <Icon name={item.icon} size={17} />
                    <span className="nav-label">{item.label}</span>
                  </Link>
                );
              })}
            </div>
          ))}
        </nav>
      </aside>

      <div className="shell-body">
        <header className="shell-topbar">
          <button className="icon-btn hamburger" onClick={() => setMobileOpen(true)} aria-label="打开菜单">
            <Icon name="menu" />
          </button>
          <button
            className="icon-btn"
            onClick={() => update({ sidebarCollapsed: !collapsed })}
            aria-label={collapsed ? "展开侧边栏" : "收起侧边栏"}
            title={collapsed ? "展开侧边栏" : "收起侧边栏"}
          >
            <Icon name="menu" />
          </button>

          <div className="topbar-search">
            <Icon name="search" size={15} />
            <input
              placeholder="全局搜索文档…"
              value={globalKeyword}
              onChange={(e) => setGlobalKeyword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && submitGlobalSearch()}
              aria-label="全局搜索"
            />
          </div>

          <div style={{ flex: 1 }} />

          <Dropdown
            align="right"
            trigger={
              <span className="btn btn-sm" style={{ gap: 6 }}>
                <Icon name="building" size={14} />
                <span style={{ maxWidth: 140, overflow: "hidden", textOverflow: "ellipsis" }}>{tenantName}</span>
                <Icon name="chevron-down" size={13} />
              </span>
            }
          >
            {MOCK_TENANTS.map((tenant) => (
              <button key={tenant.id} className={`dropdown-item${tenant.id === tenantId ? " active" : ""}`} onClick={() => switchTenant(tenant.id)}>
                <Icon name="building" size={14} />
                {tenant.name}
                {tenant.id === tenantId ? <Icon name="check" size={14} /> : null}
              </button>
            ))}
            <div className="dropdown-divider" />
            <div style={{ padding: "4px 10px", fontSize: 12, color: "var(--text-3)" }}>切换租户将清空当前缓存上下文</div>
          </Dropdown>

          <button
            className="icon-btn"
            onClick={() => update({ mode: resolveMode(config.mode) === "dark" ? "light" : "dark" })}
            aria-label="切换明暗主题"
            title="切换明暗主题"
          >
            <Icon name={resolveMode(config.mode) === "dark" ? "sun" : "moon"} />
          </button>
          <button className="icon-btn" onClick={() => setSettingsOpen(true)} aria-label="外观设置" title="外观设置">
            <Icon name="settings" />
          </button>

          <Dropdown
            trigger={
              <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                <span className="avatar">{user?.name?.slice(0, 1) ?? "…"}</span>
              </span>
            }
          >
            <div style={{ padding: "8px 10px" }}>
              <div style={{ fontWeight: 600 }}>{user?.name ?? "加载中…"}</div>
              <div style={{ fontSize: 12, color: "var(--text-3)" }}>{user?.email}</div>
              <div style={{ fontSize: 12, color: "var(--text-3)", marginTop: 2 }}>{user?.orgName}</div>
            </div>
            <div className="dropdown-divider" />
            <button
              className="dropdown-item"
              onClick={() => {
                clearSession();
                router.replace("/login");
              }}
            >
              <Icon name="logout" size={14} /> 退出登录
            </button>
          </Dropdown>
        </header>

        <main className="shell-main">
          {/* key=tenantId：切换租户即整体重挂载，清空旧租户缓存（mock 演示语义） */}
          <div className="shell-main-inner" key={tenantId}>
            {children}
          </div>
        </main>
      </div>

      <ThemeSettings open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  );
}

function ThemeSettings({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { config, update, reset } = useTheme();
  return (
    <Drawer title="外观与布局设置" open={open} onClose={onClose}>
      <div className="setting-row">
        <div>
          <div className="setting-label">主题模式</div>
          <div className="setting-desc">「跟随系统」随操作系统明暗自动切换</div>
        </div>
        <div className="seg">
          {([["light", "浅色"], ["dark", "深色"], ["system", "跟随系统"]] as [ThemeMode, string][]).map(([mode, label]) => (
            <button key={mode} className={`seg-item${config.mode === mode ? " active" : ""}`} onClick={() => update({ mode })}>
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="setting-row">
        <div>
          <div className="setting-label">字号</div>
          <div className="setting-desc">全局基础字号档位</div>
        </div>
        <div className="seg">
          {([["small", "小"], ["standard", "标准"], ["large", "大"]] as const).map(([size, label]) => (
            <button key={size} className={`seg-item${config.fontSize === size ? " active" : ""}`} onClick={() => update({ fontSize: size })}>
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="setting-row" style={{ alignItems: "flex-start" }}>
        <div>
          <div className="setting-label">主题色</div>
          <div className="setting-desc">应用于主按钮、高亮与图表</div>
        </div>
        <div className="swatch-row">
          {PRIMARY_PRESETS.map((preset) => (
            <button
              key={preset.key}
              className={`swatch${config.primary === preset.key ? " active" : ""}`}
              style={{ background: preset.color, color: preset.color }}
              title={preset.name}
              aria-label={`主题色 ${preset.name}`}
              onClick={() => update({ primary: preset.key })}
            />
          ))}
        </div>
      </div>

      <div className="setting-row">
        <div>
          <div className="setting-label">圆角大小</div>
          <div className="setting-desc">{config.radius}px</div>
        </div>
        <input
          type="range"
          min={2}
          max={16}
          value={config.radius}
          onChange={(e) => update({ radius: Number(e.target.value) })}
          aria-label="圆角大小"
        />
      </div>

      <div className="setting-row">
        <div>
          <div className="setting-label">紧凑密度</div>
          <div className="setting-desc">缩小卡片、表格与输入间距</div>
        </div>
        <Switch checked={config.density === "compact"} onChange={(v) => update({ density: v ? "compact" : "comfortable" })} />
      </div>

      <div className="setting-row">
        <div>
          <div className="setting-label">固定内容宽度</div>
          <div className="setting-desc">大屏下限制内容最大宽度居中</div>
        </div>
        <Switch checked={config.contentWidth === "fixed"} onChange={(v) => update({ contentWidth: v ? "fixed" : "fluid" })} />
      </div>

      <div className="setting-row">
        <div>
          <div className="setting-label">默认收起侧边栏</div>
          <div className="setting-desc">仅保留图标的窄栏模式</div>
        </div>
        <Switch checked={config.sidebarCollapsed} onChange={(v) => update({ sidebarCollapsed: v })} />
      </div>

      <div className="setting-row">
        <div>
          <div className="setting-label">页面动画</div>
          <div className="setting-desc">关闭后去除过渡与入场动效</div>
        </div>
        <Switch checked={config.animations} onChange={(v) => update({ animations: v })} />
      </div>

      <div className="setting-row">
        <div>
          <div className="setting-label">灰色模式</div>
          <div className="setting-desc">全站去色，适用于特殊日期</div>
        </div>
        <Switch checked={config.grayscale} onChange={(v) => update({ grayscale: v })} />
      </div>

      <button className="btn btn-block" style={{ marginTop: 20 }} onClick={reset}>
        恢复默认设置
      </button>
    </Drawer>
  );
}

export type { ThemeConfig };
