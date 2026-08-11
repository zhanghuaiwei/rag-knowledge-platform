"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

import { api } from "@/api-client";
import { Loading } from "@/components/async-state";

/** 路由守卫：(main) 分组内页面要求登录态；启动时经 getCurrentUser（含自动 refresh）校验。 */
export function AuthGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api
      .getCurrentUser()
      .then(() => {
        if (!cancelled) setAllowed(true);
      })
      .catch(() => {
        if (!cancelled) router.replace(`/login?from=${encodeURIComponent(pathname)}`);
      });
    return () => {
      cancelled = true;
    };
  }, [router, pathname]);

  if (!allowed) {
    return (
      <div style={{ minHeight: "60vh", display: "grid", placeItems: "center" }}>
        <Loading text="正在校验登录态…" />
      </div>
    );
  }
  return <>{children}</>;
}
