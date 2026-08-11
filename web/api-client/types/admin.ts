/** 管理中心域类型：成员 / 组织 / 审计 / API Key / Webhook。 */
import type { PageParams } from "@/api-client/types/common";

export interface User {
  id: number;
  name: string;
  email: string;
  status: "ACTIVE" | "DISABLED";
  role: string;
  orgName: string;
  lastLoginAt: string;
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
