"use client";

import { type ReactNode } from "react";

import { useAuth } from "@/components/auth-provider";
import type { Permission } from "@/lib/permissions";

/**
 * 动作级权限控制（dynamic-menu §8 第三道体验控制）：
 * 查看页面不等于能创建/编辑/审批/下载，按钮/动作用独立权限判断。
 * 无权限时渲染 fallback（默认不渲染）；服务端仍独立执行同一用例授权。
 */
export function PermissionGate({
  permission,
  children,
  fallback = null,
}: {
  permission: Permission;
  children: ReactNode;
  fallback?: ReactNode;
}) {
  const { user } = useAuth();
  if (!user?.permissions.includes(permission)) {
    return <>{fallback}</>;
  }
  return <>{children}</>;
}
