"use client";

import { type ReactNode } from "react";
import { Button, Result } from "antd";
import { useRouter } from "next/navigation";

import { Loading } from "@/components/async-state";
import { useAuth } from "@/components/auth-provider";
import { canAny } from "@/lib/roles";
import type { Permission } from "@/lib/permissions";

/**
 * 路由级权限守卫（体验层，dynamic-menu §8 第二道控制）：
 * 满足任一 requiredAny 权限才渲染 children，否则展示 403 与返回入口。
 * 服务端仍按统一 401/403/404 兜底；本组件不替代授权。
 */
export function RouteGuard({ requiredAny, children }: { requiredAny: readonly Permission[]; children: ReactNode }) {
  const router = useRouter();
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: "96px 0" }}>
        <Loading text="正在校验权限…" />
      </div>
    );
  }

  const allowed = user ? canAny(user.permissions, requiredAny) : false;

  if (!allowed) {
    return (
      <Result
        status="403"
        title="无权访问"
        subTitle="当前租户角色没有该功能入口权限。如需开通，请联系租户管理员。"
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
