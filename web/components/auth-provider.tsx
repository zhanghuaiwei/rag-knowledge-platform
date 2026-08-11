"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

import { api } from "@/api-client";
import type { CurrentUser } from "@/api-client";
import { clearSession } from "@/lib/auth";

/**
 * 单一认证上下文（dynamic-menu §6）：AppShell、RouteGuard、AuthGate、PermissionGate
 * 统一读取同一份用户/权限快照，避免各自重复请求 getCurrentUser。
 */
interface AuthContextValue {
  user: CurrentUser | null;
  loading: boolean;
  /** 重新拉取当前会话（403/策略版本变化时使用）。 */
  refresh: () => Promise<void>;
  /** 切换激活租户：后端校验成员关系并重签 token，成功后原子替换上下文并返回新用户。 */
  switchTenant: (tenantId: number) => Promise<CurrentUser>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api
      .getCurrentUser()
      .then((current) => {
        if (!cancelled) setUser(current);
      })
      .catch(() => {
        // 未登录/会话过期：清本地 token，由 AuthGate 引导回登录页
        if (!cancelled) clearSession();
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const refresh = useCallback(async () => {
    const current = await api.getCurrentUser();
    setUser(current);
  }, []);

  const switchTenant = useCallback(async (tenantId: number): Promise<CurrentUser> => {
    const current = await api.switchTenant(tenantId);
    // 原子替换上下文；旧租户缓存由 AppShell 以 key=tenantId 整体重挂载清理
    setUser(current);
    return current;
  }, []);

  const logout = useCallback(async () => {
    await api.logout();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, refresh, switchTenant, logout }),
    [user, loading, refresh, switchTenant, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth 必须在 <AuthProvider> 内使用");
  }
  return context;
}
