import type {
  ApiKey,
  AuditLog,
  AuditLogListParams,
  CreateApiKeyInput,
  CreateWebhookInput,
  Org,
  OrgInput,
  PageParams,
  PageResult,
  User,
  Webhook,
} from "@/api-client/types";

/** 管理中心契约（F2.6 / F2.12 / 4.15.1）。 */
export interface AdminApi {
  listUsers(params?: PageParams): Promise<PageResult<User>>;
  disableUser(id: number): Promise<User>;
  enableUser(id: number): Promise<User>;
  updateUserOrg(userId: number, orgId: number | null): Promise<User>;
  listOrgs(): Promise<Org[]>;
  createOrg(input: OrgInput): Promise<Org>;
  updateOrg(id: number, name: string): Promise<Org>;
  deleteOrg(id: number): Promise<void>;
  listAuditLogs(params?: AuditLogListParams): Promise<PageResult<AuditLog>>;
  listApiKeys(): Promise<ApiKey[]>;
  createApiKey(input: CreateApiKeyInput): Promise<{ key: ApiKey; secret: string }>;
  revokeApiKey(id: number): Promise<void>;
  listWebhooks(): Promise<Webhook[]>;
  createWebhook(input: CreateWebhookInput): Promise<Webhook>;
  toggleWebhook(id: number, paused: boolean): Promise<Webhook>;
}
