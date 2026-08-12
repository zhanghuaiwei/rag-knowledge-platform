"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";

import { Loading } from "@/components/async-state";
import { useAuth } from "@/components/auth-provider";

/**
 * 登录门禁（体验层）：读取 AuthProvider 单一上下文，(main) 分组内页面要求登录态；
 * 未登录/会话过期统一回登录页并保留当前路径。
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { user, loading } = useAuth();

  useEffect(() => {
    if (loading) return;
    if (!user) {
      router.replace(`/login?from=${encodeURIComponent(pathname)}`);
      return;
    }
    // V0.5 本地账号：首登/被重置后须先改密才能进入其他页面
    if (user.mustChangePassword && pathname !== "/change-password") {
      router.replace("/change-password");
    }
  }, [loading, user, pathname, router]);

  if (loading) {
    return (
      <div style={{ minHeight: "60vh", display: "grid", placeItems: "center" }}>
        <Loading text="正在校验登录态…" />
      </div>
    );
  }
  if (!user) {
    return null; // 已触发跳转登录页
  }
  return <>{children}</>;
}
