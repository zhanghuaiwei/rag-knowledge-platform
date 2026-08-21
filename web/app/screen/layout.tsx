import type { ReactNode } from "react";

import { AuthProvider } from "@/components/auth-provider";
import { AuthGate } from "@/components/auth-gate";

/** 大屏为独立全屏路由组（无侧边栏 AppShell），需自带 AuthProvider 以供 AuthGate 读取登录态。 */
export default function ScreenLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <AuthGate>{children}</AuthGate>
    </AuthProvider>
  );
}
