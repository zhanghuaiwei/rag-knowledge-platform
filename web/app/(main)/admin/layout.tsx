import type { ReactNode } from "react";

import { RouteGuard } from "@/components/route-guard";
import { ADMIN_PERMISSIONS } from "@/lib/permissions";

/** 管理中心路由门禁：满足任一管理权限才可访问（体验层，服务端策略兜底）。 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return <RouteGuard requiredAny={ADMIN_PERMISSIONS}>{children}</RouteGuard>;
}
