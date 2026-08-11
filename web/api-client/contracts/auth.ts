import type { CurrentUser } from "@/api-client/types";

/** 认证域契约。 */
export interface AuthApi {
  getCurrentUser(): Promise<CurrentUser>;
}
