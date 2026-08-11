"use client";

import { useEffect, useState, type ReactNode } from "react";
import { Button, Result, Spin } from "antd";
import { useRouter } from "next/navigation";

import { api } from "@/api-client";
import type { CurrentUser } from "@/api-client";
import { canAccessAdmin, canAccessGovernance } from "@/lib/roles";

/** 守卫模式：admin = 管理中心，governance = 治理中心（服务端布局传字符串，避免跨边界序列化函数）。 */
export type GuardMode = "admin" | "governance";

/**
 * 路由级权限守卫（体验层）：按角色过滤后渲染 children，无权时展示 403 与返回入口。
 * 服务端仍按统一 401/403/404 兜底；本组件只负责体验，不替代授权。
 */
export function RouteGuard({ mode, children }: { mode: GuardMode; children: ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api
      .getCurrentUser()
      .then((current) => {
        if (!cancelled) setUser(current);
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: "96px 0" }}>
        <Spin tip="正在校验权限…" />
      </div>
    );
  }

  const allowed = user ? (mode === "admin" ? canAccessAdmin(user.roles) : canAccessGovernance(user.roles)) : false;

  if (!allowed) {
    return (
      <Result
        status="403"
        title="无权访问"
        subTitle="该功能需要管理员或治理角色权限。如需开通，请联系租户管理员。"
        extra={
          <Button type="primary" onClick={() => router.replace("/dashboard")}>
            返回工作台
          </Button>
        }
      />
    );
  }

  return <>{children}</>;
}
