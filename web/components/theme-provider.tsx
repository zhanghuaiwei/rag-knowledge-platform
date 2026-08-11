"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

import {
  applyTheme,
  DEFAULT_THEME,
  loadTheme,
  saveTheme,
  type ThemeConfig,
} from "@/lib/theme";

interface ThemeContextValue {
  config: ThemeConfig;
  update: (patch: Partial<ThemeConfig>) => void;
  reset: () => void;
}

const ThemeContext = createContext<ThemeContextValue>({
  config: DEFAULT_THEME,
  update: () => undefined,
  reset: () => undefined,
});

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<ThemeConfig>(DEFAULT_THEME);

  useEffect(() => {
    const loaded = loadTheme();
    setConfig(loaded);
    applyTheme(loaded);
  }, []);

  // system 模式下监听操作系统明暗切换，实时重应用主题
  useEffect(() => {
    if (config.mode !== "system") return;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => applyTheme(config);
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [config]);

  const update = (patch: Partial<ThemeConfig>) => {
    setConfig((prev) => {
      const next = { ...prev, ...patch };
      applyTheme(next);
      saveTheme(next);
      return next;
    });
  };

  const reset = () => {
    setConfig(DEFAULT_THEME);
    applyTheme(DEFAULT_THEME);
    saveTheme(DEFAULT_THEME);
  };

  return <ThemeContext.Provider value={{ config, update, reset }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  return useContext(ThemeContext);
}
