import type { ReactNode } from "react";

import { AuthProvider } from "@/components/auth-provider";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";

/** 单一认证上下文：AuthGate / AppShell / RouteGuard 共用同一份用户与权限快照。 */
export default function MainLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <AuthGate>
        <AppShell>{children}</AppShell>
      </AuthGate>
    </AuthProvider>
  );
}
