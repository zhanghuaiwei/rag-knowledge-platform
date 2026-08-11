import type { ReactNode } from "react";

import { RouteGuard } from "@/components/route-guard";

/** 管理中心路由门禁：仅管理员角色可访问（体验层，服务端策略兜底）。 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return <RouteGuard mode="admin">{children}</RouteGuard>;
}
