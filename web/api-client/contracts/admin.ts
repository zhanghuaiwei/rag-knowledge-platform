import type {
  ApiKey,
  AuditLog,
  AuditLogListParams,
  CreateApiKeyInput,
  CreateUserInput,
  CreateWebhookInput,
  Org,
  OrgInput,
  PageParams,
  PageResult,
  ResetPasswordInput,
  TenantRole,
  User,
  Webhook,
} from "@/api-client/types";

/** 管理中心契约（F2.6 / F2.12 / 4.15.1；V0.5 新增建号/角色/移出/重置）。 */
export interface AdminApi {
  listUsers(params?: PageParams): Promise<PageResult<User>>;
  /** V0.5：管理员创建本地用户（初始密码首登强制改密）。 */
  createUser(input: CreateUserInput): Promise<User>;
  disableUser(id: number): Promise<User>;
  enableUser(id: number): Promise<User>;
  updateUserOrg(userId: number, orgId: number | null): Promise<User>;
  /** V0.5：覆盖式替换租户角色（整体替换）。 */
  setRoles(userId: number, roles: TenantRole[]): Promise<User>;
  /** V0.5：移出当前租户（全局身份保留）。 */
  removeUser(userId: number): Promise<void>;
  /** V0.5：管理员重置密码（置 mustChangePassword 首登改密）。 */
  resetPassword(userId: number, input: ResetPasswordInput): Promise<void>;
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
