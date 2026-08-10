"use client";

import "@ant-design/v5-patch-for-react-19";
import { App as AntdApp, ConfigProvider, theme as antdTheme } from "antd";
import zhCN from "antd/locale/zh_CN";
import { useEffect, useState, type ReactNode } from "react";

import { useTheme } from "@/components/theme-provider";
import { PRIMARY_PRESETS, resolveMode } from "@/lib/theme";

/**
 * antd 主题接入：token 与 ThemeProvider 配置联动（主题色/圆角/明暗算法），
 * 保证 antd 组件与自定义 CSS 组件视觉一致。
 */
export function AntdProvider({ children }: { children: ReactNode }) {
  const { config } = useTheme();
  const preset = PRIMARY_PRESETS.find((p) => p.key === config.primary) ?? PRIMARY_PRESETS[0];
  const [dark, setDark] = useState(() => resolveMode(config.mode) === "dark");

  useEffect(() => {
    setDark(resolveMode(config.mode) === "dark");
    if (config.mode !== "system") return;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => setDark(media.matches);
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [config.mode]);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        token: {
          colorPrimary: preset.color,
          colorInfo: preset.color,
          borderRadius: config.radius,
        },
      }}
    >
      <AntdApp>{children}</AntdApp>
    </ConfigProvider>
  );
}
