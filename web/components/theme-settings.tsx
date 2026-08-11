"use client";

import { Button, Drawer, Segmented, Slider, Switch, Tooltip } from "antd";
import type { ReactNode } from "react";

import { useTheme } from "@/components/theme-provider";
import { PRIMARY_PRESETS, type FontSizeLevel, type ThemeMode } from "@/lib/theme";

function SettingRow({ label, desc, children }: { label: string; desc: string; children: ReactNode }) {
  return (
    <div style={{ marginBottom: 22 }}>
      <div style={{ fontWeight: 500 }}>{label}</div>
      <div style={{ fontSize: 12, color: "var(--text-3)", margin: "2px 0 10px" }}>{desc}</div>
      {children}
    </div>
  );
}

/** 外观与布局设置抽屉：全部使用 antd 控件，状态与 ThemeProvider 联动。 */
export function ThemeSettings({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { config, update, reset } = useTheme();

  return (
    <Drawer title="外观与布局设置" open={open} onClose={onClose} width={320}>
      <SettingRow label="主题模式" desc="「跟随系统」随操作系统明暗自动切换">
        <Segmented<ThemeMode>
          block
          options={[
            { label: "浅色", value: "light" },
            { label: "深色", value: "dark" },
            { label: "跟随系统", value: "system" },
          ]}
          value={config.mode}
          onChange={(v) => update({ mode: v })}
        />
      </SettingRow>

      <SettingRow label="字号" desc="全局基础字号档位">
        <Segmented<FontSizeLevel>
          block
          options={[
            { label: "小", value: "small" },
            { label: "标准", value: "standard" },
            { label: "大", value: "large" },
          ]}
          value={config.fontSize}
          onChange={(v) => update({ fontSize: v })}
        />
      </SettingRow>

      <SettingRow label="主题色" desc="应用于主按钮、高亮与图表">
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          {PRIMARY_PRESETS.map((preset) => (
            <Tooltip key={preset.key} title={preset.name}>
              <button
                type="button"
                aria-label={`主题色 ${preset.name}`}
                onClick={() => update({ primary: preset.key })}
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: 8,
                  padding: 0,
                  cursor: "pointer",
                  background: preset.color,
                  border: config.primary === preset.key ? "2px solid var(--text-1)" : "2px solid transparent",
                }}
              />
            </Tooltip>
          ))}
        </div>
      </SettingRow>

      <SettingRow label="圆角大小" desc={`${config.radius}px`}>
        <Slider min={2} max={16} value={config.radius} onChange={(v) => update({ radius: v })} />
      </SettingRow>

      <SettingRow label="紧凑密度" desc="缩小卡片、表格与输入间距">
        <Switch checked={config.density === "compact"} onChange={(v) => update({ density: v ? "compact" : "comfortable" })} />
      </SettingRow>

      <SettingRow label="固定内容宽度" desc="大屏下限制内容最大宽度居中">
        <Switch checked={config.contentWidth === "fixed"} onChange={(v) => update({ contentWidth: v ? "fixed" : "fluid" })} />
      </SettingRow>

      <SettingRow label="默认收起侧边栏" desc="仅保留图标的窄栏模式">
        <Switch checked={config.sidebarCollapsed} onChange={(v) => update({ sidebarCollapsed: v })} />
      </SettingRow>

      <SettingRow label="页面动画" desc="关闭后去除过渡与入场动效">
        <Switch checked={config.animations} onChange={(v) => update({ animations: v })} />
      </SettingRow>

      <SettingRow label="灰色模式" desc="全站去色，适用于特殊日期">
        <Switch checked={config.grayscale} onChange={(v) => update({ grayscale: v })} />
      </SettingRow>

      <Button block onClick={reset}>
        恢复默认设置
      </Button>
    </Drawer>
  );
}
