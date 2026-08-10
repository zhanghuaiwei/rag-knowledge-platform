"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

import { Loading } from "@/components/async-state";
import { isAuthed } from "@/lib/auth";

/** 路由守卫（mock）：(main) 分组内页面要求登录态，未登录跳 /login。 */
export function AuthGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    if (isAuthed()) {
      setAllowed(true);
    } else {
      router.replace(`/login?from=${encodeURIComponent(pathname)}`);
    }
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
