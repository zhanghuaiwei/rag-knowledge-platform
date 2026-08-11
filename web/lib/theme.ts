/**
 * 主题与布局配置：所有可配置项集中在此，ThemeProvider 持久化到 localStorage
 * 并应用到 <html> 的 data-* 属性与 CSS 变量（见 app/globals.css）。
 */

export type ThemeMode = "light" | "dark" | "system";
export type Density = "comfortable" | "compact";
export type ContentWidth = "fluid" | "fixed";
export type FontSizeLevel = "small" | "standard" | "large";

export interface ThemeConfig {
  mode: ThemeMode;
  primary: string;
  radius: number;
  density: Density;
  contentWidth: ContentWidth;
  sidebarCollapsed: boolean;
  fontSize: FontSizeLevel;
  grayscale: boolean;
  animations: boolean;
}

export const DEFAULT_THEME: ThemeConfig = {
  mode: "light",
  primary: "blue",
  radius: 10,
  density: "comfortable",
  contentWidth: "fluid",
  sidebarCollapsed: false,
  fontSize: "standard",
  grayscale: false,
  animations: true,
};

/** 字号档位映射（写入 --font-size，内联样式优先于样式表中的 density 规则）。 */
export const FONT_SIZE_MAP: Record<FontSizeLevel, number> = {
  small: 13,
  standard: 14,
  large: 16,
};

/** system 模式解析为实际明暗：跟随 prefers-color-scheme。 */
export function resolveMode(mode: ThemeMode): "light" | "dark" {
  if (mode !== "system") return mode;
  if (typeof window === "undefined") return "light";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export interface PrimaryPreset {
  key: string;
  name: string;
  color: string;
  hover: string;
  softLight: string;
  softDark: string;
}

export const PRIMARY_PRESETS: PrimaryPreset[] = [
  { key: "blue", name: "湖蓝", color: "#2f6bff", hover: "#1f55d6", softLight: "rgba(47,107,255,0.10)", softDark: "rgba(47,107,255,0.22)" },
  { key: "violet", name: "绛紫", color: "#7c3aed", hover: "#6425d0", softLight: "rgba(124,58,237,0.10)", softDark: "rgba(124,58,237,0.24)" },
  { key: "green", name: "竹青", color: "#0d9463", hover: "#0a7a52", softLight: "rgba(13,148,99,0.10)", softDark: "rgba(13,148,99,0.22)" },
  { key: "orange", name: "橘橙", color: "#e0640a", hover: "#c2540a", softLight: "rgba(224,100,10,0.10)", softDark: "rgba(224,100,10,0.22)" },
  { key: "red", name: "朱砂", color: "#d9392b", hover: "#b52a1f", softLight: "rgba(217,57,43,0.10)", softDark: "rgba(217,57,43,0.22)" },
  { key: "cyan", name: "天青", color: "#0b8bd9", hover: "#0a74b8", softLight: "rgba(11,139,217,0.10)", softDark: "rgba(11,139,217,0.22)" },
];

export const THEME_STORAGE_KEY = "ragkb-theme";

export function loadTheme(): ThemeConfig {
  if (typeof window === "undefined") return DEFAULT_THEME;
  try {
    const raw = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (!raw) return DEFAULT_THEME;
    return { ...DEFAULT_THEME, ...(JSON.parse(raw) as Partial<ThemeConfig>) };
  } catch {
    return DEFAULT_THEME;
  }
}

export function saveTheme(config: ThemeConfig): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(config));
}

/** 灰色模式下的主色替代（中性灰阶，语义色保留）。 */
export const GRAY_PRIMARY: Omit<PrimaryPreset, "key" | "name"> = {
  color: "#5a6270",
  hover: "#484f5c",
  softLight: "rgba(90, 98, 112, 0.10)",
  softDark: "rgba(90, 98, 112, 0.24)",
};

/** 将配置应用到 <html>：data-* 属性驱动 globals.css，主题色直接写 CSS 变量。 */
export function applyTheme(config: ThemeConfig): void {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  const mode = resolveMode(config.mode);
  root.dataset.theme = mode;
  root.dataset.density = config.density;
  root.dataset.width = config.contentWidth;
  root.dataset.gray = String(config.grayscale);
  root.dataset.motion = config.animations ? "on" : "off";
  const found = PRIMARY_PRESETS.find((item) => item.key === config.primary) ?? PRIMARY_PRESETS[0];
  const preset = config.grayscale ? GRAY_PRIMARY : found;
  root.style.setProperty("--primary", preset.color);
  root.style.setProperty("--primary-hover", preset.hover);
  root.style.setProperty("--primary-soft", mode === "dark" ? preset.softDark : preset.softLight);
  root.style.setProperty("--radius", `${config.radius}px`);
  root.style.setProperty("--font-size", `${FONT_SIZE_MAP[config.fontSize] ?? 14}px`);
}

/**
 * 生成预水合内联脚本：首帧前从 localStorage 还原主题，避免暗色/自定义主题闪烁（FOUC）。
 * 在 layout.tsx 的 <head> 中以 dangerouslySetInnerHTML 注入；配置源仍唯一（本文件）。
 */
export function buildThemeInitScript(): string {
  const presets = Object.fromEntries(PRIMARY_PRESETS.map((p) => [p.key, [p.color, p.hover, p.softLight, p.softDark]]));
  const payload = JSON.stringify({
    key: THEME_STORAGE_KEY,
    presets,
    gray: [GRAY_PRIMARY.color, GRAY_PRIMARY.hover, GRAY_PRIMARY.softLight, GRAY_PRIMARY.softDark],
    fontSizes: FONT_SIZE_MAP,
  });
  return `(function(){try{var P=${payload};var raw=localStorage.getItem(P.key);var c=raw?JSON.parse(raw):{};var m=c.mode==="dark"||c.mode==="system"?c.mode:"light";if(m==="system"){m=window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"}var r=document.documentElement;r.dataset.theme=m;r.dataset.density=c.density==="compact"?"compact":"comfortable";r.dataset.width=c.contentWidth==="fixed"?"fixed":"fluid";r.dataset.gray=c.grayscale?"true":"false";r.dataset.motion=c.animations===false?"off":"on";var p=c.grayscale?P.gray:(P.presets[c.primary]||P.presets.blue);r.style.setProperty("--primary",p[0]);r.style.setProperty("--primary-hover",p[1]);r.style.setProperty("--primary-soft",m==="dark"?p[3]:p[2]);r.style.setProperty("--radius",(typeof c.radius==="number"?c.radius:10)+"px");r.style.setProperty("--font-size",(P.fontSizes[c.fontSize]||14)+"px")}catch(e){}})()`;
}
