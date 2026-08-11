/**
 * 管理中心域真实 HTTP transport（对齐 OpenAPI api-keys / audit / webhooks / orgs
 * + 产品契约新增 users / deliveries 端点）。
 */
import type { AdminApi } from "@/api-client/contracts/admin";
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
import { request, requestVoid } from "@/api-client/http/client";

export const adminApi: AdminApi = {
  async listUsers(params: PageParams = {}) {
    return request<PageResult<User>>({
      method: "GET",
      url: "/users",
      params: { page: params.page ?? 1, size: params.size ?? 20 },
    });
  },

  async disableUser(id: number) {
    return request<User>({ method: "POST", url: `/users/${id}/disable` });
  },

  async enableUser(id: number) {
    return request<User>({ method: "POST", url: `/users/${id}/enable` });
  },

  async updateUserOrg(userId: number, orgId: number | null) {
    return request<User>({
      method: "PATCH",
      url: `/users/${userId}/org`,
      data: { orgId },
    });
  },

  async listOrgs() {
    return request<Org[]>({ method: "GET", url: "/orgs" });
  },

  async createOrg(input: OrgInput) {
    return request<Org>({ method: "POST", url: "/orgs", data: input });
  },

  async updateOrg(id: number, name: string) {
    return request<Org>({ method: "PATCH", url: `/orgs/${id}`, data: { name } });
  },

  async deleteOrg(id: number) {
    await requestVoid({ method: "DELETE", url: `/orgs/${id}` });
  },

  async listAuditLogs(params: AuditLogListParams = {}) {
    return request<PageResult<AuditLog>>({
      method: "GET",
      url: "/audit-logs",
      params: {
        page: params.page ?? 1,
        size: params.size ?? 20,
        result: params.result,
      },
    });
  },

  async listApiKeys() {
    return request<ApiKey[]>({ method: "GET", url: "/api-keys" });
  },

  async createApiKey(input: CreateApiKeyInput) {
    const data = await request<{ key: ApiKey; secret: string }>({
      method: "POST",
      url: "/api-keys",
      data: {
        name: input.name,
        scopes: input.scopes,
        allowedKbIds: input.kbId != null ? [input.kbId] : undefined,
        expiresAt: input.expireDays != null ? new Date(Date.now() + input.expireDays * 86_400_000).toISOString() : undefined,
      },
    });
    return { key: data.key, secret: data.secret };
  },

  async revokeApiKey(id: number) {
    await requestVoid({ method: "DELETE", url: `/api-keys/${id}` });
  },

  async listWebhooks() {
    return request<Webhook[]>({ method: "GET", url: "/webhook-subscriptions" });
  },

  async createWebhook(input: CreateWebhookInput) {
    return request<Webhook>({ method: "POST", url: "/webhook-subscriptions", data: input });
  },

  async toggleWebhook(id: number, paused: boolean) {
    return request<Webhook>({
      method: "PATCH",
      url: `/webhook-subscriptions/${id}`,
      data: { paused },
    });
  },
};
