import { AntdRegistry } from "@ant-design/nextjs-registry";
import type { Metadata } from "next";
import type { ReactNode } from "react";

import { AntdProvider } from "@/components/antd-provider";
import { ThemeProvider } from "@/components/theme-provider";
import { buildThemeInitScript } from "@/lib/theme";
import "./globals.css";

export const metadata: Metadata = {
  title: "知识库平台",
  description: "通用企业知识库平台 — 问答、搜索、知识库、治理与运营",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <head>
        {/* 预水合还原主题，避免暗色/自定义主题首帧闪烁 */}
        <script dangerouslySetInnerHTML={{ __html: buildThemeInitScript() }} />
      </head>
      <body>
        <AntdRegistry>
          <ThemeProvider>
            <AntdProvider>{children}</AntdProvider>
          </ThemeProvider>
        </AntdRegistry>
      </body>
    </html>
  );
}
