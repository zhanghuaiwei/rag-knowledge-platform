/** 管理中心域类型：成员 / 组织 / 审计 / API Key / Webhook。 */
import type { PageParams } from "@/api-client/types/common";

/** 租户角色（对齐后端 tenant_member_role.role CHECK，V0.5 用户体系）。 */
export type TenantRole =
  | "TENANT_ADMIN"
  | "SECURITY_ADMIN"
  | "KNOWLEDGE_ADMIN"
  | "AUDITOR"
  | "MEMBER";

export interface User {
  id: number;
  name: string;
  email: string;
  status: "ACTIVE" | "DISABLED";
  /** V0.5：多角色（原单 role 字段废弃）。 */
  roles: TenantRole[];
  /** 首登/被重置后须改密。 */
  mustChangePassword: boolean;
  orgName: string;
  lastLoginAt: string | null;
}

export interface CreateUserInput {
  username: string;
  email: string;
  displayName: string;
  password: string;
  roles: TenantRole[];
}

export interface RoleSetInput {
  roles: TenantRole[];
}

export interface ResetPasswordInput {
  newPassword: string;
}

export interface Org {
  id: number;
  parentId: number | null;
  name: string;
  path: string;
  memberCount: number;
  status: "ACTIVE" | "DISABLED";
}

export interface OrgInput {
  name: string;
  parentId: number | null;
}

export interface AuditLog {
  id: number;
  actor: string;
  actorType: "USER" | "API_KEY" | "SERVICE" | "SYSTEM";
  action: string;
  resourceType: string;
  resourceId: string;
  result: "SUCCEEDED" | "DENIED" | "FAILED";
  reasonCode: string | null;
  requestId: string;
  occurredAt: string;
}

export interface AuditLogListParams extends PageParams {
  result?: AuditLog["result"];
}

export interface ApiKey {
  id: number;
  name: string;
  keyPrefix: string;
  scopes: string[];
  kbIds: number[];
  status: "ACTIVE" | "REVOKED" | "EXPIRED";
  expiresAt: string | null;
  lastUsedAt: string | null;
  createdAt: string;
}

export interface CreateApiKeyInput {
  name: string;
  scopes: string[];
  kbId?: number;
  expireDays?: number;
}

export interface Webhook {
  id: number;
  name: string;
  targetUrl: string;
  eventTypes: string[];
  status: "ACTIVE" | "PAUSED";
  createdAt: string;
}

export interface CreateWebhookInput {
  name: string;
  targetUrl: string;
  eventTypes: string[];
}
